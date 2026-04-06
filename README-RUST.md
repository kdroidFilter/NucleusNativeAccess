# Nucleus Native Access — Rust Bridge

← [Back to overview](README.md)

> **Proof of concept.** The Rust bridge demonstrates that the NNA pipeline is language-agnostic: the same FFM proxy infrastructure works with any language that emits a C-ABI shared library. It is not intended for production use and may change significantly or be removed.

Import **any Rust crate** and use it from Kotlin/JVM as if it were a Kotlin library. No modifications to the Rust crate required &mdash; unlike UniFFI/Gobley which require `#[uniffi::export]` annotations.

```
Rust crate (any)                Plugin generates              JVM developer sees
────────────────              ────────────────              ──────────────────
pub struct Calculator {  →    rustdoc JSON → parse      →   class Calculator : AutoCloseable {
  pub fn add(&mut self, n)    #[no_mangle] bridges            fun add(n: Int): Int
  pub fn get_current(&self)   + FFM MethodHandles              val current: Int
}                             + cargo build → .so              // backed by Rust, via FFM
                                                           }
```

**Pipeline:**

1. Plugin runs `cargo rustdoc --output-format json` to extract the crate's public API
2. Parses the rustdoc JSON &mdash; structs, enums, methods, functions, types
3. Generates Rust `#[no_mangle] pub extern "C" fn` bridge wrappers (same symbol convention as Kotlin/Native)
4. Reuses the existing `FfmProxyGenerator` to produce JVM proxy classes
5. Runs `cargo build --release` to produce the shared library
6. Bundles the `.so`/`.dylib`/`.dll` into the JAR

## Quick start

### 1. Configure the plugin

```kotlin
// build.gradle.kts
plugins {
    kotlin("multiplatform") version "2.3.20"
    id("io.github.kdroidfilter.nucleusnativeaccess")
}

kotlin {
    jvmToolchain(25)
    jvm()
}

rustImport {
    libraryName = "mylib"
    jvmPackage = "com.example.mylib"
    cratePath("my-crate", "../rust")     // local crate
    // crate("some-crate", "1.0")        // from crates.io
    // crateGit("name", "https://...", branch = "main")  // from git
}
```

### 2. Write normal Rust — no special annotations

```rust
// rust/src/lib.rs
pub struct Calculator {
    accumulator: i32,
}

impl Calculator {
    pub fn new(initial: i32) -> Self { Calculator { accumulator: initial } }
    pub fn add(&mut self, value: i32) -> i32 { self.accumulator += value; self.accumulator }
    pub fn get_current(&self) -> i32 { self.accumulator }
    pub fn describe(&self) -> String { format!("Calculator(current={})", self.accumulator) }
}

pub enum Operation { Add, Subtract, Multiply }

pub fn greet(name: String) -> String { format!("Hello, {}!", name) }
```

### 3. Use it from JVM — same API as Kotlin/Native

```kotlin
// src/jvmMain/kotlin/Main.kt
fun main() {
    val calc = Calculator(0)
    calc.add(5)
    calc.add(3)
    println(calc.current)     // 8
    println(calc.describe())  // "Calculator(current=8)"
    calc.close()
}
```

### 4. Run

```bash
./gradlew jvmTest    # generates bridges + cargo build + runs JVM tests
./gradlew run        # if using Compose Desktop
```

## What's supported

