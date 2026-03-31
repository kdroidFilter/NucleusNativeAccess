package com.example.calculator

data class Point(val x: Int, val y: Int)

data class NamedValue(val name: String, val value: Int)

data class TaggedPoint(val point: Point, val tag: Operation)

data class Rect(val topLeft: Point, val bottomRight: Point)

data class CalculatorSnapshot(val calc: Calculator, val label: String)

data class Config(val origin: Point, val scale: Int)

data class Style(val bold: Boolean, val color: Int)

data class StyledPoint(val point: Point, val style: Style)

data class TaggedRect(val rect: Rect, val tag: Operation, val name: String)

data class DeepNested(val tagged: TaggedPoint, val style: Style, val scale: Double)

data class Address(val street: String, val city: String)

data class Person(val name: String, val age: Int, val address: Address)

data class TaggedList(val label: String, val scores: List<Int>)

data class MetadataHolder(val name: String, val metadata: Map<String, Int>)

data class MultiCollDC(val tags: List<String>, val flags: List<Boolean>, val counts: List<Int>)
