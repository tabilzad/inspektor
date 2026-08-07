package io.github.tabilzad.ktor

/**
 * A class KDoc split into its free-form text and its `@property`/`@param` tag entries.
 *
 * Data classes are commonly documented with `@property name description` tags on the class
 * KDoc instead of inline KDocs on each constructor property; those tags map to per-field
 * schema descriptions while the text before the first tag is the class description.
 */
internal data class ParsedKDoc(
    val text: String?,
    val propertyDocs: Map<String, String>
)

private val PROPERTY_TAG = Regex("^@(?:property|param)\\s+(\\S+)\\s*(.*)$")

/**
 * Parses sanitized KDoc content (as produced by [getKDocComments]) into [ParsedKDoc].
 *
 * - Lines before the first `@tag` form the class description.
 * - `@property foo ...` / `@param foo ...` start a field entry; subsequent non-tag lines are
 *   continuations of that entry (multi-line descriptions are joined with single spaces).
 * - Any other `@tag` (`@throws`, `@see`, ...) ends the current entry and is ignored.
 */
@Suppress("CyclomaticComplexMethod")
internal fun parseKDoc(sanitized: String?): ParsedKDoc {
    if (sanitized.isNullOrBlank()) return ParsedKDoc(null, emptyMap())

    val textLines = mutableListOf<String>()
    val propertyDocs = mutableMapOf<String, String>()

    var currentName: String? = null
    val currentLines = mutableListOf<String>()
    var seenTag = false

    fun flush() {
        val name = currentName ?: return
        val doc = currentLines.joinToString(" ").trim()
        if (doc.isNotEmpty()) propertyDocs[name] = doc
        currentName = null
        currentLines.clear()
    }

    for (line in sanitized.lineSequence()) {
        val trimmed = line.trim()
        val tagMatch = PROPERTY_TAG.find(trimmed)
        when {
            tagMatch != null -> {
                flush()
                seenTag = true
                currentName = tagMatch.groupValues[1]
                if (tagMatch.groupValues[2].isNotBlank()) currentLines += tagMatch.groupValues[2].trim()
            }

            trimmed.startsWith("@") -> {
                flush()
                seenTag = true
            }

            currentName != null -> if (trimmed.isNotEmpty()) currentLines += trimmed

            !seenTag -> textLines += line
        }
    }
    flush()

    return ParsedKDoc(
        text = textLines.joinToString("\n").trim().ifBlank { null },
        propertyDocs = propertyDocs
    )
}
