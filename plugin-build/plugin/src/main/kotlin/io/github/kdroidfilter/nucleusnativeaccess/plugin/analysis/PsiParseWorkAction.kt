package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen.FfmProxyGenerator
import io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen.NativeBridgeGenerator
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.KneModule
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.workers.WorkAction
import org.gradle.workers.WorkParameters

/**
 * Gradle Worker Action that runs PSI parsing + code generation inside an isolated classloader.
 * Both parsing AND generation run in the worker to avoid serialization issues with KneType singletons.
 */
abstract class PsiParseWorkAction : WorkAction<PsiParseWorkAction.Params> {

    interface Params : WorkParameters {
        val nativeSourceFiles: ConfigurableFileCollection
        val commonSourceFiles: ConfigurableFileCollection
        val libName: Property<String>
        val jvmPackage: Property<String>
        val nativeBridgesDir: DirectoryProperty
        val jvmProxiesDir: DirectoryProperty
        val jvmResourcesDir: DirectoryProperty
    }

    override fun execute() {
        val nativeFiles = parameters.nativeSourceFiles.files
        val commonFiles = parameters.commonSourceFiles.files
        val libName = parameters.libName.get()
        val jvmPackage = parameters.jvmPackage.get()

        val module = PsiSourceParser().parse(nativeFiles, libName, commonFiles)

        // Generate native bridges
        val nativeBridgesDir = parameters.nativeBridgesDir.get().asFile
        if (module.classes.isNotEmpty() || module.enums.isNotEmpty() || module.functions.isNotEmpty()) {
            nativeBridgesDir.mkdirs()
            val bridgeCode = NativeBridgeGenerator().generate(module)
            nativeBridgesDir.resolve("kne_bridges.kt").writeText(bridgeCode)
        }

        // Generate JVM proxies + GraalVM metadata
        if (jvmPackage.isNotEmpty() && (module.classes.isNotEmpty() || module.enums.isNotEmpty() || module.functions.isNotEmpty())) {
            val pkgDir = parameters.jvmProxiesDir.get().asFile.resolve(jvmPackage.replace('.', '/'))
            pkgDir.mkdirs()
            val generator = FfmProxyGenerator()
            generator.generate(module, jvmPackage).forEach { (filename, content) ->
                pkgDir.resolve(filename).writeText(content)
            }

            // Generate GraalVM reachability metadata for native-image support
            generateGraalVmMetadata(parameters.jvmResourcesDir.get().asFile, module, jvmPackage, generator)
        }
    }

    /**
     * Generates GraalVM reachability metadata under META-INF/native-image/kne/.
     * Includes reflection config, resource config, and FFM foreign downcall/upcall descriptors.
     */
    private fun generateGraalVmMetadata(
        resourcesRoot: java.io.File,
        module: KneModule,
        jvmPackage: String,
        generator: FfmProxyGenerator,
    ) {
        val metaDir = resourcesRoot.resolve("META-INF/native-image/kne/${module.libName}")
        metaDir.mkdirs()

        // Collect all generated class FQNs for reflection
        val classNames = mutableListOf<String>()
        classNames.add("$jvmPackage.KneRuntime")
        classNames.add("$jvmPackage.KotlinNativeException")
        module.classes.forEach { classNames.add("$jvmPackage.${it.simpleName}") }
        module.dataClasses.filter { !it.isCommon }.forEach { classNames.add("$jvmPackage.${it.simpleName}") }
        module.enums.forEach { classNames.add("$jvmPackage.${it.simpleName}") }
        if (module.functions.isNotEmpty()) {
            classNames.add("$jvmPackage.${module.libName.replaceFirstChar { it.uppercaseChar() }}")
        }

        // Collect FFM downcall descriptors
        val downcalls = generator.collectGraalVmDowncalls(module)

        // reflect-config.json
        val reflectEntries = classNames.joinToString(",\n") { name ->
            """  {
    "name": "$name",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  }"""
        }
        metaDir.resolve("reflect-config.json").writeText("[\n$reflectEntries\n]\n")

        // resource-config.json
        metaDir.resolve("resource-config.json").writeText("""{
  "resources": {
    "includes": [
      { "pattern": "\\Qkne/native/\\E.*" }
    ]
  }
}
""")

        // reachability-metadata.json — FFM foreign downcall + upcall descriptors
        fun formatEntries(descriptors: Set<Pair<List<String>, String?>>): String =
            descriptors.joinToString(",\n") { (params, ret) ->
                val paramStr = params.joinToString(", ") { "\"$it\"" }
                val retStr = ret ?: "void"
                """      { "parameterTypes": [$paramStr], "returnType": "$retStr" }"""
            }

        val downcallEntries = formatEntries(downcalls)

        // Collect FFM upcall descriptors (suspend/flow stubs + lambda callbacks)
        val upcalls = generator.collectGraalVmUpcalls(module)
        val upcallSection = if (upcalls.isNotEmpty()) {
            val upcallEntries = formatEntries(upcalls)
            """,
    "upcalls": [
$upcallEntries
    ]"""
        } else {
            ""
        }

        metaDir.resolve("reachability-metadata.json").writeText("""{
  "reflection": [
${classNames.joinToString(",\n") { """    { "type": "$it", "allDeclaredConstructors": true, "allDeclaredMethods": true }""" }}
  ],
  "resources": [
    { "glob": "kne/native/**" }
  ],
  "foreign": {
    "downcalls": [
$downcallEntries
    ]$upcallSection
  }
}
""")
    }
}
