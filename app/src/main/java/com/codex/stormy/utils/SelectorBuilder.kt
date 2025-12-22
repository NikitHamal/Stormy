package com.codex.stormy.utils

/**
 * Utility for building CSS selectors from element data.
 * Provides consistent selector generation across the app for
 * element inspection, editing, and manipulation.
 */
object SelectorBuilder {

    /**
     * Build a CSS selector for an element with the given properties.
     * Prioritizes ID, then classes, then tag name for specificity.
     *
     * @param tagName The HTML tag name (e.g., "div", "p", "img")
     * @param id The element's ID attribute (optional)
     * @param className The element's class attribute (space-separated, optional)
     * @param maxClasses Maximum number of classes to include in selector (default: 2)
     * @return A CSS selector string
     */
    fun buildSelector(
        tagName: String,
        id: String? = null,
        className: String? = null,
        maxClasses: Int = 2
    ): String {
        val normalizedTag = tagName.lowercase()

        // ID selector takes precedence (most specific)
        if (!id.isNullOrBlank()) {
            return "$normalizedTag#$id"
        }

        // Class selector with limited classes for robustness
        if (!className.isNullOrBlank()) {
            val classes = className.split("\\s+".toRegex())
                .filter { it.isNotBlank() && isValidClassName(it) }
                .take(maxClasses)

            if (classes.isNotEmpty()) {
                return normalizedTag + classes.joinToString("") { ".$it" }
            }
        }

        // Fallback to just tag name
        return normalizedTag
    }

    /**
     * Build a more specific selector using nth-child if needed
     *
     * @param tagName The HTML tag name
     * @param id The element's ID
     * @param className The element's classes
     * @param childIndex The element's index among siblings (1-based)
     * @return A more specific CSS selector
     */
    fun buildSpecificSelector(
        tagName: String,
        id: String? = null,
        className: String? = null,
        childIndex: Int? = null
    ): String {
        val baseSelector = buildSelector(tagName, id, className)

        // If we have ID, it's already specific enough
        if (!id.isNullOrBlank()) {
            return baseSelector
        }

        // Add nth-child for extra specificity if available
        if (childIndex != null && childIndex > 0) {
            return "$baseSelector:nth-child($childIndex)"
        }

        return baseSelector
    }

    /**
     * Build a selector path from root to element (for complex selection)
     *
     * @param ancestors List of ancestor selectors from root to parent
     * @param elementSelector The element's own selector
     * @return A descendant selector path
     */
    fun buildSelectorPath(
        ancestors: List<String>,
        elementSelector: String
    ): String {
        if (ancestors.isEmpty()) {
            return elementSelector
        }
        return (ancestors + elementSelector).joinToString(" > ")
    }

    /**
     * Parse a selector string to extract components
     *
     * @param selector CSS selector string
     * @return SelectorComponents with parsed parts
     */
    fun parseSelector(selector: String): SelectorComponents {
        var remaining = selector.trim()

        // Extract tag name
        val tagMatch = TAG_PATTERN.find(remaining)
        val tagName = tagMatch?.value ?: "*"
        remaining = remaining.removePrefix(tagName)

        // Extract ID
        val idMatch = ID_PATTERN.find(remaining)
        val id = idMatch?.groupValues?.get(1)
        if (id != null) {
            remaining = remaining.replace("#$id", "")
        }

        // Extract classes
        val classes = mutableListOf<String>()
        var classMatch = CLASS_PATTERN.find(remaining)
        while (classMatch != null) {
            classes.add(classMatch.groupValues[1])
            remaining = remaining.replaceFirst(".${classMatch.groupValues[1]}", "")
            classMatch = CLASS_PATTERN.find(remaining)
        }

        return SelectorComponents(
            tagName = tagName,
            id = id,
            classes = classes
        )
    }

    /**
     * Check if a class name is valid (not a utility class we want to avoid)
     */
    private fun isValidClassName(className: String): Boolean {
        // Filter out common utility classes that might change
        val utilityPrefixes = listOf(
            "hover:", "focus:", "active:", "visited:",
            "md:", "lg:", "xl:", "sm:", "xs:",
            "dark:", "light:",
            "transition", "animate"
        )

        return className.isNotBlank() &&
                !utilityPrefixes.any { className.startsWith(it) } &&
                !className.startsWith("--") && // CSS variables
                className.matches(VALID_CLASS_PATTERN)
    }

    /**
     * Escape special CSS characters in a selector
     */
    fun escapeSelector(selector: String): String {
        return selector.replace(ESCAPE_PATTERN) { match ->
            "\\${match.value}"
        }
    }

    /**
     * Data class for parsed selector components
     */
    data class SelectorComponents(
        val tagName: String,
        val id: String? = null,
        val classes: List<String> = emptyList()
    ) {
        fun toSelector(): String = buildSelector(tagName, id, classes.joinToString(" "))
    }

    // Regex patterns (pre-compiled for performance)
    private val TAG_PATTERN = Regex("^[a-zA-Z][a-zA-Z0-9]*")
    private val ID_PATTERN = Regex("#([a-zA-Z_-][a-zA-Z0-9_-]*)")
    private val CLASS_PATTERN = Regex("\\.([a-zA-Z_-][a-zA-Z0-9_-]*)")
    private val VALID_CLASS_PATTERN = Regex("^[a-zA-Z_-][a-zA-Z0-9_-]*$")
    private val ESCAPE_PATTERN = Regex("[!\"#$%&'()*+,./:;<=>?@\\[\\]^`{|}~]")
}
