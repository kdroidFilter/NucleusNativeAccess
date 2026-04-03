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
                implementation(compose.ui)
                implementation(compose.foundation)
                implementation(compose.material3)
            }
        }
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.rustcamera.MainKt"
        nativeDistributions {
            packageName = "RustCamera"
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

rustImport {
    libraryName = "rustcamera"
    jvmPackage = "com.example.rustcamera"
    buildType = "release"
    cratePath("camera", "${projectDir}/rust")
}
