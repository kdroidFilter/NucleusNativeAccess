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
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreApplicationEnvironmentMode
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreProjectEnvironment
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.lexer.KtTokens
import org.jetbrains.kotlin.psi.KtClass
import org.jetbrains.kotlin.psi.KtDeclaration
import org.jetbrains.kotlin.psi.KtEnumEntry
import org.jetbrains.kotlin.psi.KtFunctionType
import org.jetbrains.kotlin.psi.KtNamedFunction
import org.jetbrains.kotlin.psi.KtNullableType
import org.jetbrains.kotlin.psi.KtObjectDeclaration
import org.jetbrains.kotlin.psi.KtProperty
import org.jetbrains.kotlin.psi.KtPsiFactory
import org.jetbrains.kotlin.psi.KtTypeElement
import org.jetbrains.kotlin.psi.KtTypeReference
import org.jetbrains.kotlin.psi.KtUserType
import java.io.File

/**
 * AST-based parser using Kotlin PSI from kotlin-compiler-embeddable.
 * Runs inside an isolated classloader (Gradle Worker API) to avoid conflicts
 * with Gradle's own Kotlin runtime.
 */
class PsiSourceParser {

    fun parse(files: Collection<File>, libName: String, commonFiles: Collection<File>): KneModule {
        val disposable = Disposer.newDisposable("KnePsiParser")
        try {
            // IntelliJ PathManager requires idea.home.path
            val tmpHome = java.nio.file.Files.createTempDirectory("kne-psi").toFile()
            tmpHome.resolve("product-info.json").writeText("""{"buildNumber":"999.SNAPSHOT"}""")
            System.setProperty("idea.home.path", tmpHome.absolutePath)

            val appEnv = KotlinCoreApplicationEnvironment.create(disposable, KotlinCoreApplicationEnvironmentMode.Production)
            appEnv.registerFileType(KotlinFileType.INSTANCE, "kt")
            appEnv.registerParserDefinition(org.jetbrains.kotlin.parsing.KotlinParserDefinition())
            val projEnv = KotlinCoreProjectEnvironment(disposable, appEnv)
            val psiFactory = KtPsiFactory(projEnv.project)

            val ktFiles = files.filter { it.extension == "kt" }
            val commonKtFiles = commonFiles.filter { it.extension == "kt" }

            // Phase 1: prescan type names
            val knownClasses = mutableMapOf<String, String>()
            val knownEnums = mutableMapOf<String, String>()
            val rawDataClasses = mutableMapOf<String, Triple<String, KtClass, String>>()
            val commonDataClassNames = mutableSetOf<String>()

            for (file in commonKtFiles + ktFiles) {
                val ktFile = psiFactory.createFile(file.name, file.readText())
                val pkg = ktFile.packageFqName.asString()
                for (decl in ktFile.declarations) {
                    if (decl !is KtClass) continue
                    val name = decl.name ?: continue
                    val fq = if (pkg.isNotEmpty()) "$pkg.$name" else name
                    when {
                        decl.isEnum() -> knownEnums[name] = fq
                        decl.isData() -> rawDataClasses[name] = Triple(fq, decl, pkg)
                        !decl.isInterface() -> knownClasses[name] = fq
                    }
                }
                if (file in commonKtFiles) {
                    rawDataClasses.keys.forEach { commonDataClassNames.add(it) }
                }
            }

            // Phase 2: resolve data class fields iteratively
            val knownDataClasses = mutableMapOf<String, Pair<String, List<KneParam>>>()
            var changed = true
            while (changed) {
                changed = false
                for ((name, info) in rawDataClasses) {
                    if (name in knownDataClasses) continue
                    val fields = resolveDataClassFields(info.second, knownEnums, knownClasses, knownDataClasses)
                    if (fields != null) { knownDataClasses[name] = Pair(info.first, fields); changed = true }
                }
            }
            for ((name, info) in rawDataClasses) {
                if (name !in knownDataClasses) knownClasses[name] = info.first
            }

            val typeMaps = TypeMaps(knownClasses, knownEnums, knownDataClasses)

            // Phase 3: parse nativeMain files (deduplicate by fqName)
            val classMap = mutableMapOf<String, KneClass>()
            val dataClassMap = mutableMapOf<String, KneDataClass>()
            val enumMap = mutableMapOf<String, KneEnum>()
            val functionMap = mutableMapOf<String, KneFunction>()
            val packages = mutableSetOf<String>()

            for (file in ktFiles) {
                val ktFile = psiFactory.createFile(file.name, file.readText())
                val pkg = ktFile.packageFqName.asString()
                if (pkg.isNotEmpty()) packages.add(pkg)

                for (decl in ktFile.declarations) {
                    if (decl.isPrivateOrInternal()) continue
                    when {
                        decl is KtClass && decl.isEnum() -> parseEnum(decl, pkg)?.let { enumMap.putIfAbsent(it.fqName, it) }
                        decl is KtClass && decl.isData() -> {
                            val name = decl.name ?: continue
                            val dcInfo = knownDataClasses[name] ?: continue
                            val fq = dcInfo.first
                            dataClassMap.putIfAbsent(fq, KneDataClass(name, fq, dcInfo.second, isCommon = name in commonDataClassNames))
                        }
                        decl is KtClass && !decl.isInterface() -> parseClass(decl, pkg, typeMaps)?.let { classMap.putIfAbsent(it.fqName, it) }
                        decl is KtNamedFunction -> parseFunction(decl, typeMaps)?.let { functionMap.putIfAbsent(it.name, it) }
                    }
                }
            }

            for (name in commonDataClassNames) {
                val dcInfo = knownDataClasses[name] ?: continue
                dataClassMap.putIfAbsent(dcInfo.first, KneDataClass(name, dcInfo.first, dcInfo.second, isCommon = true))
            }

            tmpHome.deleteRecursively()

            return KneModule(libName, packages, classMap.values.toList(), dataClassMap.values.toList(), enumMap.values.toList(), functionMap.values.toList())
        } finally {
            Disposer.dispose(disposable)
        }
    }

