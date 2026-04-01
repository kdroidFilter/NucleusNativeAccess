package io.github.kdroidfilter.nucleusnativeaccess.plugin

import org.gradle.api.GradleException
import java.io.File

/**
 * Resolves the path to the `cargo` binary, checking CARGO_HOME and ~/.cargo/bin
 * before falling back to PATH. Throws a clear error if cargo is not found.
 */
internal fun findCargo(): String {
    val cargoHome = System.getenv("CARGO_HOME")
    if (cargoHome != null) {
        val cargo = File(cargoHome, "bin/cargo")
        if (cargo.exists()) return cargo.absolutePath
    }
    val homeCargo = File(System.getProperty("user.home"), ".cargo/bin/cargo")
    if (homeCargo.exists()) return homeCargo.absolutePath
    // Check if cargo is available on PATH
    val onPath = try {
        val process = ProcessBuilder("cargo", "--version")
            .redirectErrorStream(true)
            .start()
        val exitCode = process.waitFor()
        exitCode == 0
    } catch (_: Exception) {
        false
    }
    if (onPath) return "cargo"
    throw GradleException(
        """
        |
        | Rust toolchain not found — 'cargo' is not installed or not on the PATH.
        |
        | Install Rust with:
        |   curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh
        |
        | Then restart your terminal (or run 'source ~/.cargo/env') and try again.
        | If running from an IDE, restart it so the updated PATH is picked up.
        """.trimMargin()
    )
}
