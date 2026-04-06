package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import com.google.gson.JsonParser
import io.github.kdroidfilter.nucleusnativeaccess.plugin.CrateDependency
import io.github.kdroidfilter.nucleusnativeaccess.plugin.findCargo
import io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen.FfmProxyGenerator
import io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen.RustBridgeGenerator
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.KneConstructorKind
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.KneModule
import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.KneType
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
        val rustdocProjectDir = resolveRustdocProject(crates, cargoProjectDir, logger)
        val crateSrcDir = cargoProjectDir.resolve("src")

        // Step 2: Write an empty bridge file so rustdoc doesn't fail
        // The bridge is included via build.rs which points to the Gradle build dir
        rustBridgesDir.mkdirs()
        val bridgeFile = rustBridgesDir.resolve("kne_bridges.rs")
        bridgeFile.writeText("// placeholder\n")

        // Generate build.rs that tells Cargo where to find the bridges
        ensureBuildRs(cargoProjectDir, rustBridgesDir, logger)

        // Ensure lib.rs includes the bridge via OUT_DIR
        ensureLibRsInclude(crateSrcDir, logger)

        // Step 3: Run cargo rustdoc to produce JSON
        val rustdocJson = runCargoRustdoc(rustdocProjectDir, libName, crates, logger)
            ?: throw org.gradle.api.GradleException("Failed to generate rustdoc JSON for '$libName'")

        // Step 4: Parse JSON → KneModule
        val unsupported = mutableListOf<String>()
        val isWrapper = crates.isNotEmpty() && crates.none { it.path != null }

        val module = if (isWrapper) {
            // For wrapper crates, parse the main crate JSON + only sub-crates that are
            // publicly re-exported. Parsing internal sub-crates (like symphonia_codec_pcm)
            // would pull in types that are not accessible from the wrapper scope.
            val docDir = rustdocProjectDir.resolve("target/doc")
            val primaryCrateName = crates.first().name.replace('-', '_')
            val mainJsonFile = docDir.resolve("$primaryCrateName.json")

            // Detect which sub-crates are re-exported by the main crate via `pub use`
            val reExportedSubCrates = mutableSetOf<String>()
            if (mainJsonFile.exists()) {
                val mainDoc = com.google.gson.JsonParser.parseString(mainJsonFile.readText()).asJsonObject
                val mainIndex = mainDoc.getAsJsonObject("index")
                val rootId = mainDoc.get("root")?.asInt?.toString()
                val rootItems = rootId?.let { mainIndex?.get(it)?.asJsonObject }
                    ?.getAsJsonObject("inner")?.getAsJsonObject("module")?.getAsJsonArray("items")
                if (rootItems != null) {
                    for (itemId in rootItems) {
                        val item = mainIndex.get(itemId.asInt.toString())?.asJsonObject ?: continue
                        val inner = item.getAsJsonObject("inner") ?: continue
                        if (inner.has("use")) {
                            val sourceElem = inner.getAsJsonObject("use").get("source")
                            if (sourceElem != null && !sourceElem.isJsonNull) {
                                // Extract crate name from source path (e.g. "nokhwa_core::pixel_format::FormatDecoder" → "nokhwa_core")
                                val source = sourceElem.asString.replace('-', '_')
                                val crateName = source.substringBefore("::")
                                reExportedSubCrates.add(crateName)
                            }
                        }
                    }
                }
            }

            // Collect JSONs to parse: main crate + referenced sub-crates + additional crates.
            // Only sub-crates that are referenced by the main crate (via pub use) are included.
            val depJsons = mutableListOf<java.io.File>()
            if (mainJsonFile.exists()) depJsons.add(mainJsonFile)
            for (subCrate in reExportedSubCrates) {
                val subJson = docDir.resolve("$subCrate.json")
                if (subJson.exists() && subJson !in depJsons) depJsons.add(subJson)
            }
            for (crate in crates.drop(1)) {
                val additionalJson = docDir.resolve("${crate.name.replace('-', '_')}.json")
                if (additionalJson.exists() && additionalJson !in depJsons) depJsons.add(additionalJson)
            }

            val mainJson = depJsons.find { it.nameWithoutExtension == primaryCrateName }
            val subJsons = depJsons.filter { it != mainJson }
            logger.lifecycle("kne-rust: Parsing main: ${mainJson?.name}, sub: ${subJsons.map { it.name }}")

            val parser = RustdocJsonParser()
            val mainModule = if (mainJson != null) {
                parser.parse(mainJson.readText(), libName) { unsupported.add(it) }
            } else null
            val subModules = subJsons.map { jsonFile ->
                parser.parse(jsonFile.readText(), libName) { unsupported.add(it) }
            }
            mergeModules(listOfNotNull(mainModule) + subModules, libName)
        } else {
            RustdocJsonParser().parse(rustdocJson.readText(), libName) { unsupported.add(it) }
        }

        logger.lifecycle("kne-rust: Parsed ${module.classes.size} classes, ${module.dataClasses.size} data classes, ${module.enums.size} enums, ${module.functions.size} functions")
        if (unsupported.isNotEmpty()) {
            logger.warn("kne-rust: Skipped unsupported API elements:")
            unsupported.take(20).forEach { logger.warn("kne-rust:   $it") }
            if (unsupported.size > 20) logger.warn("kne-rust:   ... and ${unsupported.size - 20} more")
        }

        // Step 4b: For wrapper crates, rewrite lib.rs with proper imports based on rustdoc analysis
        if (isWrapper) {
            val docDir = rustdocProjectDir.resolve("target/doc")
            rewriteWrapperLibRs(crateSrcDir, crates, docDir, logger)
        }

        // Step 5: Generate Rust bridges (into Gradle build dir, NOT into crate src)
        if (module.classes.isNotEmpty() || module.enums.isNotEmpty() || module.functions.isNotEmpty() || module.dataClasses.isNotEmpty()) {
            val bridgeCode = RustBridgeGenerator().generate(module)
            bridgeFile.writeText(bridgeCode)
            logger.lifecycle("kne-rust: Generated Rust bridges → ${bridgeFile.absolutePath}")
        } else {
            logger.lifecycle("kne-rust: No supported public API found; keeping placeholder bridges")
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
                val featuresList = if (crate.features.isNotEmpty())
                    ", features = [${crate.features.joinToString(", ") { "\"$it\"" }}]"
                else ""
                when {
                    crate.version != null && featuresList.isEmpty() -> appendLine("${crate.name} = \"${crate.version}\"")
                    crate.version != null -> appendLine("${crate.name} = { version = \"${crate.version}\"$featuresList }")
                    crate.path != null -> appendLine("${crate.name} = { path = \"${crate.path}\"$featuresList }")
                    crate.gitUrl != null -> appendLine("${crate.name} = { git = \"${crate.gitUrl}\", branch = \"${crate.gitBranch}\"$featuresList }")
                }
            }
            // pollster is used by generated bridges to block on async Rust methods
            appendLine("pollster = \"0.4\"")
        }
        rustProjectDir.resolve("Cargo.toml").writeText(cargoToml)

        val srcDir = rustProjectDir.resolve("src")
        srcDir.mkdirs()
        srcDir.resolve("lib.rs").writeText("// placeholder\n")
        // Placeholder lib.rs — will be rewritten after rustdoc analysis in rewriteWrapperLibRs

        logger.lifecycle("kne-rust: Generated wrapper Cargo project at ${rustProjectDir.absolutePath}")
        return rustProjectDir
    }

    private fun resolveRustdocProject(
        crates: List<CrateDependency>,
        cargoProjectDir: File,
        logger: org.gradle.api.logging.Logger,
    ): File {
        val localCrate = crates.singleOrNull { it.path != null }
        if (localCrate != null) return cargoProjectDir

        // For external crates, run rustdoc on the wrapper project.
        // The wrapper has `pub use crate::*;` which makes rustdoc inline re-exported types.
        // Running directly on the dependency source doesn't apply features from the wrapper.
        return cargoProjectDir
    }

    private fun resolveDependencySourceDir(
        cargoProjectDir: File,
        crate: CrateDependency,
        logger: org.gradle.api.logging.Logger,
    ): File? {
        val cargo = findCargo()
        val process = ProcessBuilder(cargo, "metadata", "--format-version", "1", "--quiet")
            .directory(cargoProjectDir)
            .redirectErrorStream(true)
            .start()

        val output = process.inputStream.bufferedReader().readText()
        val exitCode = process.waitFor()
        if (exitCode != 0) {
            logger.warn("kne-rust: cargo metadata failed while resolving '${crate.name}':\n$output")
            return null
        }

        return findPackageManifestDir(output, crate)?.parentFile
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

    private fun mergeModules(modules: List<KneModule>, libName: String): KneModule {
        if (modules.isEmpty()) return KneModule(libName = libName, packages = emptySet(), classes = emptyList(), dataClasses = emptyList(), enums = emptyList(), functions = emptyList())
        if (modules.size == 1) return modules.single()

        // Collect all type names from richer representations (sealed enums, data classes, enums)
        // to exclude their opaque class counterparts
        val sealedEnumNames = modules.flatMap { it.sealedEnums }.map { it.simpleName }.toSet()
        val dataClassNames = modules.flatMap { it.dataClasses }.map { it.simpleName }.toSet()
        val enumNames = modules.flatMap { it.enums }.map { it.simpleName }.toSet()
        val richTypeNames = sealedEnumNames + dataClassNames + enumNames

        // Detect type names that appear in multiple crates (different fqName prefixes)
        data class TypeNameInfo(val simpleName: String, val fqName: String)
        val allTypeInfos = mutableListOf<TypeNameInfo>()
        modules.forEach { m ->
            m.classes.forEach { allTypeInfos.add(TypeNameInfo(it.simpleName, it.fqName)) }
            m.enums.forEach { allTypeInfos.add(TypeNameInfo(it.simpleName, it.fqName)) }
            m.sealedEnums.forEach { allTypeInfos.add(TypeNameInfo(it.simpleName, it.fqName)) }
            m.dataClasses.forEach { allTypeInfos.add(TypeNameInfo(it.simpleName, it.fqName)) }
        }
        // Only flag types as ambiguous if they come from truly independent crate families.
        // Sub-crate re-exports (e.g. nokhwa re-exporting nokhwa_core types) are not ambiguous
        // because they are the same type — only flag when the root crate names differ
        // (e.g. "symphonia_core" vs "cpal" are different families, but "nokhwa" vs "nokhwa_core" are the same).
        val ambiguousNames = allTypeInfos.groupBy { it.simpleName }
            .filter { (_, infos) ->
                val crateRoots = infos.map { it.fqName.substringBefore('.').substringBefore('_') }.toSet()
                crateRoots.size > 1
            }
            .keys

        val seenClasses = mutableSetOf<String>()
        val seenDataClasses = mutableSetOf<String>()
        val seenEnums = mutableSetOf<String>()
        val seenFunctions = mutableSetOf<String>()
        val seenInterfaces = mutableSetOf<String>()
        val seenSealedEnums = mutableSetOf<String>()
        return KneModule(
            libName = libName,
            packages = modules.flatMap { it.packages }.toSet(),
            // Skip opaque classes that have a richer representation as sealed enum / data class / enum.
            // When the same class name appears from multiple crates, prefer the version with the
            // most methods (e.g. nokhwa_core's Resolution with new/width/height over nokhwa's opaque).
            classes = modules.flatMap { it.classes }
                .filter { it.simpleName !in richTypeNames }
                .groupBy { it.simpleName }
                .values.map { variants ->
                    if (variants.size == 1) variants.single()
                    else {
                        // Pick the richest variant (most methods/properties/constructor)
                        val richest = variants.maxByOrNull {
                            it.methods.size + it.companionMethods.size + it.properties.size +
                                if (it.constructor.kind != KneConstructorKind.NONE) 1 else 0
                        }!!
                        // Merge interfaces from all variants so trait impls aren't lost
                        val allInterfaces = variants.flatMap { it.interfaces }.distinct()
                        if (allInterfaces != richest.interfaces) richest.copy(interfaces = allInterfaces)
                        else richest
                    }
                },
            dataClasses = modules.flatMap { it.dataClasses }.filter { seenDataClasses.add(it.simpleName) },
            enums = modules.flatMap { it.enums }.filter { seenEnums.add(it.simpleName) },
            functions = modules.flatMap { it.functions }.filter { seenFunctions.add(it.name) },
            interfaces = modules.flatMap { it.interfaces }.filter { seenInterfaces.add(it.simpleName) },
            sealedEnums = modules.flatMap { it.sealedEnums }.filter { seenSealedEnums.add(it.simpleName) },
            traitImpls = modules.fold(mutableMapOf<String, MutableList<KneType.OBJECT>>()) { acc, m ->
                m.traitImpls.forEach { (k, v) -> acc.getOrPut(k) { mutableListOf() }.addAll(v) }
                acc
            },
            ambiguousTypeNames = ambiguousNames,
        )
    }

    /**
     * Rewrites the wrapper crate's lib.rs with proper use statements based on the dependency's
     * rustdoc JSON. This ensures all sub-module types are in scope for the generated bridges.
     */
    private fun rewriteWrapperLibRs(
        srcDir: File,
        crates: List<CrateDependency>,
        docDir: File,
        logger: org.gradle.api.logging.Logger,
    ) {
        // Helper: recursively collect all module paths from a rustdoc JSON index.
        // Also follows glob re-exports (`pub use crate::*;`) into their target crate's JSON
        // to discover submodules that are re-exported through the current module.
        fun collectModulePaths(index: com.google.gson.JsonObject, items: com.google.gson.JsonArray, parentPath: String): List<String> {
            val paths = mutableListOf<String>()
            for (itemId in items) {
                val item = index.get(itemId.asInt.toString())?.asJsonObject ?: continue
                val inner = item.getAsJsonObject("inner") ?: continue
                if (inner.has("module")) {
                    val name = item.get("name")?.asString ?: continue
                    val path = if (parentPath.isEmpty()) name else "$parentPath::$name"
                    paths.add(path)
                    val subItems = inner.getAsJsonObject("module")?.getAsJsonArray("items")
                    if (subItems != null) {
                        paths.addAll(collectModulePaths(index, subItems, path))
                    }
                } else if (inner.has("use")) {
                    // Handle glob re-exports like `pub use muda::*;` inside a module
                    val useItem = inner.getAsJsonObject("use")
                    val isGlob = useItem.get("is_glob")?.asBoolean == true
                    if (isGlob) {
                        val source = useItem.get("source")?.let { if (it.isJsonNull) null else it.asString } ?: continue
                        val subCrateName = source.replace('-', '_')
                        val subJson = docDir.resolve("$subCrateName.json")
                        if (subJson.exists()) {
                            // Parse the glob-re-exported crate's modules under the current path
                            val subDoc = com.google.gson.JsonParser.parseString(subJson.readText()).asJsonObject
                            val subIndex = subDoc.getAsJsonObject("index") ?: continue
                            val subRootId = subDoc.get("root")?.asInt?.toString() ?: continue
                            val subRoot = subIndex.get(subRootId)?.asJsonObject ?: continue
                            val subItems = subRoot.getAsJsonObject("inner")
                                ?.getAsJsonObject("module")?.getAsJsonArray("items") ?: continue
                            paths.addAll(collectModulePaths(subIndex, subItems, parentPath))
                        }
                    }
                }
            }
            return paths
        }

        // Helper: extract root module items from a rustdoc JSON file
        fun parseModuleTree(jsonFile: File): List<String> {
            val json = com.google.gson.JsonParser.parseString(jsonFile.readText()).asJsonObject
            val index = json.getAsJsonObject("index") ?: return emptyList()
            val rootId = json.get("root")?.asInt?.toString() ?: return emptyList()
            val rootModule = index.get(rootId)?.asJsonObject ?: return emptyList()
            val rootItems = rootModule.getAsJsonObject("inner")
                ?.getAsJsonObject("module")
                ?.getAsJsonArray("items") ?: return emptyList()
            return collectModulePaths(index, rootItems, "")
        }

        val primaryCrateName = crates.first().name.replace('-', '_')

        // Collect pub use statements
        val useStatements = mutableListOf<String>()

        // 1. Parse main crate JSON: discover direct sub-modules AND re-exported crates
        val mainJsonFile = docDir.resolve("$primaryCrateName.json")
        val reExportedCrates = mutableMapOf<String, String>() // sub-crate name → re-export alias

        if (mainJsonFile.exists()) {
            useStatements.add("pub use ${primaryCrateName}::*;")
            for (modPath in parseModuleTree(mainJsonFile)) {
                useStatements.add("pub use ${primaryCrateName}::${modPath}::*;")
            }

            // Detect re-exported sub-crates and modules (e.g. `pub use symphonia_core;` or `pub use muda::dpi;`)
            val mainJson = com.google.gson.JsonParser.parseString(mainJsonFile.readText()).asJsonObject
            val mainIndex = mainJson.getAsJsonObject("index")
            val mainPaths = mainJson.getAsJsonObject("paths")
            val rootId = mainJson.get("root")?.asInt?.toString()
            val rootItems = rootId?.let { mainIndex?.get(it)?.asJsonObject }
                ?.getAsJsonObject("inner")?.getAsJsonObject("module")?.getAsJsonArray("items")
            if (rootItems != null) {
                for (itemId in rootItems) {
                    val item = mainIndex.get(itemId.asInt.toString())?.asJsonObject ?: continue
                    val inner = item.getAsJsonObject("inner") ?: continue
                    if (inner.has("use")) {
                        val useItem = inner.getAsJsonObject("use")
                        val sourceElem = useItem.get("source")
                        if (sourceElem == null || sourceElem.isJsonNull) continue
                        val source = sourceElem.asString

                        // Check if target is a module (via paths map) — handles `pub use muda::dpi;`
                        val targetId = useItem.get("id")?.asInt?.toString()
                        val targetPath = targetId?.let { mainPaths?.get(it)?.asJsonObject }
                        val targetKind = targetPath?.get("kind")?.asString
                        val useAlias = useItem.get("name")?.let { if (it.isJsonNull) null else it.asString }

                        if (targetKind == "module" && useAlias != null) {
                            // Module re-export: add `pub use primary::alias::*;`
                            useStatements.add("pub use ${primaryCrateName}::${useAlias}::*;")
                            continue
                        }

                        // Re-exported crate: source is the crate name (e.g. "symphonia_core")
                        // The alias in the parent crate is derived from the crate name
                        // e.g. symphonia_core is accessible as symphonia::core
                        val nameElem = item.get("name")
                        val alias = if (nameElem != null && !nameElem.isJsonNull) nameElem.asString
                            else source.removePrefix("${primaryCrateName}_")
                        reExportedCrates[source.replace('-', '_')] = alias
                    }
                }
            }
        }

        // 2. For re-exported sub-crates, parse their modules and map to the correct path
        //    e.g. symphonia_core is re-exported → symphonia::core::io::*, symphonia::core::audio::*
        for ((subCrateName, alias) in reExportedCrates) {
            val subJson = docDir.resolve("$subCrateName.json")
            if (!subJson.exists()) continue
            val reExportPath = "${primaryCrateName}::${alias}"
            useStatements.add("pub use ${reExportPath}::*;")
            for (modPath in parseModuleTree(subJson)) {
                useStatements.add("pub use ${reExportPath}::${modPath}::*;")
            }
        }

        // 3. Parse additional crate JSONs (separate dependencies like cpal)
        for (crate in crates.drop(1)) {
            val additionalCrateName = crate.name.replace('-', '_')
            val additionalJson = docDir.resolve("$additionalCrateName.json")
            if (additionalJson.exists()) {
                useStatements.add("pub use ${additionalCrateName}::*;")
                for (modPath in parseModuleTree(additionalJson)) {
                    useStatements.add("pub use ${additionalCrateName}::${modPath}::*;")
                }
            }
        }

        val uniqueStatements = useStatements.distinct()
        val libRs = buildString {
            for (stmt in uniqueStatements) {
                appendLine(stmt)
            }
            appendLine()
            appendLine("include!(concat!(env!(\"OUT_DIR\"), \"/kne_bridges.rs\"));")
        }
        srcDir.resolve("lib.rs").writeText(libRs)
        logger.lifecycle("kne-rust: Rewrote wrapper lib.rs with ${uniqueStatements.size} use statements (recursive, multi-crate)")
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

    private fun runCargoRustdoc(cargoDir: File, libName: String, crates: List<CrateDependency>, logger: org.gradle.api.logging.Logger): File? {
        val cargo = findCargo()

        val isWrapper = cargoDir.resolve("Cargo.toml").readText().contains("kne-")
        val cmd = if (isWrapper) {
            // For wrapper crates, omit --no-deps so re-exported dependency types are inlined
            listOf(cargo, "doc")
        } else {
            listOf(cargo, "doc", "--no-deps")
        }
        val process = ProcessBuilder(cmd)
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
        return selectRustdocJson(docDir, cargoDir, libName, crates)
    }

    internal fun selectRustdocJson(docDir: File, cargoDir: File, libName: String, crates: List<CrateDependency> = emptyList()): File? {
        val jsonFiles = docDir.listFiles()?.filter { it.extension == "json" } ?: return null
        if (jsonFiles.isEmpty()) return null

        // For wrapper crates (no local path), prefer the dependency crate's JSON directly.
        // Wrapper's glob re-export (pub use dep::*) doesn't inline types in rustdoc JSON.
        val isWrapper = crates.isNotEmpty() && crates.none { it.path != null }
        if (isWrapper) {
            for (crate in crates) {
                val depName = crate.name.replace('-', '_')
                val depJson = docDir.resolve("$depName.json")
                if (depJson.exists()) return depJson
            }
        }

        val expectedNames = linkedSetOf<String>()
        resolveRustdocTargetName(cargoDir)?.let { expectedNames.add(it) }
        expectedNames.add(libName.replace('-', '_'))

        for (name in expectedNames) {
            val expected = docDir.resolve("$name.json")
            if (expected.exists()) return expected
        }

        return if (jsonFiles.size == 1) {
            jsonFiles.single()
        } else {
            jsonFiles.maxByOrNull { it.lastModified() }
        }
    }

    internal fun resolveRustdocTargetName(cargoDir: File): String? {
        val cargoToml = cargoDir.resolve("Cargo.toml")
        if (!cargoToml.exists()) return null

        var inPackage = false
        var inLib = false
        var packageName: String? = null
        var libTargetName: String? = null

        cargoToml.forEachLine { rawLine ->
            val line = rawLine.substringBefore('#').trim()
            when {
                line == "[package]" -> {
                    inPackage = true
                    inLib = false
                }
                line == "[lib]" -> {
                    inPackage = false
                    inLib = true
                }
                line.startsWith("[") && line.endsWith("]") -> {
                    inPackage = false
                    inLib = false
                }
                inPackage && line.startsWith("name") -> {
                    packageName = extractTomlStringValue(line)
                }
                inLib && line.startsWith("name") -> {
                    libTargetName = extractTomlStringValue(line)
                }
            }
        }

        return (libTargetName ?: packageName)?.replace('-', '_')
    }

    private fun extractTomlStringValue(line: String): String? =
        Regex("""^\s*\w+\s*=\s*"([^"]+)"""").find(line)?.groupValues?.getOrNull(1)

    internal fun findPackageManifestDir(metadataJson: String, crate: CrateDependency): File? {
        val jsonStart = metadataJson.indexOf('{')
        if (jsonStart < 0) return null
        val root = JsonParser.parseString(metadataJson.substring(jsonStart)).asJsonObject
        val packages = root.getAsJsonArray("packages") ?: return null
        for (pkg in packages) {
            val pkgObj = pkg.asJsonObject
            val name = pkgObj.get("name")?.asString ?: continue
            if (name != crate.name) continue
            val version = pkgObj.get("version")?.asString
            if (crate.version != null && version != crate.version) continue
            val manifestPath = pkgObj.get("manifest_path")?.asString ?: continue
            return File(manifestPath)
        }
        return null
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
