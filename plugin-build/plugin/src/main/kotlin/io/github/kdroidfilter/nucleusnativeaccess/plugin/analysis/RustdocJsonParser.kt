package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.*

/**
 * Parses a rustdoc JSON file (produced by `cargo rustdoc --output-format json`)
 * and builds a [KneModule] suitable for FFM proxy generation.
 *
 * Only public items with no unresolved generics are extracted.
 */
class RustdocJsonParser {

    /** Set during [parse]; used by [resolveType] to build fqNames for struct/enum references. */
    private var currentCrateName: String = ""
    /** Set during [parse]; enum IDs that have data variants (→ SEALED_ENUM, not ENUM). */
    private var currentSealedEnumIds: Set<Int> = emptySet()

    private fun JsonElement?.safeString(): String? {
        if (this == null || this.isJsonNull) return null
        return this.asString
    }

    /**
     * Result of resolving a rustdoc JSON type: the mapped [KneType] plus whether
     * the original type was wrapped in a `borrowed_ref`.
     */
    private data class ResolvedType(val type: KneType, val isBorrowed: Boolean = false)

    fun parse(json: String, libName: String): KneModule {
        val root = JsonParser.parseString(json).asJsonObject
        val index = root.getAsJsonObject("index")
        val rootModuleId = root.get("root").asInt

        // Derive crate name from the root module
        val rootModule = index.get(rootModuleId.toString())?.asJsonObject
        val crateName = rootModule?.get("name").safeString() ?: libName
        currentCrateName = crateName

        // Collect known types first (for type resolution)
        val knownStructs = mutableMapOf<Int, String>() // id → simpleName
        val knownEnums = mutableMapOf<Int, String>()    // id → simpleName
        val knownTraits = mutableMapOf<Int, String>()   // id → simpleName

        for ((id, item) in index.entrySet()) {
            val inner = item.asJsonObject.getAsJsonObject("inner") ?: continue
            val nameElem = item.asJsonObject.get("name")
            if (nameElem == null || nameElem.isJsonNull) continue
            val name = nameElem.asString
            val visElem = item.asJsonObject.get("visibility")
            if (visElem == null || visElem.isJsonNull) continue
            val vis = visElem.asString
            if (vis != "public") continue
            when {
                inner.has("struct") -> knownStructs[id.toInt()] = name
                inner.has("enum") -> knownEnums[id.toInt()] = name
                inner.has("trait") -> knownTraits[id.toInt()] = name
            }
        }

        // Pre-classify enums: separate sealed (has data variants) from simple (all plain)
        val sealedEnumIds = mutableSetOf<Int>()
        for ((id, _) in knownEnums) {
            val enumItem = index.get(id.toString())?.asJsonObject ?: continue
            val innerEnum = enumItem.getAsJsonObject("inner")?.getAsJsonObject("enum") ?: continue
            val varIds = innerEnum.getAsJsonArray("variants") ?: continue
            for (vId in varIds) {
                val variantItem = index.get(vId.asInt.toString())?.asJsonObject ?: continue
                val variantInner = variantItem.getAsJsonObject("inner") ?: continue
                if (variantInner.has("variant")) {
                    val kind = variantInner.getAsJsonObject("variant").get("kind")
                    if (kind != null && kind.isJsonObject) {
                        sealedEnumIds.add(id)
                        break
                    }
                }
            }
        }
        currentSealedEnumIds = sealedEnumIds

        // Collect inherent impl blocks and map struct id → method items
        data class MethodEntry(val item: JsonObject, val isMutating: Boolean, val docs: String?, val isOverride: Boolean = false)
        val implMethods = mutableMapOf<Int, MutableList<MethodEntry>>() // struct id → method entries
        val implConstructors = mutableMapOf<Int, JsonObject?>()        // struct id → new() fn
        val structTraitImpls = mutableMapOf<Int, MutableList<String>>() // struct id → trait names

        for ((_, item) in index.entrySet()) {
            val inner = item.asJsonObject.getAsJsonObject("inner") ?: continue
            if (!inner.has("impl")) continue
            val implObj = inner.getAsJsonObject("impl")

            // Rustdoc JSON format 56 uses "trait" (not "trait_")
            // Inherent impls have "trait": null; trait impls have "trait": { "path": "TraitName", ... }
            val traitField = implObj.get("trait")
            val isTraitImpl = traitField != null && !traitField.isJsonNull && traitField.isJsonObject

            // Get the type this impl is for
            val forType = implObj.getAsJsonObject("for") ?: continue
            val structId = resolveTypeId(forType) ?: continue

            if (isTraitImpl) {
                // Only collect methods for known user-defined traits
                val traitName = traitField.asJsonObject.get("path")?.asString ?: continue
                if (!knownTraits.values.contains(traitName)) continue
                structTraitImpls.getOrPut(structId) { mutableListOf() }.add(traitName)
                // Collect trait impl methods (note: visibility is "default" in trait impls, not "public")
                val items = implObj.getAsJsonArray("items") ?: continue
                for (methodIdElem in items) {
                    val methodId = methodIdElem.asInt
                    val methodItem = index.get(methodId.toString())?.asJsonObject ?: continue
                    val methodInner = methodItem.getAsJsonObject("inner") ?: continue
                    if (!methodInner.has("function")) continue
                    val sig = methodInner.getAsJsonObject("function").getAsJsonObject("sig")
                    val inputs = sig.getAsJsonArray("inputs")
                    if (hasSelfParam(inputs)) {
                        val isMutating = isSelfMutable(inputs)
                        val docs = methodItem.get("docs").safeString()
                        implMethods.getOrPut(structId) { mutableListOf() }.add(MethodEntry(methodItem, isMutating, docs, isOverride = true))
                    }
                }
            } else {
                // Inherent impl: collect public methods and constructors
                val items = implObj.getAsJsonArray("items") ?: continue
                for (methodIdElem in items) {
                    val methodId = methodIdElem.asInt
                    val methodItem = index.get(methodId.toString())?.asJsonObject ?: continue
                    val methodInner = methodItem.getAsJsonObject("inner") ?: continue
                    if (!methodInner.has("function")) continue
                    val methodVis = methodItem.get("visibility").safeString() ?: continue
                    if (methodVis != "public") continue
                    val methodName = methodItem.get("name").safeString() ?: continue
                    val sig = methodInner.getAsJsonObject("function").getAsJsonObject("sig")
                    val inputs = sig.getAsJsonArray("inputs")
                    // Check if this is a constructor (fn new(...) -> Self, no &self)
                    if (methodName == "new" && !hasSelfParam(inputs)) {
                        implConstructors[structId] = methodItem
                    } else if (hasSelfParam(inputs)) {
                        val isMutating = isSelfMutable(inputs)
                        val docs = methodItem.get("docs").safeString()
                        implMethods.getOrPut(structId) { mutableListOf() }.add(MethodEntry(methodItem, isMutating, docs))
                    }
                }
            }
        }

        // Detect data class candidates: all public fields, no public methods (beyond new())
        val knownDataClasses = mutableMapOf<Int, KneDataClass>()
        for ((id, name) in knownStructs) {
            val hasMethods = (implMethods[id]?.isNotEmpty() == true)
            if (hasMethods) continue // Structs with methods are regular classes

            val structItem = index.get(id.toString())?.asJsonObject ?: continue
            val fields = extractStructFields(structItem, index, knownStructs, knownEnums)
            if (fields == null || fields.isEmpty()) continue // No public fields or not a plain struct

            val fqName = "$crateName.$name"
            knownDataClasses[id] = KneDataClass(
                simpleName = name,
                fqName = fqName,
                fields = fields,
            )
        }

        // Build KneClasses (excluding data class structs)
        val classes = mutableListOf<KneClass>()
        for ((id, name) in knownStructs) {
            if (knownDataClasses.containsKey(id)) continue

            val structItem = index.get(id.toString())?.asJsonObject ?: continue

            // Build constructor from "new" function or from struct fields
            val constructor = buildConstructor(implConstructors[id], structItem, index, knownStructs, knownEnums)

            // Build methods (passing isMutating from the self param)
            val allMethods = (implMethods[id] ?: emptyList()).mapNotNull { entry ->
                buildMethod(entry.item, knownStructs, knownEnums, knownDataClasses, entry.isMutating, entry.docs)?.let {
                    if (entry.isOverride) it.copy(isOverride = true) else it
                }
            }

            // Extract properties from get_/set_ patterns
            val (methods, properties) = extractProperties(allMethods)

            val fqName = "$crateName.$name"
            val traitNames = structTraitImpls[id]?.map { "$crateName.$it" } ?: emptyList()
            classes.add(
                KneClass(
                    simpleName = name,
                    fqName = fqName,
                    constructor = constructor,
                    methods = methods,
                    properties = properties,
                    interfaces = traitNames,
                )
            )
        }

        // Build KneEnums and KneSealedEnums
        val enums = mutableListOf<KneEnum>()
        val sealedEnums = mutableListOf<KneSealedEnum>()
        for ((id, name) in knownEnums) {
            val enumItem = index.get(id.toString())?.asJsonObject ?: continue
            val inner = enumItem.getAsJsonObject("inner").getAsJsonObject("enum")
            val variantIds = inner.getAsJsonArray("variants") ?: continue

            if (id in sealedEnumIds) {
                // Build as sealed enum
                val variants = mutableListOf<KneSealedVariant>()
                for (vId in variantIds) {
                    val variantItem = index.get(vId.asInt.toString())?.asJsonObject ?: continue
                    val variantName = variantItem.get("name").safeString() ?: continue
                    val variantInner = variantItem.getAsJsonObject("inner") ?: continue
                    if (!variantInner.has("variant")) continue
                    val variantData = variantInner.getAsJsonObject("variant")
                    val kind = variantData.get("kind")
                    val parsed = parseVariantFields(kind, index, knownStructs, knownEnums)
                    if (parsed != null) {
                        val (fields, isTuple) = parsed
                        variants.add(KneSealedVariant(variantName, fields, isTuple))
                    }
                }
                sealedEnums.add(
                    KneSealedEnum(
                        simpleName = name,
                        fqName = "$crateName.$name",
                        variants = variants,
                    )
                )
            } else {
                // Build as simple enum (all variants are fieldless)
                val entries = mutableListOf<String>()
                for (vId in variantIds) {
                    val variantItem = index.get(vId.asInt.toString())?.asJsonObject ?: continue
                    val variantName = variantItem.get("name").safeString() ?: continue
                    entries.add(variantName)
                }
                enums.add(
                    KneEnum(
                        simpleName = name,
                        fqName = "$crateName.$name",
                        entries = entries,
                    )
                )
            }
        }

        // Build top-level functions (functions in the root module, not inside impl blocks)
        val rootItems = rootModule?.getAsJsonObject("inner")
            ?.getAsJsonObject("module")
            ?.getAsJsonArray("items") ?: com.google.gson.JsonArray()
        val topLevelFunctions = mutableListOf<KneFunction>()
        for (itemId in rootItems) {
            val item = index.get(itemId.asInt.toString())?.asJsonObject ?: continue
            val inner = item.getAsJsonObject("inner") ?: continue
            if (!inner.has("function")) continue
            val vis = item.get("visibility").safeString() ?: continue
            if (vis != "public") continue
            val name = item.get("name").safeString() ?: continue
            // Skip generated bridge functions (from previous runs included via build.rs)
            if (name.startsWith("${libName}_") || name.startsWith("kne_")) continue
            val sig = inner.getAsJsonObject("function").getAsJsonObject("sig")
            val inputs = sig.getAsJsonArray("inputs")
            // Skip if it has a self param (should be in an impl block)
            if (hasSelfParam(inputs)) continue
            // Skip if it has unresolved generics
            val generics = inner.getAsJsonObject("function").getAsJsonObject("generics")
            if (hasUnresolvedGenerics(generics)) continue

            val params = buildParams(inputs, knownStructs, knownEnums, knownDataClasses)
            val returnType = resolveTypeWithBorrow(sig.get("output"), knownStructs, knownEnums, knownDataClasses)?.type ?: KneType.UNIT
            val fnDocs = item.get("docs").safeString()
            val isSuspend = fnDocs?.contains("@kne:suspend") == true

            // Detect @kne:flow(Type) annotation
            val fnFlowMatch = fnDocs?.let { Regex("@kne:flow\\((\\w+)\\)").find(it) }
            val actualReturnType = if (fnFlowMatch != null) {
                val elemTypeName = fnFlowMatch.groupValues[1]
                val elemType = when (elemTypeName) {
                    "Int" -> KneType.INT
                    "Long" -> KneType.LONG
                    "Double" -> KneType.DOUBLE
                    "Float" -> KneType.FLOAT
                    "Boolean" -> KneType.BOOLEAN
                    "String" -> KneType.STRING
                    "Byte" -> KneType.BYTE
                    "Short" -> KneType.SHORT
                    else -> KneType.INT
                }
                KneType.FLOW(elemType)
            } else {
                returnType
            }

            topLevelFunctions.add(
                KneFunction(
                    name = name,
                    params = params,
                    returnType = actualReturnType,
                    isSuspend = isSuspend,
                )
            )
        }

        // Build KneInterfaces from known traits
        val interfaces = mutableListOf<KneInterface>()
        for ((id, traitName) in knownTraits) {
            val traitItem = index.get(id.toString())?.asJsonObject ?: continue
            val traitInner = traitItem.getAsJsonObject("inner")?.getAsJsonObject("trait") ?: continue
            val traitItemIds = traitInner.getAsJsonArray("items") ?: continue
            val traitMethods = traitItemIds.mapNotNull { mid ->
                val methodItem = index.get(mid.asInt.toString())?.asJsonObject ?: return@mapNotNull null
                val methodInner = methodItem.getAsJsonObject("inner") ?: return@mapNotNull null
                if (!methodInner.has("function")) return@mapNotNull null
                val sig = methodInner.getAsJsonObject("function").getAsJsonObject("sig")
                val inputs = sig.getAsJsonArray("inputs")
                val mutating = isSelfMutable(inputs)
                buildMethod(methodItem, knownStructs, knownEnums, knownDataClasses, isMutating = mutating)
            }
            interfaces.add(
                KneInterface(
                    simpleName = traitName,
                    fqName = "$crateName.$traitName",
                    methods = traitMethods,
                    properties = emptyList(),
                )
            )
        }

        // Derive package from crate name
        val pkg = crateName.replace('-', '.').replace('_', '.')

        return KneModule(
            libName = libName,
            packages = setOf(pkg),
            classes = classes,
            interfaces = interfaces,
            dataClasses = knownDataClasses.values.toList(),
            enums = enums,
            sealedEnums = sealedEnums,
            functions = topLevelFunctions,
        )
    }

