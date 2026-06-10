// android-ide/build.rs
//
// Compile all Slint UI files into Rust code.
// Slint-build processes main.slint (which imports all component files) and
// emits the generated module into OUT_DIR. The Rust side pulls it in via
// slint::include_modules!() in src/ui.rs.

fn main() {
    slint_build::compile("ui/main.slint")
        .expect("Slint UI compilation failed");
}
