package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import io.github.kdroidfilter.nucleusnativeaccess.plugin.CrateDependency
import io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen.FfmProxyGenerator
import io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen.RustBridgeGenerator
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.KneModule
import java.io.File

/**
 * Orchestrates the Rust import pipeline:
 *  1. Generate wrapper Cargo.toml + lib.rs for the target crate(s)
 *  2. Run `cargo rustdoc --output-format json` to extract the public API
 *  3. Parse the JSON with [RustdocJsonParser] → [KneModule]
 *  4. Generate Rust bridge code with [RustBridgeGenerator] → kne_bridges.rs
 *  5. Generate JVM FFM proxies with [FfmProxyGenerator] (reused as-is)
 *  6. Generate GraalVM metadata
 */
object RustWorkAction {

    fun execute(
        crates: List<CrateDependency>,
        libName: String,
        jvmPackage: String,
        rustProjectDir: File,
        rustBridgesDir: File,
        jvmProxiesDir: File,
        jvmResourcesDir: File,
        logger: org.gradle.api.logging.Logger,
    ) {
        // Step 1: Resolve Cargo project directory
        val cargoProjectDir = resolveCargoProject(crates, rustProjectDir, libName, logger)
        val crateSrcDir = cargoProjectDir.resolve("src")

        // Step 2: Write an empty bridge file so rustdoc doesn't fail
        // The bridge is included via build.rs which points to the Gradle build dir
        rustBridgesDir.mkdirs()
        val bridgeFile = rustBridgesDir.resolve("kne_bridges.rs")
        if (!bridgeFile.exists()) bridgeFile.writeText("// placeholder\n")

        // Generate build.rs that tells Cargo where to find the bridges
        ensureBuildRs(cargoProjectDir, rustBridgesDir, logger)

        // Ensure lib.rs includes the bridge via OUT_DIR
        ensureLibRsInclude(crateSrcDir, logger)

        // Step 3: Run cargo rustdoc to produce JSON
        val rustdocJson = runCargoRustdoc(cargoProjectDir, libName, logger)
            ?: throw org.gradle.api.GradleException("Failed to generate rustdoc JSON for '$libName'")

        // Step 4: Parse JSON → KneModule
        val jsonContent = rustdocJson.readText()
        val module = RustdocJsonParser().parse(jsonContent, libName)
        logger.lifecycle("kne-rust: Parsed ${module.classes.size} classes, ${module.dataClasses.size} data classes, ${module.enums.size} enums, ${module.functions.size} functions")

        // Step 5: Generate Rust bridges (into Gradle build dir, NOT into crate src)
        if (module.classes.isNotEmpty() || module.enums.isNotEmpty() || module.functions.isNotEmpty() || module.dataClasses.isNotEmpty()) {
            val bridgeCode = RustBridgeGenerator().generate(module)
            bridgeFile.writeText(bridgeCode)
            logger.lifecycle("kne-rust: Generated Rust bridges → ${bridgeFile.absolutePath}")
        }

        // Step 5: Generate JVM proxies
        val resolvedPackage = jvmPackage.ifEmpty { module.packages.firstOrNull() ?: "" }
        if (resolvedPackage.isNotEmpty() && (module.classes.isNotEmpty() || module.enums.isNotEmpty() || module.functions.isNotEmpty())) {
            val pkgDir = jvmProxiesDir.resolve(resolvedPackage.replace('.', '/'))
            pkgDir.mkdirs()
            val generator = FfmProxyGenerator()
            generator.generate(module, resolvedPackage).forEach { (filename, content) ->
                pkgDir.resolve(filename).writeText(content)
            }
            logger.lifecycle("kne-rust: Generated JVM proxies → $resolvedPackage")

            // Step 6: GraalVM metadata
            generateGraalVmMetadata(jvmResourcesDir, module, resolvedPackage, generator)
        }
    }