| Rust construct | Mapped to | Notes |
|----------------|-----------|-------|
| `pub struct` with methods | `KneClass` (opaque handle) | `Box::into_raw` / `Box::from_raw` lifecycle |
| `pub struct` (all-pub fields, no methods) | `KneDataClass` (field expansion) | Marshalled by value |
| `pub enum` (fieldless) | `KneEnum` | Ordinal mapping |
| `impl` methods (`&self`) | Instance methods | Immutable borrow |
| `impl` methods (`&mut self`) | Instance methods | Mutable borrow |
| `get_X()` / `set_X()` pattern | `val` / `var` properties | Auto-detected |
| `pub fn` (top-level) | Module-level functions | Grouped in singleton object |
| All primitives (`i32`, `i64`, `f64`, `f32`, `bool`, `i8`, `i16`) | Int, Long, Double, Float, Boolean, Byte, Short | Direct mapping |
| `String` / `&str` | String | Borrowed vs owned auto-detected |
| `Option<T>` return | `T?` | Sentinel-based null encoding |
| `Vec<T>` / `&[T]` return | `List<T>` / `ByteArray` | Buffer pattern; supports `i32`, `i64`, `f64`, `f32`, `bool`, `String` element types |
| `&[u8]` / `&[i32]` params | `ByteArray` / `List<Int>` | Pointer + length expansion |
| `HashMap<K,V>` | `Map<K,V>` | Parallel arrays |
| Error propagation | `KotlinNativeException` | `catch_unwind` + thread-local error |
| `(A, B)` / `(A, B, C)` tuples | `KneTupleN_<TypeId>` data class | Arity 0–16; nested tuples supported (e.g. `(i32, (String, bool))`) |
| Tuple as param | Expanded to individual parameters | `fn sum(coords: (i32, i32))` → `fun sum(coords: KneTuple2_TII)` |
| `!` (Never type) | Diverging functions (`panic!`, `std::process::exit`) | Returns `Unit`, throws `RuntimeException` on JVM with panic message |
| `impl Iterator<Item=T>` return | `List<T>` | Collected via `.collect()` in bridge; also `ExactSizeIterator`, `IntoIterator`, `DoubleEndedIterator` |
| `impl Display` / `impl ToString` return | `String` | Materialized via `.to_string()` in bridge |
| `impl Into<T>` return | `T` | Converted via `.into()` in bridge |
| `impl Trait` return | `T` | Resolved via known trait map (Display, ToString, IntoIterator, Iterator, ExactSizeIterator, DoubleEndedIterator, Future) |
| `async fn` / `impl Future<Output=T>` | `suspend fun` returning `T` | Rust side: `pollster::block_on()`; Kotlin side: `suspend fun` with `withContext(Dispatchers.IO)` |
| `impl Into<T>` / `impl ToString` params | `String` | Synthetic `impl Trait` params in argument position are resolved; `&[impl ToString]` → `List<String>` |
| Trait objects (`dyn Trait`) | Supported | `Box<dyn Trait>` returns via registry; `&dyn Trait` / `&mut dyn Trait` params via handle + transmute |
| `fn(A) -> B` callbacks | Supported | Function pointers and `impl Fn`/`FnOnce` with primitive, enum, object, sealed enum, and `dyn Trait` types |
| Callbacks with handle types | Supported | `impl FnOnce(Object) -> SealedEnum`, `impl FnOnce(i32) -> Box<dyn Trait>`, etc. |

### Supported with caveats

| Construct | Behaviour | Notes |
|-----------|-----------|-------|
| `HashMap<K,V>` / `BTreeMap<K,V>` return | Mapped to `Map<K, V>` | Keys/values serialized via dual-buffer pattern; MAP properties now supported via StableRef |
| `HashSet<T>` / `BTreeSet<T>` return | Mapped to `Set<T>` | Serialized as list, deduplicated on JVM side via `.toSet()` |
| `Option<DataClass>` return | Mapped to `DataClass?` | Uses presence flag (0=null, 1=present) + per-field out-params |
| `Option<Vec<u8>>` return | Mapped to `ByteArray?` | Uses buffer pattern; returns `-1` for `None`, byte count for `Some` |
| `OsStr` / `OsString` / `Path` / `PathBuf` | Mapped to `String` | Uses `to_string_lossy()` on output, may lose non-UTF-8 data |
| `Vec<Object>` return | Elements returned as borrowed handles | Pointers into the parent collection; valid while parent lives |
| Borrowed returns (`&T`) | Returned as borrowed handle (no ownership) | JVM proxy won't dispose the native object |
| `unsafe fn` methods | Generated with `unsafe { }` wrapper | Caller is responsible for safety invariants |
| Tuple return with nested tuples | `(i32, (String, bool))` → `KneTuple2_TITRZ` | Inner tuples heap-allocated with 8-byte-slot layout; supports arbitrary nesting depth |
| `impl Iterator<Item=T>` return | Mapped to `List<T>` | Collected via `.collect::<Vec<_>>()` in bridge |
| `&[T]` return (borrowed slices) | Materialized to `List<T>` | Borrowed slice is copied into a `Vec<T>` in the bridge; safe but allocates |
| `Result<impl Trait, E>` return | Combined with impl Trait | Result unwrapped first, then impl Trait conversion applied |
| `async fn` methods | `suspend fun` with `withContext(Dispatchers.IO)` | Rust bridge uses `pollster::block_on()`; Kotlin proxy emits private sync method + public `suspend fun` wrapper |

## Current limitations