    /**
     * Parses variant fields from a rustdoc JSON variant `kind` value.
     * Returns (fields, isTuple) or null if unparseable.
     * - "plain" → (empty, false) (unit variant)
     * - { "tuple": [fieldId, ...] } → (positional fields, true)
     * - { "struct": { "fields": [fieldId, ...] } } → (named fields, false)
     */
    private fun parseVariantFields(
        kind: JsonElement?,
        index: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
    ): Pair<List<KneParam>, Boolean>? {
        if (kind == null || kind.isJsonNull) return emptyList<KneParam>() to false
        if (kind.isJsonPrimitive && kind.asString == "plain") return emptyList<KneParam>() to false
        if (!kind.isJsonObject) return null
        val kindObj = kind.asJsonObject

        if (kindObj.has("tuple")) {
            val fieldIds = kindObj.getAsJsonArray("tuple") ?: return null
            val fields = mutableListOf<KneParam>()
            for ((i, fid) in fieldIds.withIndex()) {
                val fieldItem = index.get(fid.asInt.toString())?.asJsonObject ?: return null
                val fieldTypeJson = fieldItem.getAsJsonObject("inner")?.getAsJsonObject("struct_field") ?: return null
                val fieldType = resolveType(fieldTypeJson, knownStructs, knownEnums) ?: return null
                // Single-field tuple: name it "value"; multi-field: "value0", "value1", ...
                val fieldName = if (fieldIds.size() == 1) "value" else "value$i"
                fields.add(KneParam(fieldName, fieldType))
            }
            return fields to true
        }

        if (kindObj.has("struct")) {
            val structObj = kindObj.getAsJsonObject("struct")
            val fieldIds = structObj.getAsJsonArray("fields") ?: return null
            val fields = mutableListOf<KneParam>()
            for (fid in fieldIds) {
                val fieldItem = index.get(fid.asInt.toString())?.asJsonObject ?: return null
                val fieldName = fieldItem.get("name").safeString() ?: return null
                val fieldTypeJson = fieldItem.getAsJsonObject("inner")?.getAsJsonObject("struct_field") ?: return null
                val fieldType = resolveType(fieldTypeJson, knownStructs, knownEnums) ?: return null
                fields.add(KneParam(fieldName, fieldType))
            }
            return fields to false
        }

        return null
    }

