package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PageMetricsTest {
    @Test
    fun `small targets and text are named, not just counted`() {
        val raw = """{"vw":390,"sw":390,"sh":9342,"wide":[],"smallTargets":8,
            "smallTargetsAt":["a.nav-link \"Menu\" 52x22","button#theme 32x32"],
            "smallText":2,"smallTextAt":["span.badge \"New\" 11px"],
            "lowContrast":1,"lowContrastAt":["a.btn \"Reserve a Table\" 1.9:1"],"brokenImages":0,"hiddenShown":0,"title":"Himalayan Brew"}"""
        val text = PageMetrics.parse(raw)!!.describe()
        assertThat(text).contains("8 tap targets too small for a finger (aim for 40x40px, never under 32px), e.g. a.nav-link \"Menu\" 52x22, button#theme 32x32.")
        assertThat(text).contains("2 text elements under 12px, e.g. span.badge \"New\" 11px.")
        assertThat(text).contains("1 text elements with low contrast, e.g. a.btn \"Reserve a Table\" 1.9:1.")
        assertThat(text).doesNotContain("OVERFLOW")
        assertThat(text).endsWith("Title: Himalayan Brew")
    }

    @Test
    fun `older measurements without examples keep the plain wording`() {
        val text = PageMetrics.parse("""{"vw":390,"sw":780,"sh":900,"wide":["div.hero (780px wide, right edge at 780px)"],"smallTargets":3,"brokenImages":1,"hiddenShown":2}""")!!.describe()
        assertThat(text).contains("HORIZONTAL OVERFLOW")
        assertThat(text).contains("Elements reaching past the right edge: div.hero (780px wide, right edge at 780px)")
        assertThat(text).doesNotContain("No element box")
        assertThat(text).contains("3 tap targets too small for a finger (aim for 40x40px, never under 32px).")
        assertThat(text).contains("1 images failed to load.")
        assertThat(text).contains("2 elements have the hidden attribute but are displayed")
        assertThat(PageMetrics.parse("""{"vw":390,"sw":520,"sh":900}""")!!.describe()).contains("look for ::before/::after decorations")
        assertThat(PageMetrics.parse("not json")).isNull()
    }
}
