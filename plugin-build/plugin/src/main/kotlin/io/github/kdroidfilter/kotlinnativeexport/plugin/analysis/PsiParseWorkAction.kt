package io.github.kdroidfilter.kotlinnativeexport.plugin.analysis

import io.github.kdroidfilter.kotlinnativeexport.plugin.codegen.FfmProxyGenerator
import io.github.kdroidfilter.kotlinnativeexport.plugin.codegen.NativeBridgeGenerator
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

        // Generate JVM proxies
        if (jvmPackage.isNotEmpty() && (module.classes.isNotEmpty() || module.enums.isNotEmpty() || module.functions.isNotEmpty())) {
            val pkgDir = parameters.jvmProxiesDir.get().asFile.resolve(jvmPackage.replace('.', '/'))
            pkgDir.mkdirs()
            FfmProxyGenerator().generate(module, jvmPackage).forEach { (filename, content) ->
                pkgDir.resolve(filename).writeText(content)
            }
        }
    }
}
