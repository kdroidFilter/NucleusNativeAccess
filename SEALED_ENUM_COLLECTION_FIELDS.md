# Sealed Enum Variants: Collection Field Support

## Current Status

Sealed enum variants with collection fields (LIST, SET, MAP) are now **fully supported** for **primitive element types**.

```
                     Primitive elements    String/Object elements
                     ──────────────────    ──────────────────────
  LIST constructor    SUPPORTED             SKIPPED
  SET constructor     SUPPORTED             SKIPPED
  MAP constructor     SUPPORTED             SKIPPED
  Field getters       SUPPORTED             SUPPORTED
```

## What Works

### Factory Constructors (JVM → Rust)

Constructors are generated for variants with LIST, SET, or MAP fields when all element types are primitives (`i32`, `i64`, `f64`, `f32`, `i16`, `i8`, `bool`).

```kotlin
// LIST variant
DataPayload.scores(listOf(1, 2, 3))

// SET variant
DataPayload.uniqueIds(setOf(10, 20, 30))

// MAP variant
DataPayload.mapping(mapOf(1 to 10, 2 to 20))
```

### Field Getters (Rust → JVM)

All collection field types are readable, including String/Object elements:

```kotlin
val scores: List<Int> = (payload as DataPayload.Scores).value
val ids: Set<Int> = (payload as DataPayload.UniqueIds).value
val data: Map<Int, Int> = (payload as DataPayload.Mapping).value
```

## What Doesn't Work

### String/Object elements in constructors

Constructors are **skipped** when a collection field has String or Object element types because the C ABI slice protocol (`ptr + len`) can't marshal variable-length or handle-based data.

```rust
// Constructor SKIPPED — String elements not supported
Tags(HashSet<String>)

// Constructor SKIPPED — Object elements not supported
ProcessesToUpdate::Some(&[Pid])
```

**Root cause**: `slicePointerType` maps String collections to `*const u8` (wrong — each string needs individual marshalling). Object types would need handle arrays.

### Potential fix

Supporting String elements would require a different protocol:
1. Pass an array of null-terminated C string pointers (`*const *const c_char`)
2. Or pass a single concatenated buffer with length-prefix encoding

This is the same fundamental limitation as `&[String]` parameters on regular functions.

## Implementation Details

### RustBridgeGenerator Changes

1. **Skip guard** (`isSupportedCollectionElementForConstructor`): Only allows primitives in collection constructor fields
2. **SET in `slicePointerType`**: Added SET case matching LIST element type → pointer type mapping
3. **SET in `appendParamConversion`**: Always creates owned `HashSet` via `.iter().cloned().collect()`
4. **SET in `convertedParamName`**: Always returns `_set` (no `expectsOwnedVecLike` check)
5. **MAP in `appendParamConversion`**: Added `mut` keyword and `as usize` range conversion
6. **MAP in `convertedParamName`**: Added `_map` case

### FfmProxyGenerator Changes

1. **FunctionDescriptor**: MAP expands to `ADDRESS, ADDRESS, JAVA_INT` (keys, values, size)
2. **Invoke args**: Uses `buildExpandedInvokeArgs` instead of `buildJvmInvokeArg` — correctly expands LIST to `[Seg, size]`, SET to `[Seg, size]`, MAP to `[keysSeg, valuesSeg, size]`
