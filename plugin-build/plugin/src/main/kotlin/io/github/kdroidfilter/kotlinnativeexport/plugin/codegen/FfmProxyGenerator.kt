package io.github.kdroidfilter.kotlinnativeexport.plugin.codegen

import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneClass
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneDataClass
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneEnum
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneFunction
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneModule
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneParam
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneProperty
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneType

/**
 * Generates Kotlin/JVM FFM proxy code.
 *
 * Inspired by swift-java's FFMSwift2JavaGenerator:
 *  - Each exported symbol gets a descriptor class with FunctionDescriptor + MethodHandle
 *    (mirroring swift-java's inner descriptor classes per method).
 *  - Object lifecycle uses Java Cleaner + a captured Long handle
 *    (mirrors swift-java's AllocatingSwiftArena + swift_retain/swift_release pattern).
 *  - String I/O uses the output-buffer pattern (Arena.allocate + get/set ADDRESS).
 *  - Exception propagation: every downcall is followed by KneRuntime.checkError()
 *    which queries the native @ThreadLocal error state.
 *
 * Generated classes have the exact same API as the native Kotlin classes,
 * making the bridge fully transparent to the JVM developer.
 */
class FfmProxyGenerator {

    companion object {
        private const val STRING_BUF_SIZE = 8192
        private const val ERR_BUF_SIZE = 8192
        private const val MAX_COLLECTION_SIZE = 4096
    }

    private fun KneType.returnsViaBuffer(): Boolean =
        this == KneType.STRING || this == KneType.BYTE_ARRAY ||
        (this is KneType.NULLABLE && (inner == KneType.STRING || inner == KneType.BYTE_ARRAY))

    private fun KneType.isStringLike(): Boolean =
        this == KneType.STRING || (this is KneType.NULLABLE && inner == KneType.STRING)

    private fun KneType.isByteArrayType(): Boolean =
        this == KneType.BYTE_ARRAY

    private fun KneType.isFunctionType(): Boolean = this is KneType.FUNCTION

    private fun KneType.isCollection(): Boolean = when (this) {
        is KneType.LIST, is KneType.SET, is KneType.MAP -> true
        is KneType.NULLABLE -> inner is KneType.LIST || inner is KneType.SET || inner is KneType.MAP
        else -> false
    }

    private fun KneType.unwrapCollection(): KneType = when (this) {
        is KneType.NULLABLE -> inner
        else -> this
    }

    /** Check if a function needs a confined arena (for string/byte/collection alloc — callbacks use persistent arena). */
    private fun needsConfinedArena(params: List<KneParam>, returnType: KneType): Boolean =
        params.any { it.type.isStringLike() || it.type.isByteArrayType() || it.type.isCollection() } ||
        returnType.isStringLike() || returnType.isByteArrayType() || returnType.isCollection()

    private fun isDataClassReturn(type: KneType): Boolean =
        type is KneType.DATA_CLASS || (type is KneType.NULLABLE && type.inner is KneType.DATA_CLASS)

    private fun extractDataClass(type: KneType): KneType.DATA_CLASS? = when (type) {
        is KneType.DATA_CLASS -> type
        is KneType.NULLABLE -> type.inner as? KneType.DATA_CLASS
        else -> null
    }

    private fun classHasCallbacks(cls: KneClass): Boolean =
        cls.methods.any { fn -> fn.params.any { it.type is KneType.FUNCTION } } ||
        cls.companionMethods.any { fn -> fn.params.any { it.type is KneType.FUNCTION } }

    /**
     * Generates all proxy files for the module.
     * Returns a map of filename → file content.
     */
    fun generate(module: KneModule, jvmPackage: String): Map<String, String> {
        val files = mutableMapOf<String, String>()

        // Collect all unique callback signatures used across the module
        val callbackSignatures = collectCallbackSignatures(module)

        files["KneRuntime.kt"] = generateRuntime(module.libName, jvmPackage, callbackSignatures)
        files["KotlinNativeException.kt"] = generateException(jvmPackage)

        module.dataClasses.filter { !it.isCommon }.forEach { dc ->
            files["${dc.simpleName}.kt"] = generateDataClassFile(dc, jvmPackage)
        }

        module.classes.forEach { cls ->
            files["${cls.simpleName}.kt"] = generateClassProxy(cls, module, jvmPackage)
        }

        module.enums.forEach { enum ->
            files["${enum.simpleName}.kt"] = generateEnumProxy(enum, module, jvmPackage)
        }

        if (module.functions.isNotEmpty()) {
            val objectName = module.libName.replaceFirstChar { it.uppercaseChar() }
            files["$objectName.kt"] = generateFunctionObject(module.functions, objectName, module, jvmPackage)
        }

        return files
    }

    // ── Runtime helper ────────────────────────────────────────────────────────

    /** Collect all unique KneType.FUNCTION signatures used as parameters in the module. */
    private fun collectCallbackSignatures(module: KneModule): Set<KneType.FUNCTION> {
        val signatures = mutableSetOf<KneType.FUNCTION>()
        fun scanParams(params: List<KneParam>) {
            params.forEach { p -> if (p.type is KneType.FUNCTION) signatures.add(p.type) }
        }
        module.classes.forEach { cls ->
            cls.methods.forEach { scanParams(it.params) }
            cls.companionMethods.forEach { scanParams(it.params) }
        }
        module.functions.forEach { scanParams(it.params) }
        return signatures
    }

    /** Generate a unique identifier for a callback signature. */
    private fun callbackId(fnType: KneType.FUNCTION): String {
        fun sanitize(s: String) = s.replace("<", "_").replace(">", "").replace(", ", "_").replace("?", "N")
        val params = fnType.paramTypes.joinToString("_") { sanitize(it.jvmTypeName) }
        val ret = sanitize(fnType.returnType.jvmTypeName)
        return "${params.ifEmpty { "Void" }}_to_$ret"
    }

    private fun generateRuntime(libName: String, pkg: String, callbackSignatures: Set<KneType.FUNCTION> = emptySet()): String = buildString {
        appendLine("// Auto-generated by kotlin-native-export plugin. Do not modify.")
        appendLine("package $pkg")
        appendLine()
        appendLine("import java.lang.foreign.Arena")
        appendLine("import java.lang.foreign.FunctionDescriptor")
        appendLine("import java.lang.foreign.Linker")
        appendLine("import java.lang.foreign.SymbolLookup")
        appendLine("import java.lang.foreign.MemorySegment")
        appendLine("import java.lang.foreign.ValueLayout.*")
        appendLine("import java.lang.invoke.MethodHandle")
        appendLine("import java.nio.file.Paths")
        appendLine()
        appendLine("/**")
        appendLine(" * Shared FFM runtime: loads the native library, resolves MethodHandles,")
        appendLine(" * and propagates exceptions from the native side.")
        appendLine(" */")
        appendLine("internal object KneRuntime {")
        appendLine()
        appendLine("    val linker: Linker = Linker.nativeLinker()")
        appendLine("    private val globalArena: Arena = Arena.global()")
        appendLine()
        appendLine("    val library: SymbolLookup by lazy { loadLibrary(\"$libName\") }")
        appendLine()
        appendLine("    private fun loadLibrary(name: String): SymbolLookup {")
        appendLine("        val fileName = System.mapLibraryName(name)")
        appendLine("        val sep = if (System.getProperty(\"os.name\").lowercase().contains(\"win\")) \";\" else \":\"")
        appendLine("        val basePaths = System.getProperty(\"java.library.path\", \"\").split(sep)")
        appendLine("        val extraDirs = mutableListOf(System.getProperty(\"user.dir\", \".\"))")
        appendLine("        try {")
        appendLine("            ProcessHandle.current().info().command().ifPresent { cmd ->")
        appendLine("                Paths.get(cmd).parent?.toString()?.let { extraDirs.add(it) }")
        appendLine("            }")
        appendLine("        } catch (_: Exception) {}")
        appendLine("        val allBases = (basePaths + extraDirs).distinct().filter { it.isNotBlank() }")
        appendLine("        val paths = allBases + allBases.map { Paths.get(it, \"lib\").toString() }")
        appendLine("        for (dir in paths) {")
        appendLine("            val file = Paths.get(dir, fileName).toFile()")
        appendLine("            if (file.exists()) return SymbolLookup.libraryLookup(file.toPath(), globalArena)")
        appendLine("        }")
        appendLine("        return SymbolLookup.loaderLookup()")
        appendLine("    }")
        appendLine()
        appendLine("    fun handle(symbol: String, descriptor: FunctionDescriptor): MethodHandle =")
        appendLine("        linker.downcallHandle(")
        appendLine("            library.find(symbol).orElseThrow { UnsatisfiedLinkError(\"Symbol not found: \$symbol\") },")
        appendLine("            descriptor,")
        appendLine("        )")
        appendLine()
        appendLine("    // ── Exception propagation ────────────────────────────────────────────")
        appendLine()
        appendLine("    private val HAS_ERROR_HANDLE: MethodHandle by lazy {")
        appendLine("        handle(\"${libName}_kne_hasError\", FunctionDescriptor.of(JAVA_INT))")
        appendLine("    }")
        appendLine("    private val GET_LAST_ERROR_HANDLE: MethodHandle by lazy {")
        appendLine("        handle(\"${libName}_kne_getLastError\", FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT))")
        appendLine("    }")
        appendLine()
        appendLine("    fun checkError() {")
        appendLine("        val hasError = HAS_ERROR_HANDLE.invoke() as Int")
        appendLine("        if (hasError != 0) {")
        appendLine("            Arena.ofConfined().use { arena ->")
        appendLine("                val buf = arena.allocate($ERR_BUF_SIZE.toLong())")
        appendLine("                GET_LAST_ERROR_HANDLE.invoke(buf, $ERR_BUF_SIZE)")
        appendLine("                throw KotlinNativeException(buf.getString(0))")
        appendLine("            }")
        appendLine("        }")
        appendLine("    }")

        // Generate upcall infrastructure for each callback signature
        if (callbackSignatures.isNotEmpty()) {
            appendLine()
            appendLine("    // ── Callback upcall stubs ────────────────────────────────────────────")
            for (sig in callbackSignatures) {
                appendUpcallInfrastructure(sig)
            }
        }

        appendLine("}")
    }

