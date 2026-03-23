fn main() {
    tauri_build::build();

    // Export the compiled library name for use in Rust code
    // This matches the [lib] name in Cargo.toml
    println!("cargo:rustc-env=COMPILED_LIB_NAME=musicnotification_lib");
}
