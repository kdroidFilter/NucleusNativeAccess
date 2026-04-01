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

    // --- MAP return ---

    @Test
    fun `generates MAP return with String keys and Int values for instance method`() {
        val moduleWithMap = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "get_scores",
                    params = emptyList(),
                    returnType = KneType.MAP(KneType.STRING, KneType.INT),
                )
            ))
        )
        val mapCode = RustBridgeGenerator().generate(moduleWithMap)
        // Signature includes dual buffers and max count
        assertTrue("Should have out_keys param", mapCode.contains("out_keys: *mut u8"))
        assertTrue("Should have out_keys_len param", mapCode.contains("out_keys_len: i32"))
        assertTrue("Should have out_values param", mapCode.contains("out_values: *mut i32"))
        assertTrue("Should have out_max_len param", mapCode.contains("out_max_len: i32"))
        assertTrue("Should return i32 count", mapCode.contains(") -> i32"))
        // Body serializes keys as null-terminated and values as i32
        assertTrue("Should iterate keys", mapCode.contains(".keys()"))
        assertTrue("Should iterate values", mapCode.contains(".values()"))
        assertTrue("Should write null terminator", mapCode.contains("= 0;"))
    }

    @Test
    fun `generates MAP return with Int keys and Long values for companion method`() {
        val moduleWithMap = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                companionMethods = listOf(KneFunction(
                    name = "lookup_table",
                    params = emptyList(),
                    returnType = KneType.MAP(KneType.INT, KneType.LONG),
                ))
            ))
        )
        val mapCode = RustBridgeGenerator().generate(moduleWithMap)
        assertTrue("Should have companion fn", mapCode.contains("companion_lookup_table"))
        assertTrue("Should have out_keys param", mapCode.contains("out_keys: *mut i32"))
        assertTrue("Should have out_values param", mapCode.contains("out_values: *mut i64"))
        assertTrue("Should have out_max_len param", mapCode.contains("out_max_len: i32"))
    }

    @Test
    fun `generates MAP property getter with dual buffers`() {
        val moduleWithMapProp = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                properties = listOf(KneProperty(
                    name = "scores",
                    type = KneType.MAP(KneType.STRING, KneType.INT),
                    mutable = false,
                ))
            ))
        )
        val mapCode = RustBridgeGenerator().generate(moduleWithMapProp)
        assertTrue("Should generate getter for MAP property", mapCode.contains("get_scores"))
        assertTrue("Should have out_keys param", mapCode.contains("out_keys: *mut u8"))
        assertTrue("Should have out_values param", mapCode.contains("out_values: *mut i32"))
        assertTrue("Should have out_max_len param", mapCode.contains("out_max_len: i32"))
    }

    // --- SET return ---

    @Test
    fun `generates SET return as list with out_buf`() {
        val moduleWithSet = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "get_tags",
                    params = emptyList(),
                    returnType = KneType.SET(KneType.STRING),
                )
            ))
        )
        val setCode = RustBridgeGenerator().generate(moduleWithSet)
        assertTrue("Should generate SET method", setCode.contains("get_tags"))
        assertTrue("Should have out_buf param", setCode.contains("out_buf: *mut u8"))
        assertTrue("Should return i32 count", setCode.contains("-> i32"))
    }

    // --- Option<DataClass> return ---

    @Test
    fun `generates nullable DataClass return with out-params and i32 presence flag`() {
        val dc = KneType.DATA_CLASS(
            fqName = "calculator.Point",
            simpleName = "Point",
            fields = listOf(
                KneParam("x", KneType.DOUBLE),
                KneParam("y", KneType.DOUBLE),
            )
        )
        val moduleWithNullDc = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction(
                    name = "find_point",
                    params = emptyList(),
                    returnType = KneType.NULLABLE(dc),
                )
            ))
        )
        val nullDcCode = RustBridgeGenerator().generate(moduleWithNullDc)
        assertTrue("Should have find_point fn", nullDcCode.contains("find_point"))
        assertTrue("Should have out_x param", nullDcCode.contains("out_x: *mut f64"))
        assertTrue("Should have out_y param", nullDcCode.contains("out_y: *mut f64"))
        assertTrue("Should return i32", nullDcCode.contains("-> i32"))
        assertTrue("Should match Some", nullDcCode.contains("Some(v)"))
        assertTrue("Should return 1 on Some", nullDcCode.contains("1i32"))
        assertTrue("Should return 0 on None", nullDcCode.contains("0i32"))
    }

    @Test
    fun `generates nullable DataClass with String field return`() {
        val dc = KneType.DATA_CLASS(
            fqName = "calculator.NamedValue",
            simpleName = "NamedValue",
            fields = listOf(
                KneParam("name", KneType.STRING),
                KneParam("value", KneType.INT),
            )
        )
        val moduleWithNullDcStr = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                companionMethods = listOf(KneFunction(
                    name = "find_named",
                    params = emptyList(),
                    returnType = KneType.NULLABLE(dc),
                ))
            ))
        )
        val code = RustBridgeGenerator().generate(moduleWithNullDcStr)
        assertTrue("Should have companion fn", code.contains("companion_find_named"))
        assertTrue("Should have out_name string param", code.contains("out_name: *mut u8"))
        assertTrue("Should have out_name_len param", code.contains("out_name_len: i32"))
        assertTrue("Should have out_value param", code.contains("out_value: *mut i32"))
    }

    // ── Object / Interface / SealedEnum returns ────────────────────────────

    @Test
    fun `generates method returning Object reference`() {
        val m = moduleWith(KneFunction("get_child", emptyList(), KneType.OBJECT("calculator.Calculator", "Calculator")))
        assertIn(m, "fn calculator_Calculator_get_child")
        assertIn(m, "-> i64")
        assertIn(m, "Box::into_raw")
    }

    @Test
    fun `generates method returning borrowed Object`() {
        val m = moduleWith(KneFunction("peek_child", emptyList(), KneType.OBJECT("calculator.Calculator", "Calculator"), returnsBorrowed = true))
        assertIn(m, "as *const _ as i64")
    }

    @Test
    fun `generates method with Object param`() {
        val m = moduleWith(KneFunction("set_child", listOf(KneParam("child", KneType.OBJECT("calculator.Calculator", "Calculator"))), KneType.UNIT))
        assertIn(m, "child: i64")
    }

    @Test
    fun `generates method returning Interface`() {
        val mod = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction("get_measurable", emptyList(), KneType.INTERFACE("calculator.Measurable", "Measurable"))
            ))
        )
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("-> i64"))
        assertTrue(c.contains("Box::into_raw"))
    }

    // ── Sealed enum bridges ─────────────────────────────────────────────────

    @Test
    fun `generates sealed enum dispose bridge`() {
        val mod = moduleWithSealed()
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_AppResult_dispose"))
        assertTrue(c.contains("Box::from_raw(handle as *mut AppResult)"))
    }

    @Test
    fun `generates sealed enum tag bridge`() {
        val c = RustBridgeGenerator().generate(moduleWithSealed())
        assertTrue(c.contains("fn calculator_AppResult_tag"))
        assertTrue(c.contains("-> i32"))
        assertTrue(c.contains("AppResult::Ok"))
        assertTrue(c.contains("AppResult::Error"))
    }

    @Test
    fun `generates sealed enum variant field getter`() {
        val c = RustBridgeGenerator().generate(moduleWithSealed())
        // Ok variant has a value:i32 field
        assertTrue(c.contains("fn calculator_AppResult_Ok_get_value"))
    }

    @Test
    fun `generates sealed enum string variant field getter with buffer`() {
        val c = RustBridgeGenerator().generate(moduleWithSealed())
        // Error variant has a message:String field
        assertTrue(c.contains("fn calculator_AppResult_Error_get_message"))
        assertTrue(c.contains("out_buf: *mut u8"))
    }

    @Test
    fun `generates method returning sealed enum`() {
        val mod = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + KneFunction("try_op", emptyList(), KneType.SEALED_ENUM("calculator.AppResult", "AppResult"))
            )),
            sealedEnums = listOf(appResultSealed())
        )
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_try_op"))
        assertTrue(c.contains("-> i64"))
    }

    // ── Properties ──────────────────────────────────────────────────────────

    @Test
    fun `generates property getter bridge`() {
        val mod = simpleModule.copy(classes = listOf(simpleModule.classes.first().copy(
            properties = listOf(KneProperty("scale", KneType.DOUBLE, mutable = false))
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_get_scale"))
        assertTrue(c.contains("handle: i64"))
        assertTrue(c.contains("-> f64"))
    }

    @Test
    fun `generates property setter bridge for mutable property`() {
        val mod = simpleModule.copy(classes = listOf(simpleModule.classes.first().copy(
            properties = listOf(KneProperty("scale", KneType.DOUBLE, mutable = true))
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_get_scale"))
        assertTrue(c.contains("fn calculator_Calculator_set_scale"))
        assertTrue(c.contains("value: f64"))
    }

    @Test
    fun `generates String property getter with buffer`() {
        val mod = simpleModule.copy(classes = listOf(simpleModule.classes.first().copy(
            properties = listOf(KneProperty("label", KneType.STRING, mutable = false))
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_get_label"))
        assertTrue(c.contains("out_buf: *mut u8"))
        assertTrue(c.contains("out_buf_len: i32"))
    }

    @Test
    fun `generates Boolean property getter`() {
        val mod = simpleModule.copy(classes = listOf(simpleModule.classes.first().copy(
            properties = listOf(KneProperty("enabled", KneType.BOOLEAN, mutable = true))
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_get_enabled"))
        assertTrue(c.contains("-> i32"))
        assertTrue(c.contains("fn calculator_Calculator_set_enabled"))
    }

    // ── Companion methods ───────────────────────────────────────────────────

    @Test
    fun `generates companion method with params`() {
        val mod = simpleModule.copy(classes = listOf(simpleModule.classes.first().copy(
            companionMethods = listOf(KneFunction("from_value", listOf(KneParam("v", KneType.INT)), KneType.OBJECT("calculator.Calculator", "Calculator")))
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_companion_from_value"))
        assertTrue(c.contains("v: i32"))
        assertTrue(c.contains("-> i64"))
        assertTrue(c.contains("Calculator::from_value"))
    }

    @Test
    fun `generates companion method returning String`() {
        val mod = simpleModule.copy(classes = listOf(simpleModule.classes.first().copy(
            companionMethods = listOf(KneFunction("version", emptyList(), KneType.STRING))
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Calculator_companion_version"))
        assertTrue(c.contains("out_buf: *mut u8"))
    }

    // ── canFail / Result returns ────────────────────────────────────────────

    @Test
    fun `generates canFail method with match Ok Err pattern`() {
        val m = moduleWith(KneFunction("divide", listOf(KneParam("b", KneType.INT)), KneType.INT, canFail = true))
        assertIn(m, "match")
        assertIn(m, "Ok(result)")
        assertIn(m, "Err(e)")
        assertIn(m, "kne_set_error")
    }

    @Test
    fun `generates canFail method returning Object`() {
        val m = moduleWith(KneFunction("try_create", emptyList(), KneType.OBJECT("calculator.Calculator", "Calculator"), canFail = true))
        assertIn(m, "Ok(result)")
        assertIn(m, "Box::into_raw")
        assertIn(m, "Err(e)")
    }

    // ── unsafe methods ──────────────────────────────────────────────────────

    @Test
    fun `generates unsafe wrapper for unsafe methods`() {
        val m = moduleWith(KneFunction("raw_op", emptyList(), KneType.INT, isUnsafe = true))
        assertIn(m, "unsafe {")
    }

    // ── OWNED vs BORROWED receiver ──────────────────────────────────────────

    @Test
    fun `generates ptr_read for OWNED receiver`() {
        val m = moduleWith(KneFunction("consume", emptyList(), KneType.INT, receiverKind = KneReceiverKind.OWNED))
        assertIn(m, "std::ptr::read(handle as *const Calculator)")
    }

    @Test
    fun `generates shared ref for BORROWED_SHARED receiver`() {
        val m = moduleWith(KneFunction("peek", emptyList(), KneType.INT, receiverKind = KneReceiverKind.BORROWED_SHARED))
        assertIn(m, "&*(handle as *const Calculator)")
    }

    @Test
    fun `generates mut ref for BORROWED_MUT receiver`() {
        val m = moduleWith(KneFunction("mutate", emptyList(), KneType.UNIT, receiverKind = KneReceiverKind.BORROWED_MUT))
        assertIn(m, "&mut *(handle as *mut Calculator)")
    }

    // ── BYTE_ARRAY return ───────────────────────────────────────────────────

    @Test
    fun `generates BYTE_ARRAY return with buffer pattern`() {
        val m = moduleWith(KneFunction("to_bytes", emptyList(), KneType.BYTE_ARRAY))
        assertIn(m, "out_buf: *mut u8")
        assertIn(m, "out_buf_len: i32")
        assertIn(m, "-> i32")
        assertIn(m, "copy_nonoverlapping")
    }

    // ── LIST returns for various element types ──────────────────────────────

    @Test
    fun `generates LIST Long return`() {
        val m = moduleWith(KneFunction("get_ids", emptyList(), KneType.LIST(KneType.LONG)))
        assertIn(m, "out_buf: *mut u8")
        assertIn(m, "*(out_buf as *mut i64)")
    }

    @Test
    fun `generates LIST Double return`() {
        val m = moduleWith(KneFunction("get_values", emptyList(), KneType.LIST(KneType.DOUBLE)))
        assertIn(m, "*(out_buf as *mut f64)")
    }

    @Test
    fun `generates LIST String return with null-terminated serialization`() {
        val m = moduleWith(KneFunction("get_names", emptyList(), KneType.LIST(KneType.STRING)))
        assertIn(m, "out_buf: *mut u8")
        assertIn(m, "as_bytes()")
        assertIn(m, "= 0;")
    }

    @Test
    fun `generates LIST Object return with i64 handles`() {
        val m = moduleWith(KneFunction("get_children", emptyList(), KneType.LIST(KneType.OBJECT("calculator.Calculator", "Calculator"))))
        assertIn(m, "*(out_buf as *mut i64)")
        assertIn(m, "as *const _ as i64")
    }

    @Test
    fun `generates LIST Boolean return`() {
        val m = moduleWith(KneFunction("get_flags", emptyList(), KneType.LIST(KneType.BOOLEAN)))
        assertIn(m, "*(out_buf as *mut i32)")
        assertIn(m, "if *v { 1 } else { 0 }")
    }

    @Test
    fun `generates LIST Enum return`() {
        val m = moduleWith(KneFunction("get_ops", emptyList(), KneType.LIST(KneType.ENUM("calculator.Operation", "Operation"))))
        assertIn(m, "*(out_buf as *mut i32)")
        assertIn(m, "v.clone() as i32")
    }

    @Test
    fun `generates SET Long return mapped to list`() {
        val m = moduleWith(KneFunction("get_unique_ids", emptyList(), KneType.SET(KneType.LONG)))
        assertIn(m, "*(out_buf as *mut i64)")
    }

    // ── MAP key/value type diversity ────────────────────────────────────────

    @Test
    fun `generates MAP Long to Double return`() {
        val m = moduleWith(KneFunction("get_measurements", emptyList(), KneType.MAP(KneType.LONG, KneType.DOUBLE)))
        assertIn(m, "out_keys: *mut i64")
        assertIn(m, "out_values: *mut f64")
    }

    @Test
    fun `generates MAP String to String return`() {
        val m = moduleWith(KneFunction("get_env", emptyList(), KneType.MAP(KneType.STRING, KneType.STRING)))
        assertIn(m, "out_keys: *mut u8")
        assertIn(m, "out_keys_len: i32")
        assertIn(m, "out_values: *mut u8")
        assertIn(m, "out_values_len: i32")
    }

    @Test
    fun `generates MAP Int to Object return`() {
        val m = moduleWith(KneFunction("get_index", emptyList(), KneType.MAP(KneType.INT, KneType.OBJECT("calculator.Calculator", "Calculator"))))
        assertIn(m, "out_keys: *mut i32")
        assertIn(m, "out_values: *mut i64")
    }

    // ── Nullable returns for all types ───────────────────────────────────────

    @Test
    fun `generates nullable Long return with i64 MIN sentinel`() {
        val m = moduleWith(KneFunction("get_count", emptyList(), KneType.NULLABLE(KneType.LONG)))
        assertIn(m, "-> i64")
        assertIn(m, "i64::MIN")
    }

    @Test
    fun `generates nullable Double return with bits encoding`() {
        val m = moduleWith(KneFunction("get_ratio", emptyList(), KneType.NULLABLE(KneType.DOUBLE)))
        assertIn(m, "to_ne_bytes")
    }

    @Test
    fun `generates nullable Float return`() {
        val m = moduleWith(KneFunction("get_scale", emptyList(), KneType.NULLABLE(KneType.FLOAT)))
        assertIn(m, "to_bits")
    }

    @Test
    fun `generates nullable Boolean return with minus one sentinel`() {
        val m = moduleWith(KneFunction("is_ready", emptyList(), KneType.NULLABLE(KneType.BOOLEAN)))
        assertIn(m, "Some(true) => 1")
        assertIn(m, "Some(false) => 0")
        assertIn(m, "None => -1")
    }

    @Test
    fun `generates nullable Enum return`() {
        val m = moduleWith(KneFunction("get_op", emptyList(), KneType.NULLABLE(KneType.ENUM("calculator.Operation", "Operation"))))
        assertIn(m, "Some(v) => v as i32")
        assertIn(m, "None => -1")
    }

    @Test
    fun `generates nullable Object return with zero sentinel`() {
        val m = moduleWith(KneFunction("find_child", emptyList(), KneType.NULLABLE(KneType.OBJECT("calculator.Calculator", "Calculator"))))
        assertIn(m, "Some(v) => Box::into_raw")
        assertIn(m, "None => 0i64")
    }

    @Test
    fun `generates nullable String return with minus one sentinel`() {
        val m = moduleWith(KneFunction("get_label", emptyList(), KneType.NULLABLE(KneType.STRING)))
        assertIn(m, "Some(ref s)")
        assertIn(m, "None => -1")
    }

    @Test
    fun `generates nullable Byte return`() {
        val m = moduleWith(KneFunction("get_flag", emptyList(), KneType.NULLABLE(KneType.BYTE)))
        assertIn(m, "i32::MIN")
    }

    @Test
    fun `generates nullable Short return`() {
        val m = moduleWith(KneFunction("get_tag", emptyList(), KneType.NULLABLE(KneType.SHORT)))
        assertIn(m, "i32::MIN")
    }

    // ── Data class return field types ────────────────────────────────────────

    @Test
    fun `generates DataClass return with Long and Boolean fields`() {
        val dc = KneType.DATA_CLASS("calculator.Stats", "Stats", listOf(
            KneParam("count", KneType.LONG),
            KneParam("active", KneType.BOOLEAN),
        ))
        val m = moduleWith(KneFunction("get_stats", emptyList(), dc))
        assertIn(m, "out_count: *mut i64")
        assertIn(m, "out_active: *mut i32")
        assertIn(m, "-> ()")
    }

    @Test
    fun `generates DataClass return with Enum field`() {
        val dc = KneType.DATA_CLASS("calculator.Entry", "Entry", listOf(
            KneParam("op", KneType.ENUM("calculator.Operation", "Operation")),
            KneParam("value", KneType.INT),
        ))
        val m = moduleWith(KneFunction("last_entry", emptyList(), dc))
        assertIn(m, "out_op: *mut i32")
        assertIn(m, "out_value: *mut i32")
    }

    @Test
    fun `generates DataClass return with Float field`() {
        val dc = KneType.DATA_CLASS("calculator.Coord", "Coord", listOf(
            KneParam("lat", KneType.FLOAT),
            KneParam("lng", KneType.FLOAT),
        ))
        val m = moduleWith(KneFunction("get_coord", emptyList(), dc))
        assertIn(m, "out_lat: *mut f32")
        assertIn(m, "out_lng: *mut f32")
    }

    // ── Struct literal constructor ───────────────────────────────────────────

    @Test
    fun `generates struct literal constructor`() {
        val mod = simpleModule.copy(classes = listOf(KneClass(
            simpleName = "Point",
            fqName = "calculator.Point",
            constructor = KneConstructor(
                params = listOf(KneParam("x", KneType.DOUBLE), KneParam("y", KneType.DOUBLE)),
                kind = KneConstructorKind.STRUCT_LITERAL,
            ),
            methods = emptyList(),
            properties = emptyList(),
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Point_new"))
        assertTrue(c.contains("Point { x:"))
    }

    @Test
    fun `generates function constructor`() {
        assertContains("Calculator::new(")
    }

    @Test
    fun `does not generate constructor for NONE kind`() {
        val mod = simpleModule.copy(classes = listOf(KneClass(
            simpleName = "Singleton",
            fqName = "calculator.Singleton",
            constructor = KneConstructor(params = emptyList(), kind = KneConstructorKind.NONE),
            methods = listOf(KneFunction("value", emptyList(), KneType.INT)),
            properties = emptyList(),
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertFalse(c.contains("fn calculator_Singleton_new"))
        assertTrue(c.contains("fn calculator_Singleton_value"))
    }

    // ── canFail constructor ─────────────────────────────────────────────────

    @Test
    fun `generates canFail constructor with Result pattern`() {
        val mod = simpleModule.copy(classes = listOf(KneClass(
            simpleName = "Parser",
            fqName = "calculator.Parser",
            constructor = KneConstructor(
                params = listOf(KneParam("input", KneType.STRING)),
                kind = KneConstructorKind.FUNCTION,
                canFail = true,
            ),
            methods = emptyList(),
            properties = emptyList(),
        )))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Parser_new"))
        assertTrue(c.contains("Ok(result)"))
        assertTrue(c.contains("Err(e)"))
        assertTrue(c.contains("kne_set_error"))
    }

    // ── no_mangle count matches across all additions ────────────────────────

    @Test
    fun `no_mangle and extern C counts match for module with sealed enum`() {
        val c = RustBridgeGenerator().generate(moduleWithSealed())
        val noMangle = c.split("#[no_mangle]").size - 1
        val externC = c.split("pub extern \"C\"").size - 1
        assertEquals("Each extern C fn should have #[no_mangle]", noMangle, externC)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    /** Create a module with one extra method added to the Calculator class. */
    private fun moduleWith(fn: KneFunction): String {
        val mod = simpleModule.copy(
            classes = listOf(simpleModule.classes.first().copy(
                methods = simpleModule.classes.first().methods + fn
            ))
        )
        return RustBridgeGenerator().generate(mod)
    }

    private fun assertIn(code: String, substring: String) {
        assertTrue("Generated code should contain '$substring'", code.contains(substring))
    }

    private fun appResultSealed() = KneSealedEnum(
        simpleName = "AppResult",
        fqName = "calculator.AppResult",
        variants = listOf(
            KneSealedVariant("Ok", listOf(KneParam("value", KneType.INT)), isTuple = true),
            KneSealedVariant("Error", listOf(KneParam("message", KneType.STRING)), isTuple = true),
            KneSealedVariant("Nothing", emptyList()),
        )
    )

    private fun moduleWithSealed() = simpleModule.copy(
        sealedEnums = listOf(appResultSealed())
    )

    @Test
    fun `generates sealed variant LIST field getter with buffer`() {
        val sealed = KneSealedEnum(
            simpleName = "Result",
            fqName = "test.Result",
            variants = listOf(
                KneSealedVariant("Success", listOf(KneParam("values", KneType.LIST(KneType.INT))), isTuple = true),
            )
        )
        val mod = simpleModule.copy(sealedEnums = listOf(sealed))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Result_Success_get_values"))
        assertTrue(c.contains("out_buf: *mut u8, out_buf_len: i32") || c.contains("out_buf: *mut i32, out_buf_len: i32"))
        assertTrue(c.contains("-> i32"))
    }

    @Test
    fun `generates sealed variant SET field getter with buffer`() {
        val sealed = KneSealedEnum(
            simpleName = "Result",
            fqName = "test.Result",
            variants = listOf(
                KneSealedVariant("Success", listOf(KneParam("tags", KneType.SET(KneType.STRING))), isTuple = true),
            )
        )
        val mod = simpleModule.copy(sealedEnums = listOf(sealed))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Result_Success_get_tags"))
        assertTrue(c.contains("out_buf: *mut u8, out_buf_len: i32"))
        assertTrue(c.contains("-> i32"))
    }

    @Test
    fun `generates sealed variant MAP field getter with dual buffers`() {
        val sealed = KneSealedEnum(
            simpleName = "Result",
            fqName = "test.Result",
            variants = listOf(
                KneSealedVariant("Data", listOf(KneParam("mapping", KneType.MAP(KneType.STRING, KneType.INT))), isTuple = true),
            )
        )
        val mod = simpleModule.copy(sealedEnums = listOf(sealed))
        val c = RustBridgeGenerator().generate(mod)
        assertTrue(c.contains("fn calculator_Result_Data_get_mapping"))
        assertTrue(c.contains("out_keys: *mut u8, out_keys_len: i32"))
        assertTrue(c.contains("out_values: *mut i32"))
        assertTrue(c.contains("out_max_len: i32"))
        assertTrue(c.contains("-> i32"))
    }

    private fun assertContains(substring: String) {
        assertTrue(
            "Generated code should contain '$substring'.\nGenerated code:\n${code.take(3000)}",
            code.contains(substring)
        )
    }
}
