# AGENTS.md — Nucleus Native Access

## Project Overview

**Nucleus Native Access** is a Gradle plugin that bridges native code (Kotlin/Native or Rust) to the JVM via FFM (Foreign Function & Memory API). No JNI, no annotations, no boilerplate.

```
Native code (Kotlin/Native or Rust)  →  FFM bridges  →  JVM proxy classes
```

## Key Directories

```
├── plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/nucleusnativeaccess/plugin/
│   ├── ir/KneIR.kt                      # Shared IR: types, signatures, typeId generation
│   ├── analysis/
│   │   ├── RustdocJsonParser.kt         # Parses rustdoc JSON → KneIR
│   │   └── RustWorkAction.kt             # Orchestrates Rust pipeline
│   ├── codegen/
│   │   ├── FfmProxyGenerator.kt         # JVM proxies with FFM MethodHandle (shared)
│   │   ├── RustBridgeGenerator.kt       # Rust #[no_mangle] extern "C" bridges
│   │   └── NativeBridgeGenerator.kt     # Kotlin/Native @CName bridges
│   └── tasks/
│       ├── GenerateRustBindingsTask.kt  # Bridge generation task
│       └── CargoBuildTask.kt            # cargo build invocation
├── examples/
│   └── rust-calculator/                  # Rust example + tests
│       ├── rust/src/lib.rs              # Rust source
│       └── build/generated/kne/        # Generated code (bridges, proxies)
│           ├── rustBridges/kne_bridges.rs
│           └── jvmProxies/com/example/rustcalculator/
└── tests/                              # Integration tests
```

## Rust Bridge Pipeline

```
1. cargo rustdoc --output-format json  →  Extract public API
2. RustdocJsonParser.kt               →  Parse to KneIR
3. RustBridgeGenerator.kt              →  Generate #[no_mangle] pub extern "C" fn
4. FfmProxyGenerator.kt               →  Generate JVM proxies with FFM
5. cargo build --release              →  Compile Rust
6. Bundle .dylib into JAR            →  kne/native/{os}-{arch}/
```

## KneType.TUPLE and typeId

Tuples are represented as `KneType.TUPLE(elementTypes: List<KneType>)` with a `typeId` property that generates unique signatures:

```kotlin
// Format: T=Tuple, U=Unit, Z=Bool, B=Byte, S=Short, I=Int, J=Long, F=Float, D=Double, R=String
(i32, (String, bool))  →  typeId = "TITRZ"
(i32, i32)             →  typeId = "TII"
(String, bool)         →  typeId = "TRZ"
```

## Rust Bridge Generation (RustBridgeGenerator.kt)

### Tuple Return Handling

For tuple returns, the bridge generates out-param code that writes each element to a buffer:

```rust
// For (i32, String, bool):
unsafe { *out_t_0 = result.0 as i32; }
// String handling: copy to buffer
let _e_bytes = result.1.as_bytes();
unsafe { std::ptr::copy_nonoverlapping(_e_bytes.as_ptr(), out_t_1, _e_bytes.len()); }
unsafe { *out_t_1.add(_e_bytes.len()) = 0; }
unsafe { out_t_2.write(if result.2 { 1 } else { 0 }); }
```

### Nested Tuple Handling

Nested tuples are handled specially — for `isOutParam=true`, a nested tuple gets a single `ADDRESS` layout (pointer to boxed tuple), not recursively expanded.

### IMPORTANT: Memory Layout Compatibility Issue

**Rust `(String, bool)` has DIFFERENT memory layout than Kotlin expects!**

Rust layout (32 bytes):
```
Offset  0-7:   bool (1 byte) + padding 7 bytes
Offset  8-15:  String.ptr (8 bytes)
Offset 16-23:  String.len (8 bytes)
Offset 24-31:  String.cap (8 bytes)
```

