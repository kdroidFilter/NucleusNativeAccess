package io.github.kdroidfilter.nucleusnativeaccess.plugin.codegen

import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class RustBridgeGeneratorTest {

    private val simpleModule = KneModule(
        libName = "calculator",
        packages = setOf("calculator"),
        classes = listOf(
            KneClass(
                simpleName = "Calculator",
                fqName = "calculator.Calculator",
                constructor = KneConstructor(
                    params = listOf(
                        KneParam("initial_value", KneType.INT),
                        KneParam("name", KneType.STRING),
                    )
                ),
                methods = listOf(
                    KneFunction("add", listOf(KneParam("n", KneType.INT)), KneType.INT),
                    KneFunction("get_value", emptyList(), KneType.INT),
                    KneFunction("get_name", emptyList(), KneType.STRING),
                    KneFunction("reset", listOf(KneParam("new_value", KneType.INT)), KneType.INT),
                ),
                properties = emptyList(),
            ),
        ),
        dataClasses = emptyList(),
        enums = listOf(
            KneEnum(
                simpleName = "Operation",
                fqName = "calculator.Operation",
                entries = listOf("Add", "Subtract", "Multiply", "Divide"),
            )
        ),
        functions = listOf(
            KneFunction(
                name = "compute",
                params = listOf(
                    KneParam("a", KneType.INT),
                    KneParam("b", KneType.INT),
                    KneParam("op", KneType.ENUM("calculator.Operation", "Operation")),
                ),
                returnType = KneType.INT,
            ),
            KneFunction(
                name = "greet",
                params = listOf(KneParam("name", KneType.STRING)),
                returnType = KneType.STRING,
            ),
        ),
    )

    private lateinit var code: String

    @Before
    fun setUp() {
        code = RustBridgeGenerator().generate(simpleModule)
    }

    // --- Error infrastructure ---

    @Test
    fun `generates thread_local error storage`() {
        assertContains("KNE_LAST_ERROR")
        assertContains("RefCell<Option<String>>")
    }

    @Test
    fun `generates hasError function`() {
        assertContains("fn calculator_kne_hasError")
        assertContains("#[no_mangle]")
        assertContains("pub extern \"C\"")
    }

    @Test
    fun `generates getLastError function`() {
        assertContains("fn calculator_kne_getLastError")
    }

    // --- Constructor ---

    @Test
    fun `generates constructor for Calculator`() {
        assertContains("fn calculator_Calculator_new")
        assertContains("initial_value: i32")
        assertContains("-> i64")
        assertContains("Box::into_raw")
    }

    @Test
    fun `constructor has String param as pointer`() {
        // String params should be *const c_char in the extern "C" fn
        assertContains("name: *const c_char")
    }

    // --- Dispose ---

    @Test
    fun `generates dispose for Calculator`() {
        assertContains("fn calculator_Calculator_dispose")
        assertContains("handle: i64")
        assertContains("Box::from_raw")
    }

    // --- Methods ---

    @Test
    fun `generates add method`() {
        assertContains("fn calculator_Calculator_add")
        assertContains("handle: i64")
        assertContains("n: i32")
        assertContains("-> i32")
    }

    @Test
    fun `generates get_value method`() {
        assertContains("fn calculator_Calculator_get_value")
        assertContains("handle: i64")
        assertContains("-> i32")
    }

    @Test
    fun `generates get_name method with buffer pattern`() {
        assertContains("fn calculator_Calculator_get_name")
        assertContains("out_buf: *mut u8")
        assertContains("out_buf_len: i32")
        assertContains("-> i32") // returns byte count
    }

    @Test
    fun `generates reset method`() {
        assertContains("fn calculator_Calculator_reset")
        assertContains("new_value: i32")
    }

    // --- Enum ---

    @Test
    fun `generates enum name bridge`() {
        assertContains("fn calculator_Operation_name")
    }

    @Test
    fun `generates enum count bridge`() {
        assertContains("fn calculator_Operation_count")
        assertContains("-> i32")
    }

    // --- Top-level functions ---

    @Test
    fun `generates compute function`() {
        assertContains("fn calculator_compute")
        assertContains("a: i32")
        assertContains("b: i32")
        assertContains("op: i32") // enum passed as ordinal
    }

    @Test
    fun `generates greet function with string output`() {
        assertContains("fn calculator_greet")
        assertContains("name: *const c_char")
        assertContains("out_buf: *mut u8")
    }

    // --- Panic catching ---

    @Test
    fun `wraps bodies in catch_unwind`() {
        assertContains("catch_unwind")
    }

    // --- Code validity ---

    @Test
    fun `generates valid use statements`() {
        assertContains("use std::cell::RefCell")
        assertContains("use std::ffi::CStr")
    }

    @Test
    fun `all extern C functions are no_mangle`() {
        // Count #[no_mangle] and extern "C" — they should match
        val noMangle = code.split("#[no_mangle]").size - 1
        val externC = code.split("pub extern \"C\"").size - 1
        assertEquals("Each extern C fn should have #[no_mangle]", noMangle, externC)
    }

    @Test
    fun `generates slice param as pointer plus length`() {
        val moduleWithSlice = simpleModule.copy(
            functions = simpleModule.functions + KneFunction(
                name = "process_bytes",
                params = listOf(KneParam("data", KneType.BYTE_ARRAY, isBorrowed = true)),
                returnType = KneType.INT,
            )
        )
        val sliceCode = RustBridgeGenerator().generate(moduleWithSlice)
        assertTrue(sliceCode.contains("data_ptr: *const u8"))
        assertTrue(sliceCode.contains("data_len: i32"))
        assertTrue(sliceCode.contains("std::slice::from_raw_parts"))
    }

    private fun assertContains(substring: String) {
        assertTrue(
            "Generated code should contain '$substring'.\nGenerated code:\n${code.take(3000)}",
            code.contains(substring)
        )
    }
}
