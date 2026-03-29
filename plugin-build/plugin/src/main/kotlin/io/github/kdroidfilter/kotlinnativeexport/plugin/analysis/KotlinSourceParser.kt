package io.github.kdroidfilter.kotlinnativeexport.plugin.analysis

import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneClass
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneConstructor
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneDataClass
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneEnum
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneFunction
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneModule
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneParam
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneProperty
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneType
import java.io.File

/**
 * Parses Kotlin source files to extract public API declarations.
 *
 * Inspired by swift-export-standalone's module translation approach:
 * reads source declarations to build an intermediate representation.
 */
class KotlinSourceParser {

    companion object {
        private val PACKAGE_RE = Regex("""^package\s+([\w.]+)""")

        // Matches class declarations (excludes private/internal/protected and enum classes)
        private val CLASS_RE = Regex(
            """^(?:(?:public|open|data|abstract|sealed|expect|actual)\s+)*class\s+(\w+)"""
        )

        // Matches enum class declarations
        private val ENUM_CLASS_RE = Regex(
            """^(?:(?:public|expect|actual)\s+)*enum\s+class\s+(\w+)"""
        )

        // Matches fun declarations (name only — params extracted manually for nested-paren support)
        private val FUN_START_RE = Regex(
            """^(?:(?:public|open|override|operator|inline|actual)\s+)*fun\s+(\w+)\s*\("""
        )

        // Matches property declarations inside a class
        private val PROP_RE = Regex(
            """^(?:(?:public|open|override|lateinit)\s+)*(val|var)\s+(\w+)\s*:\s*([\w.<>?,\s?]+?)(?:\s*[=\n{]|$)"""
        )

        // Matches constructor parameters (handles val/var prefix)
        private val CTOR_PARAM_RE = Regex("""(?:(?:private|protected|public|internal)\s+)?(?:val|var\s+)?(\w+)\s*:\s*([\w.<>?,\s?]+?)(?:\s*=.*)?$""")

        // Matches companion object declaration
        private val COMPANION_RE = Regex("""companion\s+object\s*(\w*)\s*[\{:]?""")

        private val SKIP_MODIFIERS = setOf("private", "internal", "protected")
    }

    fun parse(files: Collection<File>, libName: String, commonFiles: Collection<File> = emptyList()): KneModule {
        val ktFiles = files.filter { it.extension == "kt" }
        val commonKtFiles = commonFiles.filter { it.extension == "kt" }

        // Phase 1: collect all type names and raw data class param strings
        val knownClasses = mutableMapOf<String, String>()
        val knownEnums = mutableMapOf<String, String>()
        val rawDataClasses = mutableMapOf<String, Pair<String, String>>() // name -> (fqName, rawParamsStr)
        val commonDataClassNames = mutableSetOf<String>()
        val allFiles = commonKtFiles + ktFiles
        for (file in commonKtFiles) {
            prescanTypeNames(file, knownClasses, knownEnums, rawDataClasses)
            rawDataClasses.keys.forEach { commonDataClassNames.add(it) }
        }
        for (file in ktFiles) {
            prescanTypeNames(file, knownClasses, knownEnums, rawDataClasses)
        }

        // Phase 2: resolve data class fields iteratively (handles dependencies between data classes)
        val knownDataClasses = mutableMapOf<String, Pair<String, List<KneParam>>>()
        var changed = true
        while (changed) {
            changed = false
            for ((name, info) in rawDataClasses) {
                if (name in knownDataClasses) continue
                val fields = resolveDataClassFields(info.second, knownEnums, knownClasses, knownDataClasses)
                if (fields != null) {
                    knownDataClasses[name] = Pair(info.first, fields)
                    changed = true
                }
            }
        }
        // Unresolved data classes fall back to regular classes
        for ((name, info) in rawDataClasses) {
            if (name !in knownDataClasses) knownClasses[name] = info.first
        }
        val knownTypes = TypeMaps(knownClasses, knownEnums, knownDataClasses)

        val classes = mutableListOf<KneClass>()
        val dataClasses = mutableListOf<KneDataClass>()
        val enums = mutableListOf<KneEnum>()
        val functions = mutableListOf<KneFunction>()
        val packages = mutableSetOf<String>()

        // Only parse nativeMain files for classes/methods/functions
        // (commonMain data classes are discovered via prescan but not parsed for methods)
        for (file in ktFiles) {
            val result = parseFile(file, knownTypes, commonDataClassNames)
            classes.addAll(result.classes)
            dataClasses.addAll(result.dataClasses)
            enums.addAll(result.enums)
            functions.addAll(result.functions)
            result.packageName?.let { packages.add(it) }
        }

        // Add common data classes that are referenced in native code
        // (they appear in knownDataClasses from prescan but weren't in nativeMain parseFile)
        for (name in commonDataClassNames) {
            val dcInfo = knownDataClasses[name] ?: continue
            if (dataClasses.none { it.simpleName == name }) {
                dataClasses.add(KneDataClass(simpleName = name, fqName = dcInfo.first, fields = dcInfo.second, isCommon = true))
            }
        }

        return KneModule(libName = libName, packages = packages, classes = classes, dataClasses = dataClasses, enums = enums, functions = functions)
    }