    // ── Exception class ──────────────────────────────────────────────────────

    private fun generateException(pkg: String): String = buildString {
        appendLine("// Auto-generated by kotlin-native-export plugin. Do not modify.")
        appendLine("package $pkg")
        appendLine()
        appendLine("/**")
        appendLine(" * Exception thrown when a Kotlin/Native function throws across the FFM boundary.")
        appendLine(" * The message contains the original Kotlin exception's message.")
        appendLine(" */")
        appendLine("class KotlinNativeException(message: String) : RuntimeException(message)")
    }

    /**
     * Generates upcall infrastructure for a callback signature:
     * - A JVM static target method that invokes the lambda
     * - A lazy MethodHandle for the target
     * - A factory method to create upcall stubs
     */
    private fun StringBuilder.appendUpcallInfrastructure(sig: KneType.FUNCTION) {
        val id = callbackId(sig)
        val paramCount = sig.paramTypes.size

        // Static upcall target method
        // Expand DATA_CLASS params into individual field params at C ABI level
        // Expand LIST/SET params into MemorySegment + Int (pointer + size)
        data class FlatParam(val name: String, val type: KneType)
        val flatParams = mutableListOf<FlatParam>()
        sig.paramTypes.forEachIndexed { i, t ->
            when (t) {
                is KneType.DATA_CLASS -> t.fields.forEach { f -> flatParams.add(FlatParam("p${i}_${f.name}", f.type)) }
                is KneType.LIST, is KneType.SET -> {
                    flatParams.add(FlatParam("p${i}_ptr", KneType.STRING)) // MemorySegment (ADDRESS)
                    flatParams.add(FlatParam("p${i}_size", KneType.INT))
                }
                is KneType.MAP -> {
                    flatParams.add(FlatParam("p${i}_keys", KneType.STRING)) // ADDRESS
                    flatParams.add(FlatParam("p${i}_values", KneType.STRING)) // ADDRESS
                    flatParams.add(FlatParam("p${i}_size", KneType.INT))
                }
                else -> flatParams.add(FlatParam("p$i", t))
            }
        }

        val targetParams = buildList {
            add("fn: Any")
            flatParams.forEach { add("${it.name}: ${upcallJvmType(it.type)}") }
        }.joinToString(", ")

        val returnJvmType = upcallJvmType(sig.returnType)
        val returnDecl = if (sig.returnType == KneType.UNIT) "" else ": $returnJvmType"

        appendLine()
        appendLine("    @JvmStatic")
        appendLine("    fun _upcall_$id($targetParams)$returnDecl {")

        appendLine("        @Suppress(\"UNCHECKED_CAST\")")
        appendLine("        val _fn = fn as ${sig.jvmTypeName}")

        // Reconstruct DATA_CLASS/LIST/SET params from flat fields, convert others
        // First emit collection reconstruction as local vals
        sig.paramTypes.forEachIndexed { i, t ->
            // MAP reconstruction
            if (t is KneType.MAP) {
                appendUpcallMapReconstruction(i, t)
            }
            val elemType = when (t) { is KneType.LIST -> t.elementType; is KneType.SET -> (t as KneType.SET).elementType; else -> null }
            if (elemType != null) {
                val layout = KneType.collectionElementLayout(elemType)
                when (elemType) {
                    KneType.STRING -> {
                        appendLine("        val _list$i = mutableListOf<String>()")
                        appendLine("        var _off$i = 0L")
                        appendLine("        val _seg$i = p${i}_ptr.reinterpret(${STRING_BUF_SIZE}.toLong())")
                        appendLine("        repeat(p${i}_size) { _list${i}.add(_seg${i}.getString(_off${i})); _off$i += _list${i}.last().toByteArray(Charsets.UTF_8).size + 1 }")
                    }
                    KneType.BOOLEAN -> {
                        appendLine("        val _seg$i = p${i}_ptr.reinterpret(p${i}_size.toLong() * 4)")
                        appendLine("        val _list$i = List(p${i}_size) { _seg${i}.getAtIndex(JAVA_INT, it.toLong()) != 0 }")
                    }
                    is KneType.ENUM -> {
                        appendLine("        val _seg$i = p${i}_ptr.reinterpret(p${i}_size.toLong() * 4)")
                        appendLine("        val _list$i = List(p${i}_size) { ${elemType.simpleName}.entries[_seg${i}.getAtIndex(JAVA_INT, it.toLong())] }")
                    }
                    else -> {
                        val elemSize = fieldSize(elemType)
                        appendLine("        val _seg$i = p${i}_ptr.reinterpret(p${i}_size.toLong() * $elemSize)")
                        appendLine("        val _list$i = List(p${i}_size) { _seg${i}.getAtIndex($layout, it.toLong()) as ${elemType.jvmTypeName} }")
                    }
                }
                if (t is KneType.SET) {
                    appendLine("        val _set$i = _list${i}.toSet()")
                }
            }
        }

        val invokeConvertedArgs = sig.paramTypes.mapIndexed { i, t ->
            when (t) {
                KneType.BOOLEAN -> "p$i != 0"
                KneType.STRING -> "p$i.reinterpret(8192).getString(0)"
                is KneType.ENUM -> "${t.simpleName}.entries[p$i]"
                is KneType.DATA_CLASS -> {
                    val fieldArgs = t.fields.joinToString(", ") { f ->
                        val pName = "p${i}_${f.name}"
                        when (f.type) {
                            KneType.BOOLEAN -> "${f.name} = $pName != 0"
                            KneType.STRING -> "${f.name} = $pName.reinterpret(8192).getString(0)"
                            else -> "${f.name} = $pName"
                        }
                    }
                    "${t.simpleName}($fieldArgs)"
                }
                is KneType.LIST -> "_list$i"
                is KneType.SET -> "_set$i"
                is KneType.MAP -> "_map$i"
                else -> "p$i"
            }
        }.joinToString(", ")

        if (sig.returnType == KneType.UNIT) {
            appendLine("        _fn.invoke($invokeConvertedArgs)")
        } else if (sig.returnType == KneType.BOOLEAN) {
            appendLine("        return if (_fn.invoke($invokeConvertedArgs)) 1 else 0")
        } else if (sig.returnType == KneType.STRING) {
            appendLine("        return Arena.ofAuto().allocateFrom(_fn.invoke($invokeConvertedArgs))")
        } else if (sig.returnType is KneType.ENUM) {
            appendLine("        return _fn.invoke($invokeConvertedArgs).ordinal")
        } else if (sig.returnType is KneType.DATA_CLASS) {
            val dc = sig.returnType
            appendLine("        val _result = _fn.invoke($invokeConvertedArgs)")
            appendLine("        val _arena = Arena.ofAuto()")
            val structSize = dc.fields.sumOf { fieldSize(it.type) }
            appendLine("        val _buf = _arena.allocate(${structSize}.toLong())")
            var offset = 0
            dc.fields.forEach { f ->
                when (f.type) {
                    KneType.INT -> { appendLine("        _buf.set(JAVA_INT, ${offset}.toLong(), _result.${f.name})"); offset += 4 }
                    KneType.LONG -> { appendLine("        _buf.set(JAVA_LONG, ${offset}.toLong(), _result.${f.name})"); offset += 8 }
                    KneType.DOUBLE -> { appendLine("        _buf.set(JAVA_DOUBLE, ${offset}.toLong(), _result.${f.name})"); offset += 8 }
                    KneType.FLOAT -> { appendLine("        _buf.set(JAVA_FLOAT, ${offset}.toLong(), _result.${f.name})"); offset += 4 }
                    KneType.BOOLEAN -> { appendLine("        _buf.set(JAVA_INT, ${offset}.toLong(), if (_result.${f.name}) 1 else 0)"); offset += 4 }
                    KneType.SHORT -> { appendLine("        _buf.set(JAVA_SHORT, ${offset}.toLong(), _result.${f.name})"); offset += 2 }
                    KneType.BYTE -> { appendLine("        _buf.set(JAVA_BYTE, ${offset}.toLong(), _result.${f.name})"); offset += 1 }
                    KneType.STRING -> { appendLine("        _buf.set(ADDRESS, ${offset}.toLong(), _arena.allocateFrom(_result.${f.name}))"); offset += 8 }
                    else -> offset += 8
                }
            }
            appendLine("        return _buf")
        } else if (sig.returnType is KneType.LIST || sig.returnType is KneType.SET) {
            val elemType = when (sig.returnType) { is KneType.LIST -> sig.returnType.elementType; is KneType.SET -> (sig.returnType as KneType.SET).elementType; else -> KneType.INT }
            appendLine("        val _result = _fn.invoke($invokeConvertedArgs)")
            val src = if (sig.returnType is KneType.SET) "_result.toList()" else "_result"
            appendUpcallCollectionReturn(elemType, src)
        } else if (sig.returnType is KneType.MAP) {
            val mapType = sig.returnType as KneType.MAP
            appendLine("        val _result = _fn.invoke($invokeConvertedArgs)")
            appendUpcallMapReturn(mapType)
        } else {
            appendLine("        return _fn.invoke($invokeConvertedArgs)")
        }
        appendLine("    }")

        // MethodHandle for the upcall target
        val methodTypeArgs = buildList {
            add(upcallMethodTypeArg(sig.returnType))
            add("Any::class.java")
            flatParams.forEach { add(upcallMethodTypeArg(it.type)) }
        }.joinToString(", ")

        appendLine()
        appendLine("    val UPCALL_MH_$id: MethodHandle by lazy {")
        appendLine("        java.lang.invoke.MethodHandles.lookup().findStatic(")
        appendLine("            KneRuntime::class.java, \"_upcall_$id\",")
        appendLine("            java.lang.invoke.MethodType.methodType($methodTypeArgs)")
        appendLine("        )")
        appendLine("    }")

        // FunctionDescriptor for the callback's C ABI (DATA_CLASS expanded to fields)
        val descLayouts = flatParams.joinToString(", ") { upcallLayout(it.type) }
        val descExpr = if (sig.returnType == KneType.UNIT) {
            if (descLayouts.isEmpty()) "FunctionDescriptor.ofVoid()"
            else "FunctionDescriptor.ofVoid($descLayouts)"
        } else {
            val retLayout = upcallLayout(sig.returnType)
            if (descLayouts.isEmpty()) "FunctionDescriptor.of($retLayout)"
            else "FunctionDescriptor.of($retLayout, $descLayouts)"
        }
        appendLine("    val UPCALL_DESC_$id: FunctionDescriptor = $descExpr")

        // Factory method to create upcall stubs
        appendLine()
        appendLine("    fun createUpcallStub_$id(fn: ${sig.jvmTypeName}, arena: Arena): Long {")
        appendLine("        val bound = UPCALL_MH_$id.bindTo(fn)")
        appendLine("        return linker.upcallStub(bound, UPCALL_DESC_$id, arena).address()")
        appendLine("    }")
    }

