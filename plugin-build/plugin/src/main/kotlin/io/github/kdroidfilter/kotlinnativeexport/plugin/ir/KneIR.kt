package io.github.kdroidfilter.kotlinnativeexport.plugin.ir

import java.io.Serializable

data class KneModule(
    val libName: String,
    val packages: Set<String>,
    val classes: List<KneClass>,
    val dataClasses: List<KneDataClass>,
    val enums: List<KneEnum>,
    val functions: List<KneFunction>,
) : Serializable

data class KneDataClass(
    val simpleName: String,
    val fqName: String,
    val fields: List<KneParam>,
) : Serializable

data class KneClass(
    val simpleName: String,
    val fqName: String,
    val constructor: KneConstructor,
    val methods: List<KneFunction>,
    val properties: List<KneProperty>,
    val companionMethods: List<KneFunction> = emptyList(),
    val companionProperties: List<KneProperty> = emptyList(),
) : Serializable

data class KneEnum(
    val simpleName: String,
    val fqName: String,
    val entries: List<String>,
) : Serializable

data class KneConstructor(
    val params: List<KneParam>,
) : Serializable

data class KneFunction(
    val name: String,
    val params: List<KneParam>,
    val returnType: KneType,
) : Serializable

data class KneProperty(
    val name: String,
    val type: KneType,
    val mutable: Boolean,
) : Serializable

data class KneParam(
    val name: String,
    val type: KneType,
) : Serializable

sealed class KneType : Serializable {
    object INT : KneType()
    object LONG : KneType()
    object DOUBLE : KneType()
    object FLOAT : KneType()
    object BOOLEAN : KneType()
    object BYTE : KneType()
    object SHORT : KneType()
    object STRING : KneType()
    object UNIT : KneType()
    data class OBJECT(val fqName: String, val simpleName: String) : KneType()
    data class ENUM(val fqName: String, val simpleName: String) : KneType()
    data class NULLABLE(val inner: KneType) : KneType()
    data class FUNCTION(val paramTypes: List<KneType>, val returnType: KneType) : KneType()
    data class DATA_CLASS(val fqName: String, val simpleName: String, val fields: List<KneParam>) : KneType()

    /** The FFM ValueLayout constant name for this type. */
    val ffmLayout: String
        get() = when (this) {
            INT -> "JAVA_INT"
            LONG -> "JAVA_LONG"
            DOUBLE -> "JAVA_DOUBLE"
            FLOAT -> "JAVA_FLOAT"
            BOOLEAN -> "JAVA_INT" // 0/1
            BYTE -> "JAVA_BYTE"
            SHORT -> "JAVA_SHORT"
            STRING -> "ADDRESS" // char* (input) or output buffer pattern (return)
            UNIT -> "" // void — used with FunctionDescriptor.ofVoid(...)
            is OBJECT -> "JAVA_LONG" // opaque handle
            is ENUM -> "JAVA_INT" // ordinal
            is NULLABLE -> when (inner) {
                STRING -> "ADDRESS"
                BOOLEAN, is ENUM -> "JAVA_INT"
                SHORT, BYTE -> "JAVA_INT" // widened for sentinel
                INT, LONG, FLOAT, DOUBLE -> "JAVA_LONG" // widened or raw bits
                is OBJECT -> "JAVA_LONG"
                else -> inner.ffmLayout
            }
            is FUNCTION -> "JAVA_LONG" // function pointer address
            is DATA_CLASS -> "ADDRESS" // fields are expanded, not used directly
        }

    /** Kotlin/JVM type name as it appears in generated JVM code. */
    val jvmTypeName: String
        get() = when (this) {
            INT -> "Int"
            LONG -> "Long"
            DOUBLE -> "Double"
            FLOAT -> "Float"
            BOOLEAN -> "Boolean"
            BYTE -> "Byte"
            SHORT -> "Short"
            STRING -> "String"
            UNIT -> "Unit"
            is OBJECT -> simpleName
            is ENUM -> simpleName
            is NULLABLE -> "${inner.jvmTypeName}?"
            is FUNCTION -> "(${paramTypes.joinToString(", ") { it.jvmTypeName }}) -> ${returnType.jvmTypeName}"
            is DATA_CLASS -> simpleName
        }

    /** Kotlin/Native type used in the @CName bridge function signature. */
    val nativeBridgeType: String
        get() = when (this) {
            INT -> "Int"
            LONG -> "Long"
            DOUBLE -> "Double"
            FLOAT -> "Float"
            BOOLEAN -> "Int" // 0/1 for clarity
            BYTE -> "Byte"
            SHORT -> "Short"
            STRING -> "CPointer<ByteVar>?" // null-terminated char*
            UNIT -> "Unit"
            is OBJECT -> "Long" // opaque handle
            is ENUM -> "Int" // ordinal
            is NULLABLE -> when (inner) {
                STRING -> "CPointer<ByteVar>?"
                BOOLEAN, is ENUM -> "Int" // -1 = null
                SHORT, BYTE -> "Int" // widened, Int.MIN_VALUE = null
                INT, LONG -> "Long" // widened, Long.MIN_VALUE = null
                FLOAT, DOUBLE -> "Long" // raw bits, Long.MIN_VALUE = null
                is OBJECT -> "Long" // 0L = null
                else -> inner.nativeBridgeType
            }
            is FUNCTION -> "Long" // function pointer address
            is DATA_CLASS -> "Long" // fields are expanded, not used directly
        }

    /** The native pointer type for out-param usage (e.g. IntVar for Int). */
    val nativePointerType: String
        get() = when (this) {
            INT -> "IntVar"
            LONG -> "LongVar"
            DOUBLE -> "DoubleVar"
            FLOAT -> "FloatVar"
            BOOLEAN -> "IntVar"
            BYTE -> "ByteVar"
            SHORT -> "ShortVar"
            else -> "ByteVar"
        }
}
