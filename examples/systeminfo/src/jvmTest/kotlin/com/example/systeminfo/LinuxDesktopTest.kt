package com.example.systeminfo

import kotlin.test.Test
import kotlin.test.assertTrue

class LinuxDesktopTest {

    @Test
    fun `hostname is not empty`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getHostname().isNotBlank())
        }
    }

    @Test
    fun `cpu model is detected`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getCpuModel() != "Unknown")
        }
    }

    @Test
    fun `cpu cores greater than zero`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getCpuCoreCount() > 0)
        }
    }

    @Test
    fun `total memory is positive`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getTotalMemoryMB() > 0)
        }
    }

    @Test
    fun `available memory is positive`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getAvailableMemoryMB() > 0)
        }
    }

    @Test
    fun `uptime is positive`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getUptime() > 0.0)
        }
    }

    @Test
    fun `kernel version starts with Linux`() {
        LinuxDesktop().use { desktop ->
            assertTrue(desktop.getKernelVersion().startsWith("Linux"))
        }
    }
}