Kotlin `readTuple2_TRZ` expects:
```
Offset  0-7:   String.ptr (8 bytes)
Offset  8-15:  String.len (8 bytes)
Offset 16-23:  String.cap (8 bytes)
Offset 24-31:  bool (1 byte) + padding 7 bytes
```

**See NESTED_TUPLE_PROBLEM.md for full details and current status.**

## FFM Conventions

### ValueLayout mappings

| KneType | FFM Layout |
|---------|------------|
| INT | `JAVA_INT` (4 bytes) |
| LONG | `JAVA_LONG` (8 bytes) |
| BOOLEAN | `JAVA_INT` (0/1) |
| STRING | `ADDRESS` (pointer) |
| TUPLE | `ADDRESS` (pointer to heap buffer) |

### Function Descriptors

Tuple returns use `FunctionDescriptor.ofVoid(...)` with out-param layouts:
```kotlin
// (i32, i32) return:
FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT)
//                          handle   out_t_0  out_t_1

// (i32, String, bool) return:
FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT)
//                          handle   out_t_0  out_t_1   out_t_1_len  out_t_2
```

## Important Code Patterns

### When modifying RustBridgeGenerator.kt

1. **String returns**: Use buffer pattern with null-termination
2. **Nested tuples**: Allocate a `Box::into_raw(Box::new([0u8; 32]))` buffer and write fields in Kotlin's expected layout
3. **Empty strings**: Check `if _s_len > 0` before using pointer (Rust empty strings have ptr=1, len=0)

### When modifying FfmProxyGenerator.kt

1. **expandTupleParamLayouts**: For out-params, nested tuples use `ADDRESS` (pointer to boxed). Primitives use their actual `ffmLayout`, not `ADDRESS`.
2. **buildMethodDescriptor**: Tuple returns become `UNIT` with out-param expansion.

### Generated Files

Files in `build/generated/kne/` are **regenerated on every build** from source:
- `rustBridges/kne_bridges.rs` — from Rustdoc JSON
- `jvmProxies/` — from KneIR

**Do not edit generated files manually.** They are overwritten on next build.

To force regeneration:
```bash
rm -rf examples/rust-calculator/build/generated/kne/
./gradlew :examples:rust-calculator:assemble --no-build-cache
```

## Running Tests

```bash
# All tuple tests
./gradlew :examples:rust-calculator:jvmTest --tests "com.example.rustcalculator.TupleTest"

# Single test
./gradlew :examples:rust-calculator:jvmTest --tests "com.example.rustcalculator.TupleTest.get_nested_tuple*"

# With debug output
./gradlew :examples:rust-calculator:jvmTest --tests "com.example.rustcalculator.TupleTest.get_nested_tuple*" --info

# Force full rebuild (clears cache)
./gradlew :examples:rust-calculator:assemble --no-build-cache
./gradlew :examples:rust-calculator:jvmTest
```

## Debugging Tips

1. **SIGSEGV crashes**: Usually indicates invalid memory address being dereferenced. Check:
   - Is the pointer 0 or very small (like 6 = string length)?
   - Is the segment size set correctly with `.reinterpret(size)`?
   - Is Rust writing to the correct memory location?

2. **WrongMethodTypeException**: FFM descriptor mismatch. Check:
   - Does the Kotlin `FunctionDescriptor` match the Rust `extern "C"` signature?
   - Did you change `expandTupleParamLayouts` without rebuilding native?

3. **String issues**: Empty strings in Rust have `ptr=1, len=0`. Always check `if _s_len > 0` before using pointer.

4. **Debug output**: Both Rust (`eprintln!`) and Kotlin (`System.err.println`) can be used for tracing. Rust debug goes to stderr captured by Gradle.

## Debugging Log Practice (IMPORTANT)

When fixing a complex bug, **maintain a debugging log** to avoid repeating the same mistakes. Before starting:

