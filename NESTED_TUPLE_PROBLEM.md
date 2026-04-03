# Problème: Support des Tuples Imbriqués Rust/Kotlin via FFM

## Architecture

- **Projet**: Kotlin Multiplatform avec FFM (Foreign Function & Memory API)
- **Langage bridge**: Rust générant des fonctions `extern "C"`
- **Example**: `get_nested_tuple() -> (i32, (String, bool))`

## Le Problème Fondamental: Incompatibilité des Layouts Mémoire

### Layout Rust `(String, bool)` (32 bytes):

```
Offset  0-7:   bool (1 byte) + padding 7 bytes
Offset  8-15:  String.ptr (8 bytes)
Offset 16-23:  String.len (8 bytes)
Offset 24-31:  String.cap (8 bytes)
```

### Layout attendu par Kotlin `readTuple2_TRZ`:

```
Offset  0-7:   String.ptr (8 bytes)
Offset  8-15:  String.len (8 bytes)
Offset 16-23:  String.cap (8 bytes)
Offset 24-31:  bool (1 byte) + padding 7 bytes
```

**Ils sont incompatibles!**

## Symptômes

1. `strPtr` lit `6` (longueur de "nested") au lieu d'un vrai pointeur
2. SIGSEGV quand Kotlin essaie de lire à l'adresse `0x0000000000000006`
3. `WrongMethodTypeException` après modification de `expandTupleParamLayouts`

## Tentatives de Résolution

### Tentative 1: Box::into_raw + Kotlin lit le Box

```rust
let _inner_box = Box::into_raw(Box::new(result.1));
unsafe { out_t_1.write(_inner_box as i64); }
```

**Résultat**: Le Box pointe vers la mémoire avec le layout Rust, pas Kotlin.

### Tentative 2: Écrire le buffer directement avec le layout Kotlin

```rust
let _tuple_buf_ptr = Box::into_raw(Box::new([0u8; 32])) as *mut u8;
*(_tuple_buf_ptr as *mut usize) = if _s_len > 0 { _s_ptr as usize } else { 0 };
*(_tuple_buf_ptr as *mut usize).add(1) = _s_len;
*(_tuple_buf_ptr as *mut usize).add(2) = _s_cap;
_tuple_buf_ptr.add(24).write(if result.1.1 { 1u8 } else { 0u8 });
```

**Résultat**: Fonctionne pour `get_nested_tuple with empty label` (7/8 tests passent).

### Tentative 3: Corriger `expandTupleParamLayouts` pour les types primitifs

```kotlin
KneType.INT, KneType.LONG, KneType.BOOLEAN, ... -> builder.add(elemType.ffmLayout)
```

**Résultat**: Cassé - `WrongMethodTypeException` sur TOUS les tests de tuples. Le descripteur FFM ne correspond plus à la fonction Rust.

## Problème Sous-jacent: Convention d'Appel Mismatch

### Fonction Rust:

```rust
pub extern "C" fn rustcalc_Calculator_get_coordinates(
    handle: i64,
    out_t_0: *mut i32,   // ADDRESS en FFM (8 bytes)
    out_t_1: *mut i32    // ADDRESS en FFM (8 bytes)
) -> ()
```

### Descripteur FFM généré:

```kotlin
FunctionDescriptor.ofVoid(JAVA_LONG, JAVA_INT, JAVA_INT)
//                                            ^^^^^^^^^^  4 bytes each
```

**Problème**: `*mut i32` est 4 bytes mais FFM utilise `JAVA_INT` qui est aussi 4 bytes - ça devrait matcher.

Mais pour `get_triple`:

```rust
pub extern "C" fn rustcalc_Calculator_get_triple(
    handle: i64,
    out_t_0: *mut i32,       // JAVA_INT (4 bytes)
    out_t_1: *mut u8,         // ADDRESS (8 bytes)
    out_t_1_len: i32,        // JAVA_INT (4 bytes)
    out_t_2: *mut i32         // JAVA_INT (4 bytes)
) -> ()
```

Le problème est que `*mut u8` (1 byte) est représenté comme `ADDRESS` (8 bytes) en FFM.

## Points Clés à Résoudre

1. **Le layout de `(String, bool)` diffère entre Rust et Kotlin** - impossible à changer sans modifier le code Kotlin de lecture

2. **Les types primitifs dans `expandTupleParamLayouts`** - utiliser `ADDRESS` vs le vrai layout cause des problèmes de taille

3. **La cohérence du layout pour tous les tuples** - une modification pour les tuples imbriqués ne doit pas casser les tuples simples

4. **String vide a un pointeur "dangling"** (`ptr=1`) - notre code gère ça mais c'est une source potentielle d'erreurs

## Fichiers Impliqués

### Plugin IR
- `plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/nucleusnativeaccess/plugin/ir/KneIR.kt`

### Plugin Parser
- `plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/nucleusnativeaccess/plugin/analysis/RustdocJsonParser.kt`

### Plugin Code Generators
- `plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/nucleusnativeaccess/plugin/codegen/FfmProxyGenerator.kt`
- `plugin-build/plugin/src/main/kotlin/io/github/kdroidfilter/nucleusnativeaccess/plugin/codegen/RustBridgeGenerator.kt`

### Rust Example
- `examples/rust-calculator/rust/src/lib.rs`

### Kotlin Tests
- `examples/rust-calculator/src/jvmTest/kotlin/com/example/rustcalculator/TupleTest.kt`

### Générés
- `examples/rust-calculator/build/generated/kne/rustBridges/kne_bridges.rs`
- `examples/rust-calculator/build/generated/kne/jvmProxies/com/example/rustcalculator/Calculator.kt`
- `examples/rust-calculator/build/generated/kne/jvmProxies/com/example/rustcalculator/KneRuntime.kt`

## État Actuel — RÉSOLU (2026-04-03)

**8/8 tests passent** pour les tuples.

### Correctifs appliqués

**1. Use-after-free sur les Strings dans les tuples imbriqués (RustBridgeGenerator.kt)**

Le bridge Rust stockait un pointeur vers le buffer interne du `String` Rust, mais ce `String` était libéré à la fin de la closure `catch_unwind` (quand `result` est droppé). Le pointeur devenait dangling.

**Fix**: copier les bytes du string dans une nouvelle allocation heap avec null-terminator via `Box::into_raw(vec![0u8; _s_len + 1].into_boxed_slice())`, au lieu de stocker le pointeur du String original.

Le test "empty label" passait car `_s_len == 0` écrivait `0usize` comme pointeur, et Kotlin vérifiait `strPtr != 0L` avant de déréférencer.

**2. Mismatch FFM descriptor pour les out-params primitifs (FfmProxyGenerator.kt)**

`expandTupleParamLayouts` utilisait `elemType.ffmLayout` (ex: `JAVA_INT`) pour les out-params primitifs des tuples retournés, mais les out-params sont des pointeurs (`*mut i32`) qui nécessitent `ADDRESS` dans le descripteur FFM.

**Fix**: vérifier `isOutParam` pour les types primitifs, comme le faisait déjà la branche `else`.

### Limitations restantes

- Les offsets dans le buffer des tuples imbriqués sont hardcodés pour le cas `(String, bool)` — un support générique nécessiterait un calcul dynamique des offsets
- Les fonctions `readTuple` côté Kotlin sont hardcodées par type (TRZ, TITRZ) — à rendre génériques
- Pas de support pour les tuples imbriqués à 3+ niveaux
- La mémoire allouée pour les buffers de tuples imbriqués (32 bytes fixe + copies de strings) n'est pas libérée (memory leak)
