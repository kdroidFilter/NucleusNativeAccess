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
    }
}

compose.desktop {
    application {
        mainClass = "com.example.rustsysinfo.MainKt"
        nativeDistributions {
            packageName = "RustSysInfo"
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

rustImport {
    libraryName = "test"
    jvmPackage = "com.example.rustsysinfo"
    buildType = "release"
    crate("sysinfo", "0.38.4")
//    cratePath("rust-sysinfo", "${projectDir}/rust")
}