    private fun resolveTypeId(typeObj: JsonObject): Int? {
        if (typeObj.has("resolved_path")) {
            val idElem = typeObj.getAsJsonObject("resolved_path").get("id")
            if (idElem == null || idElem.isJsonNull) return null
            return idElem.asInt
        }
        return null
    }

    private fun hasSelfParam(inputs: com.google.gson.JsonArray): Boolean {
        if (inputs.size() == 0) return false
        val firstParam = inputs[0].asJsonArray
        val paramName = firstParam[0].asString
        return paramName == "self"
    }

    /**
     * Checks if the self param is `&mut self` by inspecting `borrowed_ref.is_mutable`.
     */
    private fun isSelfMutable(inputs: com.google.gson.JsonArray): Boolean {
        if (inputs.size() == 0) return false
        val firstParam = inputs[0].asJsonArray
        if (firstParam[0].asString != "self") return false
        val typeObj = firstParam[1].asJsonObject
        if (!typeObj.has("borrowed_ref")) return false
        val ref = typeObj.getAsJsonObject("borrowed_ref")
        val isMutable = ref.get("is_mutable")
        return isMutable != null && !isMutable.isJsonNull && isMutable.asBoolean
    }

    private fun hasUnresolvedGenerics(generics: JsonObject): Boolean {
        val params = generics.getAsJsonArray("params") ?: return false
        return params.size() > 0
    }

