package io.github.kdroidfilter.kotlinnativeexport.plugin.analysis

import io.github.kdroidfilter.kotlinnativeexport.plugin.ir.KneModule
import java.io.File

/**
 * Parses Kotlin source files to extract public API declarations into a [KneModule] IR.
 *
 * Two implementations exist:
 * - [RegexSourceParser]: regex-based, no external dependencies (fallback)
 * - PsiSourceParser: AST-based via kotlin-compiler-embeddable (default)
 */
interface SourceParser {
    fun parse(files: Collection<File>, libName: String, commonFiles: Collection<File> = emptyList()): KneModule
}
