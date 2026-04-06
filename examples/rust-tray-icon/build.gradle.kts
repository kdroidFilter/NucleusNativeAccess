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
                implementation(compose.materialIconsExtended)
            }
        }
    }
}

compose.desktop {
    application {
        mainClass = "com.example.rusttrayicon.MainKt"
        nativeDistributions {
            packageName = "RustTrayIcon"
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

rustImport {
    libraryName = "tray_icon_wrapper"
    jvmPackage = "com.example.rusttrayicon"
    buildType = "release"
    cratePath("tray-icon-wrapper", "${projectDir}/rust")
}