```
## Bug: [short description]

### Symptom
- What the user sees
- Error type, logs

### Root Cause (if known)
- What's actually happening

### Attempts

| # | Date | Change | Result | Notes |
|---|------|--------|--------|-------|
| 1 | ... | Modified X | Failed | SIGSEGV at ... |
| 2 | ... | Modified Y | WrongMethodTypeException | Descriptor mismatch |
| 3 | ... | Reverted Y, Modified Z | Partial | 7/8 tests pass |

### Key Findings
- Finding 1
- Finding 2
```

**Why this matters**: Complex bugs (like nested tuple memory layouts) require many iterations. Without logging, you risk:
- Forgetting what you already tried
- Repeating failed approaches
- Losing track of which combination of fixes worked

**Always document**:
- Every change attempted (哪怕是小改动)
- The exact error/log output after each attempt
- Why something failed
- What you learned about the problem

**Update NESTED_TUPLE_PROBLEM.md** with findings as you progress.

## Testing Philosophy

This project bridges native code to JVM via FFM — every call crosses the native boundary. **Unit tests at the bridge level are useless.** Only end-to-end tests that actually cross the FFM boundary are meaningful.

### Reference Implementation: Kotlin/Native Tests

The `examples/calculator` module has **1700+ end-to-end tests** covering every feature. Use these as inspiration for testing patterns.

**Key test categories** (from `EdgeCaseTest.kt`, `LoadAndConcurrencyTest.kt`):

1. **Boundary values**: `Int.MAX_VALUE`, `Int.MIN_VALUE`, overflow behavior
2. **Empty/non-empty variants**: empty strings, empty collections, zero values
3. **Unicode**: emoji, international characters, null chars
4. **All enum values**: test every enum variant explicitly
5. **Nullable transitions**: null ↔ non-null for all nullable types
6. **Lifecycle**: create/use/close cycles, multiple objects
7. **Exception recovery**: verify object still works after exception
8. **Integration**: combinations of features in sequence
9. **Load tests**: 100K+ FFM calls on single instance
10. **Concurrency**: multiple threads hitting native lib simultaneously

### Rust Tests: Current Status

The `examples/rust-calculator` tests are **less comprehensive** than Kotlin/Native. They cover basic functionality but lack:
- Load tests (100K+ calls)
- Concurrency tests (multi-threaded)
- Full edge case coverage for all types
- Exception recovery tests

### Test Naming Convention

```kotlin
@Test fun `edge prim - Int MAX_VALUE`() = Calculator(Int.MAX_VALUE).use { ... }
@Test fun `load - 100K add calls single instance`() = Calculator(0).use { ... }
@Test fun `concurrent - 10 threads x 10K adds on separate instances`() = ...
```

Format: `[category] - [description]`

Categories: `edge`, `str`, `enum`, `null`, `bytes`, `dc`, `obj`, `exc`, `cb`, `integration`, `load`, `concurrent`

### When to Add Tests

- **New type support**: Add edge cases for all boundary values
- **New return type**: Add empty/non-empty, null/non-null variants
- **Bug fix**: Add regression test that reproduces the bug
- **Memory issue**: Add load test that exercises the code path heavily

### Test Infrastructure

```kotlin
// AutoCloseable pattern for all native objects
class Calculator(autoCloseable) { ... }

// Extension for automatic cleanup
fun <T : AutoCloseable, R> T.use(block: (T) -> R): R {
    return try { block(this) } finally { close() }
}

// Usage
Calculator(0).use { calc ->
    calc.add(5)
    assertEquals(5, calc.current)
} // automatically closed
```

## Conventions

- **Comments**: Write in English
- **Kotlin style**: Follow Kotlin conventions and idiomatic style
- **No Co-Authored-By**: Never add AI attribution in commits
- **Je ne cheat pas**: Implement fully regardless of complexity
- **Tests first**: Always run tests after changes, use `--no-build-cache`
- **Battle test edge cases**: Every new feature needs edge case tests, load tests, and concurrency tests