    /**
     * Extracts public fields from a plain struct. Returns null if not all fields are public
     * or the struct is not a plain struct.
     */
    private fun extractStructFields(
        structItem: JsonObject,
        index: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
    ): List<KneParam>? {
        val structData = structItem.getAsJsonObject("inner").getAsJsonObject("struct")
        val kind = structData.getAsJsonObject("kind") ?: return null
        if (!kind.has("plain")) return null
        val fieldIds = kind.getAsJsonObject("plain").getAsJsonArray("fields") ?: return null
        val params = mutableListOf<KneParam>()
        for (fieldId in fieldIds) {
            val fieldItem = index.get(fieldId.asInt.toString())?.asJsonObject ?: return null
            val fieldVis = fieldItem.get("visibility").safeString() ?: return null
            if (fieldVis != "public") return null // All fields must be public
            val fieldName = fieldItem.get("name").safeString() ?: return null
            val fieldType = fieldItem.getAsJsonObject("inner")
                ?.getAsJsonObject("struct_field")
            val resolvedType = if (fieldType != null) {
                resolveType(fieldType, knownStructs, knownEnums)
            } else return null
            if (resolvedType != null) {
                params.add(KneParam(fieldName, resolvedType))
            } else return null
        }
        return params
    }

    private fun buildConstructor(
        newFn: JsonObject?,
        structItem: JsonObject,
        index: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
    ): KneConstructor {
        if (newFn != null) {
            val sig = newFn.getAsJsonObject("inner")
                .getAsJsonObject("function")
                .getAsJsonObject("sig")
            val inputs = sig.getAsJsonArray("inputs")
            val params = buildParams(inputs, knownStructs, knownEnums)
            return KneConstructor(params)
        }
        // Fallback: build constructor from struct fields
        val structData = structItem.getAsJsonObject("inner").getAsJsonObject("struct")
        val kind = structData.getAsJsonObject("kind")
        if (kind != null && kind.has("plain")) {
            val fieldIds = kind.getAsJsonObject("plain").getAsJsonArray("fields")
            val params = mutableListOf<KneParam>()
            for (fieldId in fieldIds) {
                val fieldItem = index.get(fieldId.asInt.toString())?.asJsonObject ?: continue
                val fieldVis = fieldItem.get("visibility").safeString() ?: continue
                if (fieldVis != "public") continue
                val fieldName = fieldItem.get("name").safeString() ?: continue
                val fieldType = fieldItem.getAsJsonObject("inner")
                    ?.getAsJsonObject("struct_field")
                val resolvedType = if (fieldType != null) {
                    resolveType(fieldType, knownStructs, knownEnums)
                } else null
                if (resolvedType != null) {
                    params.add(KneParam(fieldName, resolvedType))
                }
            }
            return KneConstructor(params)
        }
        return KneConstructor(emptyList())
    }

