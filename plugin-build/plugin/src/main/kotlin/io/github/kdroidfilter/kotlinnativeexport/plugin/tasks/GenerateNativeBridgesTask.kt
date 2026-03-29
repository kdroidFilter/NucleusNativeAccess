package io.github.kdroidfilter.kotlinnativeexport.plugin.tasks

import io.github.kdroidfilter.kotlinnativeexport.plugin.analysis.PsiParseWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Classpath
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import org.gradle.workers.WorkerExecutor
import javax.inject.Inject

@DisableCachingByDefault(because = "Bridge generation depends on source analysis that is not yet cacheable")
abstract class GenerateNativeBridgesTask : DefaultTask() {

    @get:Inject abstract val workerExecutor: WorkerExecutor

    @get:InputFiles @get:SkipWhenEmpty @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val nativeSources: ConfigurableFileCollection

    @get:InputFiles @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val commonSources: ConfigurableFileCollection

    @get:Classpath abstract val psiClasspath: ConfigurableFileCollection
    @get:Input abstract val libName: Property<String>
    @get:Input abstract val jvmPackage: Property<String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty
    @get:OutputDirectory abstract val jvmOutputDir: DirectoryProperty
    @get:OutputDirectory abstract val jvmResourcesDir: DirectoryProperty

    @TaskAction
    fun generate() {
        outputDir.get().asFile.apply { deleteRecursively(); mkdirs() }
        jvmOutputDir.get().asFile.apply { deleteRecursively(); mkdirs() }

        val ktFiles = nativeSources.asFileTree.filter { it.extension == "kt" }.files
        if (ktFiles.isEmpty()) { logger.lifecycle("kne: No Kotlin sources found, skipping."); return }

        val commonKtFiles = commonSources.asFileTree.filter { it.extension == "kt" }.files
        logger.lifecycle("kne: Parsing ${ktFiles.size} native + ${commonKtFiles.size} common source file(s) [PSI]...")

        val pluginJarUrl = PsiParseWorkAction::class.java.protectionDomain?.codeSource?.location
        val pluginJar = pluginJarUrl?.let { project.files(java.io.File(it.toURI())) } ?: project.files()

        val workQueue = workerExecutor.classLoaderIsolation { spec ->
            spec.classpath.from(psiClasspath)
            spec.classpath.from(pluginJar)
        }

        workQueue.submit(PsiParseWorkAction::class.java) { params ->
            params.nativeSourceFiles.from(ktFiles)
            params.commonSourceFiles.from(commonKtFiles)
            params.libName.set(libName)
            params.jvmPackage.set(jvmPackage)
            params.nativeBridgesDir.set(outputDir)
            params.jvmProxiesDir.set(jvmOutputDir)
            params.jvmResourcesDir.set(jvmResourcesDir)
        }
    }
}
