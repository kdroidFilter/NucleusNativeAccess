package io.github.kdroidfilter.nucleusnativeaccess.plugin

import io.github.kdroidfilter.nucleusnativeaccess.plugin.tasks.GenerateNativeBridgesTask
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
        val jvmResourcesDir = project.layout.buildDirectory.dir("generated/kne/jvmResources")

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

        // Collect commonMain sources for data class discovery
        val commonMainDir = project.projectDir.resolve("src/commonMain/kotlin")
        val commonSources = project.files(if (commonMainDir.exists()) commonMainDir else null)

        // ── PSI parser classpath (kotlin-compiler-embeddable for isolated Worker classloader) ──
        val kotlinVersion = kotlin.coreLibrariesVersion
        val psiClasspath = project.configurations.create("knePsiClasspath") {
            it.isCanBeConsumed = false
            it.isCanBeResolved = true
            it.isVisible = false
        }
        project.dependencies.add("knePsiClasspath", "org.jetbrains.kotlin:kotlin-compiler-embeddable:$kotlinVersion")

        // ── Code-generation tasks ────────────────────────────────────────────

        // Single task generates both native bridges and JVM proxies (PSI parsing + codegen in isolated worker)
        val generateBridges = project.tasks.register(
            "generateKneNativeBridges",
            GenerateNativeBridgesTask::class.java,
        ) { task ->
            task.group = "kne"
            task.description = "Generate Kotlin/Native bridges and JVM FFM proxies"
            task.nativeSources.from(userNativeSources)
            task.commonSources.from(commonSources)
            task.libName.set(libName)
            task.jvmPackage.set(pkg)
            task.outputDir.set(nativeBridgesDir)
            task.jvmOutputDir.set(jvmProxiesDir)
            task.jvmResourcesDir.set(jvmResourcesDir)
            task.psiClasspath.from(psiClasspath)
        }
        // Keep old task name as alias
        project.tasks.register("generateKneJvmProxies") { it.dependsOn(generateBridges) }

        // ── Coroutines dependency (required for suspend function support) ──
        val coroutinesVersion = "1.10.2"
        nativeTarget?.let { target ->
            kotlin.sourceSets.findByName("${target.name}Main")?.dependencies {
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
            }
        }
        kotlin.sourceSets.findByName("nativeMain")?.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:$coroutinesVersion")
        }
        kotlin.sourceSets.findByName("jvmMain")?.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core-jvm:$coroutinesVersion")
        }
        kotlin.sourceSets.findByName("jvmTest")?.dependencies {
            implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:$coroutinesVersion")
        }

        // Wire generated bridges into the native source set (try nativeMain, fall back to <target>Main)
        val nativeSourceSet = kotlin.sourceSets.findByName("nativeMain")
            ?: nativeTarget?.let { kotlin.sourceSets.findByName("${it.name}Main") }
        nativeSourceSet?.kotlin?.srcDir(nativeBridgesDir)

        // Wire generated JVM proxies into jvmMain
        kotlin.sourceSets.findByName("jvmMain")?.kotlin?.srcDir(jvmProxiesDir)

        // Wire generated GraalVM metadata into JVM resources
        kotlin.sourceSets.findByName("jvmMain")?.resources?.srcDir(jvmResourcesDir)

        // Ensure compilation waits for generation
        project.tasks.configureEach { task ->
            val name = task.name
            if (name.startsWith("compileKotlin") &&
                (name.contains("Native", ignoreCase = true) || name.contains("LinuxX64") ||
                    name.contains("MacosArm64") || name.contains("MingwX64"))
            ) {
                task.dependsOn(generateBridges)
            }
            if (name == "compileKotlinJvm" || name == "compileKotlinJvmMain") {
                task.dependsOn(generateBridges)
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

        // ── Bundle native lib into JVM resources (zero-config deployment) ────

        if (nativeTarget != null) {
            val targetName = nativeTarget.name
            val targetCap = targetName.replaceFirstChar { it.uppercaseChar() }
            val libCap = libName.replaceFirstChar { it.uppercaseChar() }
            val platform = mapTargetToPlatform(targetName)
            val linkTaskName = "link${libCap}ReleaseShared$targetCap"
            val nativeLibResourceDir = project.layout.buildDirectory.dir("generated/kne/nativeLib")

            val copyNativeLib = project.tasks.register("copyKneNativeLib") { task ->
                task.group = "kne"
                task.description = "Copy native shared library into JVM resources for JAR bundling"
                task.dependsOn(project.tasks.matching { it.name == linkTaskName })
                task.doLast {
                    val releaseDir = project.layout.buildDirectory
                        .dir("bin/$targetName/${libName}ReleaseShared").get().asFile
                    val nativeFile = releaseDir.listFiles()?.firstOrNull { f ->
                        f.extension in listOf("so", "dylib", "dll")
                    }
                    if (nativeFile != null) {
                        val destDir = nativeLibResourceDir.get().asFile.resolve("kne/native/$platform")
                        destDir.mkdirs()
                        nativeFile.copyTo(destDir.resolve(nativeFile.name), overwrite = true)
                        project.logger.lifecycle("kne: Bundled ${nativeFile.name} → kne/native/$platform/")
                    }
                }
            }

            // Wire native lib resource dir into JVM resources
            kotlin.sourceSets.findByName("jvmMain")?.resources?.srcDir(nativeLibResourceDir)

            // Ensure processResources waits for the native lib copy
            project.tasks.configureEach { task ->
                if (task.name == "jvmProcessResources" || task.name == "processJvmMainResources") {
                    task.dependsOn(copyNativeLib)
                }
            }
        }

        // ── JVM test configuration ───────────────────────────────────────────

        configureJvmTestPaths(project, kotlin, extension)
    }

    /** Map Kotlin/Native target name to platform directory name. */
    private fun mapTargetToPlatform(targetName: String): String = when {
        targetName.startsWith("linux") -> if (targetName.contains("Arm") || targetName.contains("aarch")) "linux-aarch64" else "linux-x64"
        targetName.startsWith("macos") -> if (targetName.contains("Arm") || targetName.contains("arm64") || targetName.contains("Arm64")) "darwin-aarch64" else "darwin-x64"
        targetName.startsWith("mingw") -> if (targetName.contains("Arm")) "win32-arm64" else "win32-x64"
        else -> "unknown"
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
            .joinToString(File.pathSeparator) { it.absolutePath.replace("\\", "/") }

        // Link task for the configured build type
        val buildType = extension.buildType.get().replaceFirstChar { it.uppercaseChar() }
        val linkTaskName = "link${libCap}${buildType}Shared$targetCap"

        project.tasks.withType(Test::class.java).configureEach { testTask ->
            testTask.dependsOn(project.tasks.matching { it.name == linkTaskName })
            testTask.useJUnitPlatform()
            testTask.jvmArgs(
                "-Djava.library.path=$libraryPath",
                "--enable-native-access=ALL-UNNAMED",
            )
        }
    }
}
