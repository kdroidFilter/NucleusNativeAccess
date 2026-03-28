package io.github.kdroidfilter.kotlinnativeexport.plugin.analysis

import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneClass
import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneConstructor
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

        // Matches class declarations (excludes private/internal/protected)
        private val CLASS_RE = Regex(
            """^(?:(?:public|open|data|abstract|sealed)\s+)*class\s+(\w+)"""
        )

        // Matches fun declarations
        private val FUN_RE = Regex(
            """^(?:(?:public|open|override|operator|inline)\s+)*fun\s+(\w+)\s*\(([^)]*)\)(?:\s*:\s*([\w.<>?,\s]+?))?(?:\s*[{=]|$)"""
        )

        // Matches property declarations inside a class
        private val PROP_RE = Regex(
            """^(?:(?:public|open|override|lateinit)\s+)*(val|var)\s+(\w+)\s*:\s*([\w.<>?,\s?]+?)(?:\s*[=\n{]|$)"""
        )

        // Matches constructor parameters (handles val/var prefix)
        private val CTOR_PARAM_RE = Regex("""(?:(?:private|protected|public|internal)\s+)?(?:val|var\s+)?(\w+)\s*:\s*([\w.<>?,\s?]+?)(?:\s*=.*)?$""")

        private val SKIP_MODIFIERS = setOf("private", "internal", "protected")
    }

    fun parse(files: Collection<File>, libName: String): KneModule {
        val classes = mutableListOf<KneClass>()
        val functions = mutableListOf<KneFunction>()
        val packages = mutableSetOf<String>()

        for (file in files.filter { it.extension == "kt" }) {
            val result = parseFile(file)
            classes.addAll(result.first)
            functions.addAll(result.second)
            result.third?.let { packages.add(it) }
        }

        return KneModule(libName = libName, packages = packages, classes = classes, functions = functions)
    }

    private fun parseFile(file: File): Triple<List<KneClass>, List<KneFunction>, String?> {
        val classes = mutableListOf<KneClass>()
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

            // Package
            PACKAGE_RE.find(line)?.let { packageName = it.groupValues[1] }

            // Count braces before processing declarations (so we know context)
            val openCount = line.count { it == '{' }
            val closeCount = line.count { it == '}' }

            // Class declaration
            if (!isPrivate) {
                CLASS_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    // Only top-level classes (braceDepth == 0)
                    if (braceDepth == 0) {
                        currentClassName = name
                        currentClassFqName = if (packageName.isNotEmpty()) "$packageName.$name" else name
                        currentClassBraceDepth = braceDepth
                        currentMethods.clear()
                        currentProperties.clear()

                        // Parse primary constructor params from class line
                        val ctorMatch = Regex("""class\s+\w+\s*\(([^)]*)\)""").find(line)
                        currentClassCtorParams = if (ctorMatch != null) {
                            parseCtorParams(ctorMatch.groupValues[1])
                        } else {
                            emptyList()
                        }
                    }
                }
            }

            // Function declaration
            if (!isPrivate && !line.startsWith("class ") && !line.contains("class ")) {
                FUN_RE.find(line)?.let { match ->
                    val name = match.groupValues[1]
                    if (name != "init" && !name.startsWith("_")) {
                        val paramsStr = match.groupValues[2]
                        val returnStr = match.groupValues[3].trim().takeIf { it.isNotEmpty() } ?: "Unit"
                        val params = parseFunParams(paramsStr)
                        val returnType = parseType(returnStr)
                        val fn = KneFunction(name = name, params = params, returnType = returnType)
                        if (currentClassName != null) {
                            currentMethods.add(fn)
                        } else if (braceDepth == 0) {
                            topLevelFunctions.add(fn)
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
                    // Skip if it's a constructor parameter already
                    if (currentClassCtorParams.none { it.name == propName }) {
                        currentProperties.add(
                            KneProperty(name = propName, type = parseType(typeStr), mutable = mutable)
                        )
                    }
                }
            }

            braceDepth += openCount
            braceDepth -= closeCount

            // Class ended
            if (currentClassName != null && braceDepth <= currentClassBraceDepth && closeCount > 0) {
                classes.add(
                    KneClass(
                        simpleName = currentClassName!!,
                        fqName = currentClassFqName!!,
                        constructor = KneConstructor(currentClassCtorParams),
                        methods = currentMethods.toList(),
                        properties = currentProperties.toList(),
                    )
                )
                currentClassName = null
                currentClassFqName = null
                currentClassBraceDepth = -1
                currentClassCtorParams = emptyList()
                currentMethods.clear()
                currentProperties.clear()
            }
        }

        return Triple(classes, topLevelFunctions, packageName.takeIf { it.isNotEmpty() })
    }

    private fun parseCtorParams(paramsStr: String): List<KneParam> {
        if (paramsStr.isBlank()) return emptyList()
        return paramsStr.split(",").mapNotNull { raw ->
            val s = raw.trim()
            val m = CTOR_PARAM_RE.find(s) ?: return@mapNotNull null
            KneParam(name = m.groupValues[1], type = parseType(m.groupValues[2].trim()))
        }
    }

    private fun parseFunParams(paramsStr: String): List<KneParam> {
        if (paramsStr.isBlank()) return emptyList()
        return paramsStr.split(",").mapNotNull { raw ->
            val s = raw.trim()
            val colonIdx = s.indexOf(':')
            if (colonIdx < 0) return@mapNotNull null
            val name = s.substring(0, colonIdx).trim().substringAfterLast(' ').trimStart('*')
            val typeRaw = s.substring(colonIdx + 1).substringBefore('=').trim()
            KneParam(name = name, type = parseType(typeRaw))
        }
    }

    internal fun parseType(typeStr: String): KneType {
        val clean = typeStr.trimEnd('?').trim()
        return when (clean) {
            "Int" -> KneType.INT
            "Long" -> KneType.LONG
            "Double" -> KneType.DOUBLE
            "Float" -> KneType.FLOAT
            "Boolean" -> KneType.BOOLEAN
            "Byte" -> KneType.BYTE
            "Short" -> KneType.SHORT
            "String" -> KneType.STRING
            "Unit", "" -> KneType.UNIT
            else -> KneType.UNIT // Unsupported types are silently skipped for now
        }
    }
}
