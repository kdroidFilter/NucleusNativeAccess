plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("io.github.kdroidfilter.nucleus") version "1.7.2"
    id("io.github.kdroidfilter.kotlinnativeexport")
}

val hostOs: String = System.getProperty("os.name")
val hostTarget = when {
    hostOs == "Linux" -> "linuxX64"
    hostOs == "Mac OS X" -> "macosArm64"
    hostOs.startsWith("Windows") -> "mingwX64"
    else -> error("Unsupported host OS: $hostOs")
}

kotlin {
    jvmToolchain(25)

    // Suppress expect/actual classes beta warning
    targets.all {
        compilations.all {
            compileTaskProvider.configure {
                compilerOptions.freeCompilerArgs.add("-Xexpect-actual-classes")
            }
        }
    }

    val nativeTarget = when (hostTarget) {
        "linuxX64" -> linuxX64()
        "macosArm64" -> macosArm64()
        else -> mingwX64()
    }

    // cinterop with libnotify (real Linux native notifications via D-Bus)
    if (hostOs == "Linux") {
        nativeTarget.compilations["main"].cinterops {
            val libnotify by creating {
                defFile(project.file("src/nativeInterop/cinterop/libnotify.def"))
            }
            val systray by creating {
                defFile(project.file("src/nativeInterop/cinterop/systray.def"))
            }
        }
    }

    // cinterop with Win32 Shell API (tray icon + toast notifications)
    if (hostOs.startsWith("Windows")) {
        nativeTarget.compilations["main"].cinterops {
            val wintray by creating {
                defFile(project.file("src/nativeInterop/cinterop/wintray.def"))
            }
        }
    }

    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                implementation(compose.desktop.currentOs)
                implementation(compose.runtime)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

composeCompiler {
    targetKotlinPlatforms.set(setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm))
}

kotlinNativeExport {
    nativeLibName = "systeminfo"
    nativePackage = "com.example.systeminfo"
}

nucleus.application {
    mainClass = "com.example.systeminfo.MainKt"
    jvmArgs += listOf("--enable-native-access=ALL-UNNAMED")

    nativeDistributions {
        appName = "Native System Info"
        packageName = "com.example.systeminfo"
        packageVersion = "1.0.0"
        description = "System info & notifications via Kotlin/Native + FFM"

        linux { debMaintainer = "dev@example.com" }
        macOS { bundleID = "com.example.systeminfo"; dockName = "SystemInfo" }
    }
}

