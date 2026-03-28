package io.github.kdroidfilter.kotlinnativeexport.plugin.tasks

import io.github.kdroidfilter.kotlinnativeexport.plugin.analysis.KotlinSourceParser
import io.github.kdroidfilter.kotlinnativeexport.plugin.codegen.NativeBridgeGenerator
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
 * Scans nativeMain sources and generates @CName bridge functions.
 * Output is added to the nativeMain source set before native compilation.
 */
abstract class GenerateNativeBridgesTask : DefaultTask() {

    @get:InputFiles
    @get:SkipWhenEmpty
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSources: ConfigurableFileCollection

    @get:Input
    abstract val libName: Property<String>

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val outDir = outputDir.get().asFile
        outDir.deleteRecursively()
        outDir.mkdirs()

        val ktFiles = nativeSources.asFileTree.filter { it.extension == "kt" }.files
        if (ktFiles.isEmpty()) {
            logger.lifecycle("kne: No Kotlin sources found in nativeMain, skipping bridge generation.")
            return
        }

        logger.lifecycle("kne: Parsing ${ktFiles.size} native source file(s)...")
        val module = KotlinSourceParser().parse(ktFiles, libName.get())

        if (module.classes.isEmpty() && module.functions.isEmpty()) {
            logger.lifecycle("kne: No public classes or functions found, skipping.")
            return
        }

        logger.lifecycle(
            "kne: Generating native bridges for ${module.classes.size} class(es) " +
                "and ${module.functions.size} function(s)."
        )

        val code = NativeBridgeGenerator().generate(module)
        val outFile = outDir.resolve("kne_bridges.kt")
        outFile.writeText(code)

        logger.lifecycle("kne: Native bridges written to ${outFile.absolutePath}")
    }
}
