package io.github.kdroidfilter.kotlinnativeexport.plugin

import io.github.kdroidfilter.kotlinnativeexport.plugin.tasks.GenerateJvmProxiesTask
import io.github.kdroidfilter.kotlinnativeexport.plugin.tasks.GenerateNativeBridgesTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.testing.Test
import org.jetbrains.kotlin.gradle.dsl.KotlinMultiplatformExtension
import org.jetbrains.kotlin.gradle.plugin.mpp.KotlinNativeTarget
import org.jetbrains.kotlin.gradle.plugin.mpp.NativeBuildType
import java.io.File

/**
 * Main entry point for the kotlin-native-export Gradle plugin.
 *
 * Pipeline (mirrors swift-java's build plugin approach):
 *  1. Scan nativeMain sources → extract public Kotlin API
 *  2. Generate @CName bridge code → added to nativeMain compilation
 *  3. Generate FFM proxy classes → added to jvmMain compilation
 *  4. Configure sharedLib binary on native targets (both debug + release)
 *  5. Set java.library.path on JVM tasks (both dirs, like swift-java)
 *
 * Convention: both debug and release shared libs are always available as
 * binaries. [KotlinNativeExportExtension.buildType] selects which one the
 * test task links against. java.library.path includes both directories so
 * either variant can be picked up at runtime (same pattern as swift-java).
 */
class KotlinNativeExportPlugin : Plugin<Project> {

    override fun apply(project: Project) {
        val extension = project.extensions.create(
            "kotlinNativeExport",
            KotlinNativeExportExtension::class.java,
        )

        extension.nativeLibName.convention("nativelib")
        extension.nativePackage.convention("")
        extension.buildType.convention("release")

        project.plugins.withId("org.jetbrains.kotlin.multiplatform") {
            project.afterEvaluate { configureKmp(project, extension) }
        }
    }

    private fun configureKmp(project: Project, extension: KotlinNativeExportExtension) {
        val kotlin = project.extensions.getByType(KotlinMultiplatformExtension::class.java)
        val libName = extension.nativeLibName.get()
        val pkg = extension.nativePackage.get()

        val nativeBridgesDir = project.layout.buildDirectory.dir("generated/kne/nativeBridges")
        val jvmProxiesDir = project.layout.buildDirectory.dir("generated/kne/jvmProxies")

        // Detect native target and its source sets.
        // Convention: use src/nativeMain if it exists (shared native source set),
        // otherwise fall back to the first native target's main source set (e.g. linuxX64Main).
        val nativeTarget = kotlin.targets
            .filterIsInstance<KotlinNativeTarget>()
            .firstOrNull()

        val userNativeSrcDirs = mutableListOf<java.io.File>()
        val nativeMainDir = project.projectDir.resolve("src/nativeMain/kotlin")
        if (nativeMainDir.exists()) userNativeSrcDirs.add(nativeMainDir)
        if (nativeTarget != null) {
            val targetMainDir = project.projectDir.resolve("src/${nativeTarget.name}Main/kotlin")
            if (targetMainDir.exists()) userNativeSrcDirs.add(targetMainDir)
        }
        val userNativeSources = project.files(userNativeSrcDirs)

        // ── Code-generation tasks ────────────────────────────────────────────

        val generateNativeBridges = project.tasks.register(
            "generateKneNativeBridges",
            GenerateNativeBridgesTask::class.java,
        ) { task ->
            task.group = "kne"
            task.description = "Generate Kotlin/Native @CName bridge functions"
            task.nativeSources.from(userNativeSources)
            task.libName.set(libName)
            task.outputDir.set(nativeBridgesDir)
        }

        val generateJvmProxies = project.tasks.register(
            "generateKneJvmProxies",
            GenerateJvmProxiesTask::class.java,
        ) { task ->
            task.group = "kne"
            task.description = "Generate Kotlin/JVM FFM proxy classes"
            task.nativeSources.from(userNativeSources)
            task.libName.set(libName)
            task.jvmPackage.set(pkg)
            task.outputDir.set(jvmProxiesDir)
        }

        // Wire generated bridges into the native source set (try nativeMain, fall back to <target>Main)
        val nativeSourceSet = kotlin.sourceSets.findByName("nativeMain")
            ?: nativeTarget?.let { kotlin.sourceSets.findByName("${it.name}Main") }
        nativeSourceSet?.kotlin?.srcDir(nativeBridgesDir)

        // Wire generated JVM proxies into jvmMain
        kotlin.sourceSets.findByName("jvmMain")?.kotlin?.srcDir(jvmProxiesDir)

        // Ensure compilation waits for generation
        project.tasks.configureEach { task ->
            val name = task.name
            if (name.startsWith("compileKotlin") &&
                (name.contains("Native", ignoreCase = true) || name.contains("LinuxX64") ||
                    name.contains("MacosArm64") || name.contains("MingwX64"))
            ) {
                task.dependsOn(generateNativeBridges)
            }
            if (name == "compileKotlinJvm" || name == "compileKotlinJvmMain") {
                task.dependsOn(generateJvmProxies)
            }
        }

        // ── Native binaries (both debug + release, like swift-java) ──────────

        kotlin.targets
            .filterIsInstance<KotlinNativeTarget>()
            .forEach { target ->
                target.binaries.sharedLib(
                    namePrefix = libName,
                    buildTypes = listOf(NativeBuildType.DEBUG, NativeBuildType.RELEASE),
                )
            }

        // ── JVM test configuration ───────────────────────────────────────────

        configureJvmTestPaths(project, kotlin, extension)
    }

    /**
     * Mirrors swift-java's convention: include both debug + release library
     * directories in java.library.path so either variant is found at runtime.
     * The [KotlinNativeExportExtension.buildType] determines which link task
     * the test task depends on (only that variant is guaranteed to be built).
     */
    private fun configureJvmTestPaths(
        project: Project,
        kotlin: KotlinMultiplatformExtension,
        extension: KotlinNativeExportExtension,
    ) {
        val nativeTarget = kotlin.targets
            .filterIsInstance<KotlinNativeTarget>()
            .firstOrNull() ?: return

        val libName = extension.nativeLibName.get()
        val targetName = nativeTarget.name
        val targetCap = targetName.replaceFirstChar { it.uppercaseChar() }
        val libCap = libName.replaceFirstChar { it.uppercaseChar() }

        // Both variant output dirs (convention: build/bin/<target>/<lib><Variant>Shared/)
        val debugDir = project.layout.buildDirectory
            .dir("bin/$targetName/${libName}DebugShared").get().asFile
        val releaseDir = project.layout.buildDirectory
            .dir("bin/$targetName/${libName}ReleaseShared").get().asFile

        // java.library.path includes both, separated by the platform path separator
        val libraryPath = listOf(releaseDir, debugDir)
            .joinToString(File.pathSeparator) { it.absolutePath }

        // Link task for the configured build type
        val buildType = extension.buildType.get().replaceFirstChar { it.uppercaseChar() }
        val linkTaskName = "link${libCap}${buildType}Shared$targetCap"

        project.tasks.withType(Test::class.java).configureEach { testTask ->
            testTask.dependsOn(project.tasks.matching { it.name == linkTaskName })
            testTask.jvmArgs(
                "-Djava.library.path=$libraryPath",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
}
