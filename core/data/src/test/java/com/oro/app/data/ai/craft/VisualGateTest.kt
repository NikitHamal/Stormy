package com.oro.app.data.ai.craft

import com.google.common.truth.Truth.assertThat
import com.oro.app.data.preview.PageMetrics
import org.junit.Test

class VisualGateTest {

    private val files = setOf("index.html", "about.html", "styles.css", "app.js")

    @Test
    fun `changed pages are rendered, the main page first, and styles or scripts render the main page`() {
        assertThat(VisualGate.pagesFor(listOf("about.html", "./index.html")) { it in files }).containsExactly("index.html", "about.html").inOrder()
        assertThat(VisualGate.pagesFor(listOf("styles.css")) { it in files }).containsExactly("index.html")
        assertThat(VisualGate.pagesFor(listOf("app.js", "styles.css")) { it in files }).containsExactly("index.html")
        assertThat(VisualGate.pagesFor(listOf("db.json", "README.md")) { it in files }).isEmpty()
        // A deleted page is not rendered.
        assertThat(VisualGate.pagesFor(listOf("gone.html")) { it in files }).isEmpty()
        assertThat(VisualGate.pagesFor(listOf("a.html", "b.html", "c.html")) { true }).hasSize(VisualGate.MAX_PAGES)
    }

    /** The metrics text the previewer really produces, so this test breaks if its wording drifts. */
    private fun metrics(overflow: Boolean = false, broken: Int = 0, hidden: Int = 0, small: Int = 0, contrast: Int = 0) = PageMetrics(
        viewportWidth = 390, scrollWidth = if (overflow) 520 else 390, scrollHeight = 2400,
        wide = if (overflow) listOf("div.hero-art 520px") else emptyList(),
        smallTargets = small, smallTargetExamples = if (small > 0) listOf("a.icon \"\" 24x24") else emptyList(),
        lowContrast = contrast, lowContrastExamples = if (contrast > 0) listOf("button.cta \"Book\" 2.1:1") else emptyList(),
        brokenImages = broken, hiddenShown = hidden, title = "Himalayan Brew",
    ).describe()

    @Test
    fun `hard defects are overflow, broken images and hidden elements shown`() {
        assertThat(VisualGate.hardDefects(metrics())).isEmpty()
        assertThat(VisualGate.hardDefects(metrics(small = 3, contrast = 2))).isEmpty()
        val bad = VisualGate.hardDefects(metrics(overflow = true, broken = 2, hidden = 1))
        assertThat(bad).hasSize(4)
        assertThat(bad[0]).startsWith("HORIZONTAL OVERFLOW")
        assertThat(bad.any { it.contains("div.hero-art") }).isTrue()
        assertThat(bad.any { it.startsWith("2 images failed to load") }).isTrue()
        assertThat(bad.any { it.contains("hidden attribute") }).isTrue()
    }

    @Test
    fun `text under 2 to 1 contrast is a hard defect, ordinary low contrast a note`() {
        fun contrast(vararg examples: String) = PageMetrics(
            viewportWidth = 1280, scrollWidth = 1280, scrollHeight = 3000,
            lowContrast = examples.size, lowContrastExamples = examples.toList(),
        ).describe()
        assertThat(VisualGate.hardDefects(contrast("p.lead \"Small-batch\" 3.9:1", "span.tag \"New\" 2.4:1"))).isEmpty()
        val bad = VisualGate.hardDefects(contrast("a.btn.btn-primary \"Book a table\" 1.5:1", "p.lead \"Small\" 3.9:1"))
        assertThat(bad).hasSize(1)
        assertThat(bad[0]).startsWith("Text almost invisible")
        assertThat(bad[0]).contains("a.btn.btn-primary \"Book a table\" 1.5:1")
        assertThat(bad[0]).doesNotContain("p.lead")
        val review = VisualGate.review(mapOf("index.html (desktop)" to contrast("a.btn \"Book\" 1.2:1")))!!
        assertThat(review).contains("index.html (desktop): Text almost invisible")
    }

    @Test
    fun `the final check sends back only hard defects`() {
        assertThat(VisualGate.review(mapOf("index.html" to metrics(small = 4, contrast = 3)))).isNull()
        assertThat(VisualGate.review(mapOf("index.html" to "(could not measure)"))).isNull()
        assertThat(VisualGate.review(emptyMap())).isNull()
        val review = VisualGate.review(mapOf("index.html" to metrics(overflow = true), "about.html" to metrics(broken = 1)))!!
        assertThat(review).startsWith("Automatic quality review (by the Oro app, not the user) rendered")
        assertThat(review).contains("index.html: HORIZONTAL OVERFLOW")
        assertThat(review).contains("about.html: 1 images failed to load")
    }

    @Test
    fun `the round note reports every rendered page, clean ones in one line`() {
        val clean = VisualGate.roundNote(mapOf("index.html" to metrics()))!!
        assertThat(clean).startsWith("[Oro: ")
        assertThat(clean).contains("index.html: no layout problems measured.")
        assertThat(clean).doesNotContain("Title:")
        val soft = VisualGate.roundNote(mapOf("index.html" to metrics(contrast = 2)))!!
        assertThat(soft).contains("low contrast")
        assertThat(soft).doesNotContain("Fix the overflow")
        assertThat(VisualGate.hardDefects(metrics(contrast = 2))).isEmpty()
        val hard = VisualGate.roundNote(mapOf("index.html" to metrics(overflow = true), "about.html" to metrics()))!!
        assertThat(hard).contains("changed pages")
        assertThat(hard).contains("Fix the overflow, broken images, hidden elements or invisible text first.")
        assertThat(VisualGate.roundNote(mapOf("index.html" to ""))).isNull()
    }
}
