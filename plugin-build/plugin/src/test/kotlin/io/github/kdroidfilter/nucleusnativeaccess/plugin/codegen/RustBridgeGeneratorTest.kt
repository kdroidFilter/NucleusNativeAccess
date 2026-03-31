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

    @Test
    fun `generates nullable param conversion for Option INT`() {
        val moduleWithNullable = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "add_optional",
                    params = listOf(KneParam("value", KneType.NULLABLE(KneType.INT))),
                    returnType = KneType.INT,
                    isMutating = true,
                )
            ))
        )
        val nullableCode = RustBridgeGenerator().generate(moduleWithNullable)
        assertTrue(nullableCode.contains("value_opt"))
        assertTrue(nullableCode.contains("Option<i32>"))
        assertTrue(nullableCode.contains("i64::MIN"))
    }

    @Test
    fun `generates nullable String param as pointer`() {
        val moduleWithNullable = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "set_nickname",
                    params = listOf(KneParam("name", KneType.NULLABLE(KneType.STRING))),
                    returnType = KneType.UNIT,
                    isMutating = true,
                )
            ))
        )
        val nullableCode = RustBridgeGenerator().generate(moduleWithNullable)
        assertTrue(nullableCode.contains("name: *const c_char"))
        assertTrue(nullableCode.contains("name_opt"))
        assertTrue(nullableCode.contains("Option<String>"))
        assertTrue(nullableCode.contains("is_null()"))
    }

    // --- Suspend ---

    @Test
    fun `generates suspend helpers when module has suspend functions`() {
        val moduleWithSuspend = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "delayed_add",
                    params = listOf(KneParam("value", KneType.INT), KneParam("delay_ms", KneType.INT)),
                    returnType = KneType.INT,
                    isSuspend = true,
                    isMutating = true,
                )
            ))
        )
        val suspendCode = RustBridgeGenerator().generate(moduleWithSuspend)
        assertTrue(suspendCode.contains("fn calculator_kne_cancelJob"))
        assertTrue(suspendCode.contains("fn calculator_kne_disposeRef"))
        assertTrue(suspendCode.contains("fn calculator_kne_readStringRef"))
    }

    @Test
    fun `does not generate suspend helpers when no suspend functions`() {
        assertFalse(code.contains("kne_cancelJob"))
        assertFalse(code.contains("kne_disposeRef"))
        assertFalse(code.contains("kne_readStringRef"))
    }

    @Test
    fun `generates suspend method bridge with cont_ptr and exc_ptr`() {
        val moduleWithSuspend = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "delayed_add",
                    params = listOf(KneParam("value", KneType.INT), KneParam("delay_ms", KneType.INT)),
                    returnType = KneType.INT,
                    isSuspend = true,
                    isMutating = true,
                )
            ))
        )
        val suspendCode = RustBridgeGenerator().generate(moduleWithSuspend)
        assertTrue(suspendCode.contains("fn calculator_Calculator_delayed_add"))
        assertTrue(suspendCode.contains("cont_ptr: i64"))
        assertTrue(suspendCode.contains("exc_ptr: i64"))
        assertTrue(suspendCode.contains("cancel_out: *mut i64"))
        assertTrue(suspendCode.contains("std::thread::spawn"))
        assertTrue(suspendCode.contains("AtomicBool"))
    }

    @Test
    fun `suspend method with String return uses Box String handle`() {
        val moduleWithSuspend = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "delayed_describe",
                    params = listOf(KneParam("delay_ms", KneType.INT)),
                    returnType = KneType.STRING,
                    isSuspend = true,
                )
            ))
        )
        val suspendCode = RustBridgeGenerator().generate(moduleWithSuspend)
        assertTrue(suspendCode.contains("fn calculator_Calculator_delayed_describe"))
        assertTrue(suspendCode.contains("Box::into_raw(Box::new(value))"))
    }

    @Test
    fun `suspend method with Unit return calls cont_fn with zero`() {
        val moduleWithSuspend = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "delayed_noop",
                    params = listOf(KneParam("delay_ms", KneType.INT)),
                    returnType = KneType.UNIT,
                    isSuspend = true,
                )
            ))
        )
        val suspendCode = RustBridgeGenerator().generate(moduleWithSuspend)
        assertTrue(suspendCode.contains("cont_fn(1, 0i64)"))
    }

    // --- Flow ---

    @Test
    fun `generates flow method bridge with next_ptr error_ptr complete_ptr`() {
        val moduleWithFlow = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "count_up",
                    params = listOf(KneParam("max", KneType.INT), KneParam("interval_ms", KneType.INT)),
                    returnType = KneType.FLOW(KneType.INT),
                )
            ))
        )
        val flowCode = RustBridgeGenerator().generate(moduleWithFlow)
        assertTrue(flowCode.contains("fn calculator_Calculator_count_up"))
        assertTrue(flowCode.contains("next_ptr: i64"))
        assertTrue(flowCode.contains("error_ptr: i64"))
        assertTrue(flowCode.contains("complete_ptr: i64"))
        assertTrue(flowCode.contains("cancel_out: *mut i64"))
        assertTrue(flowCode.contains("std::thread::spawn"))
        assertTrue(flowCode.contains("AtomicBool"))
        assertTrue(flowCode.contains("next_fn(item as i64)"))
        assertTrue(flowCode.contains("complete_fn()"))
    }

    @Test
    fun `generates flow with String element using Box`() {
        val moduleWithFlow = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "score_labels",
                    params = listOf(KneParam("count", KneType.INT)),
                    returnType = KneType.FLOW(KneType.STRING),
                )
            ))
        )
        val flowCode = RustBridgeGenerator().generate(moduleWithFlow)
        assertTrue(flowCode.contains("fn calculator_Calculator_score_labels"))
        assertTrue(flowCode.contains("Box::into_raw(Box::new(item)) as i64"))
    }

    @Test
    fun `flow generates suspend helpers for cancelJob and disposeRef`() {
        val moduleWithFlow = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "count_up",
                    params = listOf(KneParam("max", KneType.INT)),
                    returnType = KneType.FLOW(KneType.INT),
                )
            ))
        )
        val flowCode = RustBridgeGenerator().generate(moduleWithFlow)
        assertTrue(flowCode.contains("fn calculator_kne_cancelJob"))
        assertTrue(flowCode.contains("fn calculator_kne_disposeRef"))
        assertTrue(flowCode.contains("fn calculator_kne_readStringRef"))
    }

    private fun assertContains(substring: String) {
        assertTrue(
            "Generated code should contain '$substring'.\nGenerated code:\n${code.take(3000)}",
            code.contains(substring)
        )
    }
}
