package io.github.kdroidfilter.nucleusnativeaccess.plugin.tasks

import io.github.kdroidfilter.nucleusnativeaccess.plugin.CrateDependency
import io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis.RustWorkAction
import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Rust binding generation depends on cargo rustdoc output")
abstract class GenerateRustBindingsTask : DefaultTask() {

    @get:Input abstract val libName: Property<String>
    @get:Input abstract val jvmPackage: Property<String>
    @get:Input abstract val crates: ListProperty<CrateDependency>
    @get:OutputDirectory abstract val rustProjectDir: DirectoryProperty
    @get:OutputDirectory abstract val rustBridgesDir: DirectoryProperty
    @get:OutputDirectory abstract val jvmProxiesDir: DirectoryProperty
    @get:OutputDirectory abstract val jvmResourcesDir: DirectoryProperty

    @TaskAction
    fun generate() {
        jvmProxiesDir.get().asFile.apply { deleteRecursively(); mkdirs() }

        RustWorkAction.execute(
            crates = crates.get(),
            libName = libName.get(),
            jvmPackage = jvmPackage.get(),
            rustProjectDir = rustProjectDir.get().asFile,
            rustBridgesDir = rustBridgesDir.get().asFile,
            jvmProxiesDir = jvmProxiesDir.get().asFile,
            jvmResourcesDir = jvmResourcesDir.get().asFile,
            logger = logger,
        )
    }
}
