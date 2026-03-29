# Kotlin Native Export

A Gradle plugin that lets you use **Kotlin/Native code directly from the JVM** as if it were a regular JVM library. Classes, methods, properties, enums, nullable types, companion objects, exception propagation, callbacks &mdash; everything is transparent to the JVM developer.

Under the hood, the plugin generates [FFM (Foreign Function & Memory API)](https://openjdk.org/jeps/454) bindings inspired by [swift-java](https://github.com/swiftlang/swift-java) and [swift-export-standalone](https://github.com/JetBrains/kotlin/tree/master/native/swift/swift-export-standalone).

## How it works

```
Kotlin/Native source           Plugin generates              JVM developer sees
──────────────────            ────────────────              ──────────────────
class Calculator {      →     @CName bridges (native)   →   class Calculator : AutoCloseable {
  fun add(value: Int)         + StableRef lifecycle           fun add(value: Int): Int
  val current: Int            + FFM MethodHandles             val current: Int
}                             + output-buffer strings         // backed by native, via FFM
                                                          }
```

**Pipeline:**

1. Plugin scans your `nativeMain` sources and extracts the public API
2. Generates `@CName` bridge functions with `StableRef` for object lifecycle (native side)
3. Generates JVM proxy classes with FFM `MethodHandle` downcalls (JVM side)
4. Compiles to a shared library (`.so` / `.dylib` / `.dll`)
5. JVM code calls the proxies transparently &mdash; every call crosses the FFM boundary into native

## Quick start

### 1. Apply the plugin

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
    id("io.github.kdroidfilter.kotlinnativeexport") version "0.1.0"
}
```

### 2. Configure targets

```kotlin
kotlin {
    jvmToolchain(25) // FFM is stable since JDK 22 (JEP 454), recommended JDK 25

    linuxX64()       // use the real platform name (KMP convention)
    // macosArm64()  // on macOS
    // mingwX64()    // on Windows

    jvm()

    sourceSets {
        val jvmMain by getting {
            dependencies {
                // your JVM dependencies (Compose, Ktor, etc.)
            }
        }
    }
}

kotlinNativeExport {
    nativeLibName = "mylib"         // output: libmylib.so (release ~700KB)
    nativePackage = "com.example"   // package for generated JVM proxies
    buildType = "release"           // "release" (default, optimized) or "debug"
}
```

### 3. Write Kotlin/Native code

```kotlin
// src/nativeMain/kotlin/com/example/Calculator.kt
package com.example

class Calculator(initial: Int = 0) {
    private var acc = initial

    fun add(value: Int): Int { acc += value; return acc }
    fun subtract(value: Int): Int { acc -= value; return acc }
    val current: Int get() = acc
    fun describe(): String = "Calculator(current=$acc)"
}
```

### 4. Use it from JVM as if it were a normal class

```kotlin
// src/jvmMain/kotlin/com/example/Main.kt
package com.example

fun main() {
    val calc = Calculator(0)  // allocates a Kotlin/Native object
    calc.add(5)               // FFM → native → StableRef → add()
    calc.add(3)
    println(calc.current)     // 8
    println(calc.describe())  // "Calculator(current=8)"
    calc.close()              // releases the native object (also auto-GC'd via Cleaner)
}
```

No JNI. No annotations. No boilerplate. Just write Kotlin/Native and use it from JVM.

### 5. Run

```bash
./gradlew jvmTest    # compiles native + generates bridges + runs JVM tests
./gradlew run        # if using Compose Desktop / Nucleus
```

## Supported types

| Kotlin type | FFM layout | Notes |
|-------------|-----------|-------|
| `Int` | `JAVA_INT` | direct pass-through |
| `Long` | `JAVA_LONG` | direct pass-through |
| `Double` | `JAVA_DOUBLE` | direct pass-through |
| `Float` | `JAVA_FLOAT` | direct pass-through |
| `Boolean` | `JAVA_INT` | 0/1 convention |
| `Byte` | `JAVA_BYTE` | direct pass-through |
| `Short` | `JAVA_SHORT` | direct pass-through |
| `String` | `ADDRESS` | output-buffer pattern for returns |
| `Unit` | void | `FunctionDescriptor.ofVoid(...)` |
| Classes | `JAVA_LONG` | opaque handle via `StableRef` |
| Enums | `JAVA_INT` | ordinal mapping |
| Class references | `JAVA_LONG` | pass/return handles between classes |
| `T?` (nullable) | widened | sentinel-based null encoding (see below) |
| `(T) -> R` (lambda) | `JAVA_LONG` | FFM upcall stub address (see below) |

### Callbacks & lambdas

JVM lambdas cross the FFM boundary to Kotlin/Native via upcall stubs. The plugin generates all the FFM infrastructure automatically.

**Lifecycle**: each proxy object holds a persistent `Arena.ofShared()`. Upcall stubs live as long as the object &mdash; async callbacks (event handlers, listeners) work out of the box. The arena is freed on `close()` or GC.

```kotlin
// Kotlin/Native side
class Calculator(initial: Int = 0) {
    fun onValueChanged(callback: (Int) -> Unit) {
        callback(accumulator)
    }
    fun transform(fn: (Int) -> Int): Int {
        accumulator = fn(accumulator)
        return accumulator
    }
}