    private fun buildMethod(
        methodItem: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        isMutating: Boolean = false,
        docs: String? = null,
    ): KneFunction? {
        val name = methodItem.get("name").safeString() ?: return null
        val inner = methodItem.getAsJsonObject("inner").getAsJsonObject("function")
        // Skip if has unresolved generics
        if (hasUnresolvedGenerics(inner.getAsJsonObject("generics"))) return null

        val sig = inner.getAsJsonObject("sig")
        val inputs = sig.getAsJsonArray("inputs")
        // Skip the &self/&mut self param
        val params = buildParams(inputs, knownStructs, knownEnums, knownDataClasses, skipSelf = true)
        val returnType = resolveTypeWithBorrow(sig.get("output"), knownStructs, knownEnums, knownDataClasses)?.type ?: KneType.UNIT

        // Detect @kne:suspend annotation in rustdoc comments
        val isSuspend = docs?.contains("@kne:suspend") == true

        // Detect @kne:flow(Type) annotation in rustdoc comments
        val flowMatch = docs?.let { Regex("@kne:flow\\((\\w+)\\)").find(it) }
        val actualReturnType = if (flowMatch != null) {
            val elemTypeName = flowMatch.groupValues[1]
            val elemType = when (elemTypeName) {
                "Int" -> KneType.INT
                "Long" -> KneType.LONG
                "Double" -> KneType.DOUBLE
                "Float" -> KneType.FLOAT
                "Boolean" -> KneType.BOOLEAN
                "String" -> KneType.STRING
                "Byte" -> KneType.BYTE
                "Short" -> KneType.SHORT
                else -> KneType.INT
            }
            KneType.FLOW(elemType)
        } else {
            returnType
        }

        return KneFunction(
            name = name,
            params = params,
            returnType = actualReturnType,
            isMutating = isMutating,
            isSuspend = isSuspend,
        )
    }

