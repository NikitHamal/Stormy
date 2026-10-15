package com.codex.stormy.utils

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * The inspector's correctness rests on these. Each case below is a selector
 * the previous implementation got wrong in a way that silently edited the
 * wrong element.
 */
class SelectorBuilderTest {

    @Test
    fun `id wins and is returned bare so it is document-unique`() {
        // "div#hero" and "#hero" select the same node, but the bare id form is
        // the one that stays correct if the tag changes.
        assertThat(SelectorBuilder.buildSelector("DIV", id = "hero")).isEqualTo("#hero")
    }

    @Test
    fun `tailwind utility classes are kept, not discarded`() {
        // The old isValidClassName() rejected anything containing ':' or '/',
        // which is almost every Tailwind class. With all classes dropped the
        // selector became a bare "div" and edits hit the first div on the page.
        val sel = SelectorBuilder.buildSelector("div", className = "md:flex w-1/2 bg-blue-500")
        assertThat(sel).contains("md\\:flex")
        assertThat(sel).contains("w-1\\/2")
        assertThat(sel).contains("bg-blue-500")
    }

    @Test
    fun `tailwind arbitrary values survive escaping`() {
        val sel = SelectorBuilder.buildSelector("div", className = "bg-[#ffffff]")
        assertThat(sel).contains("bg-\\[\\#ffffff\\]")
    }

    @Test
    fun `state variant classes are dropped as they do not narrow the match`() {
        val classes = SelectorBuilder.splitClasses("card hover:bg-red-500 focus:ring-2")
        assertThat(classes).containsExactly("card")
    }

    @Test
    fun `inspector bookkeeping classes never leak into a selector`() {
        val classes = SelectorBuilder.splitClasses("card __stormy_selected_marker")
        assertThat(classes).containsExactly("card")
    }

    @Test
    fun `duplicate classes are collapsed`() {
        assertThat(SelectorBuilder.splitClasses("btn btn primary")).containsExactly("btn", "primary")
    }

    @Test
    fun `a class-based selector is never claimed to be unique`() {
        // This is the central safety property: only an id form may be treated
        // as addressing exactly one element without checking the DOM.
        val sel = SelectorBuilder.buildSelector("div", className = "card shadow")
        assertThat(SelectorBuilder.isDefinitelyUnique(sel)).isFalse()
        assertThat(SelectorBuilder.isDefinitelyUnique("#hero")).isTrue()
        assertThat(SelectorBuilder.isDefinitelyUnique("#a #b")).isFalse()
    }

    @Test
    fun `class names starting with a digit use a hex escape`() {
        // "\\2col" would be read as the start of a code point, so CSS requires
        // the "\\32 col" form with a terminating space.
        assertThat(SelectorBuilder.escapeIdentifier("2col")).isEqualTo("\\32 col")
    }

    @Test
    fun `unicode class names are left intact`() {
        assertThat(SelectorBuilder.escapeIdentifier("café")).isEqualTo("café")
    }

    @Test
    fun `blank tag falls back to the universal selector`() {
        assertThat(SelectorBuilder.buildSelector("")).isEqualTo("*")
    }

    @Test
    fun `selector path uses child combinators`() {
        assertThat(SelectorBuilder.buildSelectorPath(listOf("#root", "main"), "p"))
            .isEqualTo("#root > main > p")
    }

    @Test
    fun `parse reads back the last segment of a path`() {
        val parsed = SelectorBuilder.parseSelector("#root > div.card.shadow")
        assertThat(parsed.tagName).isEqualTo("div")
        assertThat(parsed.classes).containsExactly("card", "shadow")
    }

    @Test
    fun `injected javascript defines the helper the page calls`() {
        // PreviewActivity invokes __codexUniqueSelector(el); if the helper is
        // ever renamed without updating the call site the inspector silently
        // falls back to guessed selectors.
        assertThat(SelectorBuilder.BUILD_UNIQUE_SELECTOR_JS)
            .contains("function __codexUniqueSelector")
        assertThat(SelectorBuilder.BUILD_UNIQUE_SELECTOR_JS).contains("nth-of-type")
        assertThat(SelectorBuilder.BUILD_UNIQUE_SELECTOR_JS).contains("length === 1")
    }
}
