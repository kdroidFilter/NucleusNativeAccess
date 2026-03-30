package com.example.systeminfo

import kotlin.test.Test
import kotlin.test.assertTrue

class SystemDesktopTest {

    @Test
    fun `hostname is not empty`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getHostname().isNotBlank())
        }
    }

    @Test
    fun `cpu model is detected`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getCpuModel() != "Unknown")
        }
    }

    @Test
    fun `cpu cores greater than zero`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getCpuCoreCount() > 0)
        }
    }

    @Test
    fun `total memory is positive`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getTotalMemoryMB() > 0)
        }
    }

    @Test
    fun `available memory is positive`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getAvailableMemoryMB() > 0)
        }
    }

    @Test
    fun `uptime is positive`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getUptime() > 0.0)
        }
    }

    @Test
    fun `kernel version is not empty`() {
        SystemDesktop().use { desktop ->
            assertTrue(desktop.getKernelVersion().isNotBlank())
        }
    }

    @Test
    fun `capture screen is not empty and starts with BM`() {
        SystemDesktop().use { desktop ->
            kotlinx.coroutines.runBlocking {
                val screen = desktop.captureScreen()
                assertTrue(screen.isNotEmpty())
                assertTrue(screen[0] == 'B'.toByte())
                assertTrue(screen[1] == 'M'.toByte())
            }
        }
    }
}
