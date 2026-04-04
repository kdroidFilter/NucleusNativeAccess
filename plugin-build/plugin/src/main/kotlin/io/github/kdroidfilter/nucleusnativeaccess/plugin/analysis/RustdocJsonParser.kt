package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.*

/**
 * Parses a rustdoc JSON file (produced by `cargo rustdoc --output-format json`)
 * and builds a [KneModule] suitable for FFM proxy generation.
 *
 * The parser accepts a subset of generics when they can be lowered to stable bridgeable
 * Kotlin types, for example `AsRef<str>`, `AsRef<Path>`, `Into<T>` and `Fn(...)`.
 */
class RustdocJsonParser {

    /** Set during [parse]; used by [resolveType] to build fqNames for struct/enum references. */
    private var currentCrateName: String = ""

    /** Set during [parse]; enum IDs that have data variants (→ SEALED_ENUM, not ENUM). */
    private var currentSealedEnumIds: Set<Int> = emptySet()

    /** Opaque external/current-crate types referenced by supported signatures. */
    private var encounteredOpaqueClasses: LinkedHashMap<String, KneClass> = linkedMapOf()

    /** Public type names exported by the parsed crate root. Used to avoid Kotlin name collisions. */
    private var reservedTopLevelTypeNames: Set<String> = emptySet()

    /** Known trait IDs → names, set during [parse] for `dyn Trait` resolution. */
    private var currentKnownTraits: Map<Int, String> = emptyMap()

    /** Trait names that appear as `dyn Trait` in function signatures (need synthetic wrapper classes). */
    private val dynTraitNames = mutableSetOf<String>()

    private fun JsonElement?.safeString(): String? {
        if (this == null || this.isJsonNull) return null
        if (!this.isJsonPrimitive) return null
        return this.asString
    }

    /**
     * Result of resolving a rustdoc JSON type: the mapped [KneType], whether the original type was
     * borrowed, and a best-effort Rust type hint used by Rust bridge generation for casts.
     */
    private data class ResolvedType(
        val type: KneType,
        val isBorrowed: Boolean = false,
        val rustType: String? = null,
        /** Rust expression suffix to apply in bridge code for `impl Trait` return types (e.g. `.collect::<Vec<_>>()`). */
        val implTraitConversion: String? = null,
    )