    private fun buildParams(
        inputs: com.google.gson.JsonArray,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        skipSelf: Boolean = false,
    ): List<KneParam> {
        val params = mutableListOf<KneParam>()
        for (input in inputs) {
            val arr = input.asJsonArray
            val paramName = arr[0].asString
            if (skipSelf && paramName == "self") continue
            val paramTypeJson = arr[1]
            val resolved = resolveTypeWithBorrow(paramTypeJson, knownStructs, knownEnums, knownDataClasses) ?: continue
            params.add(KneParam(paramName, resolved.type, isBorrowed = resolved.isBorrowed))
        }
        return params
    }

    /**
     * Top-level type resolution that tracks whether the original type was a `borrowed_ref`.
     * This is the entry point; internal recursion uses [resolveType].
     */
    private fun resolveTypeWithBorrow(
        typeJson: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
    ): ResolvedType? {
        if (typeJson == null || typeJson.isJsonNull) return null
        val obj = typeJson.asJsonObject

        if (obj.has("borrowed_ref")) {
            val ref = obj.getAsJsonObject("borrowed_ref")
            val innerType = ref.getAsJsonObject("type")
            val resolved = resolveType(innerType, knownStructs, knownEnums, knownDataClasses) ?: return null
            return ResolvedType(resolved, isBorrowed = true)
        }

        val resolved = resolveType(obj, knownStructs, knownEnums, knownDataClasses) ?: return null
        return ResolvedType(resolved, isBorrowed = false)
    }

