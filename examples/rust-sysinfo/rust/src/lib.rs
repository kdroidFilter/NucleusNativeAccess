// Rust System Info — mirrors the Kotlin/Native systeminfo example.
// Uses the `sysinfo` crate for cross-platform system information.

use sysinfo::System;

/// Memory information (mirrors KN MemoryInfo data class).
pub struct MemoryInfo {
    pub total_mb: i64,
    pub available_mb: i64,
}

/// Disk information.
pub struct DiskInfo {
    pub name: String,
    pub mount_point: String,
    pub total_gb: f64,
    pub available_gb: f64,
}

/// Process information.
pub struct ProcessInfo {
    pub name: String,
    pub pid: i64,
    pub memory_mb: i64,
    pub cpu_usage: f32,
}

/// Cross-platform system information powered by the Rust `sysinfo` crate.
pub struct SystemDesktop {
    sys: System,
}

impl SystemDesktop {
    pub fn new() -> Self {
        let mut sys = System::new_all();
        sys.refresh_all();
        SystemDesktop { sys }
    }

    /// Refresh all system data.
    pub fn refresh(&mut self) {
        self.sys.refresh_all();
    }

    // ── Hostname & OS info ──────────────────────────────────────────────

    pub fn get_hostname(&self) -> String {
        System::host_name().unwrap_or_else(|| "Unknown".to_string())
    }

    pub fn get_kernel_version(&self) -> String {
        System::kernel_version().unwrap_or_else(|| "Unknown".to_string())
    }

    pub fn get_os_name(&self) -> String {
        System::name().unwrap_or_else(|| "Unknown".to_string())
    }

    pub fn get_os_version(&self) -> String {
        System::os_version().unwrap_or_else(|| "Unknown".to_string())
    }

    // ── CPU info ────────────────────────────────────────────────────────

    pub fn get_cpu_model(&self) -> String {
        self.sys.cpus().first()
            .map(|c| c.brand().to_string())
            .unwrap_or_else(|| "Unknown".to_string())
    }

    pub fn get_cpu_core_count(&self) -> i32 {
        self.sys.cpus().len() as i32
    }

    pub fn get_cpu_frequency(&self) -> i64 {
        self.sys.cpus().first()
            .map(|c| c.frequency() as i64)
            .unwrap_or(0)
    }

    // get_global_cpu_usage requires &mut self — left out for now

    // ── Memory info ─────────────────────────────────────────────────────

    pub fn get_total_memory_mb(&self) -> i64 {
        (self.sys.total_memory() / 1024 / 1024) as i64
    }

    pub fn get_available_memory_mb(&self) -> i64 {
        (self.sys.available_memory() / 1024 / 1024) as i64
    }

    pub fn get_used_memory_mb(&self) -> i64 {
        (self.sys.used_memory() / 1024 / 1024) as i64
    }

    pub fn get_total_swap_mb(&self) -> i64 {
        (self.sys.total_swap() / 1024 / 1024) as i64
    }

    pub fn get_used_swap_mb(&self) -> i64 {
        (self.sys.used_swap() / 1024 / 1024) as i64
    }

    pub fn get_memory_info(&self) -> MemoryInfo {
        MemoryInfo {
            total_mb: self.get_total_memory_mb(),
            available_mb: self.get_available_memory_mb(),
        }
    }

    // ── Uptime ──────────────────────────────────────────────────────────

    pub fn get_uptime(&self) -> f64 {
        System::uptime() as f64
    }

    // ── Process info ────────────────────────────────────────────────────

    pub fn get_process_count(&self) -> i32 {
        self.sys.processes().len() as i32
    }

    // get_top_processes_by_memory returns Vec<ProcessInfo> — not yet supported in NNA bridge v1

    // ── Disk info ───────────────────────────────────────────────────────

    pub fn get_disk_count(&self) -> i32 {
        sysinfo::Disks::new_with_refreshed_list().list().len() as i32
    }

    // ── Summary string ──────────────────────────────────────────────────

    pub fn get_summary(&self) -> String {
        format!(
            "{} ({} {}) | {} | {} cores @ {} MHz | {} MB free / {} MB total | uptime {}",
            self.get_hostname(),
            self.get_os_name(),
            self.get_os_version(),
            self.get_kernel_version(),
            self.get_cpu_core_count(),
            self.get_cpu_frequency(),
            self.get_available_memory_mb(),
            self.get_total_memory_mb(),
            format_uptime(self.get_uptime()),
        )
    }
}

fn format_uptime(seconds: f64) -> String {
    let h = (seconds / 3600.0) as i32;
    let m = ((seconds % 3600.0) / 60.0) as i32;
    format!("{}h {}m", h, m)
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_system_desktop() {
        let mut sys = SystemDesktop::new();
        assert!(!sys.get_hostname().is_empty());
        assert!(sys.get_cpu_core_count() > 0);
        assert!(sys.get_total_memory_mb() > 0);
        assert!(sys.get_available_memory_mb() > 0);
        assert!(sys.get_uptime() > 0.0);
        assert!(!sys.get_summary().is_empty());
        println!("Summary: {}", sys.get_summary());
    }
}

include!(concat!(env!("OUT_DIR"), "/kne_bridges.rs"));
