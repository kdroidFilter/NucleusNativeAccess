package io.github.kdroidfilter.kotlinnativeexport.plugin.tasks

import io.github.kdroidfilter.kotlinnativeexport.plugin.analysis.KotlinSourceParser
import io.github.kdroidfilter.kotlinnativeexport.plugin.codegen.FfmProxyGenerator
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

/**
 * Scans nativeMain sources and generates Kotlin/JVM FFM proxy classes.
 * Output is added to the jvmMain source set before JVM compilation.
 */
abstract class GenerateJvmProxiesTask : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSources: ConfigurableFileCollection

    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val commonSources: ConfigurableFileCollection

    @get:Input
    abstract val libName: Property<String>

    @get:Input
    abstract val jvmPackage: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val ktFiles = nativeSources.asFileTree.filter { it.extension == "kt" }.files
        if (ktFiles.isEmpty()) {
            logger.lifecycle("kne: No Kotlin sources found, skipping JVM proxy generation.")
            return
        }

        val commonKtFiles = commonSources.asFileTree.filter { it.extension == "kt" }.files
        logger.lifecycle("kne: Parsing ${ktFiles.size} native + ${commonKtFiles.size} common source file(s) for JVM proxies...")
        val module = KotlinSourceParser().parse(ktFiles, libName.get(), commonKtFiles)

        if (module.classes.isEmpty() && module.enums.isEmpty() && module.functions.isEmpty()) {
            logger.lifecycle("kne: No public API found, skipping.")
            return
        }

        logger.lifecycle(
            "kne: Generating JVM FFM proxies for ${module.classes.size} class(es), " +
                "${module.enums.size} enum(s), and ${module.functions.size} function(s)."
        )

        val pkg = jvmPackage.get()
        val pkgDir = outDir.resolve(pkg.replace('.', '/'))
        pkgDir.mkdirs()

        val files = FfmProxyGenerator().generate(module, pkg)
        files.forEach { (filename, content) ->
            val outFile = pkgDir.resolve(filename)
            outFile.writeText(content)
            logger.lifecycle("kne: JVM proxy written to ${outFile.absolutePath}")
        }
    }
}