// JVM side — lambdas are transparent
val calc = Calculator(10)
calc.onValueChanged { value -> println("Value: $value") }    // prints "Value: 10"
val doubled = calc.transform { it * 2 }                       // 20

// Async callbacks work too (e.g. native event listeners)
desktop.setTrayClickCallback { index ->
    println("Tray item clicked: $index")
}
```

Supported callback signatures: primitive params (`Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`) and `Unit`/primitive returns.

### Exception propagation

All native bridge functions are wrapped in `try/catch`. When an exception occurs:

1. The native side captures the error message in a `@ThreadLocal` variable
2. The JVM proxy calls `kne_hasError()` after every downcall
3. If an error is detected, `kne_getLastError()` retrieves the message
4. A `KotlinNativeException(message)` is thrown on the JVM side

Zero-allocation happy path: only 2 FFM calls per invocation (function + hasError check). The error path adds 1 extra call + Arena for the string buffer.

```kotlin
// Kotlin/Native side
fun divide(divisor: Int): Int {
    require(divisor != 0) { "Division by zero" }
    return accumulator / divisor
}

// JVM side — transparent exception handling
val calc = Calculator(10)
try {
    calc.divide(0)
} catch (e: KotlinNativeException) {
    println(e.message) // "Division by zero"
}
calc.add(5) // works normally after exception
```

### Nullable type encoding

All nullable types are supported. The encoding uses sentinel values to represent `null` at the FFM boundary:

| Nullable type | Wire type | Null sentinel |
|---------------|-----------|---------------|
| `String?` | output-buffer `Int` | -1 = null |
| `Object?` | `JAVA_LONG` | 0L = null |
| `Enum?` | `JAVA_INT` | -1 = null |
| `Boolean?` | `JAVA_INT` | -1 = null, 0 = false, 1 = true |
| `Int?` | `JAVA_LONG` | `Long.MIN_VALUE` = null |
| `Long?` | `JAVA_LONG` | `Long.MIN_VALUE` = null |
| `Short?` | `JAVA_INT` | `Int.MIN_VALUE` = null |
| `Byte?` | `JAVA_INT` | `Int.MIN_VALUE` = null |
| `Float?` | `JAVA_LONG` (raw bits) | `Long.MIN_VALUE` = null |
| `Double?` | `JAVA_LONG` (raw bits) | `Long.MIN_VALUE` = null |

## Configuration reference

```kotlin
kotlinNativeExport {
    // Name of the shared library (required)
    // Produces: libmylib.so (Linux), libmylib.dylib (macOS), mylib.dll (Windows)
    nativeLibName = "mylib"

    // Package for the generated JVM proxy classes (required)
    nativePackage = "com.example"

    // Build type: "release" (default, ~700KB .so) or "debug" (~6MB .so)
    buildType = "release"
}
```

### JVM runtime requirements

The generated FFM proxies require:

- **JDK 22+** (FFM API finalized in JDK 22 via [JEP 454](https://openjdk.org/jeps/454), recommended JDK 25)
- **`--enable-native-access=ALL-UNNAMED`** JVM arg (auto-configured for tests by the plugin)
- **`java.library.path`** pointing to the directory containing the `.so` (auto-configured for tests)

### Using with Compose Desktop / Nucleus

```kotlin
plugins {
    kotlin("multiplatform") version "2.3.20"
    id("org.jetbrains.compose") version "1.8.0"
    id("org.jetbrains.kotlin.plugin.compose") version "2.3.20"
    id("io.github.kdroidfilter.nucleus") version "1.7.2"
    id("io.github.kdroidfilter.kotlinnativeexport")
}