    internal data class TypeMaps(
        val classes: Map<String, String>,
        val enums: Map<String, String>,
        val dataClasses: Map<String, Pair<String, List<KneParam>>> = emptyMap(),
    )

    /**
     * Phase 1: collects type names and raw data class constructor strings.
     */
    private fun prescanTypeNames(
        file: File,
        knownClasses: MutableMap<String, String>,
        knownEnums: MutableMap<String, String>,
        rawDataClasses: MutableMap<String, Pair<String, String>>,
    ) {
        var packageName = ""
        for (rawLine in file.readLines()) {
            val line = rawLine.substringBefore("//").trim()
            PACKAGE_RE.find(line)?.let { packageName = it.groupValues[1] }

            val isEnum = rawLine.trimStart().let { it.startsWith("enum ") || it.contains(" enum ") }
            val isDataClass = line.startsWith("data class ") || line.contains(" data class ")

            if (isEnum) {
                ENUM_CLASS_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    val fq = if (packageName.isNotEmpty()) "$packageName.$name" else name
                    knownEnums[name] = fq
                }
            } else if (isDataClass) {
                CLASS_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    val fq = if (packageName.isNotEmpty()) "$packageName.$name" else name
                    val ctorMatch = Regex("""class\s+\w+\s*\(([^)]*)\)""").find(line)
                    if (ctorMatch != null) {
                        rawDataClasses[name] = Pair(fq, ctorMatch.groupValues[1])
                    } else {
                        knownClasses[name] = fq
                    }
                }
            } else {
                CLASS_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    val fq = if (packageName.isNotEmpty()) "$packageName.$name" else name
                    knownClasses[name] = fq
                }
            }
        }
    }

    /**
     * Phase 2: resolve data class fields using full type knowledge.
     * Returns null if any field type can't be resolved yet.
     */
    private fun resolveDataClassFields(
        paramsStr: String,
        knownEnums: Map<String, String>,
        knownClasses: Map<String, String>,
        knownDataClasses: Map<String, Pair<String, List<KneParam>>>,
    ): List<KneParam>? {
        if (paramsStr.isBlank()) return null
        val fields = mutableListOf<KneParam>()
        for (raw in paramsStr.split(",")) {
            val s = raw.trim()
            if (!s.contains(Regex("""\bval\b|\bvar\b"""))) continue
            val colonIdx = s.indexOf(':')
            if (colonIdx < 0) return null
            val name = s.substring(0, colonIdx).trim().substringAfterLast(' ').trim()
            val typeStr = s.substring(colonIdx + 1).substringBefore('=').trim()
            val type = when (typeStr) {
                "Int" -> KneType.INT
                "Long" -> KneType.LONG
                "Double" -> KneType.DOUBLE
                "Float" -> KneType.FLOAT
                "Boolean" -> KneType.BOOLEAN
                "Byte" -> KneType.BYTE
                "Short" -> KneType.SHORT
                "String" -> KneType.STRING
                "ByteArray" -> KneType.BYTE_ARRAY
                else -> {
                    // Try collection types
                    val listMatch = Regex("""^(?:Mutable)?List<(.+)>$""").find(typeStr)
                    val setMatch = if (listMatch == null) Regex("""^(?:Mutable)?Set<(.+)>$""").find(typeStr) else null
                    val mapMatch = if (listMatch == null && setMatch == null) Regex("""^(?:Mutable)?Map<(.+)>$""").find(typeStr) else null
                    if (listMatch != null || setMatch != null) {
                        val elemStr = (listMatch ?: setMatch)!!.groupValues[1].trim()
                        val elemType = resolveSimpleType(elemStr, knownEnums, knownClasses, knownDataClasses) ?: return null
                        if (listMatch != null) KneType.LIST(elemType) else KneType.SET(elemType)
                    } else if (mapMatch != null) {
                        val inner = mapMatch.groupValues[1]
                        val comma = inner.indexOf(',')
                        if (comma < 0) return null
                        val kStr = inner.substring(0, comma).trim()
                        val vStr = inner.substring(comma + 1).trim()
                        val kType = resolveSimpleType(kStr, knownEnums, knownClasses, knownDataClasses) ?: return null
                        val vType = resolveSimpleType(vStr, knownEnums, knownClasses, knownDataClasses) ?: return null
                        KneType.MAP(kType, vType)
                    } else {
                        val enumFq = knownEnums[typeStr]
                        if (enumFq != null) KneType.ENUM(enumFq, typeStr)
                        else {
                            val dcInfo = knownDataClasses[typeStr]
                            if (dcInfo != null) KneType.DATA_CLASS(dcInfo.first, typeStr, dcInfo.second)
                            else {
                                val classFq = knownClasses[typeStr]
                                if (classFq != null) KneType.OBJECT(classFq, typeStr)
                                else return null // unresolvable
                            }
                        }
                    }
                }
            }
            fields.add(KneParam(name = name, type = type))
        }
        return if (fields.isNotEmpty()) fields else null
    }

    /** Resolve a simple type string (no function types) for use in data class field resolution. */
    private fun resolveSimpleType(
        typeStr: String,
        knownEnums: Map<String, String>,
        knownClasses: Map<String, String>,
        knownDataClasses: Map<String, Pair<String, List<KneParam>>>,
    ): KneType? = when (typeStr) {
        "Int" -> KneType.INT
        "Long" -> KneType.LONG
        "Double" -> KneType.DOUBLE
        "Float" -> KneType.FLOAT
        "Boolean" -> KneType.BOOLEAN
        "Byte" -> KneType.BYTE
        "Short" -> KneType.SHORT
        "String" -> KneType.STRING
        "ByteArray" -> KneType.BYTE_ARRAY
        else -> {
            knownEnums[typeStr]?.let { KneType.ENUM(it, typeStr) }
                ?: knownDataClasses[typeStr]?.let { KneType.DATA_CLASS(it.first, typeStr, it.second) }
                ?: knownClasses[typeStr]?.let { KneType.OBJECT(it, typeStr) }
        }
    }

    private data class ParseResult(
        val classes: List<KneClass>,
        val dataClasses: List<KneDataClass>,
        val enums: List<KneEnum>,
        val functions: List<KneFunction>,
        val packageName: String?,
    )

    private fun parseFile(file: File, knownTypes: TypeMaps, commonDataClassNames: Set<String> = emptySet()): ParseResult {
        val classes = mutableListOf<KneClass>()
        val dataClasses = mutableListOf<KneDataClass>()
        val enums = mutableListOf<KneEnum>()
        val topLevelFunctions = mutableListOf<KneFunction>()

        var packageName = ""
        var braceDepth = 0
        var inMultiLineComment = false

        // Class parsing state
        var currentClassName: String? = null
        var currentClassFqName: String? = null
        var currentClassCtorParams: List<KneParam> = emptyList()
        var currentClassBraceDepth = -1
        val currentMethods = mutableListOf<KneFunction>()
        val currentProperties = mutableListOf<KneProperty>()

        // Companion object parsing state
        var inCompanionObject = false
        var companionBraceDepth = -1
        val currentCompanionMethods = mutableListOf<KneFunction>()
        val currentCompanionProperties = mutableListOf<KneProperty>()

        // Enum parsing state
        var currentEnumName: String? = null
        var currentEnumFqName: String? = null
        var currentEnumBraceDepth = -1
        val currentEnumEntries = mutableListOf<String>()
        var enumEntriesDone = false

        for (rawLine in file.readLines()) {
            // Handle multi-line comments
            if (inMultiLineComment) {
                if (rawLine.contains("*/")) inMultiLineComment = false
                continue
            }
            if (rawLine.trimStart().startsWith("/*")) {
                if (!rawLine.contains("*/")) inMultiLineComment = true
                continue
            }

            // Strip inline comment and trim
            val line = rawLine.substringBefore("//").trim()
            if (line.isEmpty() || line.startsWith("@") || line.startsWith("import ")) continue

            val isPrivate = SKIP_MODIFIERS.any { line.startsWith("$it ") }
            val isExpect = line.startsWith("expect ") || line.contains(" expect ")
            val isEnum = line.startsWith("enum ") || line.contains(" enum ")

            // Package
            PACKAGE_RE.find(line)?.let { packageName = it.groupValues[1] }

            // Count braces before processing declarations (so we know context)
            val openCount = line.count { it == '{' }
            val closeCount = line.count { it == '}' }

            // Enum class declaration
            if (!isPrivate && !isExpect && isEnum) {
                ENUM_CLASS_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    if (braceDepth == 0) {
                        currentEnumName = name
                        currentEnumFqName = if (packageName.isNotEmpty()) "$packageName.$name" else name
                        currentEnumBraceDepth = braceDepth
                        currentEnumEntries.clear()
                        enumEntriesDone = false
                    }
                }
            }

            // Collect enum entries (before the first fun/val/var/companion/semicolon-only line)
            if (currentEnumName != null && !enumEntriesDone && braceDepth == currentEnumBraceDepth + 1) {
                val entryLine = line.trimEnd(',', ';')
                when {
                    line.startsWith("fun ") || line.startsWith("val ") || line.startsWith("var ") ||
                        line.startsWith("companion ") || line.startsWith("override ") ||
                        line.startsWith("open ") || line.startsWith("abstract ") ||
                        line == ";" -> enumEntriesDone = true
                    entryLine.matches(Regex("""\w+(\([^)]*\))?""")) -> {
                        val entryName = entryLine.substringBefore('(').trim()
                        if (entryName.isNotEmpty() && entryName.first().isUpperCase()) {
                            currentEnumEntries.add(entryName)
                        }
                    }
                }
            }

            val isDataClass = line.startsWith("data class ") || line.contains(" data class ")

            // Class declaration (skip expect classes, enum classes, and data classes)
            if (!isPrivate && !isExpect && !isEnum) {
                CLASS_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    // Only top-level classes (braceDepth == 0)
                    if (braceDepth == 0 && isDataClass) {
                        // Data class: add to dataClasses, skip body parsing
                        val dcInfo = knownTypes.dataClasses[name]
                        if (dcInfo != null) {
                            dataClasses.add(KneDataClass(
                                simpleName = name, fqName = dcInfo.first, fields = dcInfo.second,
                                isCommon = name in commonDataClassNames,
                            ))
                        }
                    } else if (braceDepth == 0 && !isDataClass) {
                        currentClassName = name
                        currentClassFqName = if (packageName.isNotEmpty()) "$packageName.$name" else name
                        currentClassBraceDepth = braceDepth
                        currentMethods.clear()
                        currentProperties.clear()
                        currentCompanionMethods.clear()
                        currentCompanionProperties.clear()
                        inCompanionObject = false

                        // Parse primary constructor params from class line
                        val ctorMatch = Regex("""class\s+\w+\s*\(([^)]*)\)""").find(line)
                        currentClassCtorParams = if (ctorMatch != null) {
                            parseCtorParams(ctorMatch.groupValues[1], knownTypes)
                        } else {
                            emptyList()
                        }
                    }
                }
            }

            // Companion object detection (inside a class)
            if (currentClassName != null && !inCompanionObject) {
                COMPANION_RE.find(line)?.let {
                    inCompanionObject = true
                    companionBraceDepth = braceDepth
                }
            }

            // Function declaration
            if (!isPrivate && !line.startsWith("class ") && !line.contains("class ")) {
                FUN_START_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    if (name != "init" && !name.startsWith("_")) {
                        val extracted = extractFunParamsAndReturn(line, match.range.last + 1)
                        if (extracted != null) {
                            val (paramsStr, returnStr) = extracted
                            val params = parseFunParams(paramsStr, knownTypes)
                            val returnType = parseType(returnStr, knownTypes)
                            val fn = KneFunction(name = name, params = params, returnType = returnType)
                            when {
                                inCompanionObject -> currentCompanionMethods.add(fn)
                                currentClassName != null -> currentMethods.add(fn)
                                braceDepth == 0 -> topLevelFunctions.add(fn)
                            }
                        }
                    }
                }
            }

            // Property declaration (inside class only)
            if (!isPrivate && currentClassName != null) {
                PROP_RE.find(line)?.let { match ->
                    val mutable = match.groupValues[1] == "var"
                    val propName = match.groupValues[2]
                    val typeStr = match.groupValues[3].trim()
                    val prop = KneProperty(name = propName, type = parseType(typeStr, knownTypes), mutable = mutable)
                    when {
                        inCompanionObject -> currentCompanionProperties.add(prop)
                        // Skip if it's a constructor parameter already
                        currentClassCtorParams.none { it.name == propName } -> currentProperties.add(prop)
                    }
                }
            }

            braceDepth += openCount
            braceDepth -= closeCount

            // Companion object ended
            if (inCompanionObject && braceDepth <= companionBraceDepth && closeCount > 0) {
                inCompanionObject = false
                companionBraceDepth = -1
            }

            // Enum class ended
            val enumName = currentEnumName
            val enumFqName = currentEnumFqName
            if (enumName != null && enumFqName != null && braceDepth <= currentEnumBraceDepth && closeCount > 0) {
                enums.add(
                    KneEnum(
                        simpleName = enumName,
                        fqName = enumFqName,
                        entries = currentEnumEntries.toList(),
                    )
                )
                currentEnumName = null
                currentEnumFqName = null
                currentEnumBraceDepth = -1
                currentEnumEntries.clear()
            }

            // Class ended
            val className = currentClassName
            val classFqName = currentClassFqName
            if (className != null && classFqName != null && !inCompanionObject && braceDepth <= currentClassBraceDepth && closeCount > 0) {
                classes.add(
                    KneClass(
                        simpleName = className,
                        fqName = classFqName,
                        constructor = KneConstructor(currentClassCtorParams),
                        methods = currentMethods.toList(),
                        properties = currentProperties.toList(),
                        companionMethods = currentCompanionMethods.toList(),
                        companionProperties = currentCompanionProperties.toList(),
                    )
                )
                currentClassName = null
                currentClassFqName = null
                currentClassBraceDepth = -1
                currentClassCtorParams = emptyList()
                currentMethods.clear()
                currentProperties.clear()
                currentCompanionMethods.clear()
                currentCompanionProperties.clear()
            }
        }

        return ParseResult(classes, dataClasses, enums, topLevelFunctions, packageName.takeIf { it.isNotEmpty() })
    }

    /**
     * Given a line like `fun foo(callback: (Int) -> Unit): Int {`
     * and the index right after the opening `(`, finds the matching `)`,
     * extracts the parameter string and the return type.
     * Returns (paramsStr, returnTypeStr) or null if parsing fails.
     */
    private fun extractFunParamsAndReturn(line: String, startAfterParen: Int): Pair<String, String>? {
        var depth = 1
        var i = startAfterParen
        while (i < line.length && depth > 0) {
            when (line[i]) {
                '(' -> depth++
                ')' -> depth--
            }
            i++
        }
        if (depth != 0) return null
        val paramsStr = line.substring(startAfterParen, i - 1) // everything between ( and matching )
        val afterParams = line.substring(i).trim()
        val returnStr = if (afterParams.startsWith(":")) {
            afterParams.substring(1).trim()
                .substringBefore('{').substringBefore('=').trim()
        } else {
            "Unit"
        }
        return Pair(paramsStr, returnStr)
    }

    private fun parseCtorParams(paramsStr: String, knownTypes: TypeMaps): List<KneParam> {
        if (paramsStr.isBlank()) return emptyList()
        return paramsStr.split(",").mapNotNull { raw ->
            val s = raw.trim()
            val m = CTOR_PARAM_RE.find(s) ?: return@mapNotNull null
            KneParam(name = m.groupValues[1], type = parseType(m.groupValues[2].trim(), knownTypes))
        }
    }

    private fun parseFunParams(paramsStr: String, knownTypes: TypeMaps): List<KneParam> {
        if (paramsStr.isBlank()) return emptyList()
        // Split by commas at depth 0 (handle function types with nested commas)
        val parts = splitAtTopLevelCommas(paramsStr)
        return parts.mapNotNull { raw ->
            val s = raw.trim()
            val colonIdx = s.indexOf(':')
            if (colonIdx < 0) return@mapNotNull null
            val name = s.substring(0, colonIdx).trim().substringAfterLast(' ').trimStart('*')
            val typeRaw = s.substring(colonIdx + 1).substringBefore('=').trim()
            KneParam(name = name, type = parseType(typeRaw, knownTypes))
        }
    }

    /** Split a string by commas, ignoring commas inside parentheses or angle brackets. */
    private fun splitAtTopLevelCommas(str: String): List<String> {
        val parts = mutableListOf<String>()
        var depth = 0
        var start = 0
        for (i in str.indices) {
            when (str[i]) {
                '(' -> depth++
                ')' -> depth--
                '<' -> depth++
                '>' -> {
                    // Don't count '>' in '->' as closing angle bracket
                    if (i > 0 && str[i - 1] == '-') { /* skip: arrow operator */ }
                    else depth--
                }
                ',' -> if (depth == 0) {
                    parts.add(str.substring(start, i))
                    start = i + 1
                }
            }
        }
        parts.add(str.substring(start))
        return parts
    }

    internal fun parseType(typeStr: String, knownTypes: TypeMaps = TypeMaps(emptyMap(), emptyMap())): KneType {
        val trimmed = typeStr.trim()
        if (trimmed.endsWith("?")) {
            val inner = parseType(trimmed.dropLast(1), knownTypes)
            return if (inner == KneType.UNIT || inner is KneType.NULLABLE) inner else KneType.NULLABLE(inner)
        }
        // Function type: (Params) -> Return
        if (trimmed.startsWith("(") && trimmed.contains("->")) {
            return parseFunctionType(trimmed, knownTypes) ?: KneType.UNIT
        }
        val clean = trimmed
        // Collection types: List<T>, Set<T>, Map<K,V>, MutableList<T>, MutableSet<T>, MutableMap<K,V>
        val collectionMatch = Regex("""^(Mutable)?List<(.+)>$""").find(clean)
            ?: Regex("""^(Mutable)?Set<(.+)>$""").find(clean)
        if (collectionMatch != null) {
            val elemType = parseType(collectionMatch.groupValues[2].trim(), knownTypes)
            if (elemType == KneType.UNIT) return KneType.UNIT // unsupported element
            return if (clean.contains("Set")) KneType.SET(elemType) else KneType.LIST(elemType)
        }
        val mapMatch = Regex("""^(Mutable)?Map<(.+)>$""").find(clean)
        if (mapMatch != null) {
            val inner = mapMatch.groupValues[2]
            val parts = splitAtTopLevelCommas(inner)
            if (parts.size == 2) {
                val keyType = parseType(parts[0].trim(), knownTypes)
                val valueType = parseType(parts[1].trim(), knownTypes)
                if (keyType != KneType.UNIT && valueType != KneType.UNIT) {
                    return KneType.MAP(keyType, valueType)
                }
            }
        }
        return when (clean) {
            "Int" -> KneType.INT
            "Long" -> KneType.LONG
            "Double" -> KneType.DOUBLE
            "Float" -> KneType.FLOAT
            "Boolean" -> KneType.BOOLEAN
            "Byte" -> KneType.BYTE
            "Short" -> KneType.SHORT
            "String" -> KneType.STRING
            "ByteArray" -> KneType.BYTE_ARRAY
            "Unit", "" -> KneType.UNIT
            else -> {
                val enumFq = knownTypes.enums[clean]
                if (enumFq != null) return KneType.ENUM(enumFq, clean)
                val dcInfo = knownTypes.dataClasses[clean]
                if (dcInfo != null) return KneType.DATA_CLASS(dcInfo.first, clean, dcInfo.second)
                val classFq = knownTypes.classes[clean]
                if (classFq != null) return KneType.OBJECT(classFq, clean)
                KneType.UNIT // Unsupported types are silently skipped for now
            }
        }
    }

    /**
     * Parses a function type like `(Int, Double) -> Unit` into KneType.FUNCTION.
     * Returns null if the function type contains unsupported parameter/return types.
     */
    private fun parseFunctionType(typeStr: String, knownTypes: TypeMaps): KneType.FUNCTION? {
        // Find the closing paren for the params (handle nested parens)
        var depth = 0
        var closeIdx = -1
        for (i in typeStr.indices) {
            when (typeStr[i]) {
                '(' -> depth++
                ')' -> { depth--; if (depth == 0) { closeIdx = i; break } }
            }
        }
        if (closeIdx < 0) return null

        val paramsStr = typeStr.substring(1, closeIdx).trim()
        val afterParen = typeStr.substring(closeIdx + 1).trim()
        if (!afterParen.startsWith("->")) return null
        val returnStr = afterParen.substring(2).trim()

        val paramTypes = if (paramsStr.isEmpty()) emptyList()
        else splitAtTopLevelCommas(paramsStr).map { parseType(it.trim(), knownTypes) }

        val supportedCallbackPrimitive = setOf(
            KneType.INT, KneType.LONG, KneType.DOUBLE, KneType.FLOAT,
            KneType.BOOLEAN, KneType.BYTE, KneType.SHORT, KneType.STRING,
        )
        val supportedCallbackReturns = setOf(
            KneType.INT, KneType.LONG, KneType.DOUBLE, KneType.FLOAT,
            KneType.BOOLEAN, KneType.BYTE, KneType.SHORT, KneType.UNIT,
            KneType.STRING,
        )
        val returnType = parseType(returnStr, knownTypes)
        fun isSupportedCallbackParam(t: KneType): Boolean =
            t in supportedCallbackPrimitive || t is KneType.DATA_CLASS || t is KneType.ENUM ||
            t is KneType.LIST || t is KneType.SET || t is KneType.MAP
        fun isSupportedCallbackReturn(t: KneType): Boolean =
            t in supportedCallbackReturns || t is KneType.ENUM || t is KneType.DATA_CLASS ||
            t is KneType.LIST || t is KneType.SET || t is KneType.MAP
        val unsupportedParam = paramTypes.any { !isSupportedCallbackParam(it) }
        if (unsupportedParam || !isSupportedCallbackReturn(returnType)) return null

        return KneType.FUNCTION(paramTypes, returnType)
    }
}