    private fun resolveCargoProject(
        crates: List<CrateDependency>,
        rustProjectDir: File,
        libName: String,
        logger: org.gradle.api.logging.Logger,
    ): File {
        // If there's a single local path crate, use it directly
        val localCrate = crates.singleOrNull { it.path != null }
        if (localCrate != null) {
            val dir = File(localCrate.path!!)
            val resolved = if (dir.isAbsolute) dir else rustProjectDir.resolve(dir.path)
            if (resolved.resolve("Cargo.toml").exists()) {
                logger.lifecycle("kne-rust: Using local crate at ${resolved.absolutePath}")
                return resolved
            }
        }

        // Otherwise, generate a wrapper Cargo project
        rustProjectDir.mkdirs()
        val cargoToml = buildString {
            appendLine("[package]")
            appendLine("name = \"kne-$libName-wrapper\"")
            appendLine("version = \"0.1.0\"")
            appendLine("edition = \"2021\"")
            appendLine()
            appendLine("[lib]")
            appendLine("name = \"${libName}\"")
            appendLine("crate-type = [\"cdylib\"]")
            appendLine()
            appendLine("[dependencies]")
            for (crate in crates) {
                when {
                    crate.version != null -> appendLine("${crate.name} = \"${crate.version}\"")
                    crate.path != null -> appendLine("${crate.name} = { path = \"${crate.path}\" }")
                    crate.gitUrl != null -> appendLine("${crate.name} = { git = \"${crate.gitUrl}\", branch = \"${crate.gitBranch}\" }")
                }
            }
        }
        rustProjectDir.resolve("Cargo.toml").writeText(cargoToml)

        val srcDir = rustProjectDir.resolve("src")
        srcDir.mkdirs()
        val libRs = buildString {
            for (crate in crates) {
                val crateName = crate.name.replace('-', '_')
                appendLine("pub use ${crateName}::*;")
            }
            appendLine()
            appendLine("include!(\"kne_bridges.rs\");")
        }
        srcDir.resolve("lib.rs").writeText(libRs)

        logger.lifecycle("kne-rust: Generated wrapper Cargo project at ${rustProjectDir.absolutePath}")
        return rustProjectDir
    }

    private fun ensureBuildRs(cargoDir: File, bridgesDir: File, logger: org.gradle.api.logging.Logger) {
        val buildRs = cargoDir.resolve("build.rs")
        val bridgePath = bridgesDir.resolve("kne_bridges.rs").absolutePath.replace("\\", "/")
        val content = """
            |fn main() {
            |    let src = "$bridgePath";
            |    let out_dir = std::env::var("OUT_DIR").unwrap();
            |    let dest = format!("{}/kne_bridges.rs", out_dir);
            |    if std::path::Path::new(src).exists() {
            |        std::fs::copy(src, &dest).expect("Failed to copy kne_bridges.rs");
            |    } else {
            |        std::fs::write(&dest, "// placeholder\n").expect("Failed to write placeholder");
            |    }
            |    println!("cargo:rerun-if-changed={}", src);
            |}
        """.trimMargin()
        if (!buildRs.exists() || buildRs.readText() != content) {
            buildRs.writeText(content)
            logger.lifecycle("kne-rust: Generated build.rs for bridge inclusion")
        }
    }

    private fun ensureLibRsInclude(srcDir: File, logger: org.gradle.api.logging.Logger) {
        val libRs = srcDir.resolve("lib.rs")
        if (!libRs.exists()) return
        val content = libRs.readText()
        val oldInclude = "include!(\"kne_bridges.rs\");"
        val outDirInclude = "include!(concat!(env!(\"OUT_DIR\"), \"/kne_bridges.rs\"));"
        val cleaned = content.replace("\n$oldInclude\n", "\n").replace(oldInclude, "")
        if (!cleaned.contains(outDirInclude)) {
            libRs.writeText(cleaned.trimEnd() + "\n\n$outDirInclude\n")
            logger.lifecycle("kne-rust: Injected OUT_DIR bridge include into lib.rs")
        } else if (cleaned != content) {
            libRs.writeText(cleaned)
        }
    }