    private data class TypeMaps(
        val classes: Map<String, String>,
        val enums: Map<String, String>,
        val dataClasses: Map<String, Pair<String, List<KneParam>>> = emptyMap(),
    )

    private fun resolveDataClassFields(
        ktClass: KtClass, knownEnums: Map<String, String>,
        knownClasses: Map<String, String>, knownDataClasses: Map<String, Pair<String, List<KneParam>>>,
    ): List<KneParam>? {
        val params = ktClass.primaryConstructor?.valueParameters ?: return null
        val fields = mutableListOf<KneParam>()
        for (param in params) {
            if (!param.hasValOrVar()) continue
            val name = param.name ?: return null
            val type = resolveType(param.typeReference, knownEnums, knownClasses, knownDataClasses) ?: return null
            fields.add(KneParam(name, type))
        }
        return if (fields.isNotEmpty()) fields else null
    }

    private fun parseClass(ktClass: KtClass, pkg: String, typeMaps: TypeMaps): KneClass? {
        val name = ktClass.name ?: return null
        val fq = if (pkg.isNotEmpty()) "$pkg.$name" else name
        val ctorParams = ktClass.primaryConstructor?.valueParameters?.mapNotNull { param ->
            val pName = param.name ?: return@mapNotNull null
            val type = resolveTypeFromMaps(param.typeReference, typeMaps) ?: return@mapNotNull null
            KneParam(pName, type, hasDefault = param.hasDefaultValue())
        } ?: emptyList()
        val methods = mutableListOf<KneFunction>()
        val properties = mutableListOf<KneProperty>()
        val companionMethods = mutableListOf<KneFunction>()
        val companionProperties = mutableListOf<KneProperty>()
        val ctorParamNames = ctorParams.map { it.name }.toSet()
        for (decl in ktClass.declarations) {
            if (decl.isPrivateOrInternal()) continue
            when (decl) {
                is KtNamedFunction -> {
                    if (decl.name?.startsWith("_") == true) continue
                    parseFunction(decl, typeMaps)?.let { methods.add(it) }
                }
                is KtProperty -> {
                    val propName = decl.name ?: continue
                    if (propName in ctorParamNames) continue
                    parseProperty(decl, typeMaps)?.let { properties.add(it) }
                }
                is KtObjectDeclaration -> if (decl.isCompanion()) {
                    for (cd in decl.declarations) {
                        if (cd.isPrivateOrInternal()) continue
                        when (cd) {
                            is KtNamedFunction -> parseFunction(cd, typeMaps)?.let { companionMethods.add(it) }
                            is KtProperty -> parseProperty(cd, typeMaps)?.let { companionProperties.add(it) }
                        }
                    }
                }
            }
        }
        return KneClass(name, fq, KneConstructor(ctorParams), methods, properties, companionMethods, companionProperties)
    }

    private fun parseEnum(ktClass: KtClass, pkg: String): KneEnum? {
        val name = ktClass.name ?: return null
        val fq = if (pkg.isNotEmpty()) "$pkg.$name" else name
        return KneEnum(name, fq, ktClass.declarations.filterIsInstance<KtEnumEntry>().mapNotNull { it.name })
    }

