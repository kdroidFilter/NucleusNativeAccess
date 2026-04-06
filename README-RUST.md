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

## Real-world examples

Three real crates imported directly from crates.io — no Rust modifications, no wrapper crate, just `rustImport { crate(...) }` in `build.gradle.kts`. Source: [`examples/`](examples/).

### System information — `sysinfo 0.38`

```kotlin
// build.gradle.kts
rustImport {
    libraryName = "rustsysinfo"
    jvmPackage = "com.example.rustsysinfo"
    crate("sysinfo", "0.38.4")
}
```

The plugin maps `System`, `Disks`, `Networks`, `Components`, `Users`, `Groups`, and `Process` directly from the sysinfo API.

**Data flow**: a Kotlin `Flow` polls `System.new_all()` every 2 seconds and emits structured state. The Compose UI collects it with `collectAsState`.

```kotlin
// Polling flow — runs on Dispatchers.IO
fun dynamicStateFlow(interval: Duration = 2.seconds): Flow<DynamicState> = flow {
    val sys = System.new_all()
    try {
        while (true) {
            sys.refresh_all()

            val cpus = sys.cpus().map { cpu ->
                CpuInfo(name = cpu.name(), brand = cpu.brand(), usage = cpu.cpu_usage(), frequency = cpu.frequency())
            }
            val memory = MemoryInfo(
                totalMemory = sys.total_memory(),
                usedMemory = sys.used_memory(),
                availableMemory = sys.available_memory(),
                totalSwap = sys.total_swap(),
                usedSwap = sys.used_swap(),
            )
            val processes = sys.processes().values
                .sortedByDescending { it.cpu_usage() }
                .take(30)
                .map { proc ->
                    ProcessInfo(
                        pid = proc.pid().as_u32().toLong(),
                        name = proc.name(),
                        cpuUsage = proc.cpu_usage(),
                        memory = proc.memory(),
                        status = proc.status().tag.name,
                        cmd = proc.cmd(),
                    )
                }
            val diskList = Disks.new_with_refreshed_list()
            val disks = diskList.list().map { disk ->
                DiskInfo(name = disk.name(), mountPoint = disk.mount_point(), totalSpace = disk.total_space(), availableSpace = disk.available_space())
            }
            diskList.close()

            emit(DynamicState(memory = memory, cpus = cpus, processes = processes, disks = disks, globalCpuUsage = sys.global_cpu_usage()))
            delay(interval)
        }
    } finally {
        sys.close()
    }
}.flowOn(Dispatchers.IO)
```

**Compose UI** — the state flows into `collectAsState` and drives the UI directly:

```kotlin
@Composable
fun App() {
    val state by remember { dynamicStateFlow() }.collectAsState(initial = null)

    when (selected) {
        NavItem.Cpu    -> CpuTab(state?.cpus ?: emptyList(), state?.globalCpuUsage ?: 0f)
        NavItem.Memory -> MemoryTab(state?.memory)
        NavItem.Disks  -> DisksTab(state?.disks ?: emptyList())
        NavItem.Processes -> ProcessesTab(state?.processes ?: emptyList())
        // …
    }
}

@Composable
fun CpuTab(cpus: List<CpuInfo>, globalUsage: Float) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Total Usage", "%.1f%%".format(globalUsage), Modifier.weight(1f))
                StatBox("Cores", "${cpus.size}", Modifier.weight(1f))
                StatBox("Frequency", "${cpus.firstOrNull()?.frequency} MHz", Modifier.weight(1f))
            }
        }
        items(cpus) { cpu ->
            GaugeBar(label = cpu.name, fraction = cpu.usage / 100f, detail = "%.1f%% @ %d MHz".format(cpu.usage, cpu.frequency))
        }
    }
}

@Composable
fun MemoryTab(info: MemoryInfo?) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Total", formatBytes(info.totalMemory), Modifier.weight(1f))
                StatBox("Used",  formatBytes(info.usedMemory),  Modifier.weight(1f))
                StatBox("Free",  formatBytes(info.availableMemory), Modifier.weight(1f))
            }
        }
        item {
            val usedPct = info.usedMemory.toFloat() / info.totalMemory
            GaugeBar("RAM", usedPct, "${formatBytes(info.usedMemory)} / ${formatBytes(info.totalMemory)}")
        }
    }
}

@Composable
fun ProcessesTab(processes: List<ProcessInfo>) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        items(processes) { proc ->
            Column {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(proc.name, fontWeight = FontWeight.Bold)
                    Text("PID ${proc.pid}")
                }
                GaugeBar("CPU", proc.cpuUsage / 100f, "%.1f%%".format(proc.cpuUsage))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatBox("Memory",  formatBytes(proc.memory),        Modifier.weight(1f))
                    StatBox("Virtual", formatBytes(proc.virtualMemory), Modifier.weight(1f))
                }
                Badge(proc.status)
            }
        }
    }
}
```

Full Compose Desktop app with 9 tabs (System, CPU, Memory, Disks, Network, Processes, Sensors, Users, Groups): [`examples/rust-sysinfo/`](examples/rust-sysinfo/).

```bash
./gradlew :examples:rust-sysinfo:run
```

---

### Native file dialogs — `rfd 0.17`