    fun parse(
        json: String,
        libName: String,
        onUnsupported: (String) -> Unit = {},
    ): KneModule {
        encounteredOpaqueClasses = linkedMapOf()
        reservedTopLevelTypeNames = emptySet()
        dynTraitNames.clear()

        val root = JsonParser.parseString(json).asJsonObject
        val index = root.getAsJsonObject("index")
        val rootModuleId = root.get("root").asInt

        val rootModule = index.get(rootModuleId.toString())?.asJsonObject
        val crateName = rootModule?.get("name").safeString() ?: libName
        currentCrateName = crateName
        reservedTopLevelTypeNames = rootModule
            ?.getAsJsonObject("inner")
            ?.getAsJsonObject("module")
            ?.getAsJsonArray("items")
            ?.mapNotNull { itemId ->
                index.get(itemId.asInt.toString())?.asJsonObject?.get("name").safeString()
            }
            ?.toSet()
            ?: emptySet()

        val knownStructs = mutableMapOf<Int, String>()
        val knownEnums = mutableMapOf<Int, String>()
        val knownTraits = mutableMapOf<Int, String>()

        // Build the set of type IDs that are directly accessible as bare names after
        // `pub use crate_name::*`. We traverse the root module's exported items:
        // - direct struct/enum/trait definitions in the root → their own ID
        // - explicit `pub use X` re-exports (non-glob) → the target ID
        // - glob `pub use mod::*` re-exports → all public struct/enum/trait IDs in that module
        // This correctly excludes types like `sysinfo::windows::sid::Sid` that are `pub` within
        // their module but only `pub(crate)` re-exported, and therefore not in scope after
        // `pub use sysinfo::*`.
        val rootModuleItems = rootModule
            ?.getAsJsonObject("inner")
            ?.getAsJsonObject("module")
            ?.getAsJsonArray("items")
        val rootExportedIds: Set<Int> = if (rootModuleItems != null)
            buildRootExportedIds(rootModuleItems, index)
        else
            emptySet()

        for ((id, item) in index.entrySet()) {
            val intId = id.toIntOrNull() ?: continue
            if (rootExportedIds.isNotEmpty() && intId !in rootExportedIds) continue
            val inner = item.asJsonObject.getAsJsonObject("inner") ?: continue
            val name = item.asJsonObject.get("name").safeString() ?: continue
            val vis = item.asJsonObject.get("visibility").safeString() ?: continue
            if (vis != "public") continue
            when {
                inner.has("struct") -> knownStructs[intId] = name
                inner.has("enum") -> knownEnums[intId] = name
                inner.has("trait") -> knownTraits[intId] = name
            }
        }
        currentKnownTraits = knownTraits

        val sealedEnumIds = mutableSetOf<Int>()
        for ((id, _) in knownEnums) {
            val enumItem = index.get(id.toString())?.asJsonObject ?: continue
            val innerEnum = enumItem.getAsJsonObject("inner")?.getAsJsonObject("enum") ?: continue
            val variantIds = innerEnum.getAsJsonArray("variants") ?: continue
            for (variantId in variantIds) {
                val variantItem = index.get(variantId.asInt.toString())?.asJsonObject ?: continue
                val variantInner = variantItem.getAsJsonObject("inner") ?: continue
                if (!variantInner.has("variant")) continue
                val kind = variantInner.getAsJsonObject("variant").get("kind")
                if (kind != null && kind.isJsonObject) {
                    sealedEnumIds.add(id)
                    break
                }
            }
        }
        currentSealedEnumIds = sealedEnumIds

        data class MethodEntry(
            val item: JsonObject,
            val receiverKind: KneReceiverKind,
            val docs: String?,
            val isOverride: Boolean = false,
        )

        val implMethods = mutableMapOf<Int, MutableList<MethodEntry>>()
        val implConstructors = mutableMapOf<Int, JsonObject?>()
        val implCompanionMethods = mutableMapOf<Int, MutableList<JsonObject>>()
        val structTraitImpls = mutableMapOf<Int, MutableList<String>>()

        for ((_, item) in index.entrySet()) {
            val inner = item.asJsonObject.getAsJsonObject("inner") ?: continue
            if (!inner.has("impl")) continue
            val implObj = inner.getAsJsonObject("impl")
            val traitField = implObj.get("trait")
            val isTraitImpl = traitField != null && !traitField.isJsonNull && traitField.isJsonObject
            val forType = implObj.getAsJsonObject("for") ?: continue
            val typeId = resolveTypeId(forType) ?: continue
            if (!knownStructs.containsKey(typeId)) continue

            if (isTraitImpl) {
                val traitName = traitField.asJsonObject.get("path")?.asString ?: continue
                if (!knownTraits.values.contains(traitName)) continue
                structTraitImpls.getOrPut(typeId) { mutableListOf() }.add(traitName)
                val items = implObj.getAsJsonArray("items") ?: continue
                for (methodIdElem in items) {
                    val methodItem = index.get(methodIdElem.asInt.toString())?.asJsonObject ?: continue
                    val methodInner = methodItem.getAsJsonObject("inner") ?: continue
                    if (!methodInner.has("function")) continue
                    val sig = methodInner.getAsJsonObject("function").getAsJsonObject("sig")
                    val inputs = sig.getAsJsonArray("inputs")
                    if (!hasSelfParam(inputs)) continue
                    implMethods.getOrPut(typeId) { mutableListOf() }.add(
                        MethodEntry(
                            item = methodItem,
                            receiverKind = classifyReceiverKind(inputs),
                            docs = methodItem.get("docs").safeString(),
                            isOverride = true,
                        )
                    )
                }
            } else {
                val selfType = knownStructs[typeId]?.let { name -> KneType.OBJECT("$crateName.$name", name) }
                val items = implObj.getAsJsonArray("items") ?: continue
                for (methodIdElem in items) {
                    val methodItem = index.get(methodIdElem.asInt.toString())?.asJsonObject ?: continue
                    val methodInner = methodItem.getAsJsonObject("inner") ?: continue
                    if (!methodInner.has("function")) continue
                    val methodVis = methodItem.get("visibility").safeString() ?: continue
                    if (methodVis != "public") continue
                    val methodName = methodItem.get("name").safeString() ?: continue
                    val fn = methodInner.getAsJsonObject("function")
                    val sig = fn.getAsJsonObject("sig")
                    val inputs = sig.getAsJsonArray("inputs")
                    val genericTypes = resolveGenericMappings(fn.getAsJsonObject("generics"), knownStructs, knownEnums, emptyMap(), selfType)
                    if (hasUnsupportedGenerics(fn.getAsJsonObject("generics"), genericTypes)) {
                        onUnsupported("Skipped constructor '${methodName}' for ${typeDisplayName(selfType)}: unsupported generic signature")
                        continue
                    }
                    val returnType = resolveTypeWithBorrow(sig.get("output"), knownStructs, knownEnums, emptyMap(), genericTypes, selfType)
                    val isConstructor = methodName == "new" && !hasSelfParam(inputs) && returnType?.type == selfType

                    when {
                        isConstructor -> implConstructors[typeId] = methodItem
                        hasSelfParam(inputs) -> implMethods.getOrPut(typeId) { mutableListOf() }.add(
                            MethodEntry(
                                item = methodItem,
                                receiverKind = classifyReceiverKind(inputs),
                                docs = methodItem.get("docs").safeString(),
                            )
                        )
                        else -> implCompanionMethods.getOrPut(typeId) { mutableListOf() }.add(methodItem)
                    }
                }
            }
        }

        val knownDataClasses = mutableMapOf<Int, KneDataClass>()
        for ((id, name) in knownStructs) {
            val hasMethods = implMethods[id]?.isNotEmpty() == true || implCompanionMethods[id]?.isNotEmpty() == true
            if (hasMethods) continue

            val structItem = index.get(id.toString())?.asJsonObject ?: continue
            val fields = extractStructFields(structItem, index, knownStructs, knownEnums)
            if (fields == null || fields.isEmpty()) continue
            if (!fields.all { isDataClassFieldSupported(it.type) }) continue

            knownDataClasses[id] = KneDataClass(
                simpleName = name,
                fqName = "$crateName.$name",
                fields = fields,
            )
        }

        val classes = mutableListOf<KneClass>()
        for ((id, name) in knownStructs) {
            if (knownDataClasses.containsKey(id)) continue
            val structItem = index.get(id.toString())?.asJsonObject ?: continue
            val selfType = KneType.OBJECT("$crateName.$name", name)
            val constructor = buildConstructor(
                newFn = implConstructors[id],
                structItem = structItem,
                index = index,
                knownStructs = knownStructs,
                knownEnums = knownEnums,
                knownDataClasses = knownDataClasses,
                selfType = selfType,
                onUnsupported = { onUnsupported("Class '$name': $it") },
            )

            val allMethods = (implMethods[id] ?: emptyList()).mapNotNull { entry ->
                buildMethod(
                    methodItem = entry.item,
                    knownStructs = knownStructs,
                    knownEnums = knownEnums,
                    knownDataClasses = knownDataClasses,
                    receiverKind = entry.receiverKind,
                    docs = entry.docs,
                    ownerType = selfType,
                    onUnsupported = { onUnsupported("Class '${name}': $it") },
                )?.let { if (entry.isOverride) it.copy(isOverride = true) else it }
            }

            val companionMethods = (implCompanionMethods[id] ?: emptyList()).mapNotNull { methodItem ->
                buildMethod(
                    methodItem = methodItem,
                    knownStructs = knownStructs,
                    knownEnums = knownEnums,
                    knownDataClasses = knownDataClasses,
                    receiverKind = KneReceiverKind.NONE,
                    docs = methodItem.get("docs").safeString(),
                    ownerType = selfType,
                    onUnsupported = { onUnsupported("Class '${name}': $it") },
                )
            }

            val (methods, properties) = extractProperties(allMethods)
            val traitNames = structTraitImpls[id]?.map { "$crateName.$it" } ?: emptyList()
            classes.add(
                KneClass(
                    simpleName = name,
                    fqName = "$crateName.$name",
                    constructor = constructor,
                    methods = methods,
                    properties = properties,
                    companionMethods = companionMethods,
                    interfaces = traitNames,
                )
            )
        }

        val enums = mutableListOf<KneEnum>()
        val sealedEnums = mutableListOf<KneSealedEnum>()
        for ((id, name) in knownEnums) {
            val enumItem = index.get(id.toString())?.asJsonObject ?: continue
            val inner = enumItem.getAsJsonObject("inner")?.getAsJsonObject("enum") ?: continue
            val variantIds = inner.getAsJsonArray("variants") ?: continue

            if (id in sealedEnumIds) {
                val variants = mutableListOf<KneSealedVariant>()
                for (variantId in variantIds) {
                    val variantItem = index.get(variantId.asInt.toString())?.asJsonObject ?: continue
                    val variantName = variantItem.get("name").safeString() ?: continue
                    val variantInner = variantItem.getAsJsonObject("inner") ?: continue
                    if (!variantInner.has("variant")) continue
                    val variantData = variantInner.getAsJsonObject("variant")
                    val parsed = parseVariantFields(
                        kind = variantData.get("kind"),
                        index = index,
                        knownStructs = knownStructs,
                        knownEnums = knownEnums,
                        knownDataClasses = knownDataClasses,
                        context = "${name}::${variantName}",
                        onUnsupported = onUnsupported,
                    ) ?: continue
                    variants.add(KneSealedVariant(variantName, parsed.first, parsed.second))
                }
                sealedEnums.add(
                    KneSealedEnum(
                        simpleName = name,
                        fqName = "$crateName.$name",
                        variants = variants,
                    )
                )
            } else {
                val entries = mutableListOf<String>()
                for (variantId in variantIds) {
                    val variantItem = index.get(variantId.asInt.toString())?.asJsonObject ?: continue
                    val variantName = variantItem.get("name").safeString() ?: continue
                    entries.add(variantName)
                }
                enums.add(KneEnum(simpleName = name, fqName = "$crateName.$name", entries = entries))
            }
        }

        val rootItems = rootModule?.getAsJsonObject("inner")
            ?.getAsJsonObject("module")
            ?.getAsJsonArray("items") ?: JsonArray()
        val topLevelFunctions = mutableListOf<KneFunction>()
        for (itemId in rootItems) {
            val item = index.get(itemId.asInt.toString())?.asJsonObject ?: continue
            val inner = item.getAsJsonObject("inner") ?: continue
            if (!inner.has("function")) continue
            if (isGeneratedBridgeFunction(item)) continue
            val vis = item.get("visibility").safeString() ?: continue
            if (vis != "public") continue
            val name = item.get("name").safeString() ?: continue
            if (name.startsWith("${libName}_") || name.startsWith("kne_")) continue
            val sig = inner.getAsJsonObject("function").getAsJsonObject("sig")
            if (hasSelfParam(sig.getAsJsonArray("inputs"))) continue
            buildMethod(
                methodItem = item,
                knownStructs = knownStructs,
                knownEnums = knownEnums,
                knownDataClasses = knownDataClasses,
                receiverKind = KneReceiverKind.NONE,
                docs = item.get("docs").safeString(),
                ownerType = null,
                onUnsupported = { onUnsupported("Top-level function '$name': $it") },
            )?.let(topLevelFunctions::add)
        }

        val interfaces = mutableListOf<KneInterface>()
        for ((id, traitName) in knownTraits) {
            val traitItem = index.get(id.toString())?.asJsonObject ?: continue
            val traitInner = traitItem.getAsJsonObject("inner")?.getAsJsonObject("trait") ?: continue
            val traitItems = traitInner.getAsJsonArray("items") ?: continue
            val selfType = KneType.INTERFACE("$crateName.$traitName", traitName)
            val traitMethods = traitItems.mapNotNull { methodId ->
                val methodItem = index.get(methodId.asInt.toString())?.asJsonObject ?: return@mapNotNull null
                val methodInner = methodItem.getAsJsonObject("inner") ?: return@mapNotNull null
                if (!methodInner.has("function")) return@mapNotNull null
                val sig = methodInner.getAsJsonObject("function").getAsJsonObject("sig")
                buildMethod(
                    methodItem = methodItem,
                    knownStructs = knownStructs,
                    knownEnums = knownEnums,
                    knownDataClasses = knownDataClasses,
                    receiverKind = if (hasSelfParam(sig.getAsJsonArray("inputs"))) classifyReceiverKind(sig.getAsJsonArray("inputs")) else KneReceiverKind.NONE,
                    ownerType = selfType,
                    onUnsupported = onUnsupported,
                )
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

        val occupiedTopLevelNames = (
            classes.map { it.simpleName } +
                knownDataClasses.values.map { it.simpleName } +
                enums.map { it.simpleName } +
                sealedEnums.map { it.simpleName } +
                interfaces.map { it.simpleName }
            ).toMutableSet()
        val opaqueRenames = mutableMapOf<String, String>()
        val renamedOpaqueClasses = encounteredOpaqueClasses.values.map { opaque ->
            val uniqueName = if (opaque.simpleName in occupiedTopLevelNames) {
                uniqueOpaqueSimpleName(opaque.simpleName, opaque.fqName, occupiedTopLevelNames)
            } else {
                opaque.simpleName
            }
            occupiedTopLevelNames += uniqueName
            opaqueRenames[opaque.fqName] = uniqueName
            opaque.copy(simpleName = uniqueName)
        }

        fun renameType(type: KneType): KneType = when (type) {
            is KneType.OBJECT -> opaqueRenames[type.fqName]?.let { type.copy(simpleName = it) } ?: type
            is KneType.INTERFACE -> opaqueRenames[type.fqName]?.let { type.copy(simpleName = it) } ?: type
            is KneType.SEALED_ENUM -> opaqueRenames[type.fqName]?.let { type.copy(simpleName = it) } ?: type
            is KneType.NULLABLE -> type.copy(inner = renameType(type.inner))
            is KneType.FUNCTION -> type.copy(
                paramTypes = type.paramTypes.map(::renameType),
                returnType = renameType(type.returnType),
            )
            is KneType.DATA_CLASS -> type.copy(fields = type.fields.map { it.copy(type = renameType(it.type)) })
            is KneType.LIST -> type.copy(elementType = renameType(type.elementType))
            is KneType.SET -> type.copy(elementType = renameType(type.elementType))
            is KneType.MAP -> type.copy(keyType = renameType(type.keyType), valueType = renameType(type.valueType))
            is KneType.FLOW -> type.copy(elementType = renameType(type.elementType))
            else -> type
        }

        fun renameParam(param: KneParam): KneParam = param.copy(type = renameType(param.type))
        fun renameProperty(property: KneProperty): KneProperty = property.copy(type = renameType(property.type))
        fun renameFunction(function: KneFunction): KneFunction = function.copy(
            params = function.params.map(::renameParam),
            returnType = renameType(function.returnType),
            receiverType = function.receiverType?.let(::renameType),
        )
        fun renameClass(cls: KneClass): KneClass = cls.copy(
            constructor = cls.constructor.copy(params = cls.constructor.params.map(::renameParam)),
            methods = cls.methods.map(::renameFunction),
            properties = cls.properties.map(::renameProperty),
            companionMethods = cls.companionMethods.map(::renameFunction),
            companionProperties = cls.companionProperties.map(::renameProperty),
        )
        fun renameInterface(iface: KneInterface): KneInterface = iface.copy(
            methods = iface.methods.map(::renameFunction),
            properties = iface.properties.map(::renameProperty),
        )
        fun renameSealedEnum(sealed: KneSealedEnum): KneSealedEnum = sealed.copy(
            variants = sealed.variants.map { variant ->
                variant.copy(fields = variant.fields.map(::renameParam))
            }
        )

        val renamedClasses = classes.map(::renameClass).toMutableList()
        val renamedInterfaces = interfaces.map(::renameInterface)
        val renamedTopLevelFunctions = topLevelFunctions.map(::renameFunction)
        val renamedSealedEnums = sealedEnums.map(::renameSealedEnum)

        val existingFqNames = renamedClasses.map { it.fqName }.toMutableSet()
        for (opaque in renamedOpaqueClasses) {
            if (opaque.fqName !in existingFqNames) {
                existingFqNames.add(opaque.fqName)
                renamedClasses.add(opaque)
            }
        }

        // Create synthetic wrapper classes for each trait used as `dyn Trait`
        for (iface in renamedInterfaces) {
            if (iface.simpleName !in dynTraitNames) continue
            val dynName = "Dyn${iface.simpleName}"
            val dynFqName = "${iface.fqName.substringBeforeLast('.')}.$dynName"
            if (dynFqName in existingFqNames) continue
            existingFqNames.add(dynFqName)
            val dynClass = KneClass(
                simpleName = dynName,
                fqName = dynFqName,
                constructor = KneConstructor(emptyList(), KneConstructorKind.NONE),
                methods = iface.methods.map { m ->
                    m.copy(isOverride = true)
                },
                properties = iface.properties.map { p ->
                    p.copy(isOverride = true)
                },
                interfaces = listOf(iface.fqName),
                isDynTrait = true,
                rustTypeName = "Box<dyn ${iface.simpleName}>",
            )
            renamedClasses.add(dynClass)
        }

        val pkg = crateName.replace('-', '.').replace('_', '.')
        return KneModule(
            libName = libName,
            packages = setOf(pkg),
            classes = renamedClasses,
            interfaces = renamedInterfaces,
            dataClasses = knownDataClasses.values.toList(),
            enums = enums,
            sealedEnums = renamedSealedEnums,
            functions = renamedTopLevelFunctions,
        )
    }

    private fun isGeneratedBridgeFunction(item: JsonObject): Boolean {
        val span = item.getAsJsonObject("span") ?: return false
        val filename = span.get("filename").safeString() ?: return false
        return filename.endsWith("/kne_bridges.rs") || filename.endsWith("\\kne_bridges.rs") || filename == "kne_bridges.rs"
    }

    private fun parseVariantFields(
        kind: JsonElement?,
        index: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        context: String = "unknown variant",
        onUnsupported: (String) -> Unit = {},
    ): Pair<List<KneParam>, Boolean>? {
        if (kind == null || kind.isJsonNull) return emptyList<KneParam>() to false
        if (kind.isJsonPrimitive && kind.asString == "plain") return emptyList<KneParam>() to false
        if (!kind.isJsonObject) {
            onUnsupported("Skipped $context: unsupported variant kind")
            return null
        }
        val kindObj = kind.asJsonObject

        if (kindObj.has("tuple")) {
            val fieldIds = kindObj.getAsJsonArray("tuple") ?: return null
            val fields = mutableListOf<KneParam>()
            for ((i, fieldId) in fieldIds.withIndex()) {
                val fieldItem = index.get(fieldId.asInt.toString())?.asJsonObject ?: return null
                val fieldTypeJson = fieldItem.getAsJsonObject("inner")?.getAsJsonObject("struct_field") ?: return null
                val resolved = resolveTypeWithBorrow(fieldTypeJson, knownStructs, knownEnums, knownDataClasses) ?: return null
                val fieldName = if (fieldIds.size() == 1) "value" else "value$i"
                fields.add(KneParam(fieldName, resolved.type, isBorrowed = resolved.isBorrowed, rustType = resolved.rustType))
            }
            return fields to true
        }

        if (kindObj.has("struct")) {
            val structObj = kindObj.getAsJsonObject("struct")
            val fieldIds = structObj.getAsJsonArray("fields") ?: return null
            val fields = mutableListOf<KneParam>()
            for (fieldId in fieldIds) {
                val fieldItem = index.get(fieldId.asInt.toString())?.asJsonObject ?: return null
                val fieldName = fieldItem.get("name").safeString() ?: return null
                val fieldTypeJson = fieldItem.getAsJsonObject("inner")?.getAsJsonObject("struct_field") ?: return null
                val resolved = resolveTypeWithBorrow(fieldTypeJson, knownStructs, knownEnums, knownDataClasses) ?: return null
                fields.add(KneParam(fieldName, resolved.type, isBorrowed = resolved.isBorrowed, rustType = resolved.rustType))
            }
            return fields to false
        }

        onUnsupported("Skipped $context: unsupported variant structure")
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

    private fun hasSelfParam(inputs: JsonArray): Boolean {
        if (inputs.size() == 0) return false
        val firstParam = inputs[0].asJsonArray
        return firstParam[0].asString == "self"
    }

    private fun classifyReceiverKind(inputs: JsonArray): KneReceiverKind {
        if (!hasSelfParam(inputs)) return KneReceiverKind.NONE
        val typeObj = inputs[0].asJsonArray[1].asJsonObject
        if (!typeObj.has("borrowed_ref")) return KneReceiverKind.OWNED
        val borrowed = typeObj.getAsJsonObject("borrowed_ref")
        val isMutable = borrowed.get("is_mutable")?.takeIf { !it.isJsonNull }?.asBoolean == true
        return if (isMutable) KneReceiverKind.BORROWED_MUT else KneReceiverKind.BORROWED_SHARED
    }

    private fun resolveGenericMappings(
        generics: JsonObject?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        selfType: KneType? = null,
    ): Map<String, ResolvedType> {
        if (generics == null) return emptyMap()
        val resolved = mutableMapOf<String, ResolvedType>()

        val params = generics.getAsJsonArray("params") ?: JsonArray()
        for (param in params) {
            val paramObj = param.asJsonObject
            val name = paramObj.get("name").safeString() ?: continue
            val kind = paramObj.getAsJsonObject("kind") ?: continue
            if (kind.has("lifetime")) continue
            val typeKind = kind.getAsJsonObject("type") ?: continue
            resolveGenericMappingFromBounds(typeKind, knownStructs, knownEnums, knownDataClasses, resolved, selfType)?.let {
                resolved[name] = it
            }
        }

        val wherePredicates = generics.getAsJsonArray("where_predicates") ?: JsonArray()
        for (predicate in wherePredicates) {
            val boundPredicate = predicate.asJsonObject.getAsJsonObject("bound_predicate") ?: continue
            val target = boundPredicate.getAsJsonObject("type") ?: continue
            if (!target.has("generic")) continue
            val name = target.get("generic").asString
            if (name in resolved) continue
            val pseudoTypeKind = JsonObject().apply { add("bounds", boundPredicate.getAsJsonArray("bounds") ?: JsonArray()) }
            resolveGenericMappingFromBounds(pseudoTypeKind, knownStructs, knownEnums, knownDataClasses, resolved, selfType)?.let {
                resolved[name] = it
            }
        }

        return resolved
    }

    private fun resolveGenericMappingFromBounds(
        typeKind: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass>,
        genericTypes: Map<String, ResolvedType>,
        selfType: KneType?,
    ): ResolvedType? {
        val bounds = typeKind.getAsJsonArray("bounds") ?: return null
        for (bound in bounds) {
            val boundObj = bound.asJsonObject
            val traitBound = boundObj.getAsJsonObject("trait_bound") ?: continue
            val trait = traitBound.getAsJsonObject("trait") ?: continue
            val traitPath = lastPathSegment(trait.get("path")?.asString ?: continue)
            val traitArgs = trait.get("args")

            when (traitPath) {
                "Fn", "FnMut", "FnOnce" -> {
                    val parenthesized = traitArgs?.asJsonObject?.getAsJsonObject("parenthesized") ?: continue
                    val inputs = parenthesized.getAsJsonArray("inputs") ?: continue
                    val paramTypes = inputs.mapNotNull {
                        resolveTypeWithBorrow(it, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)?.type
                    }
                    if (paramTypes.size != inputs.size()) return null
                    val outputResolved = resolveTypeWithBorrow(parenthesized.get("output"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                    return ResolvedType(
                        type = KneType.FUNCTION(paramTypes, outputResolved?.type ?: KneType.UNIT),
                        rustType = traitPath,
                    )
                }

                "AsRef", "Into", "From" -> {
                    val target = extractFirstGenericArg(traitArgs, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: continue
                    val targetSegment = lastPathSegment(target.rustType ?: renderRustType(target.type))
                    if (traitPath == "AsRef" && (targetSegment == "str" || targetSegment == "Path" || targetSegment == "PathBuf")) {
                        val rustTarget = if (targetSegment == "Path" || targetSegment == "PathBuf") "Path" else "str"
                        return ResolvedType(type = KneType.STRING, rustType = "AsRef<$rustTarget>")
                    }
                    return target.copy(rustType = "$traitPath<${target.rustType ?: renderRustType(target.type)}>")
                }
            }
        }
        return null
    }

    private fun hasUnsupportedGenerics(generics: JsonObject?, resolvedGenerics: Map<String, ResolvedType>): Boolean {
        if (generics == null) return false
        val params = generics.getAsJsonArray("params") ?: return false
        for (param in params) {
            val paramObj = param.asJsonObject
            val name = paramObj.get("name").safeString() ?: continue
            val kind = paramObj.getAsJsonObject("kind") ?: continue
            when {
                kind.has("lifetime") -> continue
                kind.has("type") -> if (name !in resolvedGenerics) return true
                else -> return true
            }
        }
        return false
    }

    private fun extractStructFields(
        structItem: JsonObject,
        index: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
    ): List<KneParam>? {
        val structData = structItem.getAsJsonObject("inner")?.getAsJsonObject("struct") ?: return null
        val kindElem = structData.get("kind") ?: return null
        if (!kindElem.isJsonObject) return null
        val kind = kindElem.asJsonObject
        if (!kind.has("plain")) return null
        val fieldIds = kind.getAsJsonObject("plain").getAsJsonArray("fields") ?: return null
        val params = mutableListOf<KneParam>()
        for (fieldId in fieldIds) {
            val fieldItem = index.get(fieldId.asInt.toString())?.asJsonObject ?: return null
            val fieldVis = fieldItem.get("visibility").safeString() ?: return null
            if (fieldVis != "public") return null
            val fieldName = fieldItem.get("name").safeString() ?: return null
            val fieldType = fieldItem.getAsJsonObject("inner")?.getAsJsonObject("struct_field") ?: return null
            val resolved = resolveTypeWithBorrow(fieldType, knownStructs, knownEnums) ?: return null
            params.add(KneParam(fieldName, resolved.type, isBorrowed = resolved.isBorrowed, rustType = resolved.rustType))
        }
        return params
    }

    private fun buildConstructor(
        newFn: JsonObject?,
        structItem: JsonObject,
        index: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass>,
        selfType: KneType.OBJECT,
        onUnsupported: (String) -> Unit = {},
    ): KneConstructor {
        if (newFn != null) {
            val function = newFn.getAsJsonObject("inner")?.getAsJsonObject("function") ?: return KneConstructor(emptyList(), KneConstructorKind.NONE)
            val generics = resolveGenericMappings(function.getAsJsonObject("generics"), knownStructs, knownEnums, knownDataClasses, selfType)
            if (hasUnsupportedGenerics(function.getAsJsonObject("generics"), generics)) {
                onUnsupported("Skipped constructor '${selfType.simpleName}::new': unsupported generic signature")
                return KneConstructor(emptyList(), KneConstructorKind.NONE)
            }

            val sig = function.getAsJsonObject("sig")
            val params = buildParams(
                inputs = sig.getAsJsonArray("inputs"),
                knownStructs = knownStructs,
                knownEnums = knownEnums,
                knownDataClasses = knownDataClasses,
                genericTypes = generics,
                selfType = selfType,
                context = "constructor ${selfType.simpleName}",
                onUnsupported = onUnsupported,
            )
            if (params != null) {
                return KneConstructor(params = params, kind = KneConstructorKind.FUNCTION, canFail = isResultType(sig.get("output")))
            }
            onUnsupported("Skipped constructor '${selfType.simpleName}::new': unsupported parameter type")
            return KneConstructor(emptyList(), KneConstructorKind.NONE)
        }

        val structData = structItem.getAsJsonObject("inner")?.getAsJsonObject("struct") ?: return KneConstructor(emptyList(), KneConstructorKind.NONE)
        val kindElem = structData.get("kind") ?: return KneConstructor(emptyList(), KneConstructorKind.NONE)
        if (!kindElem.isJsonObject) return KneConstructor(emptyList(), KneConstructorKind.NONE)
        val kind = kindElem.asJsonObject
        if (!kind.has("plain")) return KneConstructor(emptyList(), KneConstructorKind.NONE)

        val fieldIds = kind.getAsJsonObject("plain").getAsJsonArray("fields") ?: return KneConstructor(emptyList(), KneConstructorKind.NONE)
        val params = mutableListOf<KneParam>()
        for (fieldId in fieldIds) {
            val fieldItem = index.get(fieldId.asInt.toString())?.asJsonObject ?: continue
            val fieldVis = fieldItem.get("visibility").safeString() ?: continue
            if (fieldVis != "public") continue
            val fieldName = fieldItem.get("name").safeString() ?: continue
            val fieldType = fieldItem.getAsJsonObject("inner")?.getAsJsonObject("struct_field") ?: continue
            val resolved = resolveTypeWithBorrow(fieldType, knownStructs, knownEnums, knownDataClasses) ?: continue
            params.add(KneParam(fieldName, resolved.type, isBorrowed = resolved.isBorrowed, rustType = resolved.rustType))
        }
        return if (params.isNotEmpty()) KneConstructor(params, KneConstructorKind.STRUCT_LITERAL) else KneConstructor(emptyList(), KneConstructorKind.NONE)
    }

    private fun buildMethod(
        methodItem: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        receiverKind: KneReceiverKind = KneReceiverKind.NONE,
        docs: String? = null,
        ownerType: KneType? = null,
        onUnsupported: (String) -> Unit = {},
    ): KneFunction? {
        val name = methodItem.get("name").safeString() ?: return null
        val inner = methodItem.getAsJsonObject("inner")?.getAsJsonObject("function") ?: return null
        val genericTypes = resolveGenericMappings(inner.getAsJsonObject("generics"), knownStructs, knownEnums, knownDataClasses, ownerType)
        if (hasUnsupportedGenerics(inner.getAsJsonObject("generics"), genericTypes)) {
            onUnsupported("Skipped method '$name': unsupported generic signature")
            return null
        }

        val sig = inner.getAsJsonObject("sig")
        val inputs = sig.getAsJsonArray("inputs")
        val params = buildParams(
            inputs = inputs,
            knownStructs = knownStructs,
            knownEnums = knownEnums,
            knownDataClasses = knownDataClasses,
            genericTypes = genericTypes,
            selfType = ownerType,
            skipSelf = hasSelfParam(inputs),
            context = "method '$name'",
            onUnsupported = onUnsupported,
        ) ?: run {
            onUnsupported("Skipped method '$name': unsupported parameter type")
            return null
        }
        val returnResolved = resolveReturnTypeOrUnit(
            output = sig.get("output"),
            knownStructs = knownStructs,
            knownEnums = knownEnums,
            knownDataClasses = knownDataClasses,
            genericTypes = genericTypes,
            selfType = ownerType,
        )
        if (returnResolved == null) {
            onUnsupported("Skipped method '$name': unsupported return type")
            return null
        }
        val returnType = returnResolved.type

        val isSuspend = docs?.contains("@kne:suspend") == true
        val flowMatch = docs?.let { Regex("@kne:flow\\((\\w+)\\)").find(it) }
        val actualReturnType = if (flowMatch != null) {
            when (flowMatch.groupValues[1]) {
                "Int" -> KneType.FLOW(KneType.INT)
                "Long" -> KneType.FLOW(KneType.LONG)
                "Double" -> KneType.FLOW(KneType.DOUBLE)
                "Float" -> KneType.FLOW(KneType.FLOAT)
                "Boolean" -> KneType.FLOW(KneType.BOOLEAN)
                "String" -> KneType.FLOW(KneType.STRING)
                "Byte" -> KneType.FLOW(KneType.BYTE)
                "Short" -> KneType.FLOW(KneType.SHORT)
                else -> KneType.FLOW(KneType.INT)
            }
        } else {
            returnType
        }

        return KneFunction(
            name = name,
            params = params,
            returnType = actualReturnType,
            isSuspend = isSuspend,
            isMutating = receiverKind == KneReceiverKind.BORROWED_MUT,
            receiverKind = receiverKind,
            canFail = isResultType(sig.get("output")),
            returnsBorrowed = returnResolved.isBorrowed,
            returnRustType = returnResolved.rustType,
            isUnsafe = inner.getAsJsonObject("header")?.get("is_unsafe")?.asBoolean == true,
            returnConversion = returnResolved.implTraitConversion,
        )
    }

    private fun buildParams(
        inputs: JsonArray,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
        skipSelf: Boolean = false,
        context: String = "unknown item",
        onUnsupported: (String) -> Unit = {},
    ): List<KneParam>? {
        val params = mutableListOf<KneParam>()
        for (input in inputs) {
            val arr = input.asJsonArray
            val paramName = arr[0].asString
            if (skipSelf && paramName == "self") continue
            val resolved = resolveTypeWithBorrow(arr[1], knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
            if (resolved == null) {
                onUnsupported("$context has unsupported param '$paramName'")
                return null
            }
            params.add(
                KneParam(
                    name = paramName,
                    type = resolved.type,
                    isBorrowed = resolved.isBorrowed,
                    rustType = resolved.rustType,
                )
            )
        }
        return params
    }

    private fun resolveTypeWithBorrow(
        typeJson: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): ResolvedType? {
        if (typeJson == null || typeJson.isJsonNull) return null
        val obj = typeJson.asJsonObject

        if (obj.has("borrowed_ref")) {
            val ref = obj.getAsJsonObject("borrowed_ref")
            val innerResolved = resolveType(ref.get("type"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
            val lifetime = ref.get("lifetime").safeString()
            val isMutable = ref.get("is_mutable")?.takeIf { !it.isJsonNull }?.asBoolean == true
            val refPrefix = if (isMutable) "&mut " else "&"
            val rustType = innerResolved.rustType?.let {
                if (lifetime != null) "$refPrefix$lifetime $it" else "$refPrefix$it"
            }
            return ResolvedType(type = innerResolved.type, isBorrowed = true, rustType = rustType)
        }

        return resolveType(obj, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
    }

    private fun resolveReturnTypeOrUnit(
        output: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): ResolvedType? {
        if (output == null || output.isJsonNull) {
            return ResolvedType(type = KneType.UNIT, rustType = "()")
        }
        return resolveTypeWithBorrow(output, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
    }

    private fun resolveType(
        typeJson: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): ResolvedType? {
        if (typeJson == null || typeJson.isJsonNull) return null
        val obj = typeJson.asJsonObject

        if (obj.has("primitive")) {
            val primitive = obj.get("primitive").asString
            val type = when (primitive) {
                "i32", "u32" -> KneType.INT
                "i64", "u64", "usize", "isize" -> KneType.LONG
                "f64" -> KneType.DOUBLE
                "f32" -> KneType.FLOAT
                "bool" -> KneType.BOOLEAN
                "i8", "u8" -> KneType.BYTE
                "i16", "u16" -> KneType.SHORT
                "str" -> KneType.STRING
                "!" -> KneType.NEVER
                "never" -> KneType.NEVER
                else -> null
            } ?: return null
            return ResolvedType(type = type, rustType = primitive)
        }

        if (obj.has("borrowed_ref")) {
            return resolveTypeWithBorrow(obj, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
        }

        if (obj.has("resolved_path")) {
            val rp = obj.getAsJsonObject("resolved_path")
            val path = rp.get("path").asString
            val pathSegment = lastPathSegment(path)
            val id = rp.get("id")?.takeIf { !it.isJsonNull }?.asInt
            val args = rp.get("args")

            // Handle Box<dyn Trait> — unwrap Box, resolve inner dyn_trait
            if (pathSegment == "Box") {
                val innerTypeObj = extractFirstGenericArgRaw(args)
                if (innerTypeObj != null && innerTypeObj.has("dyn_trait")) {
                    val inner = resolveType(innerTypeObj, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                    if (inner != null) {
                        return inner.copy(
                            rustType = "Box<${inner.rustType ?: renderRustType(inner.type)}>",
                        )
                    }
                }
                // Non-dyn Box<T>: fall through to normal handling (will be opaque)
            }

            return when (pathSegment) {
                "String" -> ResolvedType(KneType.STRING, rustType = pathSegment)
                "PathBuf", "Path", "OsStr", "OsString" -> ResolvedType(KneType.STRING, rustType = path)

                "Result" -> extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)

                "Vec" -> {
                    val elem = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
                    val rustType = "Vec<${elem.rustType ?: renderRustType(elem.type)}>"
                    if (elem.type == KneType.BYTE) ResolvedType(KneType.BYTE_ARRAY, rustType = rustType)
                    else ResolvedType(KneType.LIST(elem.type), rustType = rustType)
                }

                "Option" -> {
                    val inner = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
                    ResolvedType(KneType.NULLABLE(inner.type), rustType = "Option<${inner.rustType ?: renderRustType(inner.type)}>")
                }

                "HashSet", "BTreeSet" -> {
                    val elem = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
                    ResolvedType(KneType.SET(elem.type), rustType = "$pathSegment<${elem.rustType ?: renderRustType(elem.type)}>")
                }

                "HashMap", "BTreeMap" -> {
                    val (keyType, valueType) = extractTwoGenericArgs(args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                        ?: return null
                    ResolvedType(
                        KneType.MAP(keyType.type, valueType.type),
                        rustType = "$pathSegment<${keyType.rustType ?: renderRustType(keyType.type)}, ${valueType.rustType ?: renderRustType(valueType.type)}>",
                    )
                }

                else -> {
                    when {
                        id != null && knownEnums.containsKey(id) -> {
                            val name = knownEnums[id]!!
                            if (id in currentSealedEnumIds) {
                                ResolvedType(KneType.SEALED_ENUM("$currentCrateName.$name", name), rustType = name)
                            } else {
                                ResolvedType(KneType.ENUM("$currentCrateName.$name", name), rustType = name)
                            }
                        }

                        id != null && knownDataClasses.containsKey(id) -> {
                            val dc = knownDataClasses[id]!!
                            ResolvedType(KneType.DATA_CLASS(dc.fqName, dc.simpleName, dc.fields), rustType = dc.simpleName)
                        }

                        id != null && knownStructs.containsKey(id) -> {
                            val name = knownStructs[id]!!
                            ResolvedType(KneType.OBJECT("$currentCrateName.$name", name), rustType = name)
                        }

                        else -> {
                            val simpleName = pathSegment
                            val fqName = path.replace("::", ".")
                            val rustType = renderResolvedPathType(path, args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                            val opaque = recordOpaqueClass(simpleName, fqName, rustType)
                            ResolvedType(KneType.OBJECT(fqName, opaque.simpleName), rustType = rustType)
                        }
                    }
                }
            }
        }

        if (obj.has("slice")) {
            val elem = resolveType(obj.get("slice"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
            val rustType = "[${elem.rustType ?: renderRustType(elem.type)}]"
            return if (elem.type == KneType.BYTE) ResolvedType(KneType.BYTE_ARRAY, rustType = rustType)
            else ResolvedType(KneType.LIST(elem.type), rustType = rustType)
        }

        if (obj.has("tuple")) {
            val elems = obj.getAsJsonArray("tuple")
            if (elems.size() == 0) return ResolvedType(KneType.UNIT, rustType = "()")
            val elementTypes = mutableListOf<KneType>()
            val rustTypeParts = mutableListOf<String>()
            for (elem in elems) {
                val resolved = resolveType(elem, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
                elementTypes.add(resolved.type)
                rustTypeParts.add(resolved.rustType ?: renderRustType(resolved.type))
            }
            val rustType = rustTypeParts.joinToString(", ", "(", ")")
            return ResolvedType(KneType.TUPLE(elementTypes), rustType = rustType)
        }

        if (obj.has("function_pointer")) {
            val fp = obj.getAsJsonObject("function_pointer")
            val sig = fp.getAsJsonObject("sig")
            val inputs = sig.getAsJsonArray("inputs")
            val paramTypes = mutableListOf<KneType>()
            for (input in inputs) {
                val arr = input.asJsonArray
                val paramType = resolveTypeWithBorrow(arr[1], knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: return null
                paramTypes.add(paramType.type)
            }
            val output = resolveTypeWithBorrow(sig.get("output"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
            return ResolvedType(KneType.FUNCTION(paramTypes, output?.type ?: KneType.UNIT), rustType = "fn")
        }

        if (obj.has("dyn_trait")) {
            val traits = obj.getAsJsonObject("dyn_trait").getAsJsonArray("traits") ?: return null
            for (traitEntry in traits) {
                val traitObj = traitEntry.asJsonObject.getAsJsonObject("trait") ?: continue
                val path = lastPathSegment(traitObj.get("path")?.asString ?: continue)
                if (path !in listOf("Fn", "FnMut", "FnOnce")) continue
                val args = traitObj.getAsJsonObject("args") ?: continue
                val parenthesized = args.getAsJsonObject("parenthesized") ?: continue
                val inputs = parenthesized.getAsJsonArray("inputs") ?: continue
                val paramTypes = inputs.mapNotNull {
                    resolveTypeWithBorrow(it, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)?.type
                }
                if (paramTypes.size != inputs.size()) return null
                val output = resolveTypeWithBorrow(parenthesized.get("output"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                return ResolvedType(KneType.FUNCTION(paramTypes, output?.type ?: KneType.UNIT), rustType = path)
            }
            // Check for known user-defined traits (dyn Trait → INTERFACE)
            for (traitEntry in traits) {
                val traitObj = traitEntry.asJsonObject.getAsJsonObject("trait") ?: continue
                val traitId = traitObj.get("id")?.takeIf { !it.isJsonNull }?.asInt
                val traitPath = traitObj.get("path")?.asString ?: continue
                val traitName = if (traitId != null && currentKnownTraits.containsKey(traitId)) {
                    currentKnownTraits[traitId]!!
                } else {
                    val seg = lastPathSegment(traitPath)
                    if (currentKnownTraits.values.contains(seg)) seg else continue
                }
                dynTraitNames.add(traitName)
                val fqName = "$currentCrateName.$traitName"
                return ResolvedType(KneType.INTERFACE(fqName, traitName), rustType = "dyn $traitName")
            }
            return null
        }

        if (obj.has("impl_trait")) {
            return resolveImplTrait(
                obj.getAsJsonArray("impl_trait"),
                knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
            )
        }

        if (obj.has("generic")) {
            val name = obj.get("generic").asString
            if (name == "Self" && selfType != null) {
                return ResolvedType(selfType, rustType = renderRustType(selfType))
            }
            return genericTypes[name]
        }

        return null
    }

    /**
     * Resolves `impl Trait` return types by mapping well-known traits to concrete KneTypes.
     * The returned [ResolvedType.implTraitConversion] carries the Rust expression suffix
     * the bridge generator must append to materialise the value (e.g. `.collect::<Vec<_>>()`).
     */
    private fun resolveImplTrait(
        bounds: JsonArray,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass>,
        genericTypes: Map<String, ResolvedType>,
        selfType: KneType?,
    ): ResolvedType? {
        for (bound in bounds) {
            val boundObj = bound.asJsonObject
            if (!boundObj.has("trait_bound")) continue
            val traitBound = boundObj.getAsJsonObject("trait_bound")
            val traitObj = traitBound.getAsJsonObject("trait") ?: continue
            val path = traitObj.get("path")?.asString ?: continue
            val traitName = lastPathSegment(path)
            val args = traitObj.get("args")

            when (traitName) {
                "Iterator", "ExactSizeIterator", "DoubleEndedIterator" -> {
                    val itemType = extractAssociatedTypeBinding(
                        args, "Item", knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
                    ) ?: return null
                    val rustType = "Vec<${itemType.rustType ?: renderRustType(itemType.type)}>"
                    val kneType = if (itemType.type == KneType.BYTE) KneType.BYTE_ARRAY else KneType.LIST(itemType.type)
                    return ResolvedType(kneType, rustType = rustType, implTraitConversion = ".collect::<Vec<_>>()")
                }

                "IntoIterator" -> {
                    val itemType = extractAssociatedTypeBinding(
                        args, "Item", knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
                    ) ?: return null
                    val rustType = "Vec<${itemType.rustType ?: renderRustType(itemType.type)}>"
                    val kneType = if (itemType.type == KneType.BYTE) KneType.BYTE_ARRAY else KneType.LIST(itemType.type)
                    return ResolvedType(kneType, rustType = rustType, implTraitConversion = ".into_iter().collect::<Vec<_>>()")
                }

                "Display", "ToString" -> {
                    return ResolvedType(KneType.STRING, rustType = "String", implTraitConversion = ".to_string()")
                }

                "AsRef" -> {
                    val innerType = extractFirstGenericArg(
                        args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
                    )
                    if (innerType?.type == KneType.STRING) {
                        return ResolvedType(KneType.STRING, rustType = "String", implTraitConversion = ".as_ref().to_string()")
                    }
                    return null
                }

                "Into" -> {
                    val innerType = extractFirstGenericArg(
                        args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
                    ) ?: return null
                    return ResolvedType(innerType.type, rustType = innerType.rustType, implTraitConversion = ".into()")
                }
            }
        }
        return null
    }

    /**
     * Extracts an associated type binding from trait args (e.g. `Item = i32` in `Iterator<Item = i32>`).
     * Looks for `angle_bracketed.bindings[].name == bindingName` and resolves the equality type.
     */
    private fun extractAssociatedTypeBinding(
        args: JsonElement?,
        bindingName: String,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass>,
        genericTypes: Map<String, ResolvedType>,
        selfType: KneType?,
    ): ResolvedType? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        // rustdoc JSON uses "constraints" (newer) or "bindings" (older) for associated types
        val bindings = ab.getAsJsonArray("constraints") ?: ab.getAsJsonArray("bindings") ?: return null
        for (binding in bindings) {
            val bindingObj = binding.asJsonObject
            val name = bindingObj.get("name")?.asString ?: continue
            if (name != bindingName) continue
            val bindingKind = bindingObj.getAsJsonObject("binding") ?: continue
            val equalityType = bindingKind.get("equality") ?: continue
            if (equalityType.isJsonObject && equalityType.asJsonObject.has("type")) {
                return resolveTypeWithBorrow(
                    equalityType.asJsonObject.get("type"),
                    knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
                )
            }
        }
        return null
    }

    private fun extractFirstGenericArg(
        args: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): ResolvedType? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() == 0) return null
        val firstArg = argsList[0]
        if (!firstArg.isJsonObject) return null
        if (firstArg.asJsonObject.has("type")) {
            return resolveTypeWithBorrow(firstArg.asJsonObject.get("type"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
        }
        return null
    }

    /** Returns the raw JSON type object of the first generic arg without resolving it. */
    private fun extractFirstGenericArgRaw(args: JsonElement?): JsonObject? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() == 0) return null
        val firstArg = argsList[0]
        if (!firstArg.isJsonObject) return null
        return firstArg.asJsonObject.getAsJsonObject("type")
    }

    private fun extractTwoGenericArgs(
        args: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): Pair<ResolvedType, ResolvedType>? {
        if (args == null || args.isJsonNull) return null
        val ab = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return null
        val argsList = ab.getAsJsonArray("args") ?: return null
        if (argsList.size() < 2) return null
        val first = argsList[0]
        val second = argsList[1]
        if (!first.isJsonObject || !second.isJsonObject) return null
        val keyType = if (first.asJsonObject.has("type")) resolveTypeWithBorrow(first.asJsonObject.get("type"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) else null
        val valueType = if (second.asJsonObject.has("type")) resolveTypeWithBorrow(second.asJsonObject.get("type"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) else null
        if (keyType == null || valueType == null) return null
        return keyType to valueType
    }

    private fun isResultType(typeJson: JsonElement?): Boolean {
        if (typeJson == null || typeJson.isJsonNull) return false
        val obj = typeJson.asJsonObject
        if (obj.has("borrowed_ref")) {
            return isResultType(obj.getAsJsonObject("borrowed_ref").get("type"))
        }
        val resolvedPath = obj.getAsJsonObject("resolved_path") ?: return false
        return lastPathSegment(resolvedPath.get("path")?.asString ?: return false) == "Result"
    }

    private fun extractProperties(methods: List<KneFunction>): Pair<List<KneFunction>, List<KneProperty>> {
        val getters = mutableMapOf<String, KneFunction>()
        val setters = mutableMapOf<String, KneFunction>()

        for (fn in methods) {
            if (fn.name.startsWith("get_") && fn.params.isEmpty()) {
                val propName = fn.name.removePrefix("get_")
                if (isSimplePropertyType(fn.returnType)) getters[propName] = fn
            } else if (fn.name.startsWith("set_") && fn.params.size == 1 && fn.returnType == KneType.UNIT) {
                setters[fn.name.removePrefix("set_")] = fn
            }
        }

        val properties = mutableListOf<KneProperty>()
        val consumedMethods = mutableSetOf<String>()
        for ((propName, getter) in getters) {
            val setter = setters[propName]
            val mutable = setter != null && setter.params[0].type == getter.returnType
            properties.add(KneProperty(propName, getter.returnType, mutable))
            consumedMethods.add(getter.name)
            if (mutable) consumedMethods.add(setter!!.name)
        }

        return methods.filter { it.name !in consumedMethods } to properties
    }

    private fun isSimplePropertyType(type: KneType): Boolean = when (type) {
        KneType.INT, KneType.LONG, KneType.DOUBLE, KneType.FLOAT,
        KneType.BOOLEAN, KneType.BYTE, KneType.SHORT, KneType.STRING -> true
        is KneType.ENUM -> true
        is KneType.MAP -> true
        else -> false
    }

    private fun isDataClassFieldSupported(type: KneType): Boolean = when (type) {
        KneType.INT, KneType.LONG, KneType.DOUBLE, KneType.FLOAT,
        KneType.BOOLEAN, KneType.BYTE, KneType.SHORT, KneType.STRING,
        KneType.UNIT, KneType.BYTE_ARRAY -> true
        is KneType.ENUM -> true
        is KneType.NULLABLE -> isDataClassFieldSupported(type.inner)
        is KneType.LIST -> isDataClassFieldSupported(type.elementType)
        is KneType.SET -> isDataClassFieldSupported(type.elementType)
        is KneType.MAP -> isDataClassFieldSupported(type.keyType) && isDataClassFieldSupported(type.valueType)
        is KneType.DATA_CLASS -> type.fields.all { isDataClassFieldSupported(it.type) }
        else -> false
    }

    private fun typeDisplayName(type: KneType?): String = when (type) {
        null -> "unknown"
        is KneType.OBJECT -> type.fqName
        is KneType.INTERFACE -> type.fqName
        is KneType.ENUM -> type.fqName
        is KneType.SEALED_ENUM -> type.fqName
        else -> type.toString()
    }

    private fun recordOpaqueClass(simpleName: String, fqName: String, rustTypeName: String): KneClass {
        return encounteredOpaqueClasses.getOrPut(fqName) {
            val uniqueSimpleName = uniqueOpaqueSimpleName(simpleName, fqName)
            KneClass(
                simpleName = uniqueSimpleName,
                fqName = fqName,
                rustTypeName = rustTypeName,
                constructor = KneConstructor(emptyList(), KneConstructorKind.NONE),
                methods = emptyList(),
                properties = emptyList(),
                isOpaque = true,
            )
        }
    }

    private fun uniqueOpaqueSimpleName(baseName: String, fqName: String): String {
        val reserved = reservedTopLevelTypeNames + encounteredOpaqueClasses.values.map { it.simpleName }
        return uniqueOpaqueSimpleName(baseName, fqName, reserved)
    }

    private fun uniqueOpaqueSimpleName(baseName: String, fqName: String, reserved: Set<String>): String {
        if (baseName !in reserved) return baseName

        val segments = fqName.split('.', ':').filter { it.isNotBlank() }
        for (prefixStart in (segments.size - 2) downTo 0) {
            val prefix = segments.subList(prefixStart, segments.size - 1)
                .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
            val candidate = prefix + baseName
            if (candidate !in reserved) return candidate
        }
        val opaqueCandidate = "Opaque$baseName"
        if (opaqueCandidate !in reserved) return opaqueCandidate
        return "Opaque" + fqName.split('.', ':')
            .filter { it.isNotBlank() }
            .joinToString("") { it.replaceFirstChar(Char::uppercaseChar) }
    }

    private fun renderResolvedPathType(
        path: String,
        args: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): String {
        if (args == null || args.isJsonNull) return path
        val angle = args.asJsonObject.getAsJsonObject("angle_bracketed") ?: return path
        val renderedArgs = angle.getAsJsonArray("args")?.mapNotNull { arg ->
            if (!arg.isJsonObject) return@mapNotNull null
            val argObj = arg.asJsonObject
            when {
                argObj.has("lifetime") -> argObj.get("lifetime").safeString()
                argObj.has("type") -> resolveTypeWithBorrow(
                    argObj.get("type"),
                    knownStructs,
                    knownEnums,
                    knownDataClasses,
                    genericTypes,
                    selfType,
                )?.let { it.rustType ?: renderRustType(it.type) }
                    ?: renderRawType(argObj.get("type"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                else -> null
            }
        } ?: return path
        if (renderedArgs.isEmpty()) return path
        return "$path<${renderedArgs.joinToString(", ")}>"
    }

    private fun renderRawType(
        typeJson: JsonElement?,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
        genericTypes: Map<String, ResolvedType> = emptyMap(),
        selfType: KneType? = null,
    ): String? {
        if (typeJson == null || typeJson.isJsonNull) return null
        val obj = typeJson.asJsonObject

        return when {
            obj.has("primitive") -> obj.get("primitive").asString

            obj.has("borrowed_ref") -> {
                val ref = obj.getAsJsonObject("borrowed_ref")
                val lifetime = ref.get("lifetime").safeString()?.let { "$it " } ?: ""
                renderRawType(ref.get("type"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                    ?.let { "&$lifetime$it" }
            }

            obj.has("resolved_path") -> {
                val rp = obj.getAsJsonObject("resolved_path")
                renderResolvedPathType(
                    rp.get("path").asString,
                    rp.get("args"),
                    knownStructs,
                    knownEnums,
                    knownDataClasses,
                    genericTypes,
                    selfType,
                )
            }

            obj.has("dyn_trait") -> {
                val dynTrait = obj.getAsJsonObject("dyn_trait")
                val traitNames = dynTrait.getAsJsonArray("traits")?.mapNotNull { traitEntry ->
                    traitEntry.asJsonObject
                        .getAsJsonObject("trait")
                        ?.get("path")
                        ?.safeString()
                } ?: emptyList()
                if (traitNames.isEmpty()) null else {
                    val lifetime = dynTrait.get("lifetime").safeString()?.let { " + $it" } ?: ""
                    "dyn ${traitNames.joinToString(" + ")}$lifetime"
                }
            }

            obj.has("generic") -> {
                val name = obj.get("generic").asString
                when {
                    name == "Self" && selfType != null -> renderRustType(selfType)
                    name in genericTypes -> genericTypes[name]?.rustType ?: genericTypes[name]?.type?.let(::renderRustType)
                    else -> name
                }
            }

            obj.has("tuple") -> {
                val elems = obj.getAsJsonArray("tuple")
                if (elems.size() == 0) {
                    "()"
                } else {
                    elems.joinToString(", ", "(", ")") {
                        renderRawType(it, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: "_"
                    }
                }
            }

            obj.has("slice") -> {
                renderRawType(obj.get("slice"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                    ?.let { "[$it]" }
            }

            else -> null
        }
    }

    /**
     * Collects the IDs of types (struct/enum/trait) directly accessible as bare names from the
     * crate root, i.e. the set of IDs that would be in scope after `pub use crate_name::*`.
     *
     * Rules:
     *  - A public struct/enum/trait defined directly in the root module → its own ID.
     *  - A public non-glob `use` re-export with a resolved target ID → that target ID.
     *  - A public glob `use module::*` → all public struct/enum/trait IDs from that module
     *    (one level only; nested globs are not expanded).
     */
    private fun buildRootExportedIds(rootItems: JsonArray, index: JsonObject): Set<Int> {
        val result = mutableSetOf<Int>()
        for (itemIdElem in rootItems) {
            val itemId = itemIdElem.asInt
            val item = index.get(itemId.toString())?.asJsonObject ?: continue
            if (item.get("visibility").safeString() != "public") continue
            val inner = item.getAsJsonObject("inner") ?: continue
            when {
                inner.has("struct") || inner.has("enum") || inner.has("trait") -> result.add(itemId)
                inner.has("use") -> {
                    val useData = inner.getAsJsonObject("use")
                    val isGlob = useData.get("is_glob")?.asBoolean ?: false
                    val targetId = useData.get("id")
                        ?.takeIf { !it.isJsonNull }
                        ?.asInt
                    if (!isGlob && targetId != null) {
                        result.add(targetId)
                    } else if (isGlob && targetId != null) {
                        expandGlobModule(targetId, index, result)
                    }
                }
            }
        }
        return result
    }

    private fun expandGlobModule(moduleId: Int, index: JsonObject, result: MutableSet<Int>) {
        val moduleItem = index.get(moduleId.toString())?.asJsonObject ?: return
        val moduleItems = moduleItem.getAsJsonObject("inner")
            ?.getAsJsonObject("module")
            ?.getAsJsonArray("items") ?: return
        for (itemIdElem in moduleItems) {
            val itemId = itemIdElem.asInt
            val item = index.get(itemId.toString())?.asJsonObject ?: continue
            if (item.get("visibility").safeString() != "public") continue
            val inner = item.getAsJsonObject("inner") ?: continue
            when {
                inner.has("struct") || inner.has("enum") || inner.has("trait") -> result.add(itemId)
                inner.has("use") -> {
                    val useData = inner.getAsJsonObject("use")
                    if (useData.get("is_glob")?.asBoolean != true) {
                        val targetId = useData.get("id")
                            ?.takeIf { !it.isJsonNull }
                            ?.asInt ?: continue
                        result.add(targetId)
                    }
                }
            }
        }
    }

    private fun lastPathSegment(path: String): String = path.substringAfterLast("::").substringAfterLast('.')

    private fun renderRustType(type: KneType): String = when (type) {
        KneType.INT -> "i32"
        KneType.LONG -> "i64"
        KneType.DOUBLE -> "f64"
        KneType.FLOAT -> "f32"
        KneType.BOOLEAN -> "bool"
        KneType.BYTE -> "i8"
        KneType.SHORT -> "i16"
        KneType.STRING -> "String"
        KneType.UNIT -> "()"
        KneType.NEVER -> "!"
        is KneType.OBJECT -> type.simpleName
        is KneType.INTERFACE -> type.simpleName
        is KneType.ENUM -> type.simpleName
        is KneType.SEALED_ENUM -> type.simpleName
        is KneType.NULLABLE -> "Option<${renderRustType(type.inner)}>"
        is KneType.FUNCTION -> "Fn"
        is KneType.DATA_CLASS -> type.simpleName
        KneType.BYTE_ARRAY -> "Vec<u8>"
        is KneType.LIST -> "Vec<${renderRustType(type.elementType)}>"
        is KneType.SET -> "HashSet<${renderRustType(type.elementType)}>"
        is KneType.MAP -> "HashMap<${renderRustType(type.keyType)}, ${renderRustType(type.valueType)}>"
        is KneType.FLOW -> "Flow"
        is KneType.TUPLE -> "(${type.elementTypes.joinToString(", ") { renderRustType(it) }})"
    }
}
