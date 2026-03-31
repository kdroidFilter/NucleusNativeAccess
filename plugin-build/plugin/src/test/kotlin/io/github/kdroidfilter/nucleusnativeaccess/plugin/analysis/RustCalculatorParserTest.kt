package io.github.kdroidfilter.nucleusnativeaccess.plugin.analysis

import io.github.kdroidfilter.nucleusnativeaccess.plugin.ir.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Tests parsing the Rust Calculator example — which mirrors the Kotlin/Native Calculator.
 * Validates that the parser produces a KneModule equivalent to what PsiSourceParser
 * would produce for the Kotlin version.
 */
class RustCalculatorParserTest {

    private lateinit var module: KneModule

    @Before
    fun setUp() {
        val json = javaClass.classLoader
            .getResourceAsStream("rustdoc-fixtures/rust-calculator.json")!!
            .bufferedReader()
            .readText()
        module = RustdocJsonParser().parse(json, "calculator")
    }

    // --- Calculator class ---

    @Test
    fun `parses Calculator struct`() {
        val calc = module.classes.find { it.simpleName == "Calculator" }
        assertNotNull("Calculator class should exist", calc)
    }

    @Test
    fun `Calculator constructor takes i32`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        assertEquals(1, calc.constructor.params.size)
        assertEquals("initial", calc.constructor.params[0].name)
        assertEquals(KneType.INT, calc.constructor.params[0].type)
    }

    @Test
    fun `Calculator has all arithmetic methods`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val methodNames = calc.methods.map { it.name }
        assertTrue("add", "add" in methodNames)
        assertTrue("subtract", "subtract" in methodNames)
        assertTrue("multiply", "multiply" in methodNames)
        assertTrue("divide", "divide" in methodNames)
        assertTrue("reset", "reset" in methodNames)
    }

    @Test
    fun `Calculator add method signature`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val add = calc.methods.first { it.name == "add" }
        assertEquals(1, add.params.size)
        assertEquals("value", add.params[0].name)
        assertEquals(KneType.INT, add.params[0].type)
        assertEquals(KneType.INT, add.returnType)
    }

    @Test
    fun `Calculator has all primitive type methods`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val methods = calc.methods.associateBy { it.name }

        // add_long: (i64) -> i64
        assertEquals(KneType.LONG, methods["add_long"]?.params?.get(0)?.type)
        assertEquals(KneType.LONG, methods["add_long"]?.returnType)

        // add_double: (f64) -> f64
        assertEquals(KneType.DOUBLE, methods["add_double"]?.params?.get(0)?.type)
        assertEquals(KneType.DOUBLE, methods["add_double"]?.returnType)

        // add_float: (f32) -> f32
        assertEquals(KneType.FLOAT, methods["add_float"]?.params?.get(0)?.type)
        assertEquals(KneType.FLOAT, methods["add_float"]?.returnType)

        // add_short: (i16) -> i16
        assertEquals(KneType.SHORT, methods["add_short"]?.params?.get(0)?.type)
        assertEquals(KneType.SHORT, methods["add_short"]?.returnType)

        // add_byte: (i8) -> i8
        assertEquals(KneType.BYTE, methods["add_byte"]?.params?.get(0)?.type)
        assertEquals(KneType.BYTE, methods["add_byte"]?.returnType)

        // is_positive: () -> bool
        assertEquals(KneType.BOOLEAN, methods["is_positive"]?.returnType)

        // check_flag: (bool) -> bool
        assertEquals(KneType.BOOLEAN, methods["check_flag"]?.params?.get(0)?.type)
        assertEquals(KneType.BOOLEAN, methods["check_flag"]?.returnType)
    }

    @Test
    fun `Calculator has string methods`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val methods = calc.methods.associateBy { it.name }

        // describe: () -> String
        assertEquals(KneType.STRING, methods["describe"]?.returnType)

        // echo: (&str) -> String
        assertEquals(KneType.STRING, methods["echo"]?.params?.get(0)?.type)
        assertEquals(KneType.STRING, methods["echo"]?.returnType)

        // concat: (&str, &str) -> String
        assertEquals(2, methods["concat"]?.params?.size)
        assertEquals(KneType.STRING, methods["concat"]?.returnType)
    }

    @Test
    fun `Calculator has properties extracted from get_ set_ accessors`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val props = calc.properties.associateBy { it.name }

        // label: get_label + set_label → mutable String property
        assertNotNull(props["label"])
        assertEquals(KneType.STRING, props["label"]!!.type)
        assertTrue("label should be mutable", props["label"]!!.mutable)

        // scale: get_scale + set_scale → mutable Double property
        assertNotNull(props["scale"])
        assertEquals(KneType.DOUBLE, props["scale"]!!.type)
        assertTrue("scale should be mutable", props["scale"]!!.mutable)

        // enabled: get_enabled + set_enabled → mutable Boolean property
        assertNotNull(props["enabled"])
        assertEquals(KneType.BOOLEAN, props["enabled"]!!.type)
        assertTrue("enabled should be mutable", props["enabled"]!!.mutable)

        // get_/set_ methods should be removed from methods list
        val methodNames = calc.methods.map { it.name }
        assertFalse("get_label should not be in methods", "get_label" in methodNames)
        assertFalse("set_label should not be in methods", "set_label" in methodNames)
    }

    @Test
    fun `Calculator has nullable return methods`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val methods = calc.methods.associateBy { it.name }

        // divide_or_null: (i32) -> Option<i32>
        val divOrNull = methods["divide_or_null"]
        assertNotNull(divOrNull)
        assertTrue(divOrNull!!.returnType is KneType.NULLABLE)
        assertEquals(KneType.INT, (divOrNull.returnType as KneType.NULLABLE).inner)

        // describe_or_null: () -> Option<String>
        val descOrNull = methods["describe_or_null"]
        assertNotNull(descOrNull)
        assertTrue(descOrNull!!.returnType is KneType.NULLABLE)
        assertEquals(KneType.STRING, (descOrNull.returnType as KneType.NULLABLE).inner)
    }

    @Test
    fun `Calculator has collection methods`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val methods = calc.methods.associateBy { it.name }

        // get_recent_scores: () -> Vec<i32> = LIST(INT)
        val scores = methods["get_recent_scores"]
        assertNotNull(scores)
        assertTrue(scores!!.returnType is KneType.LIST)
        assertEquals(KneType.INT, (scores.returnType as KneType.LIST).elementType)
    }

    @Test
    fun `Calculator has byte array methods`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val methods = calc.methods.associateBy { it.name }

        // to_bytes: () -> Vec<u8> = BYTE_ARRAY
        assertEquals(KneType.BYTE_ARRAY, methods["to_bytes"]?.returnType)

        // reverse_bytes: (&[u8]) -> Vec<u8> = BYTE_ARRAY
        assertEquals(KneType.BYTE_ARRAY, methods["reverse_bytes"]?.returnType)
    }

    // --- Point class ---

    @Test
    fun `parses Point struct`() {
        val point = module.classes.find { it.simpleName == "Point" }
        assertNotNull("Point class should exist", point)
    }

    @Test
    fun `Point constructor takes x and y`() {
        val point = module.classes.first { it.simpleName == "Point" }
        assertEquals(2, point.constructor.params.size)
        assertEquals(KneType.INT, point.constructor.params[0].type)
        assertEquals(KneType.INT, point.constructor.params[1].type)
    }

    // --- Operation enum ---

    @Test
    fun `parses Operation enum with 3 variants`() {
        val op = module.enums.find { it.simpleName == "Operation" }
        assertNotNull("Operation enum should exist", op)
        assertEquals(3, op!!.entries.size)
        assertTrue(op.entries.containsAll(listOf("Add", "Subtract", "Multiply")))
    }

    // --- Top-level functions ---

    @Test
    fun `parses compute function`() {
        val compute = module.functions.find { it.name == "compute" }
        assertNotNull(compute)
        assertEquals(3, compute!!.params.size)
        assertEquals(KneType.INT, compute.returnType)
    }

    @Test
    fun `parses greet function`() {
        val greet = module.functions.find { it.name == "greet" }
        assertNotNull(greet)
        assertEquals(KneType.STRING, greet!!.params[0].type)
        assertEquals(KneType.STRING, greet.returnType)
    }

    @Test
    fun `parses find_max with Option return`() {
        val findMax = module.functions.find { it.name == "find_max" }
        assertNotNull(findMax)
        assertTrue(findMax!!.returnType is KneType.NULLABLE)
        assertEquals(KneType.INT, (findMax.returnType as KneType.NULLABLE).inner)
    }

    // --- Slice param tests ---

    @Test
    fun `Calculator has sum_bytes method with BYTE_ARRAY param`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val sumBytes = calc.methods.find { it.name == "sum_bytes" }
        assertNotNull(sumBytes)
        assertEquals(1, sumBytes!!.params.size)
        assertEquals(KneType.BYTE_ARRAY, sumBytes.params[0].type)
        assertEquals(KneType.INT, sumBytes.returnType)
    }

    @Test
    fun `Calculator has reverse_bytes method`() {
        val calc = module.classes.first { it.simpleName == "Calculator" }
        val revBytes = calc.methods.find { it.name == "reverse_bytes" }
        assertNotNull(revBytes)
        assertEquals(KneType.BYTE_ARRAY, revBytes!!.params[0].type)
        assertEquals(KneType.BYTE_ARRAY, revBytes.returnType)
    }

    @Test
    fun `parses sum_all with slice param`() {
        val sumAll = module.functions.find { it.name == "sum_all" }
        assertNotNull(sumAll)
        assertEquals(1, sumAll!!.params.size)
        assertTrue(sumAll.params[0].type is KneType.LIST)
        assertEquals(KneType.INT, (sumAll.params[0].type as KneType.LIST).elementType)
    }

    @Test
    fun `parses find_max with slice param and Option return`() {
        val findMax = module.functions.find { it.name == "find_max" }
        assertNotNull(findMax)
        assertTrue(findMax!!.params[0].type is KneType.LIST)
        assertTrue(findMax.returnType is KneType.NULLABLE)
    }

    // --- Parity checks ---

    @Test
    fun `same number of classes as expected`() {
        // Calculator + Point = 2
        assertEquals(2, module.classes.size)
    }

    @Test
    fun `same number of enums as expected`() {
        // Operation = 1
        assertEquals(1, module.enums.size)
    }
}