    /**
     * Resolves a rustdoc JSON type to a [KneType].
     * Returns null if the type is not mappable.
     * Does NOT track borrow status — use [resolveTypeWithBorrow] for that.
     */
    private fun resolveType(
        typeJson: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
    ): KneType? {
        if (typeJson == null || typeJson.isJsonNull) return null
        val obj = typeJson.asJsonObject

        // Primitive types
        if (obj.has("primitive")) {
            return when (obj.get("primitive").asString) {
                "i32", "u32" -> KneType.INT
                "i64", "u64" -> KneType.LONG
                "f64" -> KneType.DOUBLE
                "f32" -> KneType.FLOAT
                "bool" -> KneType.BOOLEAN
                "i8", "u8" -> KneType.BYTE
                "i16", "u16" -> KneType.SHORT
                "str" -> KneType.STRING
                "usize", "isize" -> KneType.LONG
                else -> null
            }
        }

        // Borrowed reference (&T, &mut T) — when reached via internal recursion
        if (obj.has("borrowed_ref")) {
            val ref = obj.getAsJsonObject("borrowed_ref")
            val innerType = ref.getAsJsonObject("type")
            if (innerType.has("primitive") && innerType.get("primitive").asString == "str") {
                return KneType.STRING
            }
            return resolveType(innerType, knownStructs, knownEnums, knownDataClasses)
        }

        // Resolved path (named types: String, Vec, Option, HashMap, user structs/enums)
        if (obj.has("resolved_path")) {
            val rp = obj.getAsJsonObject("resolved_path")
            val path = rp.get("path").asString
            val id = rp.get("id")?.asInt
            val args = rp.get("args")

            return when (path) {
                "String" -> KneType.STRING

                "Vec" -> {
                    val elemType = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses)
                        ?: return null
                    if (elemType == KneType.BYTE) KneType.BYTE_ARRAY
                    else KneType.LIST(elemType)
                }

                "Option" -> {
                    val innerType = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses)
                        ?: return null
                    KneType.NULLABLE(innerType)
                }

                "HashSet", "BTreeSet" -> {
                    val elemType = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses)
                        ?: return null
                    KneType.SET(elemType)
                }

                "HashMap", "BTreeMap" -> {
                    val (keyType, valType) = extractTwoGenericArgs(args, knownStructs, knownEnums, knownDataClasses)
                        ?: return null
                    KneType.MAP(keyType, valType)
                }

