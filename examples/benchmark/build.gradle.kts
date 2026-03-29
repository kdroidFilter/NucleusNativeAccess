plugins {
    kotlin("multiplatform")
    id("io.github.kdroidfilter.kotlinnativeexport")
}

kotlin {
    jvmToolchain(25)

    val hostOs = System.getProperty("os.name")
    when {
        hostOs == "Mac OS X" -> macosArm64()
        hostOs == "Linux" -> linuxX64()
        hostOs.startsWith("Windows") -> mingwX64()
        else -> error("Unsupported host OS: $hostOs")
    }

    jvm()

    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

kotlinNativeExport {
    nativeLibName = "benchmark"
    nativePackage = "com.example.benchmark"
}
