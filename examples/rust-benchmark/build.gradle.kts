plugins {
    kotlin("multiplatform")
    id("io.github.kdroidfilter.nucleusnativeaccess")
}

kotlin {
    jvmToolchain(25)
    jvm()

    sourceSets {
        val jvmTest by getting {
            dependencies {
                implementation(kotlin("test"))
            }
        }
    }
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
    }
}

rustImport {
    libraryName = "rustbench"
    jvmPackage = "com.example.rustbenchmark"
    buildType = "release"
    cratePath("benchmark", "${projectDir}/rust")
}
