package io.github.kdroidfilter.nucleusnativeaccess.plugin.ir

import java.io.Serializable

data class KneModule(
    val libName: String,
    val packages: Set<String>,
    val classes: List<KneClass>,
    val dataClasses: List<KneDataClass>,
    val enums: List<KneEnum>,
    val functions: List<KneFunction>,
    val interfaces: List<KneInterface> = emptyList(),
    val sealedEnums: List<KneSealedEnum> = emptyList(),
) : Serializable

data class KneDataClass(
    val simpleName: String,
    val fqName: String,
    val fields: List<KneParam>,
    val isCommon: Boolean = false,
) : Serializable

data class KneInterface(
    val simpleName: String,
    val fqName: String,
    val methods: List<KneFunction>,
    val properties: List<KneProperty>,
    val superInterfaces: List<String> = emptyList(),
    val isCommon: Boolean = false,
) : Serializable

data class KneClass(
    val simpleName: String,
    val fqName: String,
    val constructor: KneConstructor,
    val methods: List<KneFunction>,
    val properties: List<KneProperty>,
    val companionMethods: List<KneFunction> = emptyList(),
    val companionProperties: List<KneProperty> = emptyList(),
    val isOpen: Boolean = false,
    val isAbstract: Boolean = false,
    val isSealed: Boolean = false,
    val superClass: String? = null,
    val interfaces: List<String> = emptyList(),
    val sealedSubclasses: List<String> = emptyList(),
    val isCommon: Boolean = false,
    val isOpaque: Boolean = false,
    val rustTypeName: String = simpleName,
) : Serializable

data class KneEnum(
    val simpleName: String,
    val fqName: String,
    val entries: List<String>,
) : Serializable

/**
 * Rust enum with data variants → Kotlin sealed class.
 * Each variant may carry fields (tuple or struct variant) or be a unit variant (no fields).
 */
data class KneSealedEnum(
    val simpleName: String,
    val fqName: String,
    val variants: List<KneSealedVariant>,
) : Serializable

data class KneSealedVariant(
    val name: String,
    val fields: List<KneParam>, // empty for unit variants
    val isTuple: Boolean = false, // true for tuple variants like Value(i32)
) : Serializable

enum class KneConstructorKind : Serializable {
    FUNCTION,
    STRUCT_LITERAL,
    NONE,
}

data class KneConstructor(
    val params: List<KneParam>,
    val kind: KneConstructorKind = KneConstructorKind.FUNCTION,
    val canFail: Boolean = false,
) : Serializable

enum class KneReceiverKind : Serializable {
    NONE,
    BORROWED_SHARED,
    BORROWED_MUT,
    OWNED,
}

data class KneFunction(
    val name: String,
    val params: List<KneParam>,
    val returnType: KneType,
    val isSuspend: Boolean = false,
    val isExtension: Boolean = false,
    val receiverType: KneType? = null,
    val isOverride: Boolean = false,
    /** True if the method takes `&mut self` (Rust). Used by Rust bridge generator. */
    val isMutating: Boolean = false,
    val receiverKind: KneReceiverKind = KneReceiverKind.NONE,
    val canFail: Boolean = false,
    val returnsBorrowed: Boolean = false,
    val returnRustType: String? = null,
    val isUnsafe: Boolean = false,
    /** Rust expression suffix for `impl Trait` return conversion (e.g. `.collect::<Vec<_>>()`). */
    val returnConversion: String? = null,
) : Serializable

data class KneProperty(
    val name: String,
    val type: KneType,
    val mutable: Boolean,
    val isOverride: Boolean = false,
) : Serializable

