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

    private fun JsonElement?.safeString(): String? {
        if (this == null || this.isJsonNull) return null
        return this.asString
    }

    fun parse(json: String, libName: String): KneModule {
        val root = JsonParser.parseString(json).asJsonObject
        val index = root.getAsJsonObject("index")
        val rootModuleId = root.get("root").asInt

        // Collect known types first (for type resolution)
        val knownStructs = mutableMapOf<Int, String>() // id → simpleName
        val knownEnums = mutableMapOf<Int, String>()    // id → simpleName

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
            }
        }

        // Collect inherent impl blocks (trait_ == null) and map struct id → method items
        val implMethods = mutableMapOf<Int, MutableList<JsonObject>>() // struct id → methods
        val implConstructors = mutableMapOf<Int, JsonObject?>()        // struct id → new() fn

        for ((_, item) in index.entrySet()) {
            val inner = item.asJsonObject.getAsJsonObject("inner") ?: continue
            if (!inner.has("impl")) continue
            val implObj = inner.getAsJsonObject("impl")
            // Skip trait impls
            if (implObj.get("trait_") != null && !implObj.get("trait_").isJsonNull) continue
            // Get the type this impl is for
            val forType = implObj.getAsJsonObject("for") ?: continue
            val structId = resolveTypeId(forType) ?: continue
            // Collect method items
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
                    implMethods.getOrPut(structId) { mutableListOf() }.add(methodItem)
                }
                // Static methods (no self, not "new") → could be companion, skip for now
            }
        }

        // Build KneClasses
        val classes = mutableListOf<KneClass>()
        for ((id, name) in knownStructs) {
            val structItem = index.get(id.toString())?.asJsonObject ?: continue

            // Build constructor from "new" function or from struct fields
            val constructor = buildConstructor(implConstructors[id], structItem, index, knownStructs, knownEnums)

            // Build methods
            val methods = (implMethods[id] ?: emptyList()).mapNotNull { methodItem ->
                buildMethod(methodItem, knownStructs, knownEnums)
            }

            val fqName = "kne_test_calculator.$name"
            classes.add(
                KneClass(
                    simpleName = name,
                    fqName = fqName,
                    constructor = constructor,
                    methods = methods,
                    properties = emptyList(),
                )
            )
        }

        // Build KneEnums
        val enums = mutableListOf<KneEnum>()
        for ((id, name) in knownEnums) {
            val enumItem = index.get(id.toString())?.asJsonObject ?: continue
            val inner = enumItem.getAsJsonObject("inner").getAsJsonObject("enum")
            val variantIds = inner.getAsJsonArray("variants") ?: continue
            val entries = mutableListOf<String>()
            for (vId in variantIds) {
                val variantItem = index.get(vId.asInt.toString())?.asJsonObject ?: continue
                val variantName = variantItem.get("name").safeString() ?: continue
                // Only include fieldless variants (v1)
                val variantInner = variantItem.getAsJsonObject("inner")
                if (variantInner.has("variant")) {
                    val variantData = variantInner.getAsJsonObject("variant")
                    val kind = variantData.get("kind")
                    // "plain" means no fields
                    if (kind != null && kind.isJsonPrimitive && kind.asString == "plain") {
                        entries.add(variantName)
                    } else if (kind != null && kind.isJsonObject) {
                        // Skip variants with data (tuple or struct)
                        continue
                    } else {
                        entries.add(variantName)
                    }
                } else {
                    entries.add(variantName)
                }
            }
            enums.add(
                KneEnum(
                    simpleName = name,
                    fqName = "kne_test_calculator.$name",
                    entries = entries,
                )
            )
        }

        // Build top-level functions (functions in the root module, not inside impl blocks)
        val rootModule = index.get(rootModuleId.toString())?.asJsonObject
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
            val sig = inner.getAsJsonObject("function").getAsJsonObject("sig")
            val inputs = sig.getAsJsonArray("inputs")
            // Skip if it has a self param (should be in an impl block)
            if (hasSelfParam(inputs)) continue
            // Skip if it has unresolved generics
            val generics = inner.getAsJsonObject("function").getAsJsonObject("generics")
            if (hasUnresolvedGenerics(generics)) continue

            val params = buildParams(inputs, knownStructs, knownEnums)
            val returnType = resolveType(sig.get("output"), knownStructs, knownEnums) ?: KneType.UNIT
            topLevelFunctions.add(
                KneFunction(
                    name = name,
                    params = params,
                    returnType = returnType,
                )
            )
        }

        // Derive package from crate name
        val crateName = rootModule?.get("name").safeString() ?: libName
        val pkg = crateName.replace('-', '.').replace('_', '.')

        return KneModule(
            libName = libName,
            packages = setOf(pkg),
            classes = classes,
            dataClasses = emptyList(),
            enums = enums,
            functions = topLevelFunctions,
        )
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

    private fun hasUnresolvedGenerics(generics: JsonObject): Boolean {
        val params = generics.getAsJsonArray("params") ?: return false
        return params.size() > 0
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
    ): KneFunction? {
        val name = methodItem.get("name").safeString() ?: return null
        val inner = methodItem.getAsJsonObject("inner").getAsJsonObject("function")
        // Skip if has unresolved generics
        if (hasUnresolvedGenerics(inner.getAsJsonObject("generics"))) return null

        val sig = inner.getAsJsonObject("sig")
        val inputs = sig.getAsJsonArray("inputs")
        // Skip the &self/&mut self param
        val params = buildParams(inputs, knownStructs, knownEnums, skipSelf = true)
        val returnType = resolveType(sig.get("output"), knownStructs, knownEnums) ?: KneType.UNIT

        return KneFunction(
            name = name,
            params = params,
            returnType = returnType,
        )
    }

    private fun buildParams(
        inputs: com.google.gson.JsonArray,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        skipSelf: Boolean = false,
    ): List<KneParam> {
        val params = mutableListOf<KneParam>()
        for (input in inputs) {
            val arr = input.asJsonArray
            val paramName = arr[0].asString
            if (skipSelf && paramName == "self") continue
            val paramTypeJson = arr[1]
            val type = resolveType(paramTypeJson, knownStructs, knownEnums) ?: continue
            params.add(KneParam(paramName, type))
        }
        return params
    }

    /**
     * Resolves a rustdoc JSON type to a [KneType].
     * Returns null if the type is not mappable.
     */
    private fun resolveType(
        typeJson: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
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

        // Borrowed reference (&T, &mut T)
        if (obj.has("borrowed_ref")) {
            val ref = obj.getAsJsonObject("borrowed_ref")
            val innerType = ref.getAsJsonObject("type")
            // &str → STRING
            if (innerType.has("primitive") && innerType.get("primitive").asString == "str") {
                return KneType.STRING
            }
            // &T where T is a known struct → OBJECT
            return resolveType(innerType, knownStructs, knownEnums)
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
                    val elemType = extractFirstGenericArg(args, knownStructs, knownEnums)
                        ?: return null
                    if (elemType == KneType.BYTE) KneType.BYTE_ARRAY
                    else KneType.LIST(elemType)
                }

                "Option" -> {
                    val innerType = extractFirstGenericArg(args, knownStructs, knownEnums)
                        ?: return null
                    KneType.NULLABLE(innerType)
                }

                "HashSet", "BTreeSet" -> {
                    val elemType = extractFirstGenericArg(args, knownStructs, knownEnums)
                        ?: return null
                    KneType.SET(elemType)
                }

                "HashMap", "BTreeMap" -> {
                    val (keyType, valType) = extractTwoGenericArgs(args, knownStructs, knownEnums)
                        ?: return null
                    KneType.MAP(keyType, valType)
                }

                else -> {
                    // Check if it's a known struct or enum
                    if (id != null && knownEnums.containsKey(id)) {
                        val name = knownEnums[id]!!
                        KneType.ENUM("kne_test_calculator.$name", name)
                    } else if (id != null && knownStructs.containsKey(id)) {
                        val name = knownStructs[id]!!
                        KneType.OBJECT("kne_test_calculator.$name", name)
                    } else {
                        null
                    }
                }
            }
        }

        // Tuple (empty tuple = unit)
        if (obj.has("tuple")) {
            val elems = obj.getAsJsonArray("tuple")
            if (elems.size() == 0) return KneType.UNIT
            return null // Non-empty tuples not supported in v1
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
    ): KneType? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() == 0) return null
        val firstArg = argsList[0].asJsonObject
        if (firstArg.has("type")) {
            return resolveType(firstArg.getAsJsonObject("type"), knownStructs, knownEnums)
        }
        return null
    }

    private fun extractTwoGenericArgs(
        args: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
    ): Pair<KneType, KneType>? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() < 2) return null
        val first = argsList[0].asJsonObject
        val second = argsList[1].asJsonObject
        val keyType = if (first.has("type")) resolveType(first.getAsJsonObject("type"), knownStructs, knownEnums) else null
        val valType = if (second.has("type")) resolveType(second.getAsJsonObject("type"), knownStructs, knownEnums) else null
        if (keyType == null || valType == null) return null
        return keyType to valType
    }
}
