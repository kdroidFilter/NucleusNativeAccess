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

    companion object {
        /** Standard Rust traits that should not be bridged to Kotlin. */
        private val SKIPPED_RUST_TRAITS = setOf(
            "Drop", "Clone", "Copy", "Send", "Sync", "Unpin", "Sized",
            "Debug", "Display", "Default", "Hash", "Eq", "PartialEq", "Ord", "PartialOrd",
            "From", "Into", "TryFrom", "TryInto", "AsRef", "AsMut",
            "Borrow", "BorrowMut", "ToOwned", "ToString",
            "Any", "Freeze", "RefUnwindSafe", "UnwindSafe",
            "StructuralPartialEq", "CloneToUninit",
            "Serialize", "Deserialize",
            "Iterator", "IntoIterator", "ExactSizeIterator", "DoubleEndedIterator",
            "FormatDecoder", // nokhwa-specific: complex trait with associated types, not bridgeable as interface
        )
    }

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

    /** Trait -> [ConcreteType] registry built from impl blocks. Used for generic monomorphisation. */
    private var traitImpls: Map<String, List<KneType.OBJECT>> = emptyMap()

    /** Tracks generic params with user-defined trait bounds (e.g., `F: FormatDecoder`).
     *  Set during method building to enable monomorphisation. */
    private var currentUnresolvedBounds: Map<String, List<GenericBound>> = emptyMap()

    // -- Lazy cross-crate type resolution state --

    /** Set during [parse]; the full rustdoc JSON index for lazy type lookups. */
    private var currentIndex: JsonObject? = null

    /** Mutable aliases set during [parse]; lazy-discovered types are registered here. */
    private var currentKnownStructs: MutableMap<Int, String> = mutableMapOf()
    private var currentKnownDataClasses: MutableMap<Int, KneDataClass> = mutableMapOf()
    private var currentKnownEnums: MutableMap<Int, String> = mutableMapOf()

    /** The `paths` field from rustdoc JSON — maps IDs to {crate_id, path, kind} for all referenced types. */
    private var currentPaths: JsonObject? = null

    /** Recursion guard: IDs currently being lazily resolved (prevents infinite loops). */
    private val lazyResolutionInProgress: MutableSet<Int> = mutableSetOf()

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
        /** True when the return type was `impl Future<Output = T>` — the bridge must block on the future. */
        val isFuture: Boolean = false,
    )

    /**
     * Result of resolving generic type parameters, including both resolved types
     * (for built-in traits like Fn, AsRef) and unresolved bounds (for user-defined traits
     * that require monomorphisation via traitImpls).
     */
    private data class GenericResolution(
        /** Maps generic param name -> resolved type (for Fn, AsRef, Into, etc.) */
        val resolvedTypes: Map<String, ResolvedType>,
        /** Maps generic param name -> trait bounds that could not be resolved to a concrete type.
         *  These require monomorphisation using traitImpls. */
        val unresolvedBounds: Map<String, List<GenericBound>>,
    )

    fun parse(
        json: String,
        libName: String,
        onUnsupported: (String) -> Unit = {},
    ): KneModule {
        encounteredOpaqueClasses = linkedMapOf()
        reservedTopLevelTypeNames = emptySet()
        dynTraitNames.clear()
        lazyResolutionInProgress.clear()

        val root = JsonParser.parseString(json).asJsonObject
        val index = root.getAsJsonObject("index")
        currentIndex = index
        currentPaths = root.getAsJsonObject("paths")
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
        // Wire up mutable map aliases for lazy cross-crate type resolution.
        // Must be set before impl-scanning so lazy resolution can register types.
        currentKnownStructs = knownStructs
        currentKnownEnums = knownEnums

        val sealedEnumIds = mutableSetOf<Int>()
        for ((id, _) in knownEnums.toMap()) {
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
        /** Trait impls from skipped traits — used only for monomorphisation, not interface generation. */
        val allTraitImpls = mutableMapOf<String, MutableList<KneType.OBJECT>>()
        /** Impl-block-level generics per struct type ID (for `impl<T: Trait> Struct<T>`). */
        val implGenerics = mutableMapOf<Int, JsonObject>()

        for ((_, item) in index.entrySet()) {
            val inner = item.asJsonObject.getAsJsonObject("inner") ?: continue
            if (!inner.has("impl")) continue
            val implObj = inner.getAsJsonObject("impl")
            val traitField = implObj.get("trait")
            val isTraitImpl = traitField != null && !traitField.isJsonNull && traitField.isJsonObject
            val forType = implObj.getAsJsonObject("for") ?: continue
            val typeId = resolveTypeId(forType) ?: continue
            if (!knownStructs.containsKey(typeId)) continue

            // Store impl-level generics for struct-level expansion (e.g., impl<T: Trait> Struct<T>)
            val implGenObj = implObj.getAsJsonObject("generics")
            if (implGenObj != null && !isTraitImpl) {
                val implParams = implGenObj.getAsJsonArray("params") ?: JsonArray()
                val hasTypeParams = implParams.any { p ->
                    val kind = p.asJsonObject.getAsJsonObject("kind")
                    kind != null && kind.has("type")
                }
                if (hasTypeParams) implGenerics[typeId] = implGenObj
            }

            if (isTraitImpl) {
                val traitName = traitField.asJsonObject.get("path")?.asString ?: continue
                if (traitName in SKIPPED_RUST_TRAITS) {
                    // Still register for monomorphisation, but don't add as interface impl
                    val structName = knownStructs[typeId] ?: continue
                    allTraitImpls.getOrPut(traitName) { mutableListOf() }.add(KneType.OBJECT("$crateName.$structName", structName))
                    continue
                }
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
                val implGenObj = implObj.getAsJsonObject("generics")
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
                    // Merge impl-level generics with function-level generics
                    val mergedGenerics = mergeGenerics(implGenObj, fn.getAsJsonObject("generics"))
                    val genericResolution = resolveGenericMappings(mergedGenerics, knownStructs, knownEnums, emptyMap(), selfType)
                    if (hasUnsupportedGenerics(mergedGenerics, genericResolution)) {
                        onUnsupported("Skipped constructor '${methodName}' for ${typeDisplayName(selfType)}: unsupported generic signature")
                        continue
                    }
                    // Constructors with unresolved generic bounds (e.g., fn new<T: Trait>)
                    // where the struct itself is NOT generic: treat as companion methods for expansion.
                    // If the struct IS generic (like Processor<T>), the struct-level expansion handles it.
                    if (genericResolution.unresolvedBounds.isNotEmpty() && !hasSelfParam(inputs)) {
                        val structGenericObj = implObj.getAsJsonObject("generics")
                        val structHasTypeParams = structGenericObj?.getAsJsonArray("params")?.any { p ->
                            val kind = p.asJsonObject.getAsJsonObject("kind")
                            kind != null && kind.has("type")
                        } == true
                        if (!structHasTypeParams) {
                            implCompanionMethods.getOrPut(typeId) { mutableListOf() }.add(methodItem)
                            continue
                        }
                    }
                    val returnType = resolveTypeWithBorrow(sig.get("output"), knownStructs, knownEnums, emptyMap(), genericResolution.resolvedTypes, selfType)
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

        val traitToImplTypes = mutableMapOf<String, MutableList<KneType.OBJECT>>()
        for ((typeId, traitNames) in structTraitImpls) {
            val structName = knownStructs[typeId] ?: continue
            val implementingType = KneType.OBJECT("$crateName.$structName", structName)
            for (traitName in traitNames) {
                val fqTraitName = "$crateName.$traitName"
                traitToImplTypes.getOrPut(fqTraitName) { mutableListOf() }.add(implementingType)
            }
        }
        // Merge skipped-trait impls into the registry for monomorphisation
        for ((traitName, impls) in allTraitImpls) {
            traitToImplTypes.getOrPut(traitName) { mutableListOf() }.addAll(impls)
        }
        traitImpls = traitToImplTypes

        val knownDataClasses = mutableMapOf<Int, KneDataClass>()
        // Snapshot the initial struct IDs to avoid processing lazily-discovered cross-crate types
        val initialStructIds = knownStructs.keys.toSet()
        for (id in initialStructIds) {
            val name = knownStructs[id] ?: continue
            val hasMethods = implMethods[id]?.isNotEmpty() == true || implCompanionMethods[id]?.isNotEmpty() == true
            if (hasMethods) continue

            val structItem = index.get(id.toString())?.asJsonObject ?: continue
            val fields = extractStructFields(structItem, index, knownStructs, knownEnums, knownDataClasses)
            if (fields == null || fields.isEmpty()) continue
            if (!fields.all { isDataClassFieldSupported(it.type) }) continue

            knownDataClasses[id] = KneDataClass(
                simpleName = name,
                fqName = "$crateName.$name",
                fields = fields,
            )
        }

        currentKnownDataClasses = knownDataClasses

        val classes = mutableListOf<KneClass>()
        for ((id, name) in knownStructs.toMap()) {
            if (knownDataClasses.containsKey(id)) continue
            val structItem = index.get(id.toString())?.asJsonObject ?: continue

            // Note: structs with generic type params (e.g. Processor<T>, ReadOnlySource<R>)
            // are NOT skipped here — the monomorphisation step below may produce concrete
            // variants. The bridge generator handles unresolved generics at code gen time.
            val selfType = KneType.OBJECT("$crateName.$name", name)
            val constructor = buildConstructor(
                newFn = implConstructors[id],
                structItem = structItem,
                index = index,
                knownStructs = knownStructs,
                knownEnums = knownEnums,
                knownDataClasses = knownDataClasses,
                selfType = selfType,
                implGenericJson = implGenerics[id],
                onUnsupported = { onUnsupported("Class '$name': $it") },
            )

            val structImplGenerics = implGenerics[id]
            val allMethods = (implMethods[id] ?: emptyList()).flatMap { entry ->
                buildMethod(
                    methodItem = entry.item,
                    knownStructs = knownStructs,
                    knownEnums = knownEnums,
                    knownDataClasses = knownDataClasses,
                    receiverKind = entry.receiverKind,
                    docs = entry.docs,
                    ownerType = selfType,
                    implGenericJson = structImplGenerics,
                    onUnsupported = { onUnsupported("Class '${name}': $it") },
                ).map { if (entry.isOverride) it.copy(isOverride = true) else it }
            }

            val companionMethods = (implCompanionMethods[id] ?: emptyList()).flatMap { methodItem ->
                buildMethod(
                    methodItem = methodItem,
                    knownStructs = knownStructs,
                    knownEnums = knownEnums,
                    knownDataClasses = knownDataClasses,
                    receiverKind = KneReceiverKind.NONE,
                    docs = methodItem.get("docs").safeString(),
                    ownerType = selfType,
                    implGenericJson = structImplGenerics,
                    onUnsupported = { onUnsupported("Class '${name}': $it") },
                )
            }

            // Deduplicate methods by signature — same trait may be impl'd multiple times
            // for different type params (e.g., AsAudioBufferRef for AudioBuffer<T>)
            val deduplicatedMethods = allMethods.distinctBy { fn ->
                fn.name + "(" + fn.params.joinToString(",") { it.type.toString() } + ")"
            }
            val (methods, properties) = extractProperties(deduplicatedMethods)
            // Only add traits that are known (exported at root level) as Kotlin interfaces
            val traitNames = structTraitImpls[id]
                ?.filter { it in knownTraits.values }
                ?.distinct()
                ?.map { "$crateName.$it" }
                ?: emptyList()
            val hasLifetimeParams = structHasLifetimeParams(structItem)
            val hasTypeParams = structHasTypeParams(structItem)
            // If any methods are marked as overrides but no interfaces are known,
            // clear the override flag (the trait wasn't resolved in this crate's scope)
            val cleanedMethods = if (traitNames.isEmpty() && methods.any { it.isOverride }) {
                methods.map { if (it.isOverride) it.copy(isOverride = false) else it }
            } else methods
            val klass = KneClass(
                simpleName = name,
                fqName = "$crateName.$name",
                constructor = constructor,
                methods = cleanedMethods,
                properties = properties,
                companionMethods = companionMethods,
                interfaces = traitNames,
                hasLifetimeParams = hasLifetimeParams,
                hasUnresolvedGenericTypeParams = hasTypeParams,
            )
            val expandedClasses = expandClassWithGenerics(klass, structItem, crateName)
            classes.addAll(expandedClasses)
        }

        val enums = mutableListOf<KneEnum>()
        val sealedEnums = mutableListOf<KneSealedEnum>()
        for ((id, name) in knownEnums.toMap()) {
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
        // Use rootExportedIds (which traverses submodules via pub use) to find
        // functions that are accessible from the crate root, not just direct root items.
        val functionItemIds: Iterable<Int> = if (rootExportedIds.isNotEmpty()) {
            rootExportedIds
        } else {
            rootItems.map { it.asInt }
        }
        for (itemId in functionItemIds) {
            val item = index.get(itemId.toString())?.asJsonObject ?: continue
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
            ).let(topLevelFunctions::addAll)
        }

        val interfaces = mutableListOf<KneInterface>()
        for ((id, traitName) in knownTraits) {
            val traitItem = index.get(id.toString())?.asJsonObject ?: continue
            val traitInner = traitItem.getAsJsonObject("inner")?.getAsJsonObject("trait") ?: continue
            val traitItems = traitInner.getAsJsonArray("items") ?: continue
            val selfType = KneType.INTERFACE("$crateName.$traitName", traitName)
            val traitMethods = traitItems.flatMap { methodId ->
                val methodItem = index.get(methodId.asInt.toString())?.asJsonObject ?: return@flatMap emptyList()
                val methodInner = methodItem.getAsJsonObject("inner") ?: return@flatMap emptyList()
                if (!methodInner.has("function")) return@flatMap emptyList()
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

        // Clean up lazy resolution state
        currentIndex = null
        currentPaths = null
        lazyResolutionInProgress.clear()

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
            traitImpls = traitToImplTypes,
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

    /** Merges impl-level generics with function-level generics (params + where_predicates). */
    private fun mergeGenerics(implGenerics: JsonObject?, fnGenerics: JsonObject?): JsonObject? {
        if (implGenerics == null) return fnGenerics
        if (fnGenerics == null) return implGenerics
        val merged = JsonObject()
        val mergedParams = JsonArray()
        implGenerics.getAsJsonArray("params")?.forEach { mergedParams.add(it) }
        fnGenerics.getAsJsonArray("params")?.forEach { mergedParams.add(it) }
        merged.add("params", mergedParams)
        val mergedWhere = JsonArray()
        implGenerics.getAsJsonArray("where_predicates")?.forEach { mergedWhere.add(it) }
        fnGenerics.getAsJsonArray("where_predicates")?.forEach { mergedWhere.add(it) }
        merged.add("where_predicates", mergedWhere)
        return merged
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
    ): GenericResolution {
        if (generics == null) return GenericResolution(emptyMap(), emptyMap())
        val resolved = mutableMapOf<String, ResolvedType>()
        val unresolvedBounds = mutableMapOf<String, MutableList<GenericBound>>()

        val params = generics.getAsJsonArray("params") ?: JsonArray()
        for (param in params) {
            val paramObj = param.asJsonObject
            val name = paramObj.get("name").safeString() ?: continue
            val kind = paramObj.getAsJsonObject("kind") ?: continue
            if (kind.has("lifetime")) continue
            val typeKind = kind.getAsJsonObject("type") ?: continue
            // Skip synthetic generics created by `impl Trait` params (e.g., `impl Into<String>`, `impl ToString`).
            // These are desugared by rustdoc; the actual types are resolved via `impl_trait` in the signature.
            if (typeKind.get("is_synthetic")?.asBoolean == true) continue
            val result = resolveGenericMappingFromBoundsWithTracking(typeKind, knownStructs, knownEnums, knownDataClasses, resolved, selfType)
            if (result.resolvedType != null) {
                resolved[name] = result.resolvedType
            }
            if (result.unresolvedBounds.isNotEmpty()) {
                unresolvedBounds.getOrPut(name) { mutableListOf() }.addAll(result.unresolvedBounds)
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
            val result = resolveGenericMappingFromBoundsWithTracking(pseudoTypeKind, knownStructs, knownEnums, knownDataClasses, resolved, selfType)
            if (result.resolvedType != null) {
                resolved[name] = result.resolvedType
            }
            if (result.unresolvedBounds.isNotEmpty()) {
                unresolvedBounds.getOrPut(name) { mutableListOf() }.addAll(result.unresolvedBounds)
            }
        }

        return GenericResolution(resolved, unresolvedBounds)
    }

    private data class BoundResolution(
        val resolvedType: ResolvedType?,
        val unresolvedBounds: List<GenericBound>,
    )

    private fun resolveGenericMappingFromBounds(
        typeKind: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass>,
        genericTypes: Map<String, ResolvedType>,
        selfType: KneType?,
    ): ResolvedType? = resolveGenericMappingFromBoundsWithTracking(typeKind, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType).resolvedType

    private fun resolveGenericMappingFromBoundsWithTracking(
        typeKind: JsonObject,
        knownStructs: Map<Int, String>,
        knownEnums: Map<Int, String>,
        knownDataClasses: Map<Int, KneDataClass>,
        genericTypes: Map<String, ResolvedType>,
        selfType: KneType?,
    ): BoundResolution {
        val bounds = typeKind.getAsJsonArray("bounds") ?: return BoundResolution(null, emptyList())
        val unresolvedBounds = mutableListOf<GenericBound>()
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
                    if (paramTypes.size != inputs.size()) continue
                    val outputResolved = resolveTypeWithBorrow(parenthesized.get("output"), knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                    return BoundResolution(
                        ResolvedType(
                            type = KneType.FUNCTION(paramTypes, outputResolved?.type ?: KneType.UNIT),
                            rustType = traitPath,
                        ),
                        emptyList(),
                    )
                }

                "AsRef", "Into", "From" -> {
                    val target = extractFirstGenericArg(traitArgs, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType) ?: continue
                    val targetSegment = lastPathSegment(target.rustType ?: renderRustType(target.type))
                    if (traitPath == "AsRef" && (targetSegment == "str" || targetSegment == "Path" || targetSegment == "PathBuf")) {
                        val rustTarget = if (targetSegment == "Path" || targetSegment == "PathBuf") "Path" else "str"
                        return BoundResolution(ResolvedType(type = KneType.STRING, rustType = "AsRef<$rustTarget>"), emptyList())
                    }
                    return BoundResolution(target.copy(rustType = "$traitPath<${target.rustType ?: renderRustType(target.type)}>"), emptyList())
                }

                else -> {
                    val traitFullPath = trait.get("path")?.asString ?: continue
                    val fqTraitName = traitFullPath.replace("::", ".")
                    unresolvedBounds.add(GenericBound(fqTraitName, traitPath))
                }
            }
        }
        return if (unresolvedBounds.isNotEmpty()) BoundResolution(null, unresolvedBounds) else BoundResolution(null, emptyList())
    }

    private fun hasUnsupportedGenerics(generics: JsonObject?, genericResolution: GenericResolution): Boolean {
        if (generics == null) return false
        val resolved = genericResolution.resolvedTypes
        val unresolved = genericResolution.unresolvedBounds
        val params = generics.getAsJsonArray("params") ?: return false
        for (param in params) {
            val paramObj = param.asJsonObject
            val name = paramObj.get("name").safeString() ?: continue
            val kind = paramObj.getAsJsonObject("kind") ?: continue
            when {
                kind.has("lifetime") -> continue
                kind.has("type") -> {
                    val typeKind = kind.getAsJsonObject("type")
                    if (typeKind?.get("is_synthetic")?.asBoolean == true) continue
                    if (name !in resolved && name !in unresolved) return true
                }
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
        knownDataClasses: Map<Int, KneDataClass> = emptyMap(),
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
            val resolved = resolveTypeWithBorrow(fieldType, knownStructs, knownEnums, knownDataClasses) ?: return null
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
        implGenericJson: JsonObject? = null,
        onUnsupported: (String) -> Unit = {},
    ): KneConstructor {
        if (newFn != null) {
            val function = newFn.getAsJsonObject("inner")?.getAsJsonObject("function") ?: return KneConstructor(emptyList(), KneConstructorKind.NONE)
            val mergedGen = mergeGenerics(implGenericJson, function.getAsJsonObject("generics"))
            val generics = resolveGenericMappings(mergedGen, knownStructs, knownEnums, knownDataClasses, selfType)
            if (hasUnsupportedGenerics(mergedGen, generics)) {
                onUnsupported("Skipped constructor '${selfType.simpleName}::new': unsupported generic signature")
                return KneConstructor(emptyList(), KneConstructorKind.NONE)
            }

            // Allow unresolved generic bounds through so struct-level expansion can substitute them
            val prevUnresolvedBounds = currentUnresolvedBounds
            currentUnresolvedBounds = generics.unresolvedBounds
            val sig = function.getAsJsonObject("sig")
            val params = buildParams(
                inputs = sig.getAsJsonArray("inputs"),
                knownStructs = knownStructs,
                knownEnums = knownEnums,
                knownDataClasses = knownDataClasses,
                genericTypes = generics.resolvedTypes,
                selfType = selfType,
                context = "constructor ${selfType.simpleName}",
                onUnsupported = onUnsupported,
            )
            currentUnresolvedBounds = prevUnresolvedBounds
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
        implGenericJson: JsonObject? = null,
        onUnsupported: (String) -> Unit = {},
    ): List<KneFunction> {
        val name = methodItem.get("name").safeString() ?: return emptyList()
        val inner = methodItem.getAsJsonObject("inner")?.getAsJsonObject("function") ?: return emptyList()
        // Resolve function generics first; impl-level generics are handled separately
        // via currentUnresolvedBounds (for type resolution) and struct-level expansion.
        val fnGenerics = inner.getAsJsonObject("generics")
        val mergedGen = mergeGenerics(implGenericJson, fnGenerics)
        val genericResolution = resolveGenericMappings(mergedGen, knownStructs, knownEnums, knownDataClasses, ownerType)
        if (hasUnsupportedGenerics(mergedGen, genericResolution)) {
            onUnsupported("Skipped method '$name': unsupported generic signature")
            return emptyList()
        }

        currentUnresolvedBounds = genericResolution.unresolvedBounds
        val sig = inner.getAsJsonObject("sig")
        val inputs = sig.getAsJsonArray("inputs")
        val params = buildParams(
            inputs = inputs,
            knownStructs = knownStructs,
            knownEnums = knownEnums,
            knownDataClasses = knownDataClasses,
            genericTypes = genericResolution.resolvedTypes,
            selfType = ownerType,
            skipSelf = hasSelfParam(inputs),
            context = "method '$name'",
            onUnsupported = onUnsupported,
        ) ?: run {
            currentUnresolvedBounds = emptyMap()
            onUnsupported("Skipped method '$name': unsupported parameter type")
            return emptyList()
        }
        val returnResolved = resolveReturnTypeOrUnit(
            output = sig.get("output"),
            knownStructs = knownStructs,
            knownEnums = knownEnums,
            knownDataClasses = knownDataClasses,
            genericTypes = genericResolution.resolvedTypes,
            selfType = ownerType,
        )
        if (returnResolved == null) {
            currentUnresolvedBounds = emptyMap()
            onUnsupported("Skipped method '$name': unsupported return type")
            return emptyList()
        }
        currentUnresolvedBounds = emptyMap()
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

        // Only expand method-level generic params (not impl-level ones — those are handled by struct expansion)
        val fnOnlyBoundNames = resolveGenericMappings(fnGenerics, knownStructs, knownEnums, knownDataClasses, ownerType)
            .unresolvedBounds.keys
        val genericParams = genericResolution.unresolvedBounds
            .filter { (paramName, _) -> paramName in fnOnlyBoundNames }
            .map { (paramName, bounds) ->
                val concreteTypes = if (bounds.size == 1) {
                    lookupTraitImpls(bounds[0].traitFqName)
                } else {
                    bounds.map { bound -> lookupTraitImpls(bound.traitFqName).toSet() }
                        .reduceOrNull { acc, types -> acc.intersect(types) }?.toList() ?: emptyList()
                }
                GenericParamInfo(paramName, bounds, concreteTypes)
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
            isAsync = inner.getAsJsonObject("header")?.get("is_async")?.asBoolean == true || returnResolved.isFuture,
            returnConversion = returnResolved.implTraitConversion,
            genericParams = genericParams,
        ).let { method ->
            expandMethodWithGenerics(method, genericParams)
        }
    }

    /** Looks up trait implementors, trying both the raw fqName and the crate-prefixed form. */
    private fun lookupTraitImpls(traitFqName: String): List<KneType.OBJECT> {
        traitImpls[traitFqName]?.let { return it }
        // Rustdoc may emit bare trait names (e.g. "ValueTransformer") while the registry
        // uses crate-prefixed keys (e.g. "rustcalc.ValueTransformer"). Try all prefixes.
        for (key in traitImpls.keys) {
            if (key.endsWith(".$traitFqName")) return traitImpls[key]!!
        }
        return emptyList()
    }

    private fun expandMethodWithGenerics(method: KneFunction, genericParams: List<GenericParamInfo>): List<KneFunction> {
        if (genericParams.isEmpty()) return listOf(method)
        val unresolvedToConcrete = genericParams.associate { gp ->
            "__unresolved_generic__${gp.paramName}" to gp.concreteTypes
        }
        val firstGenericParam = unresolvedToConcrete.keys.firstOrNull() ?: return listOf(method)
        val concreteTypes = unresolvedToConcrete[firstGenericParam] ?: return listOf(method)
        if (concreteTypes.isEmpty()) return emptyList()
        val restGenericParams = genericParams.drop(1)
        return concreteTypes.flatMap { concreteType ->
            val substitutedMethod = substituteUnresolvedGeneric(method, firstGenericParam.removePrefix("__unresolved_generic__"), concreteType)
            if (restGenericParams.isEmpty()) {
                listOf(substitutedMethod)
            } else {
                expandMethodWithGenerics(substitutedMethod, restGenericParams)
            }
        }
    }

    private fun substituteUnresolvedGeneric(method: KneFunction, paramName: String, concreteType: KneType.OBJECT): KneFunction {
        val concreteRustName = concreteType.simpleName
        fun subType(type: KneType) = substituteGenericType(type, paramName, concreteType)
        fun subParam(param: KneParam) = param.copy(
            type = subType(param.type),
            rustType = substituteRustType(param.rustType, paramName, concreteRustName),
        )
        val suffix = "_${toSnakeCase(concreteRustName)}"
        val originalName = method.rustMethodName ?: method.name
        val existingTurbofish = method.turbofish
        val newTurbofish = if (existingTurbofish != null) {
            existingTurbofish.removeSuffix(">") + ", $concreteRustName>"
        } else {
            "::<$concreteRustName>"
        }
        return method.copy(
            name = method.name + suffix,
            params = method.params.map(::subParam),
            returnType = subType(method.returnType),
            returnRustType = substituteRustType(method.returnRustType, paramName, concreteRustName),
            receiverType = method.receiverType?.let(::subType),
            genericParams = emptyList(),
            rustMethodName = originalName,
            turbofish = newTurbofish,
        )
    }

    private fun toSnakeCase(name: String): String =
        name.replace(Regex("([a-z])([A-Z])"), "$1_$2")
            .replace(Regex("([A-Z]+)([A-Z][a-z])"), "$1_$2")
            .lowercase()

    /** Substitutes all occurrences of `__unresolved_generic__${paramName}` with [concreteType] in a type tree. */
    private fun substituteGenericType(type: KneType, paramName: String, concreteType: KneType.OBJECT): KneType = when (type) {
        is KneType.OBJECT -> if (type.fqName == "__unresolved_generic__$paramName") concreteType else type
        is KneType.NULLABLE -> type.copy(inner = substituteGenericType(type.inner, paramName, concreteType))
        is KneType.FUNCTION -> type.copy(
            paramTypes = type.paramTypes.map { substituteGenericType(it, paramName, concreteType) },
            returnType = substituteGenericType(type.returnType, paramName, concreteType),
        )
        is KneType.DATA_CLASS -> type.copy(fields = type.fields.map { it.copy(type = substituteGenericType(it.type, paramName, concreteType)) })
        is KneType.LIST -> type.copy(elementType = substituteGenericType(type.elementType, paramName, concreteType))
        is KneType.SET -> type.copy(elementType = substituteGenericType(type.elementType, paramName, concreteType))
        is KneType.MAP -> type.copy(
            keyType = substituteGenericType(type.keyType, paramName, concreteType),
            valueType = substituteGenericType(type.valueType, paramName, concreteType),
        )
        is KneType.FLOW -> type.copy(elementType = substituteGenericType(type.elementType, paramName, concreteType))
        is KneType.TUPLE -> type.copy(elementTypes = type.elementTypes.map { substituteGenericType(it, paramName, concreteType) })
        else -> type
    }

    /** Substitutes the generic param name in a rustType string (handles `&T`, `&mut T`, etc.). */
    private fun substituteRustType(rustType: String?, paramName: String, concreteRustName: String): String? =
        rustType?.replace(Regex("\\b${Regex.escape(paramName)}\\b"), concreteRustName)

    /** Returns true if the struct has any lifetime parameters (e.g. `BufReader<'a>`). */
    private fun structHasLifetimeParams(structItem: JsonObject): Boolean {
        val structData = structItem.getAsJsonObject("inner")?.getAsJsonObject("struct") ?: return false
        val generics = structData.getAsJsonObject("generics") ?: return false
        val params = generics.getAsJsonArray("params") ?: return false
        return params.any { param ->
            val kind = param.asJsonObject.getAsJsonObject("kind")
            kind != null && kind.has("lifetime")
        }
    }

    /** Returns true if the struct has type parameters that are NOT resolved to concrete types. */
    private fun structHasTypeParams(structItem: JsonObject): Boolean {
        val structData = structItem.getAsJsonObject("inner")?.getAsJsonObject("struct") ?: return false
        val generics = structData.getAsJsonObject("generics") ?: return false
        val params = generics.getAsJsonArray("params") ?: return false
        return params.any { param ->
            val kind = param.asJsonObject.getAsJsonObject("kind")
            kind != null && kind.has("type")
        }
    }

    private fun extractStructGenerics(structItem: JsonObject): List<GenericParamInfo> {
        val structData = structItem.getAsJsonObject("inner")?.getAsJsonObject("struct") ?: return emptyList()
        val generics = structData.getAsJsonObject("generics") ?: return emptyList()
        val unresolvedBounds = mutableMapOf<String, MutableList<GenericBound>>()
        val params = generics.getAsJsonArray("params") ?: JsonArray()
        for (param in params) {
            val paramObj = param.asJsonObject
            val name = paramObj.get("name").safeString() ?: continue
            val kind = paramObj.getAsJsonObject("kind") ?: continue
            if (kind.has("lifetime")) continue
            if (!kind.has("type")) continue
            val typeKind = kind.getAsJsonObject("type") ?: continue
            val bounds = typeKind.getAsJsonArray("bounds") ?: JsonArray()
            val paramBounds = mutableListOf<GenericBound>()
            for (bound in bounds) {
                val boundObj = bound.asJsonObject
                // Rustdoc JSON structure: bound → trait_bound → trait → path
                val traitBound = boundObj.getAsJsonObject("trait_bound") ?: continue
                val trait = traitBound.getAsJsonObject("trait") ?: continue
                val traitFullPath = trait.get("path")?.asString ?: continue
                val traitSimpleName = lastPathSegment(traitFullPath)
                val fqTraitName = traitFullPath.replace("::", ".")
                paramBounds.add(GenericBound(fqTraitName, traitSimpleName))
            }
            if (paramBounds.isNotEmpty()) {
                unresolvedBounds.getOrPut(name) { mutableListOf() }.addAll(paramBounds)
            }
        }
        return unresolvedBounds.map { (paramName, bounds) ->
            val concreteTypes = if (bounds.size == 1) {
                lookupTraitImpls(bounds[0].traitFqName)
            } else {
                bounds.map { bound -> lookupTraitImpls(bound.traitFqName).toSet() }
                    .reduceOrNull { acc, types -> acc.intersect(types) }?.toList() ?: emptyList()
            }
            GenericParamInfo(paramName, bounds, concreteTypes)
        }
    }

    private fun expandClassWithGenerics(klass: KneClass, structItem: JsonObject, crateName: String): List<KneClass> {
        val genericParams = extractStructGenerics(structItem)
        if (genericParams.isEmpty()) return listOf(klass)
        return expandClassGenericParams(klass, genericParams, crateName)
    }

    private fun expandClassGenericParams(klass: KneClass, remainingParams: List<GenericParamInfo>, crateName: String): List<KneClass> {
        if (remainingParams.isEmpty()) return listOf(klass)
        val firstParam = remainingParams.first()
        if (firstParam.concreteTypes.isEmpty()) return listOf(klass)
        val restParams = remainingParams.drop(1)
        return firstParam.concreteTypes.flatMap { concreteType ->
            val substituted = substituteClassGeneric(klass, firstParam.paramName, concreteType, crateName)
            expandClassGenericParams(substituted, restParams, crateName)
        }
    }

    private fun substituteClassGeneric(klass: KneClass, paramName: String, concreteType: KneType.OBJECT, crateName: String): KneClass {
        val concreteRustName = concreteType.simpleName
        val suffix = "_$concreteRustName"
        val newSimpleName = klass.simpleName + suffix
        val newFqName = "$crateName.${klass.simpleName}$suffix"
        // The Rust type for the bridge: original struct name with turbofish
        val newRustTypeName = "${klass.rustTypeName}<$concreteRustName>"

        fun subType(type: KneType) = substituteGenericType(type, paramName, concreteType)
        fun subParam(param: KneParam) = param.copy(
            type = subType(param.type),
            rustType = substituteRustType(param.rustType, paramName, concreteRustName),
        )
        fun subProp(property: KneProperty) = property.copy(type = subType(property.type))
        fun subFn(fn: KneFunction) = fn.copy(
            params = fn.params.map(::subParam),
            returnType = subType(fn.returnType),
            returnRustType = substituteRustType(fn.returnRustType, paramName, concreteRustName),
            receiverType = fn.receiverType?.let(::subType),
        )
        // Filter out methods that were already monomorphized for THIS generic param
        // (method-level expansion happened earlier in buildMethod)
        val filteredMethods = klass.methods.filter { fn ->
            fn.genericParams.none { gp -> gp.paramName == paramName }
        }
        return klass.copy(
            simpleName = newSimpleName,
            fqName = newFqName,
            rustTypeName = newRustTypeName,
            constructor = klass.constructor.copy(params = klass.constructor.params.map(::subParam)),
            methods = filteredMethods.map(::subFn),
            properties = klass.properties.map(::subProp),
            companionMethods = klass.companionMethods.map(::subFn),
            genericParams = emptyList(),
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
        val usedNames = mutableMapOf<String, Int>()
        for (input in inputs) {
            val arr = input.asJsonArray
            var paramName = arr[0].asString
            if (skipSelf && paramName == "self") continue
            // Disambiguate duplicate or anonymous parameter names (e.g. Rust's `_`)
            val count = usedNames.getOrDefault(paramName, 0)
            usedNames[paramName] = count + 1
            if (paramName == "_") paramName = "_arg$count"
            else if (count > 0) paramName = "${paramName}_$count"
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

                // Cow<[u8]> → BYTE_ARRAY, Cow<str> → STRING, others → unwrap inner
                "Cow" -> {
                    val inner = extractFirstGenericArg(args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                    inner?.copy(rustType = "Cow<${inner.rustType ?: renderRustType(inner.type)}>")
                }

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
                    // Use a qualified Rust path as rustType only for standard library types
                    // (std::io::Error, std::time::Duration, etc.) to avoid ambiguity.
                    // For all other types, use the simple name — they are accessible via
                    // pub use re-exports in the wrapper lib.rs.
                    val qualifiedRustType = if (id != null) lookupFullPath(id) ?: pathSegment
                        else pathSegment
                    when {
                        id != null && knownEnums.containsKey(id) -> {
                            val name = knownEnums[id]!!
                            if (id in currentSealedEnumIds) {
                                ResolvedType(KneType.SEALED_ENUM("$currentCrateName.$name", name), rustType = qualifiedRustType)
                            } else {
                                ResolvedType(KneType.ENUM("$currentCrateName.$name", name), rustType = qualifiedRustType)
                            }
                        }

                        id != null && knownDataClasses.containsKey(id) -> {
                            val dc = knownDataClasses[id]!!
                            ResolvedType(KneType.DATA_CLASS(dc.fqName, dc.simpleName, dc.fields), rustType = qualifiedRustType)
                        }

                        id != null && knownStructs.containsKey(id) -> {
                            val name = knownStructs[id]!!
                            ResolvedType(KneType.OBJECT("$currentCrateName.$name", name), rustType = qualifiedRustType)
                        }

                        else -> {
                            // Try lazy cross-crate resolution before falling back to opaque
                            if (id != null) {
                                when (val lazy = tryLazyResolve(id)) {
                                    is LazyResolveResult.AsDataClass -> {
                                        val dc = lazy.dc
                                        return ResolvedType(KneType.DATA_CLASS(dc.fqName, dc.simpleName, dc.fields), rustType = qualifiedRustType)
                                    }
                                    is LazyResolveResult.AsEnum -> {
                                        val fq = "$currentCrateName.${lazy.name}"
                                        // Use the full path from paths table when available for qualified rustType
                                        val lazyRustType = if (lazy.fullPath != null && lazy.fullPath.contains("::")) lazy.fullPath else qualifiedRustType
                                        return if (lazy.isSealed) ResolvedType(KneType.SEALED_ENUM(fq, lazy.name), rustType = lazyRustType)
                                        else ResolvedType(KneType.ENUM(fq, lazy.name), rustType = lazyRustType)
                                    }
                                    is LazyResolveResult.AsStruct -> {
                                        val fq = "$currentCrateName.${lazy.name}"
                                        val lazyRustType = if (lazy.fullPath != null && lazy.fullPath.contains("::")) lazy.fullPath else qualifiedRustType
                                        // Only create an opaque proxy class for types not already
                                        // in knownStructs (to avoid shadowing richer versions from sub-crates)
                                        recordOpaqueClass(lazy.name, fq, lazyRustType)
                                        return ResolvedType(KneType.OBJECT(fq, lazy.name), rustType = lazyRustType)
                                    }
                                    null -> { /* fall through to opaque */ }
                                }
                            }
                            val simpleName = pathSegment
                            val fqName = path.replace("::", ".")
                            val rustType = renderResolvedPathType(path, args, knownStructs, knownEnums, knownDataClasses, genericTypes, selfType)
                            recordOpaqueClass(simpleName, fqName, rustType)
                            ResolvedType(KneType.OBJECT(fqName, simpleName), rustType = rustType)
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

        // Handle raw pointers: *const *const c_char represents LIST<String> (array of C strings)
        if (obj.has("raw_pointer")) {
            val ptrObj = obj.getAsJsonObject("raw_pointer")
            val innerType = ptrObj.get("type") ?: return null
            val innerObj = innerType.asJsonObject

            fun isCChar(typeObj: JsonObject): Boolean {
                // Check if primitive c_char
                if (typeObj.has("primitive") && typeObj.get("primitive").asString == "c_char") return true
                // Check if resolved_path to c_char or ffi::c_char
                if (typeObj.has("resolved_path")) {
                    val rp = typeObj.getAsJsonObject("resolved_path")
                    val path = rp.get("path").asString
                    return path == "c_char" || path.endsWith("::c_char")
                }
                return false
            }

            // Check for double pointer: *const *const c_char
            if (innerObj.has("raw_pointer")) {
                val innerPtrObj = innerObj.getAsJsonObject("raw_pointer")
                val innermostType = innerPtrObj.get("type")
                if (innermostType != null && isCChar(innermostType.asJsonObject)) {
                    return ResolvedType(KneType.LIST(KneType.STRING), rustType = "*const *const c_char")
                }
            }
            // Single pointer to c_char could be treated as STRING
            if (isCChar(innerObj)) {
                return ResolvedType(KneType.STRING, rustType = "*const c_char")
            }
            // For other raw pointers, return null (unsupported)
            return null
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
            if (name in genericTypes) {
                return genericTypes[name]
            }
            if (name in currentUnresolvedBounds) {
                return ResolvedType(
                    type = KneType.OBJECT("__unresolved_generic__$name", name),
                    rustType = name,
                )
            }
            return null
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

                "Future" -> {
                    val outputType = extractAssociatedTypeBinding(
                        args, "Output", knownStructs, knownEnums, knownDataClasses, genericTypes, selfType,
                    ) ?: return null
                    return ResolvedType(
                        outputType.type,
                        isBorrowed = outputType.isBorrowed,
                        rustType = outputType.rustType,
                        implTraitConversion = outputType.implTraitConversion,
                        isFuture = true,
                    )
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

            // Check for known crate-local traits: impl Trait → bridge as Box<dyn Trait>
            val traitId = traitObj.get("id")?.takeIf { !it.isJsonNull }?.asInt
            val knownTraitName = if (traitId != null && currentKnownTraits.containsKey(traitId)) {
                currentKnownTraits[traitId]!!
            } else if (currentKnownTraits.values.contains(traitName)) {
                traitName
            } else {
                null
            }
            if (knownTraitName != null) {
                dynTraitNames.add(knownTraitName)
                val fqName = "$currentCrateName.$knownTraitName"
                return ResolvedType(
                    KneType.INTERFACE(fqName, knownTraitName),
                    rustType = "impl dyn $knownTraitName",
                )
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
        is KneType.DATA_CLASS -> true
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

    // -- Lazy cross-crate type resolution --

    private sealed class LazyResolveResult {
        data class AsDataClass(val dc: KneDataClass) : LazyResolveResult()
        data class AsStruct(val name: String, val fullPath: String? = null) : LazyResolveResult()
        data class AsEnum(val name: String, val isSealed: Boolean, val fullPath: String? = null) : LazyResolveResult()
    }

    /**
     * Attempts to lazily discover a type from the rustdoc JSON index when it's not in the
     * known maps. This handles cross-crate re-exported types whose full definition is
     * available in the index but wasn't traversed during the initial root-exported scan.
     */
    private fun tryLazyResolve(id: Int): LazyResolveResult? {
        val index = currentIndex ?: return null
        if (id in lazyResolutionInProgress) return null
        lazyResolutionInProgress.add(id)
        try {
            val item = index.get(id.toString())?.asJsonObject
            if (item == null) {
                // Item not in index — check paths for basic type info (kind: enum/struct)
                return tryLazyResolveFromPaths(id)
            }
            val name = item.get("name").safeString() ?: return null
            val inner = item.getAsJsonObject("inner") ?: return null

            when {
                inner.has("struct") -> {
                    // Register in knownStructs before field resolution (breaks cycles)
                    currentKnownStructs[id] = name

                    val fields = extractStructFields(item, index, currentKnownStructs, currentKnownEnums, currentKnownDataClasses)
                    if (fields != null && fields.isNotEmpty() && fields.all { isDataClassFieldSupported(it.type) }) {
                        val dc = KneDataClass(
                            simpleName = name,
                            fqName = "$currentCrateName.$name",
                            fields = fields,
                        )
                        currentKnownDataClasses[id] = dc
                        return LazyResolveResult.AsDataClass(dc)
                    }
                    return LazyResolveResult.AsStruct(name)
                }
                inner.has("enum") -> {
                    currentKnownEnums[id] = name
                    val enumData = inner.getAsJsonObject("enum")
                    val variantIds = enumData?.getAsJsonArray("variants")
                    val isSealed = variantIds?.any { vid ->
                        val vi = index.get(vid.asInt.toString())?.asJsonObject
                        val vInner = vi?.getAsJsonObject("inner")?.getAsJsonObject("variant")
                        val kind = vInner?.get("kind")
                        kind != null && kind.isJsonObject
                    } ?: false
                    if (isSealed) {
                        currentSealedEnumIds = currentSealedEnumIds + id
                    }
                    return LazyResolveResult.AsEnum(name, isSealed)
                }
                else -> return null
            }
        } finally {
            lazyResolutionInProgress.remove(id)
        }
    }

    /**
     * Fallback when type ID is not in the index: use the `paths` field to determine
     * if it's an enum or struct. This provides basic type identity (enum ordinal vs
     * opaque handle) even without the full definition.
     */
    private fun tryLazyResolveFromPaths(id: Int): LazyResolveResult? {
        val paths = currentPaths ?: return null
        val pathEntry = paths.get(id.toString())?.asJsonObject ?: return null
        val kind = pathEntry.get("kind")?.asString ?: return null
        val pathSegments = pathEntry.getAsJsonArray("path") ?: return null
        val name = pathSegments.last().asString

        val rawFullPath = pathSegments.map { it.asString }.joinToString("::")
        // For std/core library paths, the paths table may include private modules
        // (e.g. std::io::error::Error). Collapse to the public form (std::io::Error).
        val fullPath = canonicalizeStdPath(rawFullPath)
        return when (kind) {
            // Without the full definition in the index, we can't determine if an enum is
            // unit-only (simple) or has data variants (sealed). Default to opaque struct
            // which is always safe (passed as handle). The actual type from the dependency's
            // JSON will override this during module merging.
            "enum", "struct" -> {
                currentKnownStructs[id] = name
                LazyResolveResult.AsStruct(name, fullPath)
            }
            else -> null
        }
    }

    /**
     * Look up the full Rust path for a type ID from the `paths` table.
     * Returns null if not found or if the type is from the current crate (no qualification needed).
     * The result is canonicalized for std/core types.
     */
    private fun lookupFullPath(id: Int): String? {
        val paths = currentPaths ?: return null
        val pathEntry = paths.get(id.toString())?.asJsonObject ?: return null
        // crate_id 0 = current crate, skip (no qualification needed for local types)
        val crateId = pathEntry.get("crate_id")?.asInt ?: return null
        if (crateId == 0) return null
        val pathSegments = pathEntry.getAsJsonArray("path") ?: return null
        val rawPath = pathSegments.map { it.asString }.joinToString("::")
        // Only qualify types from standard library crates (std, core, alloc).
        // Types from dependency crates (nokhwa_core, symphonia_core, cpal, etc.)
        // are re-exported and accessible by their simple name via pub use.
        val rootCrate = pathSegments.firstOrNull()?.asString ?: return null
        if (rootCrate !in standardLibraryCrates) return null
        return canonicalizeStdPath(rawPath)
    }

    private val standardLibraryCrates = setOf("std", "core", "alloc")

    /**
     * Canonicalize standard library paths from rustdoc's internal representation to the
     * public API form. E.g. `std::io::error::Error` -> `std::io::Error`,
     * `core::time::Duration` -> `std::time::Duration`.
     */
    private fun canonicalizeStdPath(path: String): String {
        val segments = path.split("::")
        if (segments.size < 2) return path
        val crate = segments[0]
        if (crate != "std" && crate != "core" && crate != "alloc") return path
        val typeName = segments.last()
        // For core:: types, prefer std:: as the canonical import path
        val prefix = if (crate == "core") "std" else crate
        // Build canonical path: crate::module::TypeName (skip private sub-modules)
        // Standard form is usually max 3 segments: std::module::Type
        return if (segments.size <= 3) {
            "$prefix::${segments.drop(1).joinToString("::")}"
        } else {
            // Collapse: std::io::error::Error -> std::io::Error
            "$prefix::${segments[1]}::$typeName"
        }
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
                inner.has("struct") || inner.has("enum") || inner.has("trait") || inner.has("function") -> result.add(itemId)
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
                // For crates whose root only has modules (e.g. symphonia_core with
                // modules: io, audio, formats, ...), recursively expand each public
                // module to discover the exported types.
                inner.has("module") -> {
                    expandGlobModule(itemId, index, result)
                }
            }
        }
        return result
    }

    private fun expandGlobModule(moduleId: Int, index: JsonObject, result: MutableSet<Int>, depth: Int = 0) {
        if (depth > 5) return // Prevent infinite recursion
        val moduleItem = index.get(moduleId.toString())?.asJsonObject ?: return
        // Only follow modules that have an entry in the rustdoc paths table.
        // Private/internal modules (like `bit` in symphonia_core::io::bit) have no
        // path entry and their types are not accessible from outside the crate.
        val hasPathEntry = currentPaths?.has(moduleId.toString()) == true
        if (depth > 0 && !hasPathEntry) return
        val moduleItems = moduleItem.getAsJsonObject("inner")
            ?.getAsJsonObject("module")
            ?.getAsJsonArray("items") ?: return
        for (itemIdElem in moduleItems) {
            val itemId = itemIdElem.asInt
            val item = index.get(itemId.toString())?.asJsonObject ?: continue
            if (item.get("visibility").safeString() != "public") continue
            val inner = item.getAsJsonObject("inner") ?: continue
            when {
                inner.has("struct") || inner.has("enum") || inner.has("trait") || inner.has("function") -> result.add(itemId)
                inner.has("use") -> {
                    val useData = inner.getAsJsonObject("use")
                    val isGlob = useData.get("is_glob")?.asBoolean ?: false
                    val targetId = useData.get("id")
                        ?.takeIf { !it.isJsonNull }
                        ?.asInt ?: continue
                    if (isGlob) {
                        expandGlobModule(targetId, index, result, depth + 1)
                    } else {
                        result.add(targetId)
                    }
                }
                inner.has("module") -> {
                    // Expand public sub-modules to find types declared within them
                    expandGlobModule(itemId, index, result, depth + 1)
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
