# Rust `crate()` Import — Support Matrix

Status of the `rustImport { crate(...) }` feature that auto-generates Kotlin bindings from Rust crates via rustdoc JSON.

## Type Mapping

| Rust Type | Kotlin Type | Status |
|-----------|-------------|--------|
| `i32` | `Int` | Supported |
| `i64` | `Long` | Supported |
| `f64` | `Double` | Supported |
| `f32` | `Float` | Supported |
| `bool` | `Boolean` | Supported |
| `i8` / `u8` | `Byte` | Supported |
| `i16` / `u16` | `Short` | Supported |
| `String` / `&str` | `String` | Supported |
| `()` | `Unit` | Supported |
| `Vec<u8>` / `&[u8]` | `ByteArray` | Supported |
| `Option<T>` | `T?` | Supported |
| `Vec<T>` | `List<T>` | Supported |
| `HashSet<T>` | `Set<T>` | Supported |
| `HashMap<K,V>` | `Map<K,V>` | Supported |
| `(A, B)` tuples | `KneTuple2<A,B>` | Supported |
| `fn(A) -> B` callbacks | `(A) -> B` | Supported |
| Structs (opaque) | Classes | Supported |
| Enums (C-like) | `enum class` | Supported |
| Enums (with data) | `sealed class` | Supported |
| Traits (as interfaces) | `interface` | Supported |
| `dyn Trait` returns | `DynXxx` wrapper class | Supported |
| `Box<dyn Trait>` params | — | Not supported |
| Generics (`Struct<T>`) | Monomorphized variants | Supported |
| `async fn` | `suspend fun` | Supported |
| Streams / iterators | `Flow<T>` | Supported |

## Struct / Class Features

| Feature | Status | Notes |
|---------|--------|-------|
| Public methods (`&self`, `&mut self`) | Supported | Auto-bridged |
| Constructors (`fn new(...)`) | Supported | Mapped to `operator fun invoke(...)` |
| Companion / static methods | Supported | Methods without `self` param |
| Properties (getter-only fields) | Supported | Generated as `fun field_name(): T` |
| Mutable properties | Supported | Getter + setter |
| Builder pattern (returns `Self`) | Supported | Chaining works |
| `impl Trait for Struct` | Supported | Mapped to Kotlin interface impl |
| Multiple trait impls | Supported | Deduplicated |
| Lifetime params (`'a`) | Supported | Erased at bridge level |
| Generic structs | Supported | Expanded into concrete variants |
| `Drop` / resource cleanup | Supported | `AutoCloseable` + `Cleaner` |
| Borrowed returns (`&T`) | Supported | Non-owning handle wrapper |

## Enum Features

| Feature | Status | Notes |
|---------|--------|-------|
| C-like enums (no data) | Supported | `enum class` with `entries` |
| Enums with data variants | Supported | `sealed class` with subclasses |
| Variant constructors | Supported | `EnumName.variantName(...)` |
| Variant field access | Supported | Properties on variant subclass |
| Tag inspection | Supported | `.tag` property |

## Trait / Interface Features

| Feature | Status | Notes |
|---------|--------|-------|
| Trait → Kotlin interface | Supported | Methods as abstract declarations |
| `dyn Trait` return types | Supported | `DynXxx` wrapper class with `fromNativeHandle` |
| `dyn Trait` as param | Not supported | Interface params don't have `.handle` |
| Trait with associated types | Not supported | Skipped |
| Super-traits | Partial | Interface inheritance not chained |

## Callback / Function Pointer Features

| Feature | Status | Notes |
|---------|--------|-------|
| `fn(primitives) -> primitive` | Supported | Upcall stub generated |
| `fn(Object) -> ()` | Supported | Object handle passed |
| `fn() -> Object` | Supported | Handle returned |
| `fn() -> dyn Trait` | Supported | Handle returned via upcall stub |
| `fn() -> SealedEnum` | Supported | Handle returned via upcall stub |
| Callbacks in methods | Supported | Stub allocated from object arena |
| Callbacks in constructors | Supported | Stub allocated from confined arena |
| Callbacks in sealed enum factories | Supported | Stub allocated from confined arena |

## Multi-Crate Support

| Feature | Status | Notes |
|---------|--------|-------|
| Multiple `crate()` declarations | Supported | Merged into single module |
| Cross-crate type references | Supported | Lazy resolution from rustdoc |
| Cross-crate trait impls | Supported | Deduplicated |
| Type name collisions | Handled | Ambiguous names detected and skipped |
| Cross-crate enum type mismatch | Handled | OBJECT→ENUM redirect at codegen |

## Known Limitations

### Not Yet Bridgeable

1. **`dyn Trait` as function parameter** — Rust trait objects used as params (`Box<dyn MediaSource>`) require dynamic dispatch that the C bridge can't express. Constructors and methods with trait-object params are skipped.

2. **Top-level functions** — Free functions like `fn default_host()` or `fn get_probe()` are only bridged when they have supported param/return types. Functions returning `impl Trait` or taking `dyn Trait` are skipped.

3. **Generic types without concrete instantiation** — If a generic struct `Foo<T>` has no trait impl that binds `T` to a concrete type, it stays unresolved and is skipped.

### Automatically Handled

- **Duplicate trait impls** — When a type implements the same trait multiple times (e.g., `AudioBuffer<T>` for different `T`), supertypes and methods are deduplicated.
- **Override validation** — Methods marked `override` are validated against the actual interface/superclass hierarchy. Invalid overrides are silently downgraded to regular methods.
- **Type mismatches** — When a type is resolved as `OBJECT` in one crate but exists as `ENUM` in the module, the codegen redirects to the correct pattern (ordinal-based).
- **Missing method implementations** — If a class can't implement all methods of a trait interface (due to unsupported types in signatures), the interface is dropped from the supertype list rather than generating a compile error.
- **Unknown types in signatures** — Methods, constructors, and interface declarations referencing types not present in the module are filtered out at codegen time.

## Symphonia + cpal Example Stats

Generated from `symphonia 0.5.5` + `cpal 0.15` with zero hand-written Rust:

| Metric | Count |
|--------|-------|
| Kotlin proxy files | 187 |
| Classes | 129 |
| Interfaces (from traits) | 24 |
| Simple enums | 12 |
| Sealed enums | 19 |
| Compilation errors | 0 |
