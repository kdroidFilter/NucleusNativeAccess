import io.github.kdroidfilter.nucleus.desktop.application.dsl.TargetFormat

plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.10.2"
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
                implementation("io.github.kdroidfilter:nucleus.graalvm-runtime:1.7.2")
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

val nativeBuildDir = "${project.projectDir}/build".replace("\\", "/")
val hostTarget = when {
    System.getProperty("os.name") == "Linux" -> "linuxX64"
    System.getProperty("os.name") == "Mac OS X" -> "macosArm64"
    else -> "mingwX64"
}
val nativeLibPaths = listOf(
    "$nativeBuildDir/bin/$hostTarget/calculatorReleaseShared",
    "$nativeBuildDir/bin/$hostTarget/calculatorDebugShared",
).joinToString(File.pathSeparator)

nucleus.application {
    mainClass = "com.example.calculator.MainKt"
    jvmArgs += listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=$nativeLibPaths",
    )

    graalvm {
        isEnabled = true
        javaLanguageVersion = 25
        jvmVendor = JvmVendorSpec.BELLSOFT
        imageName = "native-calculator"
        march = "compatibility"
        buildArgs.addAll(
            "-H:+AddAllCharsets",
            "-Djava.awt.headless=false",
            "-Os",
            "-H:-IncludeMethodData",
            "--enable-native-access=ALL-UNNAMED",
            "-Djava.library.path=$nativeLibPaths",
        )
    }

    nativeDistributions {
        targetFormats(TargetFormat.Deb, TargetFormat.Nsis, TargetFormat.Dmg)
        appName = "Native Calculator"
        packageName = "com.example.nativecalculator"
        packageVersion = "1.0.0"
        description = "Compose Desktop calculator powered by Kotlin/Native via FFM"
        homepage = "https://github.com/kdroidFilter/KotlinNativeExport"

        linux {
            debMaintainer = "dev@example.com"
        }

        windows {
            upgradeUuid = "a1b2c3d4-5678-9012-abcd-ef0123456789"
            nsis {
                oneClick = false
                allowElevation = true
                perMachine = true
                allowToChangeInstallationDirectory = true
                createDesktopShortcut = true
                createStartMenuShortcut = true
                runAfterFinish = true
            }
        }

        macOS {
            bundleID = "com.example.nativecalculator"
            appCategory = "public.app-category.utilities"
            dockName = "NativeCalc"
            dmg {
                title = "Native Calculator"
                iconSize = 128
            }
        }
    }
}

afterEvaluate {
    tasks.matching { it.name == "run" }.configureEach {
        val cap = hostTarget.replaceFirstChar { it.uppercaseChar() }
        dependsOn("linkCalculatorReleaseShared$cap")
    }

    // Copy the Kotlin/Native shared library into the GraalVM output so runGraalvmNative works
    tasks.matching { it.name == "packageGraalvmNative" }.configureEach {
        val cap = hostTarget.replaceFirstChar { it.uppercaseChar() }
        dependsOn("linkCalculatorReleaseShared$cap")
        doLast {
            val libFileName = System.mapLibraryName("calculator")
            val src = file("build/bin/$hostTarget/calculatorReleaseShared/$libFileName")
            val graalvmOutputDir = file("build/compose/tmp/main/graalvm/output/com.example.nativecalculator")
            val dst = graalvmOutputDir.resolve(libFileName)
            if (src.exists()) {
                src.copyTo(dst, overwrite = true)
                println("kne: Copied $libFileName to GraalVM output")
            }
        }
    }
}