    /** Emit collection return as packed buffer [count:Int64][ elements...] from upcall. 8-byte header for alignment. */
    private fun StringBuilder.appendUpcallCollectionReturn(elemType: KneType, srcExpr: String) {
        val H = 8 // header: 8 bytes (Int at offset 0, 4 bytes padding) — ensures 8-byte alignment for all element types
        appendLine("        val _list = $srcExpr")
        appendLine("        val _arena = Arena.ofAuto()")
        when (elemType) {
            KneType.STRING -> {
                appendLine("        val _totalBytes = $H + _list.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }")
                appendLine("        val _buf = _arena.allocate(_totalBytes.toLong())")
                appendLine("        _buf.set(JAVA_INT, 0L, _list.size)")
                appendLine("        var _off = ${H}L")
                appendLine("        for (_s in _list) { _buf.setString(_off, _s); _off += _s.toByteArray(Charsets.UTF_8).size + 1 }")
            }
            else -> {
                val layout = KneType.collectionElementLayout(elemType)
                val elemSize = fieldSize(elemType)
                appendLine("        val _buf = _arena.allocate(($H + _list.size * $elemSize).toLong())")
                appendLine("        _buf.set(JAVA_INT, 0L, _list.size)")
                when (elemType) {
                    KneType.BOOLEAN -> appendLine("        _list.forEachIndexed { i, v -> _buf.set(JAVA_INT, ($H + i * 4).toLong(), if (v) 1 else 0) }")
                    is KneType.ENUM -> appendLine("        _list.forEachIndexed { i, v -> _buf.set(JAVA_INT, ($H + i * 4).toLong(), v.ordinal) }")
                    else -> appendLine("        _list.forEachIndexed { i, v -> _buf.set($layout, ($H + i * $elemSize).toLong(), v) }")
                }
            }
        }
        appendLine("        return _buf")
    }

    /** Emit map return as packed buffer from upcall. 8-byte header for alignment. */
    private fun StringBuilder.appendUpcallMapReturn(mapType: KneType.MAP) {
        val H = 8 // 8-byte header
        appendLine("        val _keys = _result.keys.toList()")
        appendLine("        val _values = _result.values.toList()")
        appendLine("        val _arena = Arena.ofAuto()")
        val kSize = if (mapType.keyType == KneType.STRING) 0 else fieldSize(mapType.keyType)
        val vSize = if (mapType.valueType == KneType.STRING) 0 else fieldSize(mapType.valueType)
        if (kSize > 0 && vSize > 0) {
            appendLine("        val _buf = _arena.allocate(($H + _keys.size * ${kSize + vSize}).toLong())")
        } else {
            appendLine("        val _totalBytes = $H + ${if (kSize > 0) "_keys.size * $kSize" else "_keys.sumOf { it.toString().toByteArray(Charsets.UTF_8).size + 1 }"} + ${if (vSize > 0) "_values.size * $vSize + $vSize" else "_values.sumOf { it.toString().toByteArray(Charsets.UTF_8).size + 1 }"} // extra padding for alignment")
            appendLine("        val _buf = _arena.allocate(_totalBytes.toLong())")
        }
        appendLine("        _buf.set(JAVA_INT, 0L, _keys.size)")
        appendUpcallWriteArray("_keys", mapType.keyType, "${H}L")
        if (kSize > 0) {
            appendUpcallWriteArray("_values", mapType.valueType, "($H + _keys.size * $kSize).toLong()")
        } else {
            if (vSize > 1) {
                appendLine("        val _valStart = (_keysEndOff + ${vSize - 1}) / $vSize * $vSize")
                appendUpcallWriteArray("_values", mapType.valueType, "_valStart")
            } else {
                appendUpcallWriteArray("_values", mapType.valueType, "_keysEndOff")
            }
        }
        appendLine("        return _buf")
    }

    private fun StringBuilder.appendUpcallWriteArray(listExpr: String, elemType: KneType, startOffset: String) {
        when (elemType) {
            KneType.STRING -> {
                appendLine("        var _${listExpr}Off = $startOffset")
                appendLine("        for (_s in $listExpr) { _buf.setString(_${listExpr}Off, _s.toString()); _${listExpr}Off += _s.toString().toByteArray(Charsets.UTF_8).size + 1 }")
                appendLine("        val _${listExpr.removePrefix("_")}EndOff = _${listExpr}Off")
            }
            KneType.BOOLEAN -> appendLine("        $listExpr.forEachIndexed { i, v -> _buf.set(JAVA_INT, $startOffset + i * 4, if (v) 1 else 0) }")
            is KneType.ENUM -> appendLine("        $listExpr.forEachIndexed { i, v -> _buf.set(JAVA_INT, $startOffset + i * 4, v.ordinal) }")
            else -> {
                val layout = KneType.collectionElementLayout(elemType)
                val elemSize = fieldSize(elemType)
                appendLine("        $listExpr.forEachIndexed { i, v -> _buf.set($layout, $startOffset + i * $elemSize, v) }")
            }
        }
    }

    /** Emit MAP reconstruction code in upcall target. */
    private fun StringBuilder.appendUpcallMapReconstruction(i: Int, t: KneType.MAP) {
        // Read keys
        appendUpcallCollectionRead(i, t.keyType, "keys", "p${i}_keys")
        // Read values
        appendUpcallCollectionRead(i, t.valueType, "values", "p${i}_values")
        appendLine("        val _map$i = _keys${i}.zip(_values${i}).toMap()")
    }

    /** Emit collection array read for a specific role (keys/values) in upcall context. */
    private fun StringBuilder.appendUpcallCollectionRead(i: Int, elemType: KneType, role: String, ptrName: String) {
        val layout = KneType.collectionElementLayout(elemType)
        when (elemType) {
            KneType.STRING -> {
                appendLine("        val _${role}${i} = mutableListOf<String>()")
                appendLine("        var _${role}Off${i} = 0L")
                appendLine("        val _${role}Seg${i} = $ptrName.reinterpret(${STRING_BUF_SIZE}.toLong())")
                appendLine("        repeat(p${i}_size) { _${role}${i}.add(_${role}Seg${i}.getString(_${role}Off${i})); _${role}Off$i += _${role}${i}.last().toByteArray(Charsets.UTF_8).size + 1 }")
            }
            KneType.BOOLEAN -> {
                appendLine("        val _${role}Seg${i} = $ptrName.reinterpret(p${i}_size.toLong() * 4)")
                appendLine("        val _${role}${i} = List(p${i}_size) { _${role}Seg${i}.getAtIndex(JAVA_INT, it.toLong()) != 0 }")
            }
            is KneType.ENUM -> {
                appendLine("        val _${role}Seg${i} = $ptrName.reinterpret(p${i}_size.toLong() * 4)")
                appendLine("        val _${role}${i} = List(p${i}_size) { ${elemType.simpleName}.entries[_${role}Seg${i}.getAtIndex(JAVA_INT, it.toLong())] }")
            }
            else -> {
                val elemSize = fieldSize(elemType)
                appendLine("        val _${role}Seg${i} = $ptrName.reinterpret(p${i}_size.toLong() * $elemSize)")
                appendLine("        val _${role}${i} = List(p${i}_size) { _${role}Seg${i}.getAtIndex($layout, it.toLong()) as ${elemType.jvmTypeName} }")
            }
        }
    }

    /** The Java type name for upcall method signatures (C ABI compatible). */
    private fun upcallJvmType(type: KneType): String = when (type) {
        KneType.INT -> "Int"
        KneType.LONG -> "Long"
        KneType.DOUBLE -> "Double"
        KneType.FLOAT -> "Float"
        KneType.BOOLEAN -> "Int" // C ABI: int for bool
        KneType.BYTE -> "Byte"
        KneType.SHORT -> "Short"
        KneType.STRING -> "MemorySegment"
        KneType.UNIT -> "Unit"
        is KneType.DATA_CLASS -> "MemorySegment" // returns struct pointer
        is KneType.LIST, is KneType.SET, is KneType.MAP -> "MemorySegment" // packed buffer
        else -> "Int"
    }

    /** The MethodType argument for a callback param/return type. */
    private fun upcallMethodTypeArg(type: KneType): String = when (type) {
        KneType.UNIT -> "Void.TYPE"
        KneType.STRING -> "java.lang.foreign.MemorySegment::class.java"
        is KneType.DATA_CLASS -> "java.lang.foreign.MemorySegment::class.java"
        is KneType.LIST, is KneType.SET, is KneType.MAP -> "java.lang.foreign.MemorySegment::class.java"
        else -> "${upcallJvmType(type)}::class.javaPrimitiveType"
    }

    /** The FFM ValueLayout for a callback parameter/return type. */
    private fun upcallLayout(type: KneType): String = when (type) {
        KneType.INT -> "JAVA_INT"
        KneType.LONG -> "JAVA_LONG"
        KneType.DOUBLE -> "JAVA_DOUBLE"
        KneType.FLOAT -> "JAVA_FLOAT"
        KneType.BOOLEAN -> "JAVA_INT"
        KneType.BYTE -> "JAVA_BYTE"
        KneType.SHORT -> "JAVA_SHORT"
        KneType.STRING -> "ADDRESS"
        is KneType.DATA_CLASS -> "ADDRESS" // struct pointer
        is KneType.LIST, is KneType.SET, is KneType.MAP -> "ADDRESS" // packed buffer
        else -> "JAVA_INT"
    }

    private fun fieldSize(type: KneType): Int = when (type) {
        KneType.INT, KneType.FLOAT, KneType.BOOLEAN -> 4
        KneType.LONG, KneType.DOUBLE, KneType.STRING -> 8
        KneType.BYTE -> 1
        KneType.SHORT -> 2
        else -> 8
    }

