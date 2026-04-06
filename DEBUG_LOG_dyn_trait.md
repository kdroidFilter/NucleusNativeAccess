# dyn Trait Implementation Debug Log

## Date
April 4, 2026

## Goal
Implement full support for `dyn Trait` (trait objects) in the Rust bridge generation for the Nucleus Native Access Gradle plugin.

## Context

The plugin bridges Rust code to JVM via FFM (Foreign Function & Memory API). The challenge is that:
- `dyn Trait` in Rust is a "fat pointer" (16 bytes: data pointer + vtable)
- FFM can only pass 8 bytes (i64) across the boundary
- `Box<dyn Trait>` IS a thin pointer (8 bytes) - this CAN be passed

## Key Concepts

### What Works
1. **Factory functions returning `Box<dyn Trait>`** - Box pointer passed as handle
2. **`Option<Box<dyn Trait>>` returns** - Same as above with 0 as null handle
3. **`Result<Box<dyn Trait>, E>` returns** - Same with error handling
4. **`Vec<Box<dyn Trait>>` returns** - Array of handles

### What Doesn't Work
- **Functions taking `&dyn Trait` params** - Fat pointer exceeds FFM limit (16 bytes vs 8 bytes)

## Problems Encountered

### Problem 1: Type Erasure with Box<dyn Any>

**Initial approach**: Store `Box<dyn Any + Send>` in registry

**Error**:
```
error[E0308]: mismatched types
  | expected: Box<(dyn Any + Send + 'static)>>
  | found: Box<dyn Describable>>
```

**Root cause**: `Box<dyn Trait>` cannot be coerced to `Box<dyn Any>` unless the trait implements `Any`. User traits like `Describable` don't implement `Any`.

**Solution**: Use `std::mem::transmute` to extract raw fat pointer components:
```rust
let fat_ptr_words: [usize; 2] = unsafe { std::mem::transmute(result) };
```

### Problem 2: Result Detection

**Initial approach**: Check `rustRetType.startsWith("Result<Box<dyn ")`

**Error**:
```
error[E0512]: cannot transmute between types of different sizes
  source type: Result<Box<dyn Describable>, String> (192 bits)
  target type: [usize; 2] (128 bits)
```

**Root cause**: The parser extracts the inner type of `Result<T, E>` and stores it in `returnRustType`. So `Result<Box<dyn Describable>, String>` becomes `Box<dyn Describable>` in `returnRustType`.

**Solution**: Use `fn.canFail` flag combined with `isBoxDynTrait`:
```kotlin
val isResultReturn = fn.canFail && isBoxDynTrait
```

### Problem 3: &dyn Trait Param Detection

**Initial approach**: Check `rt.startsWith("&dyn ")` for params

**Error**: Functions like `describe_trait_object(obj: &dyn Describable)` were being routed to dyn Trait handler but failing with:
```
error[E0606]: cannot cast `i64` to a pointer that is wide
```

**Root cause**: `&dyn Trait` in rustdoc JSON has `rustType = "dyn Trait"` (WITHOUT the `&` prefix), and `type = INTERFACE`.

**Solution**: Check `p.type is KneType.INTERFACE` to detect `&dyn Trait` params and skip those functions entirely:
```kotlin
if (fn.params.any { p -> p.type is KneType.INTERFACE }) {
    return false  // Skip - can't handle &dyn Trait params
}
```

### Problem 4: Slice Variable Not Generated

**Error**:
```
error[E0425]: cannot find value `values_slice` in this scope
```

**Root cause**: For `Vec<Box<dyn Trait>>` returns, the `&[i32]` slice parameter was not being converted to `values_slice`.

**Solution**: Add slice creation before calling the function:
```rust
for (p in params) {
    if (p.type is KneType.LIST || p.type == KneType.BYTE_ARRAY) {
        appendLine("        let ${p.name}_slice = unsafe { std::slice::from_raw_parts(${p.name}_ptr, ${p.name}_len as usize) };")
    }
}
```

### Problem 5: when Clause Ordering

**Initial code**:
```kotlin
when {
    isBoxDynTrait -> { ... }  // This matched Result returns too!
    isResultBoxDyn -> { ... }  // Never reached
}
```