                else -> {
                    // Check if it's a known data class, enum, or regular struct
                    if (id != null && knownEnums.containsKey(id)) {
                        val name = knownEnums[id]!!
                        if (id in currentSealedEnumIds) {
                            KneType.SEALED_ENUM("$currentCrateName.$name", name)
                        } else {
                            KneType.ENUM("$currentCrateName.$name", name)
                        }
                    } else if (id != null && knownDataClasses.containsKey(id)) {
                        val dc = knownDataClasses[id]!!
                        KneType.DATA_CLASS(dc.fqName, dc.simpleName, dc.fields)
                    } else if (id != null && knownStructs.containsKey(id)) {
                        val name = knownStructs[id]!!
                        KneType.OBJECT("$currentCrateName.$name", name)
                    } else {
                        null
                    }
                }
            }
        }

        // Slice (&[T]) — typically reached via borrowed_ref → slice
        if (obj.has("slice")) {
            val elemType = resolveType(obj.getAsJsonObject("slice"), knownStructs, knownEnums) ?: return null
            return if (elemType == KneType.BYTE) KneType.BYTE_ARRAY else KneType.LIST(elemType)
        }

        // Tuple (empty tuple = unit)
        if (obj.has("tuple")) {
            val elems = obj.getAsJsonArray("tuple")
            if (elems.size() == 0) return KneType.UNIT
            return null // Non-empty tuples not supported in v1
        }

        // Function pointer: fn(T) -> R
        if (obj.has("function_pointer")) {
            val fp = obj.getAsJsonObject("function_pointer")
            val sig = fp.getAsJsonObject("sig")
            val inputs = sig.getAsJsonArray("inputs")
            val paramTypes = mutableListOf<KneType>()
            for (input in inputs) {
                val arr = input.asJsonArray
                val paramType = resolveType(arr[1], knownStructs, knownEnums, knownDataClasses) ?: return null
                paramTypes.add(paramType)
            }
            val output = sig.get("output")
            val returnType = if (output == null || output.isJsonNull) KneType.UNIT
                else resolveType(output.asJsonObject, knownStructs, knownEnums, knownDataClasses) ?: return null
            return KneType.FUNCTION(paramTypes, returnType)
        }

        // dyn Trait (for &dyn Fn(T) -> R, reached via borrowed_ref recursion)
        if (obj.has("dyn_trait")) {
            val traits = obj.getAsJsonObject("dyn_trait").getAsJsonArray("traits") ?: return null
            for (traitEntry in traits) {
                val traitObj = traitEntry.asJsonObject.getAsJsonObject("trait") ?: continue
                val path = traitObj.get("path")?.asString ?: continue
                if (path !in listOf("Fn", "FnMut", "FnOnce")) continue
                val args = traitObj.getAsJsonObject("args") ?: continue
                if (!args.has("parenthesized")) continue
                val paren = args.getAsJsonObject("parenthesized")
                val inputs = paren.getAsJsonArray("inputs") ?: return null
                val paramTypes = inputs.mapNotNull { resolveType(it, knownStructs, knownEnums, knownDataClasses) }
                if (paramTypes.size != inputs.size()) return null
                val output = paren.get("output")
                val returnType = if (output == null || output.isJsonNull) KneType.UNIT
                    else resolveType(output.asJsonObject, knownStructs, knownEnums, knownDataClasses) ?: return null
                return KneType.FUNCTION(paramTypes, returnType)
            }
            return null
        }

        // Generic "Self" → skip (handled by caller context)
        if (obj.has("generic")) {
            return null
        }

        return null
    }

    private fun extractFirstGenericArg(
        args: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
    ): KneType? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() == 0) return null
        val firstArg = argsList[0].asJsonObject
        if (firstArg.has("type")) {
            return resolveType(firstArg.getAsJsonObject("type"), knownStructs, knownEnums, knownDataClasses)
        }
        return null
    }

    private fun extractTwoGenericArgs(
        args: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
    ): Pair<KneType, KneType>? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() < 2) return null
        val first = argsList[0].asJsonObject
        val second = argsList[1].asJsonObject
        val keyType = if (first.has("type")) resolveType(first.getAsJsonObject("type"), knownStructs, knownEnums, knownDataClasses) else null
        val valType = if (second.has("type")) resolveType(second.getAsJsonObject("type"), knownStructs, knownEnums, knownDataClasses) else null
        if (keyType == null || valType == null) return null
        return keyType to valType
    }

    /**
     * Detects get_/set_ accessor patterns among [methods] and converts them to [KneProperty] entries.
     *
     * Rules:
     * - `get_X()` with no params and a simple return type (primitives, STRING, BOOLEAN, ENUM)
     *   becomes a read-only property `X`.
     * - If a matching `set_X(value: T)` exists (1 param, same type), the property becomes mutable.
     * - OBJECT and LIST return types are skipped (too complex for now).
     * - Matched get_/set_ methods are removed from the returned methods list.
     */
    private fun extractProperties(methods: List<KneFunction>): Pair<List<KneFunction>, List<KneProperty>> {
        val getters = mutableMapOf<String, KneFunction>() // propName → getter fn
        val setters = mutableMapOf<String, KneFunction>() // propName → setter fn

        for (fn in methods) {
            if (fn.name.startsWith("get_") && fn.params.isEmpty()) {
                val propName = fn.name.removePrefix("get_")
                // Only simple types become properties
                if (isSimplePropertyType(fn.returnType)) {
                    getters[propName] = fn
                }
            } else if (fn.name.startsWith("set_") && fn.params.size == 1 && fn.returnType == KneType.UNIT) {
                val propName = fn.name.removePrefix("set_")
                setters[propName] = fn
            }
        }

        val properties = mutableListOf<KneProperty>()
        val consumedMethods = mutableSetOf<String>() // fn names consumed as properties

        for ((propName, getter) in getters) {
            val setter = setters[propName]
            val mutable = setter != null && setter.params[0].type == getter.returnType
            properties.add(KneProperty(propName, getter.returnType, mutable))
            consumedMethods.add(getter.name)
            if (mutable) consumedMethods.add(setter!!.name)
        }

        val remainingMethods = methods.filter { it.name !in consumedMethods }
        return remainingMethods to properties
    }

    /** Returns true for types that are simple enough to expose as properties. */
    private fun isSimplePropertyType(type: KneType): Boolean = when (type) {
        KneType.INT, KneType.LONG, KneType.DOUBLE, KneType.FLOAT,
        KneType.BOOLEAN, KneType.BYTE, KneType.SHORT, KneType.STRING -> true
        is KneType.ENUM -> true
        else -> false
    }
}
