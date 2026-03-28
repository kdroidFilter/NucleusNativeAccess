package io.github.kdroidfilter.kotlinnativeexport.plugin

import org.gradle.api.provider.Property

/**
 * DSL extension for the kotlin-native-export plugin.
 *
 * Minimal configuration needed — the plugin derives everything else
 * from the nativeMain source set automatically.
 *
 * Convention follows swift-java / swift-export patterns:
 * - Both debug and release variants are built
 * - [buildType] selects which variant the test/run tasks link against
 * - java.library.path includes both debug + release dirs (like swift-java)
 */
abstract class KotlinNativeExportExtension {

    /** Name of the native shared library to produce (e.g. "myexample" → libmyexample.so). */
    abstract val nativeLibName: Property<String>

    /**
     * The Kotlin package that the JVM proxy classes will be generated in.
     * Defaults to matching the native package.
     */
    abstract val nativePackage: Property<String>

    /**
     * Build type for the native shared library that tests and JVM tasks depend on.
     * Accepted values: "debug", "release" (case-insensitive).
     * Defaults to "release" (smaller .so, optimised).
     *
     * Both variants are always configured as binaries on the native target,
     * but only the selected one is linked before tests run.
     */
    abstract val buildType: Property<String>
}