data class KneParam(
    val name: String,
    val type: KneType,
    val hasDefault: Boolean = false,
    /** True if this param is borrowed (`&str`, `&T`) in Rust. Affects bridge codegen. */
    val isBorrowed: Boolean = false,
    /** Best-effort original Rust type hint used by bridge generation for casts and ownership. */
    val rustType: String? = null,
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
    object NEVER : KneType()
    data class OBJECT(val fqName: String, val simpleName: String) : KneType()
    data class INTERFACE(val fqName: String, val simpleName: String) : KneType()
    data class ENUM(val fqName: String, val simpleName: String) : KneType()
    data class SEALED_ENUM(val fqName: String, val simpleName: String) : KneType()
    data class NULLABLE(val inner: KneType) : KneType()
    data class FUNCTION(val paramTypes: List<KneType>, val returnType: KneType) : KneType()
    data class DATA_CLASS(val fqName: String, val simpleName: String, val fields: List<KneParam>) : KneType()
    object BYTE_ARRAY : KneType()
    data class LIST(val elementType: KneType) : KneType()
    data class SET(val elementType: KneType) : KneType()
    data class MAP(val keyType: KneType, val valueType: KneType) : KneType()
    data class FLOW(val elementType: KneType) : KneType()
    data class TUPLE(val elementTypes: List<KneType>) : KneType() {
        val typeId: String get() {
            fun go(sb: StringBuilder, t: KneType) {
                when (t) {
                    is TUPLE -> { sb.append("T"); t.elementTypes.forEach { go(sb, it) } }
                    UNIT -> sb.append("U"); BOOLEAN -> sb.append("Z"); BYTE -> sb.append("B")
                    SHORT -> sb.append("S"); INT -> sb.append("I"); LONG -> sb.append("J")
                    FLOAT -> sb.append("F"); DOUBLE -> sb.append("D"); STRING -> sb.append("R")
                    BYTE_ARRAY -> sb.append("Y")
                    is ENUM -> sb.append("E${t.simpleName}")
                    is OBJECT -> sb.append("O${t.simpleName}")
                    is DATA_CLASS -> sb.append("D${t.simpleName}")
                    is LIST -> { sb.append("L"); go(sb, t.elementType) }
                    is SET -> { sb.append("S"); go(sb, t.elementType) }
                    is MAP -> { sb.append("M"); go(sb, t.keyType); go(sb, t.valueType) }
                    is FLOW -> { sb.append("W"); go(sb, t.elementType) }
                    is NULLABLE -> { sb.append("N"); go(sb, t.inner) }
                    is FUNCTION -> { sb.append("F"); t.paramTypes.forEach { go(sb, it) }; sb.append("X"); go(sb, t.returnType) }
                    is INTERFACE -> sb.append("I${t.simpleName}")
                    is SEALED_ENUM -> sb.append("K${t.simpleName}")
                    NEVER -> sb.append("V")
                }
            }
            return buildString { go(this, this@TUPLE) }
        }
    }

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
            NEVER -> "" // void — diverging type never returns
            is OBJECT -> "JAVA_LONG" // opaque handle
            is INTERFACE -> "JAVA_LONG" // opaque handle (same as OBJECT)
            is SEALED_ENUM -> "JAVA_LONG" // opaque handle
            is ENUM -> "JAVA_INT" // ordinal
            is NULLABLE -> when (inner) {
                STRING -> "ADDRESS"
                BOOLEAN, is ENUM -> "JAVA_INT"
                SHORT, BYTE -> "JAVA_INT" // widened for sentinel
                INT, LONG, FLOAT, DOUBLE -> "JAVA_LONG" // widened or raw bits
                is OBJECT, is INTERFACE, is SEALED_ENUM -> "JAVA_LONG"
                else -> inner.ffmLayout
            }
            is FUNCTION -> "JAVA_LONG" // function pointer address
            is DATA_CLASS -> "ADDRESS" // fields are expanded, not used directly
            BYTE_ARRAY -> "ADDRESS" // pointer to byte buffer + JAVA_INT size
            is LIST -> "ADDRESS" // pointer to element array + JAVA_INT size
            is SET -> "ADDRESS"  // same encoding as LIST
            is MAP -> "ADDRESS"  // keys pointer (+ values pointer + size handled in expansion)
            is FLOW -> "" // void — result delivered via callbacks (like suspend)
            is TUPLE -> "ADDRESS" // pointer to tuple data on heap
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
            NEVER -> "Nothing"
            is OBJECT -> simpleName
            is INTERFACE -> simpleName
            is SEALED_ENUM -> simpleName
            is ENUM -> simpleName
            is NULLABLE -> if (inner is FUNCTION) "(${inner.jvmTypeName})?" else "${inner.jvmTypeName}?"
            is FUNCTION -> "(${paramTypes.joinToString(", ") { it.jvmTypeName }}) -> ${returnType.jvmTypeName}"
            is DATA_CLASS -> simpleName
            BYTE_ARRAY -> "ByteArray"
            is LIST -> "List<${elementType.jvmTypeName}>"
            is SET -> "Set<${elementType.jvmTypeName}>"
            is MAP -> "Map<${keyType.jvmTypeName}, ${valueType.jvmTypeName}>"
            is FLOW -> "kotlinx.coroutines.flow.Flow<${elementType.jvmTypeName}>"
            is TUPLE -> "KneTuple${elementTypes.size}_${typeId}"
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
            NEVER -> "Nothing" // diverging type - never returns
            is OBJECT -> "Long" // opaque handle
            is INTERFACE -> "Long" // opaque handle (same as OBJECT)
            is SEALED_ENUM -> "Long" // opaque handle
            is ENUM -> "Int" // ordinal
            is NULLABLE -> when (inner) {
                STRING -> "CPointer<ByteVar>?"
                BOOLEAN, is ENUM -> "Int" // -1 = null
                SHORT, BYTE -> "Int" // widened, Int.MIN_VALUE = null
                INT, LONG -> "Long" // widened, Long.MIN_VALUE = null
                FLOAT, DOUBLE -> "Long" // raw bits, Long.MIN_VALUE = null
                is OBJECT, is INTERFACE, is SEALED_ENUM -> "Long" // 0L = null
                else -> inner.nativeBridgeType
            }
            is FUNCTION -> "Long" // function pointer address
            is DATA_CLASS -> "Long" // fields are expanded, not used directly
            BYTE_ARRAY -> "CPointer<ByteVar>?" // pointer to bytes
            is LIST -> collectionPointerType(elementType)
            is SET -> collectionPointerType(elementType)
            is MAP -> collectionPointerType(keyType) // keys pointer type; values handled in expansion
            is FLOW -> "Unit" // void — callbacks deliver values
            is TUPLE -> "Long" // pointer to tuple on heap
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
            is ENUM -> "IntVar" // ordinal
            is OBJECT, is INTERFACE, is SEALED_ENUM -> "LongVar" // StableRef handle
            else -> "ByteVar"
        }

    companion object {
        /** Native pointer type for a collection element (e.g. CPointer<IntVar>? for Int elements). */
        fun collectionPointerType(elemType: KneType): String = when (elemType) {
            INT, BOOLEAN -> "CPointer<IntVar>?"
            LONG -> "CPointer<LongVar>?"
            DOUBLE -> "CPointer<DoubleVar>?"
            FLOAT -> "CPointer<FloatVar>?"
            SHORT -> "CPointer<ShortVar>?"
            BYTE -> "CPointer<ByteVar>?"
            STRING -> "CPointer<ByteVar>?" // packed null-terminated
            BYTE_ARRAY -> "CPointer<LongVar>?" // StableRef handles
            is ENUM -> "CPointer<IntVar>?" // ordinals
            is OBJECT, is INTERFACE, is SEALED_ENUM -> "CPointer<LongVar>?" // handles
            is LIST, is SET, is MAP -> "CPointer<LongVar>?" // nested collection handles
            else -> "CPointer<ByteVar>?"
        }

        /** Native element pointer var type (e.g. IntVar for Int). */
        fun collectionElementVarType(elemType: KneType): String = when (elemType) {
            INT, BOOLEAN -> "IntVar"
            LONG -> "LongVar"
            DOUBLE -> "DoubleVar"
            FLOAT -> "FloatVar"
            SHORT -> "ShortVar"
            BYTE, STRING -> "ByteVar"
            is ENUM -> "IntVar"
            is OBJECT, is INTERFACE, is SEALED_ENUM -> "LongVar"
            else -> "ByteVar"
        }

        /** FFM ValueLayout for a collection element. */
        fun collectionElementLayout(elemType: KneType): String = when (elemType) {
            INT, BOOLEAN -> "JAVA_INT"
            LONG -> "JAVA_LONG"
            DOUBLE -> "JAVA_DOUBLE"
            FLOAT -> "JAVA_FLOAT"
            SHORT -> "JAVA_SHORT"
            BYTE -> "JAVA_BYTE"
            STRING -> "JAVA_BYTE" // packed buffer uses byte layout
            is ENUM -> "JAVA_INT"
            is OBJECT, is INTERFACE, is SEALED_ENUM -> "JAVA_LONG"
            BYTE_ARRAY -> "JAVA_LONG" // StableRef handles
            is LIST, is SET, is MAP -> "JAVA_LONG" // nested collection handles
            else -> "JAVA_BYTE"
        }
    }
}
