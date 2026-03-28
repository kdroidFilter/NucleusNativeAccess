# Kotlin Native Export

A Gradle plugin that lets you use **Kotlin/Native code directly from the JVM** as if it were a regular JVM library. Classes, methods, properties, top-level functions &mdash; everything is transparent to the JVM developer.

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
./gradlew :examples:calculator:jvmTest    # 76 tests
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

The current implementation covers: classes, methods, properties, top-level functions, all primitive types, String, enums, companion objects, object composition, and nullable types. The roadmap extends the type system, improves analysis, and adds deployment features.

Design references: [swift-export-standalone](https://github.com/JetBrains/kotlin/tree/master/native/swift/swift-export-standalone) (SIR model, K2 analysis, bridge generation pipeline) and [swift-java](https://github.com/swiftlang/swift-java) (FFM proxy generation, upcall handles, memory management).

### Phase 2a &mdash; Foundation (unblocks everything else)

- [ ] **Kotlin Analysis API (K2)** &mdash; replace regex `KotlinSourceParser` with proper K2 symbol extraction
  - [ ] Add `kotlin-analysis-api` dependency, set up `analyze {}` session lifecycle
  - [ ] Create a `KneSession` facade (inspired by SIR's `SirSession`) aggregating providers: `DeclarationProvider`, `TypeProvider`, `ParentProvider`
  - [ ] Dispatch `KaDeclarationSymbol` kinds &rarr; `KneClass`, `KneFunction`, `KneProperty` (like `SirDeclarationFromKtSymbolProvider`)
  - [ ] Extract full type information: nullability, generics bounds, modality, annotations
  - [ ] Detect `@Throws` annotation for exception propagation
  - [ ] Extract companion object members via `combinedDeclaredMemberScope`
  - [ ] Keep regex parser as fallback (flag `useAnalysisApi = true|false` in extension DSL)

- [ ] **Extend the IR model (`KneIR.kt`)** &mdash; add missing concepts to represent Phase 2 features
  - [x] `KneType.NULLABLE(inner: KneType)` &mdash; wrapper type for nullable values
  - [ ] `KneType.SEALED(fqName, subclasses)` &mdash; algebraic types
  - [ ] `KneType.FUNCTION(params, returnType)` &mdash; function/lambda types
  - [ ] `KneInterface` &mdash; Kotlin interface declaration (protocols in SIR)
  - [ ] `KneClass.superClass`, `KneClass.interfaces`, `KneClass.modality` (OPEN / FINAL / ABSTRACT / SEALED)
  - [x] `KneClass.companionMethods`, `KneClass.companionProperties` &mdash; static members
  - [ ] `KneConstructor.overloads` &mdash; list of parameter combinations (from default params)
  - [ ] `KneFunction.errorType` &mdash; nullable, present when `@Throws` detected
  - [x] `KneEnum` with entries list (name, ordinal)

### Phase 2b &mdash; Type system extensions

- [x] **Nullable types** &mdash; proper `null` handling across FFM boundary
  - [x] Native bridge: sentinel-based encoding (widened types for primitives, -1/0L for reference types)
  - [x] FFM proxy: check sentinels after downcall, return `null` when sentinel detected
  - [x] String params/returns: `MemorySegment.NULL` for null, -1 return for null strings

- [x] **Enums** &mdash; map Kotlin `enum class` to JVM enum
  - [x] Native bridge: `@CName` function returning ordinal `Int` + name `String` (output-buffer)
  - [x] FFM proxy: generate JVM `enum class` with `values()` / `valueOf()`, backed by ordinal downcall
  - [x] Support enum as parameter type (pass ordinal, reconstruct on native side)

- [ ] **Sealed classes** &mdash; map to JVM sealed interface + data classes (inspired by swift-java's sealed interface + records pattern)
  - [ ] Native bridge: discriminator function returning subclass tag `Int`
  - [ ] FFM proxy: `sealed interface` with per-subclass `data class` implementing it
  - [ ] Pattern: `getDiscriminator()` downcall &rarr; `when(tag)` dispatch on JVM side

### Phase 2c &mdash; OOP features

- [ ] **Inheritance & open classes** &mdash; preserve class hierarchy on JVM
  - [ ] IR: track `superClass` reference and `modality`
  - [ ] Native bridge: virtual dispatch via `StableRef` (actual object type preserved)
  - [ ] FFM proxy: generate `open class` with `override` methods where applicable
  - [ ] Upcast support: child handle usable where parent type expected

- [ ] **Interfaces** &mdash; generate JVM interfaces for Kotlin interfaces
  - [ ] IR: `KneInterface` with method signatures (no body)
  - [ ] FFM proxy: generate `interface` + ensure implementing classes declare `override`
  - [ ] No native bridge needed for interface declarations themselves (only implementing classes)

- [x] **Companion objects** &mdash; expose as static methods/properties on JVM proxy
  - [x] Extract companion members (via K2 `combinedDeclaredMemberScope` or regex `companion object` block)
  - [x] Native bridge: `@CName` functions without handle parameter (like top-level)
  - [x] FFM proxy: `companion object` with MethodHandles (no instance needed)

- [ ] **Constructor overloads** &mdash; generate overloads from default parameter values
  - [ ] K2: detect `hasDefaultValue` on `KaValueParameterSymbol`
  - [ ] Generate N overloads: full params, drop last default, drop last 2, etc.
  - [ ] Native bridge: one `@CName` per overload, each calling `ClassName(...)` with appropriate args
  - [ ] FFM proxy: multiple `operator fun invoke(...)` overloads in companion

### Phase 2d &mdash; Advanced type features

- [ ] **Exceptions** &mdash; catch on native side, propagate to JVM
  - [ ] Native bridge: wrap body in `try/catch`, write error code + message to out-params (inspired by swift-java's `throwAsException` / swift-export's `errorType` out-parameter)
  - [ ] FFM proxy: check error code after downcall, throw `KotlinNativeException(message)` on JVM
  - [ ] Only generate for functions annotated with `@Throws`

- [ ] **Lambdas & callbacks** &mdash; FFM upcall handles for JVM &rarr; native callbacks
  - [ ] IR: `KneType.FUNCTION` with param/return types
  - [ ] FFM proxy: accept `@FunctionalInterface` parameter, create upcall stub via `Linker.nativeLinker().upcallStub()` (swift-java pattern)
  - [ ] Native bridge: receive `CPointer<CFunction<...>>`, invoke as C function pointer
  - [ ] Lifetime: upcall stub tied to `Arena.ofConfined()`, freed after native call returns

- [ ] **Generics** &mdash; type-erased proxy generation
  - [ ] IR: `KneClass.typeParameters` with optional upper bound
  - [ ] Single upper bound &rarr; use bound type; no bound &rarr; `Any` (like SIR)
  - [ ] Native bridge: erased to upper bound at bridge level (same `StableRef<Any>`)
  - [ ] FFM proxy: generate generic class with unchecked casts on return values
  - [ ] Multiple bounds &rarr; unsupported (warn and skip, like swift-export)

### Phase 2e &mdash; Packaging & deployment

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

### Won't do (out of scope)

- Suspend functions / coroutines across FFM (fundamentally different runtimes)
- Full Kotlin Multiplatform expect/actual (that's KMP's job, not ours)

## Requirements

- **Kotlin** 2.3.20+
- **Gradle** 9.1+ (for JDK 25 support)
- **JDK** 22+ (FFM stable since JDK 22 / JEP 454), recommended 25
- **Kotlin/Native** toolchain (bundled with KMP plugin)

## License

This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
