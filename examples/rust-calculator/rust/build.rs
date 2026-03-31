fn main() {
    let src = "/home/elie-gambache/IdeaProjects/KotlinNativeExport/examples/rust-calculator/build/generated/kne/rustBridges/kne_bridges.rs";
    let out_dir = std::env::var("OUT_DIR").unwrap();
    let dest = format!("{}/kne_bridges.rs", out_dir);
    if std::path::Path::new(src).exists() {
        std::fs::copy(src, &dest).expect("Failed to copy kne_bridges.rs");
    } else {
        std::fs::write(&dest, "// placeholder\n").expect("Failed to write placeholder");
    }
    println!("cargo:rerun-if-changed={}", src);
}