    private fun parseFunction(fn: KtNamedFunction, typeMaps: TypeMaps): KneFunction? {
        val name = fn.name ?: return null
        if (name == "init") return null
        val params = fn.valueParameters.mapNotNull { param ->
            val pName = param.name ?: return@mapNotNull null
            val type = resolveTypeFromMaps(param.typeReference, typeMaps) ?: return@mapNotNull null
            KneParam(pName, type)
        }
        return KneFunction(name, params, fn.typeReference?.let { resolveTypeFromMaps(it, typeMaps) } ?: KneType.UNIT)
    }

    private fun parseProperty(prop: KtProperty, typeMaps: TypeMaps): KneProperty? {
        val name = prop.name ?: return null
        val type = resolveTypeFromMaps(prop.typeReference, typeMaps) ?: return null
        return KneProperty(name, type, prop.isVar)
    }

    private fun resolveTypeFromMaps(typeRef: KtTypeReference?, typeMaps: TypeMaps): KneType? =
        resolveType(typeRef, typeMaps.enums, typeMaps.classes, typeMaps.dataClasses)

    private fun resolveType(
        typeRef: KtTypeReference?, knownEnums: Map<String, String>,
        knownClasses: Map<String, String>, knownDataClasses: Map<String, Pair<String, List<KneParam>>>,
    ): KneType? {
        val typeElem = typeRef?.typeElement ?: return null
        if (typeElem is KtNullableType) {
            val inner = resolveTypeElement(typeElem.innerType, knownEnums, knownClasses, knownDataClasses) ?: return null
            return if (inner == KneType.UNIT || inner is KneType.NULLABLE) inner else KneType.NULLABLE(inner)
        }
        return resolveTypeElement(typeElem, knownEnums, knownClasses, knownDataClasses)
    }

    private fun resolveTypeElement(
        typeElem: KtTypeElement?, knownEnums: Map<String, String>,
        knownClasses: Map<String, String>, knownDataClasses: Map<String, Pair<String, List<KneParam>>>,
    ): KneType? {
        if (typeElem == null) return null
        if (typeElem is KtFunctionType) {
            val paramTypes = typeElem.parameters.mapNotNull { p ->
                resolveType(p.typeReference, knownEnums, knownClasses, knownDataClasses)
            }
            val returnType = resolveType(typeElem.returnTypeReference, knownEnums, knownClasses, knownDataClasses) ?: KneType.UNIT
            val supported = setOf(KneType.INT, KneType.LONG, KneType.DOUBLE, KneType.FLOAT, KneType.BOOLEAN, KneType.BYTE, KneType.SHORT, KneType.STRING)
            fun ok(t: KneType) = t in supported || t is KneType.DATA_CLASS || t is KneType.ENUM || t is KneType.LIST || t is KneType.SET || t is KneType.MAP
            fun okRet(t: KneType) = ok(t) || t == KneType.UNIT
            if (paramTypes.any { !ok(it) } || !okRet(returnType)) return null
            return KneType.FUNCTION(paramTypes, returnType)
        }
        if (typeElem is KtUserType) {
            val name = typeElem.referencedName ?: return null
            val typeArgs = typeElem.typeArguments.mapNotNull { arg ->
                resolveType(arg.typeReference, knownEnums, knownClasses, knownDataClasses)
            }
            return when (name) {
                "Int" -> KneType.INT; "Long" -> KneType.LONG; "Double" -> KneType.DOUBLE; "Float" -> KneType.FLOAT
                "Boolean" -> KneType.BOOLEAN; "Byte" -> KneType.BYTE; "Short" -> KneType.SHORT
                "String" -> KneType.STRING; "ByteArray" -> KneType.BYTE_ARRAY; "Unit" -> KneType.UNIT
                "List", "MutableList" -> if (typeArgs.size == 1) KneType.LIST(typeArgs[0]) else null
                "Set", "MutableSet" -> if (typeArgs.size == 1) KneType.SET(typeArgs[0]) else null
                "Map", "MutableMap" -> if (typeArgs.size == 2) KneType.MAP(typeArgs[0], typeArgs[1]) else null
                else -> knownEnums[name]?.let { KneType.ENUM(it, name) }
                    ?: knownDataClasses[name]?.let { KneType.DATA_CLASS(it.first, name, it.second) }
                    ?: knownClasses[name]?.let { KneType.OBJECT(it, name) }
            }
        }
        return null
    }

    private fun KtDeclaration.isPrivateOrInternal(): Boolean {
        val mods = modifierList ?: return false
        return mods.hasModifier(KtTokens.PRIVATE_KEYWORD) || mods.hasModifier(KtTokens.INTERNAL_KEYWORD) || mods.hasModifier(KtTokens.PROTECTED_KEYWORD)
    }
}