    // ── Class proxy ──────────────────────────────────────────────────────────

    private fun generateClassProxy(cls: KneClass, module: KneModule, pkg: String): String = buildString {
        val p = module.libName
        val n = cls.simpleName

        appendLine("// Auto-generated by kotlin-native-export plugin. Do not modify.")
        appendLine("package $pkg")
        appendLine()
        appendLine("import java.lang.foreign.Arena")
        appendLine("import java.lang.foreign.FunctionDescriptor")
        appendLine("import java.lang.foreign.MemorySegment")
        appendLine("import java.lang.foreign.ValueLayout.*")
        appendLine("import java.lang.invoke.MethodHandle")
        appendLine("import java.lang.ref.Cleaner")
        appendLine()

        appendLine("/**")
        appendLine(" * JVM proxy for Kotlin/Native class [$n].")
        appendLine(" * Uses FFM MethodHandles to dispatch every call to the native shared library.")
        appendLine(" * Object lifecycle is managed via Java Cleaner (automatic GC) or explicit close().")
        appendLine(" */")
        val hasCallbacks = classHasCallbacks(cls)

        appendLine("class $n private constructor(internal val handle: Long) : AutoCloseable {")
        if (hasCallbacks) {
            appendLine("    internal val _callbackArena: Arena = Arena.ofShared()")
        }
        appendLine()

        val companionHasCallbacks = cls.companionMethods.any { fn -> fn.params.any { it.type is KneType.FUNCTION } }

        // Companion: MethodHandles + factory
        appendLine("    companion object {")
        appendLine("        private val CLEANER = Cleaner.create()")
        if (companionHasCallbacks) {
            appendLine("        private val _companionCallbackArena: Arena = Arena.ofShared()")
        }
        appendLine()

        val ctorLayouts = buildLayouts(cls.constructor.params.map { it.type })
        appendLine("        private val NEW_HANDLE: MethodHandle by lazy {")
        appendLine("            KneRuntime.handle(\"${p}_${n}_new\",")
        appendLine("                FunctionDescriptor.of(JAVA_LONG$ctorLayouts))")
        appendLine("        }")
        appendLine("        private val DISPOSE_HANDLE: MethodHandle by lazy {")
        appendLine("            KneRuntime.handle(\"${p}_${n}_dispose\",")
        appendLine("                FunctionDescriptor.ofVoid(JAVA_LONG))")
        appendLine("        }")

        cls.methods.forEach { method ->
            val handleName = "${method.name.uppercase()}_HANDLE"
            val descriptor = buildMethodDescriptor(method)
            appendLine("        private val $handleName: MethodHandle by lazy {")
            appendLine("            KneRuntime.handle(\"${p}_${n}_${method.name}\",")
            appendLine("                $descriptor)")
            appendLine("        }")
        }

        cls.properties.forEach { prop ->
            val getHandleName = "GET_${prop.name.uppercase()}_HANDLE"
            val getDescriptor = buildGetterDescriptor(prop)
            appendLine("        private val $getHandleName: MethodHandle by lazy {")
            appendLine("            KneRuntime.handle(\"${p}_${n}_get_${prop.name}\",")
            appendLine("                $getDescriptor)")
            appendLine("        }")
            if (prop.mutable) {
                val setHandleName = "SET_${prop.name.uppercase()}_HANDLE"
                val setLayouts = buildLayouts(listOf(prop.type))
                appendLine("        private val $setHandleName: MethodHandle by lazy {")
                appendLine("            KneRuntime.handle(\"${p}_${n}_set_${prop.name}\",")
                appendLine("                FunctionDescriptor.ofVoid(JAVA_LONG$setLayouts))")
                appendLine("        }")
            }
        }

        cls.companionMethods.forEach { method ->
            val handleName = "COMPANION_${method.name.uppercase()}_HANDLE"
            val descriptor = buildTopLevelDescriptor(method)
            appendLine("        private val $handleName: MethodHandle by lazy {")
            appendLine("            KneRuntime.handle(\"${p}_${n}_companion_${method.name}\",")
            appendLine("                $descriptor)")
            appendLine("        }")
        }
        cls.companionProperties.forEach { prop ->
            val getHandleName = "COMPANION_GET_${prop.name.uppercase()}_HANDLE"
            val getDescriptor = buildCompanionGetterDescriptor(prop)
            appendLine("        private val $getHandleName: MethodHandle by lazy {")
            appendLine("            KneRuntime.handle(\"${p}_${n}_companion_get_${prop.name}\",")
            appendLine("                $getDescriptor)")
            appendLine("        }")
            if (prop.mutable) {
                val setHandleName = "COMPANION_SET_${prop.name.uppercase()}_HANDLE"
                appendLine("        private val $setHandleName: MethodHandle by lazy {")
                appendLine("            KneRuntime.handle(\"${p}_${n}_companion_set_${prop.name}\",")
                appendLine("                FunctionDescriptor.ofVoid(${prop.type.ffmLayout}))")
                appendLine("        }")
            }
        }

        // Factory
        val ctorParams = cls.constructor.params.joinToString(", ") { "${it.name}: ${it.type.jvmTypeName}" }
        appendLine()
        appendLine("        /**")
        appendLine("         * Creates a new [$n] backed by a Kotlin/Native object.")
        appendLine("         * The native object is automatically released when GC'd or close() is called.")
        appendLine("         */")
        appendLine("        operator fun invoke($ctorParams): $n {")
        if (cls.constructor.params.any { it.type.isStringLike() || it.type.isFunctionType() }) {
            appendLine("            Arena.ofConfined().use { arena ->")
            appendStringInvokeArgsAlloc("                ", cls.constructor.params)
            val ctorInvokeArgs = buildCtorInvokeArgs(cls.constructor.params)
            appendLine("                val h = NEW_HANDLE.invoke($ctorInvokeArgs) as Long")
            appendLine("                KneRuntime.checkError()")
            appendLine("                return fromNativeHandle(h)")
            appendLine("            }")
        } else {
            val ctorInvokeArgs = buildCtorInvokeArgs(cls.constructor.params)
            appendLine("            val h = NEW_HANDLE.invoke($ctorInvokeArgs) as Long")
            appendLine("            KneRuntime.checkError()")
            appendLine("            return fromNativeHandle(h)")
        }
        appendLine("        }")
        appendLine()

        appendLine("        internal fun fromNativeHandle(h: Long): $n {")
        appendLine("            val obj = $n(h)")
        if (hasCallbacks) {
            appendLine("            val cbArena = obj._callbackArena")
            appendLine("            CLEANER.register(obj) { runCatching { cbArena.close() }; runCatching { DISPOSE_HANDLE.invoke(h) } }")
        } else {
            appendLine("            CLEANER.register(obj) { runCatching { DISPOSE_HANDLE.invoke(h) } }")
        }
        appendLine("            return obj")
        appendLine("        }")

        // Companion methods
        cls.companionMethods.forEach { method -> appendCompanionMethodProxy(method) }
        cls.companionProperties.forEach { prop -> appendCompanionPropertyProxy(prop) }

        appendLine("    }")
        appendLine()

        // Methods
        cls.methods.forEach { method -> appendMethodProxy(method, cls, p) }
        cls.properties.forEach { prop -> appendPropertyProxy(prop, cls) }

        // close()
        appendLine("    override fun close() {")
        if (hasCallbacks) {
            appendLine("        runCatching { _callbackArena.close() }")
        }
        appendLine("        runCatching { DISPOSE_HANDLE.invoke(handle) }")
        appendLine("    }")
        appendLine("}")
    }

    private fun StringBuilder.appendMethodProxy(fn: KneFunction, cls: KneClass, prefix: String) {
        val handleName = "${fn.name.uppercase()}_HANDLE"
        val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.jvmTypeName}" }

        appendLine("    fun ${fn.name}($params): ${fn.returnType.jvmTypeName} {")

        // Allocate callback stubs in persistent arena (survives async calls)
        appendCallbackStubAlloc("        ", fn.params, "_callbackArena")

        val returnDc = extractDataClass(fn.returnType)
        val returnsNullableDc = fn.returnType is KneType.NULLABLE && fn.returnType.inner is KneType.DATA_CLASS
        val hasAnyDcParams = fn.params.any { extractDataClass(it.type) != null }
        val returnsCollection = fn.returnType.isCollection()
        val needsConfinedArena = needsConfinedArena(fn.params, fn.returnType) || returnDc != null ||
            hasAnyDcParams && fn.params.any { dc -> val d = extractDataClass(dc.type); d != null && d.fields.any { f -> f.type == KneType.STRING } }

        if (needsConfinedArena || returnDc != null || returnsCollection) {
            appendLine("        Arena.ofConfined().use { arena ->")
            appendStringInvokeArgsAlloc("            ", fn.params)
            appendCollectionParamAlloc("            ", fn.params)
            if (returnDc != null) {
                appendDataClassReturnProxy("            ", fn, handleName, returnsNullableDc)
            } else if (returnsCollection) {
                appendCollectionReturnProxy("            ", fn, handleName)
            } else {
                val invokeArgs = buildClassInvokeArgsExpanded(fn)
                appendCallAndReturn("            ", fn.returnType, handleName, invokeArgs)
            }
            appendLine("        }")
        } else {
            val invokeArgs = buildClassInvokeArgsExpandedDirect(fn)
            appendCallAndReturn("        ", fn.returnType, handleName, invokeArgs)
        }

