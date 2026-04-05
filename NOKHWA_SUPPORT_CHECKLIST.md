# Nokhwa Crate Support Checklist

What remains to support [nokhwa](https://github.com/l1npengtul/nokhwa) natively,
without writing Rust and without any manual configuration.
NNA must infer everything from the rustdoc JSON.

---

## 1. Auto-monomorphisation des generiques via scan des impls

> **Bloquant** — `Camera::new` prend `RequestedFormat<T: FormatDecoder>`,
> `Buffer::decode_image::<F>()` est generique sur le format de sortie.

- [x] **Trait-to-Type registry** — scanner tous les blocs `impl Trait for ConcreteType`
      dans le rustdoc JSON et construire un index `Trait -> [ConcreteType]`
- [x] **Resolution des bornes generiques** — quand un param `T: SomeTrait` est rencontre,
      consulter le registry pour trouver les types concrets qui implementent `SomeTrait`
      (aujourd'hui seuls `Fn`/`AsRef`/`Into`/`From` sont geres)
- [ ] **Instanciation des structs generiques** — pour `RequestedFormat<T>`, generer
      une classe JVM par type concret trouve (ex: `RequestedFormatRgb`, `RequestedFormatGray`)
- [x] **Instanciation des methodes generiques** — pour `buffer.decode_image::<F>()`,
      generer un bridge par monomorphisation avec turbofish (ex: `decode_image_rgb`)
- [x] **Multi-bound resolution** — gerer les contraintes combinees (`T: Trait1 + Trait2`)
- [ ] **Constructeurs generiques** — `RequestedFormat::new::<Decoder>()` doit generer
      un constructeur par impl concrete de `FormatDecoder`

## 2. Sealed enum variants multi-champs

> **Necessaire** — `NokhwaError` a 14 variants dont certains avec 2-3 champs.

- [ ] **Tuple variants a N champs** — `OpenDeviceError(String, String)`,
      `SetPropertyError(property, value, error)` : le parsing existe deja
      (`value0`, `value1`, ...) mais verifier que le bridge Rust + FFM proxy
      generent correctement les constructeurs et getters pour N > 1
- [ ] **Struct variants** — `ProcessFrameError { src, destination, error }` :
      le parsing existe, verifier la generation de bout en bout
      (constructeur avec champs nommes, getters individuels)
- [ ] **Variants avec types complexes dans les champs** — certains champs de
      `NokhwaError` referencent `ApiBackend` (enum), `FrameFormat` (enum),
      etc. Verifier le support des enums imbriques dans les variants

## 3. Types opaques cross-crate

> **Necessaire** — nokhwa reexporte des types de `nokhwa-core` et `nokhwa-types`.

- [ ] **Resolution des types reexportes** — quand rustdoc JSON reference un type
      defini dans un sous-crate (`nokhwa_core::types::Resolution`), le parser
      doit suivre le chemin et creer le `KneClass`/`KneDataClass` correspondant
- [ ] **Structs de sous-crates comme data classes** — `Resolution { width: u32, height: u32 }`
      et `CameraFormat { resolution, format, frame_rate }` doivent etre detectes
      comme data classes (tous champs publics, pas de methodes complexes)
- [ ] **Enums de sous-crates** — `FrameFormat`, `ApiBackend`, `CameraControl`
      doivent etre resolus meme s'ils viennent de `nokhwa-types`

## 4. Support `&[u8]` en retour (borrowed slice)

> **Necessaire** — `Buffer::buffer()` retourne `&[u8]`, pas `Vec<u8>`.

- [ ] **Mapping `&[u8]` -> `ByteArray`** — aujourd'hui `Vec<u8>` est mappe en
      `BYTE_ARRAY`, mais `&[u8]` (borrowed slice) n'est probablement pas reconnu
      par le parser ; ajouter la detection du pattern slice dans rustdoc JSON
- [ ] **Bridge copie** — le bridge doit copier le slice dans un buffer alloue
      cote JVM (le borrow ne peut pas survivre au-dela de l'appel FFI)

## 5. `Result<T, E>` avec erreurs structurees

> **Amelioration** — aujourd'hui `Result` est unwrap avec panic/error string.
> Pour nokhwa, propager `NokhwaError` de facon structuree serait preferable.

- [ ] **Propagation du variant d'erreur** — au lieu de `.unwrap()` ou
      `.map_err(|e| e.to_string())`, generer un bridge qui transmet
      le discriminant + les champs de l'erreur au JVM
- [ ] **Mapping vers exception Kotlin typee** — generer une hierarchie
      d'exceptions correspondant aux variants de `NokhwaError`
      (ex: `NokhwaOpenDeviceException(msg1, msg2)`)

## 6. Proprietes avec types non-primitifs

> **Mineur** — `Camera` expose `index()`, `backend()`, `info()`, `resolution()`.

- [ ] **Getters retournant un enum** — `camera.backend() -> ApiBackend` :
      verifier que le pattern getter (`fn backend(&self) -> ApiBackend`)
      est detecte comme propriete et non comme methode
- [ ] **Getters retournant un struct/data class** — `camera.resolution() -> Resolution` :
      verifier que le retour d'un data class par valeur fonctionne via getter

## 7. Methodes `&mut self`

> **Deja supporte** en theorie, mais a valider pour le volume de nokhwa.

- [ ] **Verifier la generation `&mut self`** pour les methodes comme
      `open_stream()`, `stop_stream()`, `set_resolution()`, `set_frame_rate()`
- [ ] **Methodes avec `Result` retour + `&mut self`** — `frame(&mut self) -> Result<Buffer, NokhwaError>`
      combine deux patterns, verifier que la composition fonctionne

---

## Ordre de priorite suggere

1. **Auto-monomorphisation** (section 1) — debloque l'API principale
2. **Types cross-crate** (section 3) — debloque les types de parametre/retour
3. **Sealed enum multi-champs** (section 2) — debloque `NokhwaError`
4. **Borrowed slices** (section 4) — debloque `Buffer::buffer()`
5. **Result structure** (section 5) — ameliore l'ergonomie des erreurs
6. **Proprietes / &mut self** (sections 6-7) — polish