// Compose compiler only targets JVM (native sources have no @Composable)
composeCompiler {
    targetKotlinPlatforms.set(
        setOf(org.jetbrains.kotlin.gradle.plugin.KotlinPlatformType.jvm)
    )
}

// Configure native lib path for the run task
nucleus.application {
    mainClass = "com.example.MainKt"
    jvmArgs += listOf(
        "--enable-native-access=ALL-UNNAMED",
        "-Djava.library.path=${project.projectDir}/build/bin/linuxX64/mylibReleaseShared",
    )
}
```

### Using with C interop (e.g. libnotify)

You can combine the plugin with Kotlin/Native cinterop to wrap native C libraries and expose them to JVM:

```kotlin
// build.gradle.kts
kotlin {
    linuxX64().compilations["main"].cinterops {
        val libnotify by creating {
            defFile(project.file("src/nativeInterop/cinterop/libnotify.def"))
        }
    }
}
```

```kotlin
// src/nativeMain/kotlin/LinuxDesktop.kt
class LinuxDesktop {
    fun sendNotification(title: String, body: String, icon: String): Boolean {
        // calls libnotify via cinterop — impossible from JVM without JNI
        notify_init("MyApp")
        val n = notify_notification_new(title, body, icon) ?: return false
        return notify_notification_show(n, null) != 0
    }

