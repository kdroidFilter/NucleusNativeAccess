package io.github.kdroidfilter.nucleusnativeaccess.plugin

import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.Property
import java.io.Serializable

/**
 * DSL extension for importing Rust crates as Kotlin libraries.
 *
 * Usage:
 * ```kotlin
 * rustImport {
 *     libraryName = "calculator"
 *     jvmPackage = "com.example.calculator"
 *     crate("calculator", path = "../rust")
 * }
 * ```
 */
abstract class RustImportExtension {

    /** Name of the native shared library (e.g. "calculator" → libcalculator.so). */
    abstract val libraryName: Property<String>

    /** Package for generated JVM proxy classes. */
    abstract val jvmPackage: Property<String>

    /** Build type: "debug" or "release". */
    abstract val buildType: Property<String>

    /** Registered crate dependencies. */
    abstract val crates: ListProperty<CrateDependency>

    /** Add a crate from crates.io. */
    fun crate(name: String, version: String) {
        crates.add(CrateDependency(name = name, version = version))
    }

    /** Add a crate from a local path. */
    fun cratePath(name: String, path: String) {
        crates.add(CrateDependency(name = name, path = path))
    }

    /** Add a crate from a git repository. */
    fun crateGit(name: String, repository: String, branch: String = "main") {
        crates.add(CrateDependency(name = name, gitUrl = repository, gitBranch = branch))
    }
}

data class CrateDependency(
    val name: String,
    val version: String? = null,
    val path: String? = null,
    val gitUrl: String? = null,
    val gitBranch: String? = null,
) : Serializable