        appendLine("    }")
        appendLine()
    }

    /** Flatten data class into out-param (name, type) pairs (recursive for nested). */
    private fun flattenDcFields(dc: KneType.DATA_CLASS, prefix: String): List<Pair<String, KneType>> =
        dc.fields.flatMap { f ->
            val name = "${prefix}_${f.name}"
            if (f.type is KneType.DATA_CLASS) flattenDcFields(f.type, name)
            else listOf(Pair(name, f.type))
        }

    /** Generate the return-via-out-params pattern for DATA_CLASS return types. */
    private fun StringBuilder.appendDataClassReturnProxy(indent: String, fn: KneFunction, handleName: String, nullable: Boolean = false) {
        val dc = extractDataClass(fn.returnType)!!
        val flatFields = flattenDcFields(dc, "out")

        // Allocate out-params for each flat field
        flatFields.forEach { (name, type) ->
            when (type) {
                KneType.STRING, KneType.BYTE_ARRAY -> appendLine("${indent}val $name = arena.allocate($STRING_BUF_SIZE.toLong())")
                else -> appendLine("${indent}val $name = arena.allocate(${type.ffmLayout})")
            }
        }

        // Build invoke args: handle + expanded params + out-params
        val paramArgs = buildList {
            add("handle")
            fn.params.forEach { p -> addAll(buildExpandedInvokeArgs(p)) }
            flatFields.forEach { (name, type) ->
                when (type) {
                    KneType.STRING, KneType.BYTE_ARRAY -> { add(name); add("$STRING_BUF_SIZE") }
                    else -> add(name)
                }
            }
        }.joinToString(", ")

        if (nullable) {
            appendLine("${indent}val _isPresent = $handleName.invoke($paramArgs) as Int")
            appendLine("${indent}KneRuntime.checkError()")
            appendLine("${indent}if (_isPresent == 0) return null")
        } else {
            appendLine("${indent}$handleName.invoke($paramArgs)")
            appendLine("${indent}KneRuntime.checkError()")
        }

        // Reconstruct the data class from flat out-params (recursive)
        appendLine("${indent}return ${buildDcCtorFromOutParams(dc, "out")}")
    }

    /** Build a constructor call that reads from out-params (recursive for nested data classes). */
    private fun buildDcCtorFromOutParams(dc: KneType.DATA_CLASS, prefix: String): String {
        val args = dc.fields.joinToString(", ") { f ->
            val name = "${prefix}_${f.name}"
            when (f.type) {
                KneType.STRING -> "${f.name} = $name.getString(0)"
                KneType.BOOLEAN -> "${f.name} = $name.get(JAVA_INT, 0) != 0"
                is KneType.ENUM -> "${f.name} = ${f.type.simpleName}.entries[$name.get(JAVA_INT, 0)]"
                is KneType.OBJECT -> "${f.name} = ${f.type.simpleName}.fromNativeHandle($name.get(JAVA_LONG, 0) as Long)"
                is KneType.DATA_CLASS -> "${f.name} = ${buildDcCtorFromOutParams(f.type, name)}"
                else -> "${f.name} = $name.get(${f.type.ffmLayout}, 0) as ${f.type.jvmTypeName}"
            }
        }
        return "${dc.simpleName}($args)"
    }

    /** Build invoke args with DATA_CLASS params expanded into individual fields. */
    private fun buildClassInvokeArgsExpanded(fn: KneFunction): String {
        val args = buildList {
            add("handle")
            fn.params.forEach { p -> addAll(buildExpandedInvokeArgs(p)) }
            if (fn.returnType.returnsViaBuffer()) { add("buf"); add("$STRING_BUF_SIZE") }
        }
        return args.joinToString(", ")
    }

    private fun buildClassInvokeArgsExpandedDirect(fn: KneFunction): String {
        val args = buildList {
            add("handle")
            fn.params.forEach { p -> addAll(buildExpandedInvokeArgs(p)) }
        }
        return args.joinToString(", ")
    }

    /** Expand a single param into invoke args. DATA_CLASS becomes N args (recursive), ByteArray/collections add size. */
    private fun buildExpandedInvokeArgs(p: KneParam): List<String> {
        if (p.type == KneType.BYTE_ARRAY) return listOf("${p.name}Seg", "${p.name}.size")
        val isNullableColl = p.type is KneType.NULLABLE && (p.type as KneType.NULLABLE).inner.let { it is KneType.LIST || it is KneType.SET || it is KneType.MAP }
        if (p.type is KneType.LIST) return listOf("${p.name}Seg", "${p.name}.size")
        if (p.type is KneType.SET) return listOf("${p.name}Seg", "${p.name}.size")
        if (p.type is KneType.MAP) return listOf("${p.name}_keysSeg", "${p.name}_valuesSeg", "${p.name}.size")
        if (isNullableColl) {
            val inner = (p.type as KneType.NULLABLE).inner
            val sizeExpr = "if (${p.name} == null) -1 else ${p.name}.size"
            return when (inner) {
                is KneType.LIST -> listOf("${p.name}Seg", sizeExpr)
                is KneType.SET -> listOf("${p.name}Seg", sizeExpr)
                is KneType.MAP -> listOf("${p.name}_keysSeg", "${p.name}_valuesSeg", sizeExpr)
                else -> listOf(buildJvmInvokeArg(p.name, p.type))
            }
        }
        val dc = extractDataClass(p.type)
        if (dc == null) return listOf(buildJvmInvokeArg(p.name, p.type))
        val isNullable = p.type is KneType.NULLABLE
        val objExpr = p.name
        val flatArgs = buildFlatInvokeArgs(dc, objExpr, p.name, isNullable)
        return if (isNullable) listOf("if ($objExpr == null) 1 else 0") + flatArgs else flatArgs
    }

    private fun buildFlatInvokeArgs(dc: KneType.DATA_CLASS, objExpr: String, prefix: String, nullable: Boolean): List<String> =
        dc.fields.flatMap { f ->
            val access = if (nullable) "$objExpr?.${f.name}" else "$objExpr.${f.name}"
            val paramName = "${prefix}_${f.name}"
            when (f.type) {
                KneType.STRING -> listOf("${paramName}Seg")
                KneType.BOOLEAN -> listOf("if ($access == true) 1 else 0")
                is KneType.ENUM -> listOf("$access?.ordinal ?: 0")
                is KneType.OBJECT -> listOf("$access?.handle ?: 0L")
                is KneType.DATA_CLASS -> buildFlatInvokeArgs(f.type, access ?: "null", paramName, nullable)
                else -> listOf("$access ?: 0")
            }
        }

    private fun StringBuilder.appendPropertyProxy(prop: KneProperty, cls: KneClass) {
        val getHandleName = "GET_${prop.name.uppercase()}_HANDLE"
        if (prop.mutable) {
            appendLine("    var ${prop.name}: ${prop.type.jvmTypeName}")
        } else {
            appendLine("    val ${prop.name}: ${prop.type.jvmTypeName}")
        }
        appendLine("        get() {")
        val needsArena = prop.type.returnsViaBuffer()
        if (needsArena) {
            appendLine("            Arena.ofConfined().use { arena ->")
            appendLine("                val buf = arena.allocate($STRING_BUF_SIZE.toLong())")
            if (prop.type is KneType.NULLABLE) {
                appendLine("                val len = $getHandleName.invoke(handle, buf, $STRING_BUF_SIZE) as Int")
                appendLine("                KneRuntime.checkError()")
                appendLine("                return if (len < 0) null else buf.getString(0)")
            } else {
                appendLine("                $getHandleName.invoke(handle, buf, $STRING_BUF_SIZE)")
                appendLine("                KneRuntime.checkError()")
                appendLine("                return buf.getString(0)")
            }
            appendLine("            }")
        } else {
            appendCallAndReturn("            ", prop.type, getHandleName, "handle")
        }
        appendLine("        }")

        if (prop.mutable) {
            val setHandleName = "SET_${prop.name.uppercase()}_HANDLE"
            appendLine("        set(value) {")
            appendSetterInvoke("            ", setHandleName, prop.type, "handle")
            appendLine("        }")
        }
        appendLine()
    }

    // ── Companion method/property proxies ────────────────────────────────────

    private fun StringBuilder.appendCompanionMethodProxy(fn: KneFunction) {
        val handleName = "COMPANION_${fn.name.uppercase()}_HANDLE"
        val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.jvmTypeName}" }

        appendLine()
        appendLine("        fun ${fn.name}($params): ${fn.returnType.jvmTypeName} {")

        appendCallbackStubAlloc("            ", fn.params, "_companionCallbackArena")

        val arenaNeeded = needsConfinedArena(fn.params, fn.returnType)
        if (arenaNeeded) {
            appendLine("            Arena.ofConfined().use { arena ->")
            appendStringInvokeArgsAlloc("                ", fn.params)
            val invokeArgs = buildTopLevelInvokeArgs(fn)
            appendCallAndReturn("                ", fn.returnType, handleName, invokeArgs)
            appendLine("            }")
        } else {
            val invokeArgs = fn.params.joinToString(", ") { p -> buildJvmInvokeArg(p.name, p.type) }
            appendCallAndReturn("            ", fn.returnType, handleName, invokeArgs)
        }

        appendLine("        }")
    }

    private fun StringBuilder.appendCompanionPropertyProxy(prop: KneProperty) {
        val getHandleName = "COMPANION_GET_${prop.name.uppercase()}_HANDLE"
        appendLine()
        if (prop.mutable) {
            appendLine("        var ${prop.name}: ${prop.type.jvmTypeName}")
        } else {
            appendLine("        val ${prop.name}: ${prop.type.jvmTypeName}")
        }
        appendLine("            get() {")
        val needsArena = prop.type.returnsViaBuffer()
        if (needsArena) {
            appendLine("                Arena.ofConfined().use { arena ->")
            appendLine("                    val buf = arena.allocate($STRING_BUF_SIZE.toLong())")
            if (prop.type is KneType.NULLABLE) {
                appendLine("                    val len = $getHandleName.invoke(buf, $STRING_BUF_SIZE) as Int")
                appendLine("                    KneRuntime.checkError()")
                appendLine("                    return if (len < 0) null else buf.getString(0)")
            } else {
                appendLine("                    $getHandleName.invoke(buf, $STRING_BUF_SIZE)")
                appendLine("                    KneRuntime.checkError()")
                appendLine("                    return buf.getString(0)")
            }
            appendLine("                }")
        } else {
            appendCallAndReturn("                ", prop.type, getHandleName, "")
        }
        appendLine("            }")

        if (prop.mutable) {
            val setHandleName = "COMPANION_SET_${prop.name.uppercase()}_HANDLE"
            appendLine("            set(value) {")
            appendSetterInvoke("                ", setHandleName, prop.type, null)
            appendLine("            }")
        }
    }

    // ── Data class file ───────────────────────────────────────────────────────

    private fun generateDataClassFile(dc: KneDataClass, pkg: String): String = buildString {
        appendLine("// Auto-generated by kotlin-native-export plugin. Do not modify.")
        appendLine("package $pkg")
        appendLine()
        val fields = dc.fields.joinToString(", ") { "val ${it.name}: ${it.type.jvmTypeName}" }
        appendLine("data class ${dc.simpleName}($fields)")
    }

    // ── Enum proxy ───────────────────────────────────────────────────────────

    private fun generateEnumProxy(enum: KneEnum, module: KneModule, pkg: String): String = buildString {
        appendLine("// Auto-generated by kotlin-native-export plugin. Do not modify.")
        appendLine("package $pkg")
        appendLine()
        appendLine("enum class ${enum.simpleName} {")
        enum.entries.forEachIndexed { idx, entry ->
            val separator = if (idx < enum.entries.size - 1) "," else ";"
            appendLine("    $entry$separator")
        }
        appendLine("}")
    }

    // ── Top-level function object ────────────────────────────────────────────

    private fun generateFunctionObject(
        fns: List<KneFunction>,
        objectName: String,
        module: KneModule,
        pkg: String,
    ): String = buildString {
        val p = module.libName

        appendLine("// Auto-generated by kotlin-native-export plugin. Do not modify.")
        appendLine("package $pkg")
        appendLine()
        appendLine("import java.lang.foreign.Arena")
        appendLine("import java.lang.foreign.FunctionDescriptor")
        appendLine("import java.lang.foreign.MemorySegment")
        appendLine("import java.lang.foreign.ValueLayout.*")
        appendLine("import java.lang.invoke.MethodHandle")
        appendLine()

        val objectHasCallbacks = fns.any { fn -> fn.params.any { it.type is KneType.FUNCTION } }

        appendLine("object $objectName {")
        if (objectHasCallbacks) {
            appendLine("    private val _callbackArena: Arena = Arena.ofShared()")
        }
        appendLine()

        fns.forEach { fn ->
            val handleName = "${fn.name.uppercase()}_HANDLE"
            val descriptor = buildTopLevelDescriptor(fn)
            appendLine("    private val $handleName: MethodHandle by lazy {")
            appendLine("        KneRuntime.handle(\"${p}_${fn.name}\", $descriptor)")
            appendLine("    }")
        }
        appendLine()

        fns.forEach { fn ->
            val handleName = "${fn.name.uppercase()}_HANDLE"
            val params = fn.params.joinToString(", ") { "${it.name}: ${it.type.jvmTypeName}" }
            appendLine("    fun ${fn.name}($params): ${fn.returnType.jvmTypeName} {")

            appendCallbackStubAlloc("        ", fn.params, "_callbackArena")

            val arenaNeeded = needsConfinedArena(fn.params, fn.returnType)
            if (arenaNeeded) {
                appendLine("        Arena.ofConfined().use { arena ->")
                appendStringInvokeArgsAlloc("            ", fn.params)
                val invokeArgs = buildTopLevelInvokeArgs(fn)
                appendCallAndReturn("            ", fn.returnType, handleName, invokeArgs)
                appendLine("        }")
            } else {
                val invokeArgs = fn.params.joinToString(", ") { fp ->
                    buildJvmInvokeArg(fp.name, fp.type)
                }
                appendCallAndReturn("        ", fn.returnType, handleName, invokeArgs)
            }

            appendLine("    }")
            appendLine()
        }

        appendLine("}")
    }

    // ── Descriptor builders ──────────────────────────────────────────────────

    private fun buildMethodDescriptor(fn: KneFunction): String {
        val returnDc = extractDataClass(fn.returnType)
        val returnsNullableDc = fn.returnType is KneType.NULLABLE && fn.returnType.inner is KneType.DATA_CLASS
        val paramLayouts = buildList {
            add("JAVA_LONG") // handle
            fn.params.forEach { p ->
                val dc = extractDataClass(p.type)
                if (dc != null) {
                    if (p.type is KneType.NULLABLE) add("JAVA_INT") // isNull flag
                    // Param: String fields are just ADDRESS (null-terminated), no size needed
                    flattenDcFields(dc, "").forEach { (_, type) ->
                        when (type) {
                            KneType.BYTE_ARRAY -> { add("ADDRESS"); add("JAVA_INT") }
                            else -> add(type.ffmLayout)
                        }
                    }
                } else if (p.type == KneType.BYTE_ARRAY) {
                    add("ADDRESS"); add("JAVA_INT")
                } else if (p.type.isCollection()) {
                    val inner = p.type.unwrapCollection()
                    when (inner) {
                        is KneType.LIST, is KneType.SET -> { add("ADDRESS"); add("JAVA_INT") }
                        is KneType.MAP -> { add("ADDRESS"); add("ADDRESS"); add("JAVA_INT") }
                        else -> {}
                    }
                } else {
                    add(p.type.ffmLayout)
                }
            }
            if (fn.returnType.returnsViaBuffer()) {
                add("ADDRESS"); add("JAVA_INT")
            }
            if (returnDc != null) {
                flattenDcFields(returnDc, "").forEach { (_, type) ->
                    when (type) {
                        KneType.STRING, KneType.BYTE_ARRAY -> { add("ADDRESS"); add("JAVA_INT") }
                        else -> add("ADDRESS")
                    }
                }
            }
            // Collection return out-params (unwrap nullable)
            if (fn.returnType.isCollection()) {
                val collInner = fn.returnType.unwrapCollection()
                when (collInner) {
                    is KneType.LIST, is KneType.SET -> {
                        add("ADDRESS"); add("JAVA_INT") // outBuf + outLen/outBufLen
                    }
                    is KneType.MAP -> {
                        add("ADDRESS") // outKeys
                        if (collInner.keyType == KneType.STRING) add("JAVA_INT")
                        add("ADDRESS") // outValues
                        if (collInner.valueType == KneType.STRING) add("JAVA_INT")
                        add("JAVA_INT") // outLen
                    }
                    else -> {}
                }
            }
        }
        val effectiveReturn = when {
            returnDc != null && returnsNullableDc -> KneType.INT // 0=null, 1=present
            returnDc != null -> KneType.UNIT
            fn.returnType.isCollection() -> KneType.INT // element count
            else -> fn.returnType
        }
        return buildDescriptor(effectiveReturn, paramLayouts)
    }

    private fun buildGetterDescriptor(prop: KneProperty): String {
        val paramLayouts = buildList {
            add("JAVA_LONG")
            if (prop.type.returnsViaBuffer()) {
                add("ADDRESS"); add("JAVA_INT")
            }
        }
        return buildDescriptor(prop.type, paramLayouts)
    }

    private fun buildCompanionGetterDescriptor(prop: KneProperty): String {
        val paramLayouts = buildList {
            if (prop.type.returnsViaBuffer()) {
                add("ADDRESS"); add("JAVA_INT")
            }
        }
        return buildDescriptor(prop.type, paramLayouts)
    }

    private fun buildTopLevelDescriptor(fn: KneFunction): String {
        val paramLayouts = buildList {
            fn.params.forEach { p -> add(p.type.ffmLayout) }
            if (fn.returnType.returnsViaBuffer()) {
                add("ADDRESS"); add("JAVA_INT")
            }
        }
        return buildDescriptor(fn.returnType, paramLayouts)
    }

    private fun buildDescriptor(returnType: KneType, paramLayouts: List<String>): String {
        val params = paramLayouts.filter { it.isNotEmpty() }.joinToString(", ")
        return if (returnType == KneType.UNIT || returnType.returnsViaBuffer()) {
            val retLayout = if (returnType.returnsViaBuffer()) "JAVA_INT" else ""
            if (retLayout.isEmpty()) "FunctionDescriptor.ofVoid($params)"
            else "FunctionDescriptor.of($retLayout${if (params.isNotEmpty()) ", $params" else ""})"
        } else {
            "FunctionDescriptor.of(${returnType.ffmLayout}${if (params.isNotEmpty()) ", $params" else ""})"
        }
    }

    private fun buildLayouts(types: List<KneType>): String =
        types.filter { it.ffmLayout.isNotEmpty() }.joinToString("") { ", ${it.ffmLayout}" }

    // ── Invoke arg builders ──────────────────────────────────────────────────

    private fun buildJvmInvokeArg(name: String, type: KneType): String = when (type) {
        KneType.STRING -> "${name}Seg"
        KneType.BYTE_ARRAY -> "${name}Seg"
        KneType.BOOLEAN -> "if ($name) 1 else 0"
        is KneType.OBJECT -> "$name.handle"
        is KneType.ENUM -> "$name.ordinal"
        is KneType.NULLABLE -> buildNullableJvmInvokeArg(name, type)
        is KneType.FUNCTION -> "${name}Stub"
        is KneType.LIST -> "${name}Seg"
        is KneType.SET -> "${name}Seg"
        is KneType.MAP -> "${name}Seg" // shouldn't be reached; MAP expands to keys+values
        else -> name
    }

    private fun buildNullableJvmInvokeArg(name: String, type: KneType.NULLABLE): String = when (type.inner) {
        KneType.STRING -> "${name}Seg"
        KneType.BOOLEAN -> "if ($name == null) -1 else if ($name) 1 else 0"
        KneType.INT -> "$name?.toLong() ?: Long.MIN_VALUE"
        KneType.LONG -> "$name ?: Long.MIN_VALUE"
        KneType.SHORT -> "$name?.toInt() ?: Int.MIN_VALUE"
        KneType.BYTE -> "$name?.toInt() ?: Int.MIN_VALUE"
        KneType.FLOAT -> "if ($name != null) $name.toRawBits().toLong() else Long.MIN_VALUE"
        KneType.DOUBLE -> "if ($name != null) $name.toRawBits() else Long.MIN_VALUE"
        is KneType.OBJECT -> "$name?.handle ?: 0L"
        is KneType.ENUM -> "$name?.ordinal ?: -1"
        else -> name
    }

    private fun buildCtorInvokeArgs(params: List<KneParam>): String {
        if (params.isEmpty()) return ""
        return params.joinToString(", ") { p -> buildJvmInvokeArg(p.name, p.type) }
    }

    private fun buildClassInvokeArgs(fn: KneFunction): String {
        val args = buildList {
            add("handle")
            fn.params.forEach { p -> add(buildJvmInvokeArg(p.name, p.type)) }
            if (fn.returnType.returnsViaBuffer()) { add("buf"); add("$STRING_BUF_SIZE") }
        }
        return args.joinToString(", ")
    }

    private fun buildClassInvokeArgsDirect(fn: KneFunction): String {
        val args = buildList {
            add("handle")
            fn.params.forEach { p -> add(buildJvmInvokeArg(p.name, p.type)) }
        }
        return args.joinToString(", ")
    }

    private fun buildTopLevelInvokeArgs(fn: KneFunction): String {
        val args = buildList {
            fn.params.forEach { p -> add(buildJvmInvokeArg(p.name, p.type)) }
            if (fn.returnType.returnsViaBuffer()) { add("buf"); add("$STRING_BUF_SIZE") }
        }
        return args.joinToString(", ")
    }

    // ── Code emission helpers ────────────────────────────────────────────────

    private fun StringBuilder.appendStringInvokeArgsAlloc(indent: String, params: List<KneParam>) {
        params.filter { it.type == KneType.STRING }.forEach { p ->
            appendLine("${indent}val ${p.name}Seg = arena.allocateFrom(${p.name})")
        }
        params.filter { it.type == KneType.BYTE_ARRAY }.forEach { p ->
            appendLine("${indent}val ${p.name}Seg = arena.allocate(${p.name}.size.toLong())")
            appendLine("${indent}MemorySegment.copy(${p.name}, 0, ${p.name}Seg, JAVA_BYTE, 0, ${p.name}.size)")
        }
        params.filter { it.type is KneType.NULLABLE && (it.type as KneType.NULLABLE).inner == KneType.STRING }.forEach { p ->
            appendLine("${indent}val ${p.name}Seg = if (${p.name} != null) arena.allocateFrom(${p.name}) else MemorySegment.NULL")
        }
        // Allocate String fields from data class params (including nullable)
        params.forEach { p ->
            val dc = extractDataClass(p.type) ?: return@forEach
            val isNullable = p.type is KneType.NULLABLE
            dc.fields.filter { it.type == KneType.STRING }.forEach { f ->
                if (isNullable) {
                    appendLine("${indent}val ${p.name}_${f.name}Seg = if (${p.name} != null) arena.allocateFrom(${p.name}.${f.name}) else MemorySegment.NULL")
                } else {
                    appendLine("${indent}val ${p.name}_${f.name}Seg = arena.allocateFrom(${p.name}.${f.name})")
                }
            }
        }
    }

    /** Emit callback stub allocation using the persistent arena. */
    private fun StringBuilder.appendCallbackStubAlloc(indent: String, params: List<KneParam>, arenaExpr: String) {
        params.filter { it.type is KneType.FUNCTION }.forEach { p ->
            val fnType = p.type as KneType.FUNCTION
            val id = callbackId(fnType)
            appendLine("${indent}val ${p.name}Stub = KneRuntime.createUpcallStub_$id(${p.name}, $arenaExpr)")
        }
    }

    // ── Collection marshaling ────────────────────────────────────────────────

    /** Allocate MemorySegments for collection parameters (including nullable). */
    private fun StringBuilder.appendCollectionParamAlloc(indent: String, params: List<KneParam>) {
        params.forEach { p ->
            val isNullable = p.type is KneType.NULLABLE
            val inner = p.type.unwrapCollection()
            when (inner) {
                is KneType.LIST -> {
                    if (isNullable) {
                        appendLine("${indent}val ${p.name}Unwrapped = ${p.name} ?: emptyList()")
                        appendListParamAlloc(indent, p.name, inner.elementType, srcExpr = "${p.name}Unwrapped")
                    } else {
                        appendListParamAlloc(indent, p.name, inner.elementType)
                    }
                }
                is KneType.SET -> {
                    if (isNullable) {
                        appendLine("${indent}val ${p.name}AsList = ${p.name}?.toList() ?: emptyList()")
                    } else {
                        appendLine("${indent}val ${p.name}AsList = ${p.name}.toList()")
                    }
                    appendListParamAlloc(indent, p.name, inner.elementType, srcExpr = "${p.name}AsList")
                }
                is KneType.MAP -> {
                    if (isNullable) {
                        appendLine("${indent}val ${p.name}Unwrapped = ${p.name} ?: emptyMap()")
                        appendMapParamAlloc(indent, p.name, inner, srcExpr = "${p.name}Unwrapped")
                    } else {
                        appendMapParamAlloc(indent, p.name, inner)
                    }
                }
                else -> {}
            }
        }
    }

    private fun StringBuilder.appendListParamAlloc(indent: String, name: String, elemType: KneType, srcExpr: String = name) {
        when (elemType) {
            KneType.STRING -> {
                // Pack strings as null-terminated sequence in a single buffer
                appendLine("${indent}val ${name}TotalBytes = $srcExpr.sumOf { it.toByteArray(Charsets.UTF_8).size + 1 }")
                appendLine("${indent}val ${name}Seg = arena.allocate(${name}TotalBytes.toLong().coerceAtLeast(1))")
                appendLine("${indent}var ${name}Off = 0L")
                appendLine("${indent}for (_s in $srcExpr) { ${name}Seg.setString(${name}Off, _s); ${name}Off += _s.toByteArray(Charsets.UTF_8).size + 1 }")
            }
            KneType.BOOLEAN -> {
                appendLine("${indent}val ${name}Seg = arena.allocate(JAVA_INT, $srcExpr.size.toLong())")
                appendLine("${indent}$srcExpr.forEachIndexed { i, v -> ${name}Seg.setAtIndex(JAVA_INT, i.toLong(), if (v) 1 else 0) }")
            }
            is KneType.ENUM -> {
                appendLine("${indent}val ${name}Seg = arena.allocate(JAVA_INT, $srcExpr.size.toLong())")
                appendLine("${indent}$srcExpr.forEachIndexed { i, v -> ${name}Seg.setAtIndex(JAVA_INT, i.toLong(), v.ordinal) }")
            }
            is KneType.OBJECT -> {
                appendLine("${indent}val ${name}Seg = arena.allocate(JAVA_LONG, $srcExpr.size.toLong())")
                appendLine("${indent}$srcExpr.forEachIndexed { i, v -> ${name}Seg.setAtIndex(JAVA_LONG, i.toLong(), v.handle) }")
            }
            else -> {
                val layout = KneType.collectionElementLayout(elemType)
                appendLine("${indent}val ${name}Seg = arena.allocate($layout, $srcExpr.size.toLong())")
                appendLine("${indent}$srcExpr.forEachIndexed { i, v -> ${name}Seg.setAtIndex($layout, i.toLong(), v) }")
            }
        }
    }

    private fun StringBuilder.appendMapParamAlloc(indent: String, name: String, type: KneType.MAP, srcExpr: String = name) {
        appendLine("${indent}val ${name}KeysList = ${srcExpr}.keys.toList()")
        appendLine("${indent}val ${name}ValuesList = ${srcExpr}.values.toList()")
        appendListParamAlloc(indent, "${name}_keys", type.keyType, srcExpr = "${name}KeysList")
        appendListParamAlloc(indent, "${name}_values", type.valueType, srcExpr = "${name}ValuesList")
    }

    /** Generate the return-proxy for collection types (handles nullable: -1 = null). */
    private fun StringBuilder.appendCollectionReturnProxy(indent: String, fn: KneFunction, handleName: String) {
        val isNullable = fn.returnType is KneType.NULLABLE
        val inner = fn.returnType.unwrapCollection()
        when (inner) {
            is KneType.LIST -> appendListReturnProxy(indent, fn, handleName, inner.elementType, "List", isNullable)
            is KneType.SET -> appendListReturnProxy(indent, fn, handleName, inner.elementType, "Set", isNullable)
            is KneType.MAP -> appendMapReturnProxy(indent, fn, handleName, inner, isNullable)
            else -> {}
        }
    }

    private fun StringBuilder.appendListReturnProxy(indent: String, fn: KneFunction, handleName: String, elemType: KneType, collType: String, nullable: Boolean = false) {
        when (elemType) {
            KneType.STRING -> {
                appendLine("${indent}val _outBuf = arena.allocate($STRING_BUF_SIZE.toLong())")
                val invokeArgs = buildClassInvokeArgsExpanded(fn) + ", _outBuf, $STRING_BUF_SIZE"
                appendLine("${indent}val _count = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                if (nullable) appendLine("${indent}if (_count < 0) return null")
                appendLine("${indent}val _list = mutableListOf<String>()")
                appendLine("${indent}var _off = 0L")
                appendLine("${indent}repeat(_count) { _list.add(_outBuf.getString(_off)); _off += _list.last().toByteArray(Charsets.UTF_8).size + 1 }")
                if (collType == "Set") appendLine("${indent}return _list.toSet()")
                else appendLine("${indent}return _list")
            }
            else -> {
                val layout = KneType.collectionElementLayout(elemType)
                appendLine("${indent}val _outBuf = arena.allocate($layout, $MAX_COLLECTION_SIZE.toLong())")
                val invokeArgs = buildClassInvokeArgsExpanded(fn) + ", _outBuf, $MAX_COLLECTION_SIZE"
                appendLine("${indent}val _count = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                if (nullable) appendLine("${indent}if (_count < 0) return null")
                appendCollectionElementRead(indent, elemType, "_count", collType)
            }
        }
    }

    private fun StringBuilder.appendCollectionElementRead(indent: String, elemType: KneType, countExpr: String, collType: String) {
        when (elemType) {
            KneType.BOOLEAN -> {
                appendLine("${indent}val _list = List($countExpr) { _outBuf.getAtIndex(JAVA_INT, it.toLong()) != 0 }")
            }
            is KneType.ENUM -> {
                appendLine("${indent}val _list = List($countExpr) { ${elemType.simpleName}.entries[_outBuf.getAtIndex(JAVA_INT, it.toLong())] }")
            }
            is KneType.OBJECT -> {
                appendLine("${indent}val _list = List($countExpr) { ${elemType.simpleName}.fromNativeHandle(_outBuf.getAtIndex(JAVA_LONG, it.toLong()) as Long) }")
            }
            else -> {
                val layout = KneType.collectionElementLayout(elemType)
                appendLine("${indent}val _list = List($countExpr) { _outBuf.getAtIndex($layout, it.toLong()) as ${elemType.jvmTypeName} }")
            }
        }
        if (collType == "Set") appendLine("${indent}return _list.toSet()")
        else appendLine("${indent}return _list")
    }

    private fun StringBuilder.appendMapReturnProxy(indent: String, fn: KneFunction, handleName: String, type: KneType.MAP, nullable: Boolean = false) {
        val kLayout = KneType.collectionElementLayout(type.keyType)
        val vLayout = KneType.collectionElementLayout(type.valueType)
        val isKeyString = type.keyType == KneType.STRING
        val isValString = type.valueType == KneType.STRING
        if (isKeyString) appendLine("${indent}val _keysBuf = arena.allocate($STRING_BUF_SIZE.toLong())")
        else appendLine("${indent}val _keysBuf = arena.allocate($kLayout, $MAX_COLLECTION_SIZE.toLong())")
        if (isValString) appendLine("${indent}val _valuesBuf = arena.allocate($STRING_BUF_SIZE.toLong())")
        else appendLine("${indent}val _valuesBuf = arena.allocate($vLayout, $MAX_COLLECTION_SIZE.toLong())")

        val invokeArgs = buildList {
            add("handle")
            fn.params.forEach { p -> addAll(buildExpandedInvokeArgs(p)) }
            add("_keysBuf")
            if (isKeyString) add("$STRING_BUF_SIZE")
            add("_valuesBuf")
            if (isValString) add("$STRING_BUF_SIZE")
            add("$MAX_COLLECTION_SIZE")
        }.joinToString(", ")

        appendLine("${indent}val _count = $handleName.invoke($invokeArgs) as Int")
        appendLine("${indent}KneRuntime.checkError()")
        if (nullable) appendLine("${indent}if (_count < 0) return null")
        appendLine("${indent}val _map = mutableMapOf<${type.keyType.jvmTypeName}, ${type.valueType.jvmTypeName}>()")
        // Read keys
        if (isKeyString) {
            appendLine("${indent}val _keys = mutableListOf<String>()")
            appendLine("${indent}var _kOff = 0L")
            appendLine("${indent}repeat(_count) { _keys.add(_keysBuf.getString(_kOff)); _kOff += _keys.last().toByteArray(Charsets.UTF_8).size + 1 }")
        } else {
            appendMapElementRead(indent, "_keys", type.keyType, "_count", "_keysBuf")
        }
        // Read values
        if (isValString) {
            appendLine("${indent}val _values = mutableListOf<String>()")
            appendLine("${indent}var _vOff = 0L")
            appendLine("${indent}repeat(_count) { _values.add(_valuesBuf.getString(_vOff)); _vOff += _values.last().toByteArray(Charsets.UTF_8).size + 1 }")
        } else {
            appendMapElementRead(indent, "_values", type.valueType, "_count", "_valuesBuf")
        }
        appendLine("${indent}repeat(_count) { _map[_keys[it]] = _values[it] }")
        appendLine("${indent}return _map")
    }

    private fun StringBuilder.appendMapElementRead(indent: String, varName: String, elemType: KneType, countExpr: String, bufExpr: String) {
        val layout = KneType.collectionElementLayout(elemType)
        when (elemType) {
            KneType.BOOLEAN -> appendLine("${indent}val $varName = List($countExpr) { $bufExpr.getAtIndex(JAVA_INT, it.toLong()) != 0 }")
            is KneType.ENUM -> appendLine("${indent}val $varName = List($countExpr) { ${elemType.simpleName}.entries[$bufExpr.getAtIndex(JAVA_INT, it.toLong())] }")
            else -> appendLine("${indent}val $varName = List($countExpr) { $bufExpr.getAtIndex($layout, it.toLong()) as ${elemType.jvmTypeName} }")
        }
    }

    /**
     * Emits a handle invocation with checkError() after the call.
     * For value-returning functions, stores result in a local var, checks error, then returns.
     */
    private fun StringBuilder.appendCallAndReturn(
        indent: String,
        returnType: KneType,
        handleName: String,
        invokeArgs: String,
    ) {
        when (returnType) {
            KneType.UNIT -> {
                appendLine("${indent}$handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            KneType.STRING -> {
                appendLine("${indent}val buf = arena.allocate($STRING_BUF_SIZE.toLong())")
                appendLine("${indent}$handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return buf.getString(0)")
            }
            KneType.BYTE_ARRAY -> {
                appendLine("${indent}val buf = arena.allocate($STRING_BUF_SIZE.toLong())")
                appendLine("${indent}val len = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return buf.asSlice(0, len.toLong()).toArray(JAVA_BYTE)")
            }
            KneType.BOOLEAN -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r != 0")
            }
            KneType.INT -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
            KneType.LONG -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
            KneType.DOUBLE -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Double")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
            KneType.FLOAT -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Float")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
            KneType.BYTE -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Byte")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
            KneType.SHORT -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Short")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
            is KneType.OBJECT -> {
                appendLine("${indent}val resultHandle = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return ${returnType.simpleName}.fromNativeHandle(resultHandle)")
            }
            is KneType.ENUM -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return ${returnType.simpleName}.entries[_r]")
            }
            is KneType.NULLABLE -> appendNullableCallAndReturn(indent, returnType, handleName, invokeArgs)
            is KneType.FUNCTION -> {
                appendLine("${indent}$handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            is KneType.DATA_CLASS -> {
                // DATA_CLASS returns are handled separately in appendMethodProxy
                appendLine("${indent}$handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            is KneType.LIST, is KneType.SET, is KneType.MAP -> {
                // Collection returns are handled separately in appendCollectionReturnProxy
                appendLine("${indent}$handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
            }
        }
    }

    private fun StringBuilder.appendNullableCallAndReturn(
        indent: String,
        type: KneType.NULLABLE,
        handleName: String,
        invokeArgs: String,
    ) {
        when (type.inner) {
            KneType.STRING -> {
                appendLine("${indent}val buf = arena.allocate($STRING_BUF_SIZE.toLong())")
                appendLine("${indent}val len = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (len < 0) null else buf.getString(0)")
            }
            KneType.BOOLEAN -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw < 0) null else raw != 0")
            }
            KneType.INT -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw == Long.MIN_VALUE) null else raw.toInt()")
            }
            KneType.LONG -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw == Long.MIN_VALUE) null else raw")
            }
            KneType.SHORT -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw == Int.MIN_VALUE) null else raw.toShort()")
            }
            KneType.BYTE -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw == Int.MIN_VALUE) null else raw.toByte()")
            }
            KneType.FLOAT -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw == Long.MIN_VALUE) null else Float.fromBits(raw.toInt())")
            }
            KneType.DOUBLE -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw == Long.MIN_VALUE) null else Double.fromBits(raw)")
            }
            is KneType.OBJECT -> {
                appendLine("${indent}val resultHandle = $handleName.invoke($invokeArgs) as Long")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (resultHandle == 0L) null else ${type.inner.simpleName}.fromNativeHandle(resultHandle)")
            }
            is KneType.ENUM -> {
                appendLine("${indent}val raw = $handleName.invoke($invokeArgs) as Int")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return if (raw < 0) null else ${type.inner.simpleName}.entries[raw]")
            }
            KneType.UNIT -> {
                appendLine("${indent}$handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            else -> {
                appendLine("${indent}val _r = $handleName.invoke($invokeArgs)")
                appendLine("${indent}KneRuntime.checkError()")
                appendLine("${indent}return _r")
            }
        }
    }

    private fun StringBuilder.appendSetterInvoke(indent: String, handleName: String, type: KneType, handleArg: String?) {
        val prefix = if (handleArg != null) "$handleArg, " else ""
        when (type) {
            KneType.STRING -> {
                appendLine("${indent}Arena.ofConfined().use { arena ->")
                appendLine("${indent}    val valueSeg = arena.allocateFrom(value)")
                appendLine("${indent}    $handleName.invoke(${prefix}valueSeg)")
                appendLine("${indent}    KneRuntime.checkError()")
                appendLine("${indent}}")
            }
            KneType.BOOLEAN -> {
                appendLine("${indent}$handleName.invoke(${prefix}if (value) 1 else 0)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            is KneType.OBJECT -> {
                appendLine("${indent}$handleName.invoke(${prefix}value.handle)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            is KneType.ENUM -> {
                appendLine("${indent}$handleName.invoke(${prefix}value.ordinal)")
                appendLine("${indent}KneRuntime.checkError()")
            }
            is KneType.NULLABLE -> appendNullableSetterInvoke(indent, handleName, type, handleArg)
            else -> {
                appendLine("${indent}$handleName.invoke(${prefix}value)")
                appendLine("${indent}KneRuntime.checkError()")
            }
        }
    }

    private fun StringBuilder.appendNullableSetterInvoke(indent: String, handleName: String, type: KneType.NULLABLE, handleArg: String?) {
        val prefix = if (handleArg != null) "$handleArg, " else ""
        when (type.inner) {
            KneType.STRING -> {
                appendLine("${indent}Arena.ofConfined().use { arena ->")
                appendLine("${indent}    val valueSeg = if (value != null) arena.allocateFrom(value) else MemorySegment.NULL")
                appendLine("${indent}    $handleName.invoke(${prefix}valueSeg)")
                appendLine("${indent}    KneRuntime.checkError()")
                appendLine("${indent}}")
            }
            else -> {
                val valueExpr = when (type.inner) {
                    KneType.BOOLEAN -> "if (value == null) -1 else if (value) 1 else 0"
                    KneType.INT -> "value?.toLong() ?: Long.MIN_VALUE"
                    KneType.LONG -> "value ?: Long.MIN_VALUE"
                    KneType.SHORT -> "value?.toInt() ?: Int.MIN_VALUE"
                    KneType.BYTE -> "value?.toInt() ?: Int.MIN_VALUE"
                    KneType.FLOAT -> "if (value != null) value.toRawBits().toLong() else Long.MIN_VALUE"
                    KneType.DOUBLE -> "if (value != null) value.toRawBits() else Long.MIN_VALUE"
                    is KneType.OBJECT -> "value?.handle ?: 0L"
                    is KneType.ENUM -> "value?.ordinal ?: -1"
                    else -> "value"
                }
                appendLine("${indent}$handleName.invoke(${prefix}$valueExpr)")
                appendLine("${indent}KneRuntime.checkError()")
            }
        }
    }
}
