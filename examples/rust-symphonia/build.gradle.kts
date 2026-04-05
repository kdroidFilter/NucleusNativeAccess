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
                implementation(compose.materialIconsExtended)
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
        mainClass = "com.example.rustsymphonia.MainKt"
        nativeDistributions {
            packageName = "RustSymphonia"
        }
        jvmArgs("--enable-native-access=ALL-UNNAMED")
    }
}

rustImport {
    libraryName = "rustsymphonia"
    jvmPackage = "com.example.rustsymphonia"
    buildType = "release"
    // symphonia: pure-Rust audio decoding (MP3, FLAC, OGG, WAV, AAC/M4A, AIFF…)
    crate("symphonia", "0.5.5", features = listOf("mp3", "aac", "alac", "isomp4", "aiff", "all-codecs"))
    // cpal: cross-platform audio output (CoreAudio on macOS, WASAPI on Windows, ALSA on Linux)
    crate("cpal", "0.15")
}
