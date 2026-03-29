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

## What's supported

### Types

| Feature | As param | As return | As property | In callbacks | Notes |
|---------|----------|-----------|-------------|--------------|-------|
| `Int` | ✅ | ✅ | ✅ | ✅ param + return | direct pass-through |
| `Long` | ✅ | ✅ | ✅ | ✅ param + return | direct pass-through |
| `Double` | ✅ | ✅ | ✅ | ✅ param + return | direct pass-through |
| `Float` | ✅ | ✅ | ✅ | ✅ param + return | direct pass-through |
| `Boolean` | ✅ | ✅ | ✅ | ✅ param + return | 0/1 convention over FFM |
| `Byte` | ✅ | ✅ | ✅ | ✅ param + return | direct pass-through |
| `Short` | ✅ | ✅ | ✅ | ✅ param + return | direct pass-through |
| `String` | ✅ | ✅ | ✅ | ✅ param + return | output-buffer pattern for returns |
| `Unit` | &mdash; | ✅ | &mdash; | ✅ return only | `FunctionDescriptor.ofVoid(...)` |
| `enum class` | ✅ | ✅ | ✅ | ✅ param + return | ordinal mapping, auto-generates JVM enum |
| Classes | ✅ | ✅ | &mdash; | ❌ | opaque handle via `StableRef` |
| `T?` (nullable) | ✅ | ✅ | ✅ | ❌ | sentinel-based null encoding |
| `data class` | ✅ | ✅ | &mdash; | ✅ param only | field decomposition (primitive + String fields) |
| `(T) -> R` (lambda) | ✅ | &mdash; | &mdash; | &mdash; | FFM upcall stubs, persistent arena |

### Declarations

| Feature | Supported | Notes |
|---------|-----------|-------|
| Top-level classes | ✅ | `StableRef` lifecycle, `AutoCloseable` on JVM |
| Methods (fun) | ✅ | instance methods with any supported param/return types |
| Properties (val/var) | ✅ | getters + setters, all supported types |
| Constructors | ✅ | primary constructor with supported param types |
| Companion objects | ✅ | static methods and properties on JVM proxy |
| Top-level functions | ✅ | grouped into a singleton `object` on JVM |
| Enum classes | ✅ | auto-generated JVM enum with ordinal mapping |
| Data classes (nativeMain) | ✅ | auto-generates JVM data class + field marshalling |
| Data classes (commonMain) | ✅ | reuses existing JVM type, no proxy generated |
| Exception propagation | ✅ | `try/catch` wrapping, `KotlinNativeException` on JVM |
| Object lifecycle | ✅ | `Cleaner` for GC + `close()` for explicit release |

### Callbacks & lambdas

JVM lambdas cross the FFM boundary via upcall stubs. The plugin generates all the FFM infrastructure automatically.

**Lifecycle**: each proxy object holds a persistent `Arena.ofShared()`. Upcall stubs live as long as the object &mdash; async callbacks (event handlers, listeners) work out of the box. The arena is freed on `close()` or GC.

**Supported callback signatures**:
- Params: `Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`, `String`, `enum class`, `data class`
- Returns: `Int`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Short`, `String`, `Unit`, `enum class`
- Multi-param: `(T, U) -> R` with any supported types
- Data class params are decomposed into individual fields at C ABI level

```kotlin
// Kotlin/Native
fun onValueChanged(callback: (Int) -> Unit) { callback(accumulator) }
fun transform(fn: (Int) -> Int): Int { accumulator = fn(accumulator); return accumulator }
fun formatWith(formatter: (Int) -> String): String = formatter(accumulator)

// JVM — transparent
calc.onValueChanged { value -> println("Value: $value") }
calc.transform { it * 2 }
calc.formatWith { "Result: $it" }

// Async callbacks work (e.g. native event listeners)
desktop.setTrayClickCallback { index -> println("Clicked: $index") }
```

### Data classes

Data classes are marshalled **by value** (field decomposition) &mdash; each field becomes a separate C ABI argument. Supported field types: all primitives + `String`.

```kotlin
// Can be in commonMain or nativeMain
data class Point(val x: Int, val y: Int)

// nativeMain
fun getPoint(): Point = Point(accumulator, accumulator * 2)
fun addPoint(p: Point): Int { accumulator += p.x + p.y; return accumulator }

// JVM — uses the real data class (not an opaque handle)
val p = calc.getPoint()          // Point(x=5, y=10)
calc.addPoint(Point(3, 7))       // 10
```

- **commonMain data classes**: the JVM already has the type &mdash; no proxy generated, field marshalling only
- **nativeMain data classes**: the plugin generates the JVM `data class` file automatically

### Exception propagation

All native bridge functions are wrapped in `try/catch`. When an exception occurs:

1. The native side captures the error message in a `@ThreadLocal` variable
2. The JVM proxy calls `kne_hasError()` after every downcall
3. If an error is detected, `kne_getLastError()` retrieves the message
4. A `KotlinNativeException(message)` is thrown on the JVM side

```kotlin
try { calc.divide(0) } catch (e: KotlinNativeException) { println(e.message) }
calc.add(5) // works normally after exception
```

### Nullable type encoding

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

## What's NOT supported

| Feature | Reason | Alternative |
|---------|--------|-------------|
| Interfaces | Can live in `commonMain` | Define in shared KMP code |
| Inheritance / open classes | Can live in `commonMain` | Define in shared KMP code |
| Sealed classes | Can live in `commonMain` | Define in shared KMP code |
| Generics | Complex type erasure at FFM boundary | Use concrete types |
| Nested/inner classes | Parser limitation | Use top-level classes |
| Data class fields: `Enum`, `Object`, other data classes | Not yet implemented | Use primitives + String fields |
| Data class as callback return | Not yet implemented | Return individual fields or use out-param pattern |
| Nullable data class (`DataClass?`) | Not yet implemented | Use non-null with sentinel values |
| Object (class) in callbacks | Not yet implemented | Use data class or primitives |
| Lambda as return type | Callback param only, not return | Return a class with methods instead |
| Suspend functions / coroutines | Different runtimes | Use callbacks for async patterns |
| `ByteArray` / collections | Not yet implemented | Use individual elements or C buffers |
| Constructor default parameters | Parser limitation | Define overloads manually |
| Private/internal members | By design | Only public API is exported |
| Expect/actual declarations | KMP's responsibility | Use platform-specific source sets |

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
./gradlew :examples:calculator:jvmTest    # 168 tests
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

Design references: [swift-export-standalone](https://github.com/JetBrains/kotlin/tree/master/native/swift/swift-export-standalone) (SIR model, K2 analysis, bridge generation pipeline) and [swift-java](https://github.com/swiftlang/swift-java) (FFM proxy generation, upcall handles, memory management).

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

Features like interfaces, inheritance, sealed classes, and generics belong in `commonMain` (shared KMP code) and do not need FFM bridges. Data classes are supported as value types (field marshalling) to enable natural APIs, and `commonMain` data classes are reused directly without generating duplicates.

## Requirements

- **Kotlin** 2.3.20+
- **Gradle** 9.1+ (for JDK 25 support)
- **JDK** 22+ (FFM stable since JDK 22 / JEP 454), recommended 25
- **Kotlin/Native** toolchain (bundled with KMP plugin)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