Excluded functions are logged at generation time (stderr), so you know exactly which functions were skipped and why.

| Category | Unsupported construct | Impact | Workaround |
|----------|----------------------|--------|------------|
| **Generics** | Generic **methods** with custom trait bounds (`fn process<T: MyTrait>(...)`) | **Auto-monomorphised**: NNA scans `impl Trait for Type` blocks and generates one bridge per concrete implementor. Turbofish applied automatically. | &mdash; |
| **Generics** | Generic **structs** with custom trait bounds (`struct Foo<T: MyTrait>`) | **Auto-monomorphised**: NNA generates one class per concrete implementor. | &mdash; |
| **Generics** | Generic types with lifetime parameters in args | Lifetime args in generic position are skipped | &mdash; |
| **Types** | Function pointer types (`fn(A) -> B`) as return | Skipped with log message | &mdash; |
| **Types** | Tuple parameters on standalone `pub fn` | Tuples as parameters not supported | Expand tuple fields into individual parameters |
| **Enums** | Tagged enum variants with collection fields | Constructors supported for `Vec<T>`, `HashSet<T>`, `HashMap<K,V>` with **primitive** element types only | Use primitive element types |
| **Types** | Cross-crate re-exported types | **Lazy resolution**: types from sub-crates are auto-discovered from rustdoc JSON index | &mdash; |
| **Constructors** | Generic constructors (`fn new<T: Trait>(...)`) on standalone structs | Skipped if generics can't be resolved | Use concrete types or non-generic factory methods |
| **Mutability** | Interior mutability (`Cell`, `RefCell`, `Mutex`) | No special handling; may cause UB if misused | &mdash; |
| **Concurrency** | `Send` / `Sync` bounds | Not enforced on JVM side | Be careful with multithreaded access |
| **Lifetimes** | Explicit lifetime parameters on structs (`struct Ref<'a>`) | Entire struct skipped with log message | Remove lifetime parameters or use owned types |
| **Mutability** | `&mut T` parameters on standalone `pub fn` | Treated as `&T` (immutable borrow) | Use `impl` methods with `&mut self` instead |

## Benchmarks — Rust (FFM) vs Pure JVM

Same methodology as the Kotlin/Native benchmarks. Run with `./gradlew :examples:rust-benchmark:jvmTest`.

| Benchmark | Rust (FFM) | JVM | Ratio | Analysis |
|-----------|-----------|-----|-------|----------|
| Fibonacci recursive (n=35) | 18.42 ms | 24.21 ms | **0.76x** | **Rust faster** (no JIT warmup) |
| Fibonacci iterative (n=1M) | 0.26 ms | 0.26 ms | **1.00x** | Identical |
| Pi Leibniz series (10M) | 8.44 ms | 8.40 ms | **1.00x** | Identical |
| Sum array (10M) | ~0 ms | 0.67 ms | **~0x** | **Rust much faster** |
| String concat (10K) | 0.14 ms | 18.74 ms | **0.01x** | **Rust 100x faster** (Rust string alloc) |
| Bubble sort (5K) | 13.90 ms | 5.99 ms | 2.32x | JVM JIT better at array access |
| FFM overhead (100K calls) | 1.86 ms | 0.24 ms | 7.63x | ~19 ns/call FFM overhead |
| Object create+close (10K) | 1.63 ms | 0.09 ms | 17x | Box alloc+drop cost |
| String return (10K) | 5.30 ms | 0.64 ms | 8.23x | Buffer copy overhead |
| Data class return (10K) | 2.06 ms | 0.04 ms | 48x | Out-param marshaling |
| Concurrent fib (10t&times;1K) | 0.94 ms | 0.45 ms | 2.07x | Thread contention |
| Concurrent string (10t&times;1K) | 0.97 ms | 1.24 ms | **0.78x** | **Rust faster** |

**Rust vs Kotlin/Native comparison**: Rust and Kotlin/Native show similar FFM overhead profiles. Rust excels at string concatenation (~100x faster than JVM) and array summation. Both are competitive on heavy compute (fibonacci, pi). The FFM call overhead is lower for Rust (~19 ns/call vs ~49 ns/call for KN).

## Requirements

- **Rust** toolchain — `cargo` must be on PATH or in `~/.cargo/bin/`
- **JDK 22+** (FFM stable since [JEP 454](https://openjdk.org/jeps/454)), recommended JDK 25
- **`--enable-native-access=ALL-UNNAMED`** JVM arg (auto-configured for tests by the plugin)
