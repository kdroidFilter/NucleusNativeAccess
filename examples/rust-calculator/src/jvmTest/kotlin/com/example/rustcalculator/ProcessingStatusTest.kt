package com.example.rustcalculator

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProcessingStatusTest {

    // ═══════════════════════════════════════════════════════════════════════════
    // get_processing_status — struct variants returned from Rust
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `get_processing_status zero returns Idle`() {
        Calculator(0).use { calc ->
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.Idle)
                assertEquals(ProcessingStatus.Tag.Idle, status.tag)
            }
        }
    }

    @Test fun `get_processing_status negative returns FrameError with named fields`() {
        Calculator(-5).use { calc ->
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.FrameError)
                val fe = status as ProcessingStatus.FrameError
                assertEquals("input_5", fe.src)
                assertEquals("output", fe.destination)
                assertEquals("negative value: -5", fe.error)
            }
        }
    }

    @Test fun `get_processing_status large returns OperationFailed with enum field`() {
        Calculator(200).use { calc ->
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.OperationFailed)
                val of = status as ProcessingStatus.OperationFailed
                // Default last_operation is Add
                assertEquals(Operation.Add, of.operation)
                assertEquals(200, of.code)
                assertEquals("overflow", of.message)
            }
        }
    }

    @Test fun `get_processing_status small positive returns Progress`() {
        Calculator(42).use { calc ->
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.Progress)
                val p = status as ProcessingStatus.Progress
                assertEquals(42, p.step)
                assertEquals(100, p.total)
                assertEquals("", p.label)
                assertFalse(p.done)
            }
        }
    }

    @Test fun `get_processing_status with label returns Progress with label`() {
        Calculator(10).use { calc ->
            calc.label = "processing"
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.Progress)
                assertEquals("processing", (status as ProcessingStatus.Progress).label)
            }
        }
    }

    @Test fun `get_processing_status after apply_op has correct operation`() {
        Calculator(200).use { calc ->
            calc.apply_op(Operation.Multiply, 1)
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.OperationFailed)
                assertEquals(Operation.Multiply, (status as ProcessingStatus.OperationFailed).operation)
            }
        }
    }

    @Test fun `get_processing_status after subtract has Subtract operation`() {
        Calculator(500).use { calc ->
            calc.apply_op(Operation.Subtract, 1)
            calc.get_processing_status().use { status ->
                assertTrue(status is ProcessingStatus.OperationFailed)
                assertEquals(Operation.Subtract, (status as ProcessingStatus.OperationFailed).operation)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Factory methods — construct struct variants from Kotlin
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `factory FrameError with named string fields`() {
        ProcessingStatus.frameError("camera_0", "/tmp/output.raw", "permission denied").use { status ->
            assertTrue(status is ProcessingStatus.FrameError)
            assertEquals("camera_0", status.src)
            assertEquals("/tmp/output.raw", status.destination)
            assertEquals("permission denied", status.error)
            assertEquals(ProcessingStatus.Tag.FrameError, status.tag)
        }
    }

    @Test fun `factory OperationFailed with enum field`() {
        ProcessingStatus.operationFailed(Operation.Multiply, 42, "overflow").use { status ->
            assertTrue(status is ProcessingStatus.OperationFailed)
            assertEquals(Operation.Multiply, status.operation)
            assertEquals(42, status.code)
            assertEquals("overflow", status.message)
            assertEquals(ProcessingStatus.Tag.OperationFailed, status.tag)
        }
    }

    @Test fun `factory OperationFailed with each Operation variant`() {
        for (op in Operation.entries) {
            ProcessingStatus.operationFailed(op, 1, "test").use { status ->
                assertTrue(status is ProcessingStatus.OperationFailed)
                assertEquals(op, status.operation)
            }
        }
    }

    @Test fun `factory Progress with all field types`() {
        ProcessingStatus.progress(3, 10, "step three", true).use { status ->
            assertTrue(status is ProcessingStatus.Progress)
            assertEquals(3, status.step)
            assertEquals(10, status.total)
            assertEquals("step three", status.label)
            assertTrue(status.done)
        }
    }

    @Test fun `factory Progress with false done`() {
        ProcessingStatus.progress(1, 100, "starting", false).use { status ->
            assertTrue(status is ProcessingStatus.Progress)
            assertFalse(status.done)
        }
    }

    @Test fun `factory Idle`() {
        ProcessingStatus.idle().use { status ->
            assertTrue(status is ProcessingStatus.Idle)
            assertEquals(ProcessingStatus.Tag.Idle, status.tag)
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Edge cases
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `edge - FrameError with empty strings`() {
        ProcessingStatus.frameError("", "", "").use { status ->
            assertTrue(status is ProcessingStatus.FrameError)
            assertEquals("", status.src)
            assertEquals("", status.destination)
            assertEquals("", status.error)
        }
    }

    @Test fun `edge - FrameError with unicode`() {
        ProcessingStatus.frameError("ソース", "出力先", "エラー 🔥").use { status ->
            assertTrue(status is ProcessingStatus.FrameError)
            assertEquals("ソース", status.src)
            assertEquals("出力先", status.destination)
            assertEquals("エラー 🔥", status.error)
        }
    }

    @Test fun `edge - FrameError with long strings`() {
        val longStr = "x".repeat(10_000)
        ProcessingStatus.frameError(longStr, longStr, longStr).use { status ->
            assertTrue(status is ProcessingStatus.FrameError)
            assertEquals(longStr, status.src)
            assertEquals(longStr, status.destination)
            assertEquals(longStr, status.error)
        }
    }

    @Test fun `edge - OperationFailed with zero code`() {
        ProcessingStatus.operationFailed(Operation.Add, 0, "none").use { status ->
            assertTrue(status is ProcessingStatus.OperationFailed)
            assertEquals(0, status.code)
        }
    }

    @Test fun `edge - OperationFailed with MAX_VALUE code`() {
        ProcessingStatus.operationFailed(Operation.Add, Int.MAX_VALUE, "max").use { status ->
            assertTrue(status is ProcessingStatus.OperationFailed)
            assertEquals(Int.MAX_VALUE, status.code)
        }
    }

    @Test fun `edge - OperationFailed with MIN_VALUE code`() {
        ProcessingStatus.operationFailed(Operation.Add, Int.MIN_VALUE, "min").use { status ->
            assertTrue(status is ProcessingStatus.OperationFailed)
            assertEquals(Int.MIN_VALUE, status.code)
        }
    }

    @Test fun `edge - Progress with boundary values`() {
        ProcessingStatus.progress(0, 0, "", false).use { status ->
            assertTrue(status is ProcessingStatus.Progress)
            assertEquals(0, status.step)
            assertEquals(0, status.total)
        }
    }

    @Test fun `edge - all tags cycle`() {
        ProcessingStatus.frameError("a", "b", "c").use { assertEquals(ProcessingStatus.Tag.FrameError, it.tag) }
        ProcessingStatus.operationFailed(Operation.Add, 1, "x").use { assertEquals(ProcessingStatus.Tag.OperationFailed, it.tag) }
        ProcessingStatus.progress(1, 2, "l", true).use { assertEquals(ProcessingStatus.Tag.Progress, it.tag) }
        ProcessingStatus.idle().use { assertEquals(ProcessingStatus.Tag.Idle, it.tag) }
    }

    @Test fun `edge - lifecycle create and close many`() {
        repeat(50) { i ->
            ProcessingStatus.frameError("src$i", "dst$i", "err$i").use { status ->
                assertTrue(status is ProcessingStatus.FrameError)
                assertEquals("src$i", status.src)
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Load tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `load - 100K factory FrameError calls`() {
        repeat(100_000) {
            ProcessingStatus.frameError("src", "dst", "err").use { status ->
                assertTrue(status is ProcessingStatus.FrameError)
            }
        }
    }

    @Test fun `load - 100K factory OperationFailed with enum field`() {
        repeat(100_000) {
            ProcessingStatus.operationFailed(Operation.Multiply, 42, "msg").use { status ->
                assertTrue(status is ProcessingStatus.OperationFailed)
                assertEquals(Operation.Multiply, status.operation)
            }
        }
    }

    @Test fun `load - 100K get_processing_status calls`() {
        Calculator(-1).use { calc ->
            repeat(100_000) {
                calc.get_processing_status().use { status ->
                    assertTrue(status is ProcessingStatus.FrameError)
                }
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Concurrency tests
    // ═══════════════════════════════════════════════════════════════════════════

    @Test fun `concurrent - 10 threads x 10K factory FrameError`() {
        val threads = (1..10).map { tid ->
            Thread {
                repeat(10_000) {
                    ProcessingStatus.frameError("src$tid", "dst$tid", "err$tid").use { status ->
                        assertTrue(status is ProcessingStatus.FrameError)
                        assertEquals("src$tid", status.src)
                        assertEquals("dst$tid", status.destination)
                        assertEquals("err$tid", status.error)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K factory OperationFailed with enum`() {
        val ops = Operation.entries
        val threads = (1..10).map { tid ->
            Thread {
                val op = ops[tid % ops.size]
                repeat(10_000) {
                    ProcessingStatus.operationFailed(op, tid, "m$tid").use { status ->
                        assertTrue(status is ProcessingStatus.OperationFailed)
                        assertEquals(op, status.operation)
                        assertEquals(tid, status.code)
                        assertEquals("m$tid", status.message)
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }

    @Test fun `concurrent - 10 threads x 10K get_processing_status`() {
        val threads = (1..10).map { tid ->
            Thread {
                Calculator(tid * -10).use { calc ->
                    repeat(10_000) {
                        calc.get_processing_status().use { status ->
                            assertTrue(status is ProcessingStatus.FrameError)
                        }
                    }
                }
            }
        }
        threads.forEach { it.start() }
        threads.forEach { it.join() }
    }
}
