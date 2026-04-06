# Nucleus Native Access

[![Gradle Plugin Portal](https://img.shields.io/maven-metadata/v?metadataUrl=https%3A%2F%2Fplugins.gradle.org%2Fm2%2Fio%2Fgithub%2Fkdroidfilter%2Fnucleusnativeaccess%2Fio.github.kdroidfilter.nucleusnativeaccess.gradle.plugin%2Fmaven-metadata.xml&label=Gradle%20Plugin%20Portal)](https://plugins.gradle.org/plugin/io.github.kdroidfilter.nucleusnativeaccess)

A Gradle plugin that lets you use **Kotlin/Native or Rust code directly from the JVM** as if it were a regular JVM library. Classes, methods, properties, enums, nullable types, companion objects, exception propagation, callbacks &mdash; everything is transparent to the JVM developer.

Under the hood, the plugin generates [FFM (Foreign Function & Memory API)](https://openjdk.org/jeps/454) bindings inspired by [swift-java](https://github.com/swiftlang/swift-java) and [swift-export-standalone](https://github.com/JetBrains/kotlin/tree/master/native/swift/swift-export-standalone).

## Two bridges, one plugin

| Bridge | Source language | Gradle DSL | Status | Guide |
|--------|----------------|------------|--------|-------|
| **Kotlin/Native** | `nativeMain` Kotlin sources | `kotlinNativeExport { }` | Experimental | [README-KOTLIN-NATIVE.md](README-KOTLIN-NATIVE.md) |
| **Rust** | Any Rust crate — no annotations required | `rustImport { }` | Proof of concept | [README-RUST.md](README-RUST.md) |

Both bridges produce the same result on the JVM: idiomatic Kotlin proxy classes backed by a native shared library loaded via FFM. No JNI. No annotations. No boilerplate.

## How it works

```
Source (Kotlin/Native or Rust)    Plugin generates              JVM developer sees
──────────────────────────────    ────────────────              ──────────────────
class Calculator {           →    C bridges (native)        →   class Calculator : AutoCloseable {
  fun add(value: Int): Int        + FFM MethodHandles                fun add(value: Int): Int
  val current: Int                + object lifecycle                 val current: Int
}                                 + shared library (.so)             // backed by native, via FFM
                                                               }
```

**Shared pipeline**:

1. Plugin analyses the source (Kotlin PSI for KN, rustdoc JSON for Rust) and extracts the public API
2. Generates C-ABI bridge functions on the native side
3. Generates JVM proxy classes with FFM `MethodHandle` downcalls on the JVM side
4. Compiles to a shared library (`.so` / `.dylib` / `.dll`)
5. Bundles the native library into the JAR under `kne/native/{os}-{arch}/`
6. Generates GraalVM reachability metadata

## Quick start

### Apply the plugin

```kotlin
// settings.gradle.kts
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.20"
    id("io.github.kdroidfilter.nucleusnativeaccess") version "0.1.0"
}
```

Then follow the guide for your use case:

- **[Kotlin/Native bridge →](README-KOTLIN-NATIVE.md)** — wrap `nativeMain` Kotlin code
- **[Rust bridge →](README-RUST.md)** — import any Rust crate

## Examples

| Example | Bridge | Description |
|---------|--------|-------------|
| [`calculator/`](examples/calculator/) | Kotlin/Native | Calculator with 1700+ end-to-end tests: all types, callbacks, collections, suspend, Flow, nested classes, inheritance, interfaces, sealed classes, extensions, concurrency |
| [`systeminfo/`](examples/systeminfo/) | Kotlin/Native | Linux system info (`/proc`, POSIX, `gethostname`) + native notifications via `libnotify` cinterop, with Compose Desktop UI |
| [`benchmark/`](examples/benchmark/) | Kotlin/Native | Performance benchmarks: native vs JVM |
| [`rust-calculator/`](examples/rust-calculator/) | Rust | Calculator with Compose Desktop UI &mdash; same API as the Kotlin/Native calculator |
| [`rust-benchmark/`](examples/rust-benchmark/) | Rust | Performance benchmarks: Rust vs JVM |
| [`rust-tray-icon/`](examples/rust-tray-icon/) | Rust (local wrapper) | macOS/Windows/Linux system tray icon via `tray-icon 0.19` &mdash; demonstrates `dispatch_sync` main-thread bridging and event polling |

```bash
# Kotlin/Native examples
./gradlew :examples:calculator:jvmTest    # 1700+ end-to-end FFM tests
./gradlew :examples:benchmark:jvmTest     # KN performance benchmarks

# Rust examples
./gradlew :examples:rust-calculator:run           # Compose Desktop UI powered by Rust
./gradlew :examples:rust-benchmark:jvmTest        # Rust performance benchmarks
./gradlew :examples:rust-tray-icon:run            # System tray icon via Rust
```

## Architecture

```
plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/nucleusnativeaccess/plugin/
├── ir/
│   └── KneIR.kt                    # Shared IR: KneModule, KneClass, KneFunction, KneType...
├── analysis/
│   ├── PsiSourceParser.kt          # Kotlin PSI-based parser (Kotlin/Native path)
│   ├── PsiParseWorkAction.kt       # Gradle Worker for isolated PSI classloader
│   ├── RustdocJsonParser.kt        # Rustdoc JSON parser (Rust path)
│   └── RustWorkAction.kt           # Rust pipeline orchestration
├── codegen/
│   ├── NativeBridgeGenerator.kt    # @CName + StableRef bridges (Kotlin/Native)
│   ├── RustBridgeGenerator.kt      # #[no_mangle] extern "C" fn bridges (Rust)
│   └── FfmProxyGenerator.kt        # JVM proxy classes with FFM (shared, language-agnostic)
├── tasks/
│   ├── GenerateNativeBridgesTask.kt # Kotlin/Native bridge generation task
│   ├── GenerateRustBindingsTask.kt  # Rust bridge + proxy generation task
│   └── CargoBuildTask.kt           # Invokes cargo build
├── KotlinNativeExportExtension.kt   # kotlinNativeExport { } DSL
├── RustImportExtension.kt           # rustImport { } DSL
└── KotlinNativeExportPlugin.kt      # Plugin entry: KMP + Rust wiring
```

The IR layer (`KneIR.kt`) is the key abstraction: both the PSI parser (Kotlin/Native) and the rustdoc JSON parser (Rust) emit the same IR, which is then consumed by `FfmProxyGenerator` to produce identical JVM proxy code regardless of the source language.

## Requirements

- **Kotlin** 2.3.20+
- **Gradle** 9.1+ (for JDK 25 support)
- **JDK** 22+ (FFM stable since JDK 22 / [JEP 454](https://openjdk.org/jeps/454)), recommended 25
- **Kotlin/Native** toolchain (bundled with KMP plugin) — for `kotlinNativeExport`
- **Rust** toolchain — `cargo` must be on PATH or in `~/.cargo/bin/` — for `rustImport`

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
