package io.github.kdroidfilter.nucleusnativeaccess.plugin.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault
import io.github.kdroidfilter.nucleusnativeaccess.plugin.findCargo

@DisableCachingByDefault(because = "Cargo build has its own caching")
abstract class CargoBuildTask : DefaultTask() {

    @get:Input abstract val buildType: Property<String>
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val cargoProjectDir: DirectoryProperty
    @get:OutputDirectory abstract val nativeLibOutputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val projectDir = cargoProjectDir.get().asFile
        val buildType = buildType.get().lowercase()
        val cargo = findCargo()

        val args = mutableListOf(cargo, "build")
        if (buildType == "release") {
            args.add("--release")
        }

        logger.lifecycle("kne-rust: Running ${args.joinToString(" ")} in ${projectDir.absolutePath}")

        val process = ProcessBuilder(args)
            .directory(projectDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            throw org.gradle.api.GradleException("cargo build failed (exit $exitCode):\n$output")
        }

        // Copy the built shared library to the output dir
        val targetSubdir = if (buildType == "release") "release" else "debug"
        val targetDir = projectDir.resolve("target/$targetSubdir")
        val platform = detectPlatform()
        val destDir = nativeLibOutputDir.get().asFile.resolve("kne/native/$platform")
        destDir.mkdirs()

        val libFiles = targetDir.listFiles()?.filter { f ->
            f.extension in listOf("so", "dylib", "dll")
        } ?: emptyList()

        for (libFile in libFiles) {
            libFile.copyTo(destDir.resolve(libFile.name), overwrite = true)
            logger.lifecycle("kne-rust: Bundled ${libFile.name} → kne/native/$platform/")
        }
    }

    private fun detectPlatform(): String {
        val os = System.getProperty("os.name").lowercase()
        val arch = System.getProperty("os.arch").lowercase()
        return when {
            os.contains("linux") -> if (arch.contains("aarch") || arch.contains("arm")) "linux-aarch64" else "linux-x64"
            os.contains("mac") || os.contains("darwin") -> if (arch.contains("aarch") || arch.contains("arm")) "darwin-aarch64" else "darwin-x64"
            os.contains("win") -> if (arch.contains("aarch") || arch.contains("arm")) "win32-arm64" else "win32-x64"
            else -> "unknown"
        }
    }
}