[rfd](https://crates.io/crates/rfd) opens native OS file pickers and message dialogs on Linux (GTK), macOS, and Windows — no AWT, no Swing.

```kotlin
// build.gradle.kts
rustImport {
    libraryName = "rustrfd"
    jvmPackage = "com.example.rustrfd"
    crate("rfd", "0.17.2")
}
```

The plugin maps `FileDialog`, `MessageDialog`, `MessageLevel`, `MessageButtons`, and `MessageDialogResult`. All dialog methods are `async fn` on the Rust side → automatically mapped to `suspend fun`. In Compose, call them from a `rememberCoroutineScope`:

```kotlin
@Composable
fun FilePickerTab() {
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Single file picker with filters
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val path = FileDialog()
                        .set_title("Select an image")
                        .add_filter("Images", listOf("png", "jpg", "jpeg", "gif"))
                        .pick_file()
                    result = path ?: "Cancelled"
                    busy = false
                }
            }) { Text("Pick Image") }

            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val paths = FileDialog()
                        .set_title("Select source files")
                        .add_filter("Kotlin", listOf("kt", "kts"))
                        .add_filter("Rust", listOf("rs", "toml"))
                        .pick_files()
                    result = paths?.joinToString("\n") ?: "Cancelled"
                    busy = false
                }
            }) { Text("Pick Sources") }

            Button(enabled = !busy, onClick = {
                busy = true
                scope.launch {
                    val path = FileDialog()
                        .set_title("Save as")
                        .set_file_name("output.txt")
                        .add_filter("Text", listOf("txt"))
                        .save_file()
                    result = path ?: "Cancelled"
                    busy = false
                }
            }) { Text("Save File") }
        }

        result?.let { Text(it) }
    }
}

@Composable
fun MessageTab() {
    val scope = rememberCoroutineScope()
    var answer by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {

        // Warning dialog — Yes/No/Cancel
        Button(onClick = {
            scope.launch {
                val r = MessageDialog()
                    .set_title("Save Changes?")
                    .set_description("You have unsaved changes. Do you want to save before closing?")
                    .set_level(MessageLevel.Warning)
                    .set_buttons(MessageButtons.yesNoCancelCustom("Save", "Discard", "Go Back"))
                    .show()
                answer = r.tag.name  // "Yes", "No", or "Cancel"
            }
        }) { Text("Unsaved changes…") }

        // Error dialog
        Button(onClick = {
            scope.launch {
                val r = MessageDialog()
                    .set_title("Critical Error")
                    .set_description("A critical error was detected. Would you like to try again?")
                    .set_level(MessageLevel.Error)
                    .set_buttons(MessageButtons.okCancel())
                    .show()
                answer = r.tag.name
            }
        }) { Text("Show Error") }

        answer?.let { Text("Result: $it") }
    }
}
```

Full Compose Desktop app with file picker, folder picker, save dialog, and message dialog tabs: [`examples/rust-rfd/`](examples/rust-rfd/).

```bash
./gradlew :examples:rust-rfd:run
```

---

### Webcam capture — `nokhwa 0.10`

[nokhwa](https://crates.io/crates/nokhwa) provides cross-platform webcam access (V4L2 on Linux, AVFoundation on macOS, DirectShow on Windows).

```kotlin
// build.gradle.kts
rustImport {
    libraryName = "rustcamera"
    jvmPackage = "com.example.rustcamera"
    crate("nokhwa", "0.10", features = listOf("input-native"))
}
```

The plugin maps `Camera`, `CameraInfo`, `CameraIndex`, `CameraFormat`, `Resolution`, `RequestedFormat`, `ApiBackend`, and format enums (`RgbFormat`, `YuyvFormat`, `LumaFormat`…).

**Data flow**: a `channelFlow` opens the camera, captures frames continuously, and emits `CameraState`. The Compose UI renders the live feed via `Image(bitmap = ...)`.

```kotlin
fun cameraStateFlow(): Flow<CameraState> = channelFlow {
    val index  = CameraIndex.new_idx(0)
    val format = RequestedFormat.new_with(RequestedFormatType.AbsoluteHighestResolution)
    val camera = Camera.new(index, format)
    camera.open_stream()

    try {
        while (isActive) {
            val buffer = camera.frame_raw()
            val fmt    = camera.camera_format()
            val w      = fmt.width_x()
            val h      = fmt.height_y()

            val image = BufferedImage(w, h, BufferedImage.TYPE_3BYTE_BGR).also {
                it.raster.setDataElements(0, 0, w, h, buffer)
            }
            send(CameraState(
                isOpen        = true,
                frame         = image,
                currentFormat = FormatInfo(w, h, fmt.frame_rate(), fmt.format().tag.name),
            ))
        }
    } finally {
        camera.stop_stream()
        camera.close()
    }
}.flowOn(Dispatchers.IO)
```

**Compose UI** — the live frame is rendered as a `Bitmap`, format info displayed alongside:

```kotlin
@Composable
fun App() {
    val state by remember { cameraStateFlow() }.collectAsState(initial = null)
    PreviewTab(state)
}

@Composable
fun PreviewTab(state: CameraState?) {
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(12.dp)) {

        // Stats bar
        state?.currentFormat?.let { fmt ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatBox("Resolution", "${fmt.width}x${fmt.height}", Modifier.weight(1f))
                StatBox("Frame Rate", "${fmt.frameRate} fps",        Modifier.weight(1f))
                StatBox("Format",     fmt.format,                    Modifier.weight(1f))
            }
        }

        // Live feed — BufferedImage → Compose ImageBitmap
        state?.frame?.let { frame ->
            Image(
                bitmap           = frame.toComposeImageBitmap(),
                contentDescription = "Camera Feed",
                modifier         = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)),
                contentScale     = ContentScale.Fit,
            )
        }

        // Stream status
        state?.let {
            MetricRow("Stream", if (it.isOpen) "Active" else "Closed")
        }
    }
}
```

Full Compose Desktop app with live preview, format selection, and camera controls: [`examples/rust-camera/`](examples/rust-camera/).

```bash
./gradlew :examples:rust-camera:run
```

---

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
