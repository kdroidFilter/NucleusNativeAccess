plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("io.github.kdroidfilter.nucleus") version "1.7.2"
    id("io.github.kdroidfilter.kotlinnativeexport")
}

kotlin {
    jvmToolchain(25)

    // Native target: use the real platform name (KMP convention)
    val hostOs = System.getProperty("os.name")
    val nativeTarget = when {
        hostOs == "Mac OS X" -> macosArm64()
        hostOs == "Linux" -> linuxX64()
        hostOs.startsWith("Windows") -> mingwX64()
        else -> error("Unsupported host OS: $hostOs")
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
    nativeLibName = "calculator"
    nativePackage = "com.example.calculator"
}

val nativeBuildDir = "${project.projectDir}/build"
val hostTarget = when {
    System.getProperty("os.name") == "Linux" -> "linuxX64"
    System.getProperty("os.name") == "Mac OS X" -> "macosArm64"
    else -> "mingwX64"
}
val nativeLibPaths = "$nativeBuildDir/bin/$hostTarget/calculatorReleaseShared:$nativeBuildDir/bin/$hostTarget/calculatorDebugShared"

nucleus.application {
    mainClass = "com.example.calculator.MainKt"
    jvmArgs += listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=$nativeLibPaths",
    )

    nativeDistributions {
        appName = "Native Calculator"
        packageName = "com.example.nativecalculator"
        packageVersion = "1.0.0"
        description = "Compose Desktop calculator powered by Kotlin/Native via FFM"

        linux { debMaintainer = "dev@example.com" }
        macOS { bundleID = "com.example.nativecalculator"; dockName = "NativeCalc" }
    }
}

afterEvaluate {
    tasks.matching { it.name == "run" }.configureEach {
        val cap = hostTarget.replaceFirstChar { it.uppercaseChar() }
        dependsOn("linkCalculatorReleaseShared$cap")
    }
}