    private fun runCargoRustdoc(cargoDir: File, libName: String, logger: org.gradle.api.logging.Logger): File? {
        val cargo = findCargo()
        val crateName = libName.replace('-', '_')

        val process = ProcessBuilder(
            cargo, "doc", "--no-deps",
        )
            .directory(cargoDir)
            .apply {
                environment()["RUSTC_BOOTSTRAP"] = "1"
                environment()["RUSTDOCFLAGS"] = "-Z unstable-options --output-format json"
            }
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()

        if (exitCode != 0) {
            logger.error("kne-rust: cargo rustdoc failed (exit $exitCode):\n$output")
            return null
        }

        // Find the JSON file in target/doc/
        val docDir = cargoDir.resolve("target/doc")
        return docDir.listFiles()?.firstOrNull { it.extension == "json" && it.name.endsWith(".json") }
    }

    private fun findCargo(): String {
        // Check CARGO_HOME
        val cargoHome = System.getenv("CARGO_HOME")
        if (cargoHome != null) {
            val cargo = File(cargoHome, "bin/cargo")
            if (cargo.exists()) return cargo.absolutePath
        }
        // Check ~/.cargo/bin
        val homeCargo = File(System.getProperty("user.home"), ".cargo/bin/cargo")
        if (homeCargo.exists()) return homeCargo.absolutePath
        // Fallback to PATH
        return "cargo"
    }

    private fun generateGraalVmMetadata(
        resourcesRoot: File,
        module: KneModule,
        jvmPackage: String,
        generator: FfmProxyGenerator,
    ) {
        val metaDir = resourcesRoot.resolve("META-INF/native-image/kne/${module.libName}")
        metaDir.mkdirs()

        val classNames = mutableListOf<String>()
        classNames.add("$jvmPackage.KneRuntime")
        classNames.add("$jvmPackage.KotlinNativeException")
        module.classes.forEach { classNames.add("$jvmPackage.${it.simpleName}") }
        module.dataClasses.filter { !it.isCommon }.forEach { classNames.add("$jvmPackage.${it.simpleName}") }
        module.enums.forEach { classNames.add("$jvmPackage.${it.simpleName}") }
        if (module.functions.isNotEmpty()) {
            classNames.add("$jvmPackage.${module.libName.replaceFirstChar { it.uppercaseChar() }}")
        }

        val downcalls = generator.collectGraalVmDowncalls(module)

        val reflectEntries = classNames.joinToString(",\n") { name ->
            """  {
    "name": "$name",
    "allDeclaredConstructors": true,
    "allDeclaredMethods": true,
    "allDeclaredFields": true
  }"""
        }
        metaDir.resolve("reflect-config.json").writeText("[\n$reflectEntries\n]\n")

        metaDir.resolve("resource-config.json").writeText("""{
  "resources": {
    "includes": [
      { "pattern": "\\Qkne/native/\\E.*" }
    ]
  }
}
""")

        fun formatEntries(descriptors: Set<Pair<List<String>, String?>>): String =
            descriptors.joinToString(",\n") { (params, ret) ->
                val paramStr = params.joinToString(", ") { "\"$it\"" }
                val retStr = ret ?: "void"
                """      { "parameterTypes": [$paramStr], "returnType": "$retStr" }"""
            }

        val downcallEntries = formatEntries(downcalls)
        val upcalls = generator.collectGraalVmUpcalls(module)
        val upcallSection = if (upcalls.isNotEmpty()) {
            val upcallEntries = formatEntries(upcalls)
            """,
    "upcalls": [
$upcallEntries
    ]"""
        } else ""

        metaDir.resolve("reachability-metadata.json").writeText("""{
  "reflection": [
${classNames.joinToString(",\n") { """    { "type": "$it", "allDeclaredConstructors": true, "allDeclaredMethods": true }""" }}
  ],
  "resources": [
    { "glob": "kne/native/**" }
  ],
  "foreign": {
    "downcalls": [
$downcallEntries
    ]$upcallSection
  }
}
""")
    }
}
