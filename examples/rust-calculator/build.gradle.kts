plugins {
    kotlin("multiplatform")
    id("org.jetbrains.compose") version "1.10.2"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("io.github.kdroidfilter.nucleusnativeaccess")
}

kotlin {
    jvmToolchain(25)
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
                implementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.rustcalculator.MainKt"
        nativeDistributions {
            packageName = "RustCalculator"
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

rustImport {
    libraryName = "rustcalc"
    jvmPackage = "com.example.rustcalculator"
    buildType = "release"
    cratePath("calculator", "${projectDir}/rust")
}