    fun getHostname(): String = memScoped {
        val buf = allocArray<ByteVar>(256)
        gethostname(buf, 256u)
        buf.toKString()
    }
}
```

```kotlin
// src/jvmMain/kotlin/Main.kt — transparent usage
val desktop = LinuxDesktop()
desktop.sendNotification("Hello", "From Kotlin/Native via FFM!", "dialog-information")
println(desktop.getHostname())
desktop.close()
```

## Examples

The repository includes two complete examples in [`examples/`](examples/):

| Example | Description |
|---------|-------------|
| [`calculator/`](examples/calculator/) | Stateful Calculator class exported from Kotlin/Native, with Compose Desktop UI and Nucleus packaging |
| [`systeminfo/`](examples/systeminfo/) | Linux system info (`/proc`, POSIX, `gethostname`) + native notifications via `libnotify` cinterop, with Compose Desktop UI |

Run them:

```bash
./gradlew :examples:calculator:run
./gradlew :examples:systeminfo:run
./gradlew :examples:calculator:jvmTest    # 95 tests
./gradlew :examples:systeminfo:jvmTest    # 7 tests
```

## Architecture

The plugin is inspired by two projects:

- **[swift-export-standalone](https://github.com/JetBrains/kotlin/tree/master/native/swift/swift-export-standalone)** (JetBrains) &mdash; how Kotlin exports its API to Swift via C bridges. We adapted the approach: scan Kotlin sources, generate `@CName` bridge functions with `StableRef` for object lifecycle.

- **[swift-java](https://github.com/swiftlang/swift-java)** (Apple) &mdash; how Swift code is made callable from Java via FFM `MethodHandle` downcalls. We adapted the FFM binding generation: each method gets a `FunctionDescriptor` + `MethodHandle`, classes use `Cleaner` for GC safety.

```
plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/kotlinnativeexport/plugin/
├── ir/                          # Intermediate representation (inspired by SirModule)
│   └── KneIR.kt                 # KneModule, KneClass, KneFunction, KneType...
├── analysis/
│   └── KotlinSourceParser.kt    # Parses .kt files, extracts public API
├── codegen/
│   ├── NativeBridgeGenerator.kt # @CName + StableRef bridges (inspired by @_cdecl thunks)
│   └── FfmProxyGenerator.kt     # JVM proxy classes with FFM (inspired by FFMSwift2JavaGenerator)
├── tasks/
│   ├── GenerateNativeBridgesTask.kt
│   └── GenerateJvmProxiesTask.kt
├── KotlinNativeExportExtension.kt
└── KotlinNativeExportPlugin.kt
```

## Roadmap

The current implementation covers: classes, methods, properties, top-level functions, all primitive types, String, enums, companion objects, object composition, nullable types, exception propagation, and callbacks/lambdas.

Design references: [swift-export-standalone](https://github.com/JetBrains/kotlin/tree/master/native/swift/swift-export-standalone) (SIR model, K2 analysis, bridge generation pipeline) and [swift-java](https://github.com/swiftlang/swift-java) (FFM proxy generation, upcall handles, memory management).

### What's done

- [x] Classes, methods, properties, constructors
- [x] All primitive types (`Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`)
- [x] `String` (output-buffer pattern for returns, `CPointer<ByteVar>` for params)
- [x] `Unit` / void functions
- [x] Enums (ordinal mapping, enum as param/return, mutable enum properties)
- [x] Companion objects (static methods and properties, factory functions)
- [x] Object composition (class as param/return, `StableRef` handle passing)
- [x] Nullable types (sentinel-based encoding for all supported types)
- [x] Exception propagation (`@ThreadLocal` error state, `KotlinNativeException` on JVM)
- [x] Callbacks/lambdas (FFM upcall stubs, persistent `Arena.ofShared()`, async-safe)

### Next &mdash; Packaging & deployment

Make the plugin production-ready with zero-config deployment.

- [ ] **Automatic native lib bundling** &mdash; single-JAR deployment
  - [ ] Embed `.so`/`.dylib`/`.dll` in JAR under `META-INF/native/{os}-{arch}/`
  - [ ] `KneRuntime.loadLibrary()`: extract to temp dir at startup, load via `System.load()`
  - [ ] Detect GraalVM native-image: skip extraction, use `SymbolLookup.loaderLookup()` (swift-java pattern)

- [ ] **GraalVM reachability-metadata generation** &mdash; auto-generate for native-image
  - [ ] Generate `reachability-metadata.json` listing all FFM downcall descriptors
  - [ ] Output to `META-INF/native-image/{groupId}/{artifactId}/` in JAR
  - [ ] Register generated proxy classes for reflection if needed

- [ ] **Multi-target support** &mdash; fat JARs with per-platform native libs
  - [ ] Detect all configured `KotlinNativeTarget`s in the project
  - [ ] Bundle each platform's shared lib under `META-INF/native/{os}-{arch}/`
  - [ ] `KneRuntime`: detect current OS/arch at startup, load correct variant

### Phase 5 &mdash; Analysis robustness

- [ ] **Kotlin Analysis API (K2)** &mdash; replace regex `KotlinSourceParser` with proper K2 symbol extraction
  - [ ] Add `kotlin-analysis-api` dependency, set up `analyze {}` session lifecycle
  - [ ] Create a `KneSession` facade (inspired by SIR's `SirSession`)
  - [ ] Extract full type information: nullability, annotations, companion members
  - [ ] Keep regex parser as fallback (flag `useAnalysisApi = true|false` in extension DSL)

### Future considerations

- **Suspend functions / coroutines** &mdash; swift-java maps `async` to `CompletableFuture`, kotlin-swift-export maps `suspend` to Swift `async/await`. A similar approach could map `suspend` to `CompletableFuture` on JVM, but this requires significant runtime support.

### Design philosophy

This plugin binds **only what must cross the native boundary** &mdash; platform-specific APIs (cinterop, POSIX, C libraries), performance-critical native code, and types that cannot exist in common Kotlin.

Features like interfaces, inheritance, sealed classes, generics, and data classes belong in `commonMain` (shared KMP code) and do not need FFM bridges. If it can be written in common Kotlin, it should be.

### Out of scope

- Interfaces, inheritance, sealed classes, generics (use `commonMain`)
- Full Kotlin Multiplatform expect/actual (that's KMP's job)

## Requirements

- **Kotlin** 2.3.20+
- **Gradle** 9.1+ (for JDK 25 support)
- **JDK** 22+ (FFM stable since JDK 22 / JEP 454), recommended 25
- **Kotlin/Native** toolchain (bundled with KMP plugin)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
