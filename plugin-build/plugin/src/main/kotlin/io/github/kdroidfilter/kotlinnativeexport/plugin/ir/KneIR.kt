package io.github.kdroidfilter.kotlinnativeexport.plugin.ir

import java.io.Serializable

data class KneModule(
    val libName: String,
    val packages: Set<String>,
    val classes: List<KneClass>,
    val functions: List<KneFunction>,
) : Serializable

data class KneClass(
    val simpleName: String,
    val fqName: String,
    val constructor: KneConstructor,
    val methods: List<KneFunction>,
    val properties: List<KneProperty>,
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
        }
}