**Issue**: `isBoxDynTrait` checks `rustRetType.startsWith("Box<dyn ")` which matches `Result<Box<dyn ...>>` because it starts with `Box<dyn` inside.

**Solution**: Check more specific cases first:
```kotlin
when {
    isResultReturn -> { }  // canFail + isBoxDynTrait
    isOptionBoxDyn -> { }
    isVecBoxDyn -> { }
    isBoxDynTrait -> { }
}
```

### Problem 6: kne_trait_registry_get Lifetime Issue

**Error**:
```
error[E0515]: cannot return value referencing temporary value
```

**Solution**: We removed `kne_trait_registry_get` since we're using inline transmute instead.

## Final Architecture

### Registry Storage
```rust
thread_local! {
    static KNE_TRAIT_REGISTRY: RefCell<HashMap<u64, [usize; 2]>> = RefCell::new(HashMap::new());
    static KNE_NEXT_HANDLE: RefCell<u64> = RefCell::new(1);
}
```

### Registration (Inline)
```rust
let fat_ptr_words: [usize; 2] = unsafe { std::mem::transmute(result) };
KNE_TRAIT_REGISTRY.with(|reg| { reg.borrow_mut().insert(handle, fat_ptr_words); });
```

### Drop
```rust
fn kne_drop_trait_object(handle: u64) {
    KNE_TRAIT_REGISTRY.with(|reg| {
        if let Some(words) = reg.borrow_mut().remove(&handle) {
            unsafe {
                let fat_ptr: *mut dyn std::any::Any = std::mem::transmute(words);
                let boxed: Box<Box<dyn std::any::Any>> = Box::from_raw(fat_ptr as *mut Box<dyn std::any::Any>);
                drop(boxed);
            }
        }
    });
}
```

## Working Functions

| Function | Return Type | Status |
|----------|-------------|--------|
| `create_describable` | `Box<dyn Describable>` | ✓ |
| `create_measurable` | `Box<dyn Measurable>` | ✓ |
| `create_resettable` | `Box<dyn Resettable>` | ✓ |
| `maybe_create_describable` | `Option<Box<dyn Describable>>` | ✓ |
| `create_describable_list` | `Vec<Box<dyn Describable>>` | ✓ |
| `try_create_describable` | `Result<Box<dyn Describable>, String>` | ✓ |

## Excluded Functions

| Function | Reason |
|----------|--------|
| `describe_trait_object` | `&dyn Trait` params - fat pointer can't cross FFM |
| `measure_trait_object` | `&dyn Trait` params - fat pointer can't cross FFM |
| `reset_trait_object` | `&dyn Trait` params - fat pointer can't cross FFM |

## Key Files Modified

- `plugin-build/plugin/src/main/kotlin/.../codegen/RustBridgeGenerator.kt`
  - `isDynTraitFunction()`: Skip functions with INTERFACE params
  - `appendDynTraitTopLevelFunction()`: Handle Box<dyn Trait>, Option, Result, Vec cases
  - `appendPreamble()`: Updated registry to store `[usize; 2]`

## Build Commands

```bash
# Build
./gradlew :examples:rust-calculator:assemble

# Test
./gradlew :examples:rust-calculator:jvmTest

# Force regeneration
rm -rf examples/rust-calculator/build/generated/kne/
./gradlew :examples:rust-calculator:assemble --no-build-cache
```

## Lessons Learned

1. **Type erasure is fundamental**: Rust's `dyn Trait` loses its concrete type information. We can't recover `Box<dyn ConcreteTrait>` from `Box<dyn Any>`.

2. **Transmute is powerful but dangerous**: We can convert fat pointer to `[usize; 2]` for storage, but need to be careful about sizes.

3. **Parser behavior matters**: The rustdoc JSON parser extracts inner types for generics, so `Result<Box<dyn Trait>, E>` becomes `Box<dyn Trait>` in `returnRustType`.

4. **Filter at the right level**: Functions with `&dyn Trait` params must be filtered before code generation, not during.

5. **Order matters in when clauses**: More specific checks must come before general ones.
