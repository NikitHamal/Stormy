package com.oro.app.browser

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** Snapshot JSON → PageState → the listing the model reads, and change detection. */
class PageModelTest {

    private val snapshotJson = """
    {
      "ok": true,
      "url": "https://shop.example/cart",
      "title": "Your cart",
      "scrollY": 120, "pageHeight": 4200, "viewportW": 412, "viewportH": 915,
      "loading": false,
      "forms": 1,
      "consoleErrors": 2,
      "frames": [
        {"id": 1, "src": "https://www.google.com/recaptcha/api2/anchor?x=1", "crossOrigin": true, "control": false, "humanCheck": true}
      ],
      "humanCheck": {"kind": "recaptcha", "passed": false},
      "elements": [
        {"i":1,"r":"link","t":"Home","href":"https://shop.example/","x":8,"y":20,"w":60,"h":30,"oid":"o1","sel":"#home","f":0},
        {"i":2,"r":"textbox","t":"Email","x":8,"y":100,"w":300,"h":48,"oid":"o2","sel":"input#email","f":0,"ed":true,"val":"","itype":"email"},
        {"i":3,"r":"textbox","t":"Password","x":8,"y":160,"w":300,"h":48,"oid":"o3","sel":"input#pass","f":0,"ed":true,"pw":true,"hasValue":true,"itype":"password"},
        {"i":4,"r":"checkbox","t":"Remember me","x":8,"y":220,"w":24,"h":24,"oid":"o4","sel":".remember","f":0,"chk":true},
        {"i":5,"r":"combobox","t":"Country","x":8,"y":280,"w":300,"h":48,"oid":"o5","sel":"select#c","f":0,"opt":12,"val":"India"},
        {"i":6,"r":"button","t":"Place order","x":8,"y":360,"w":300,"h":48,"oid":"o6","sel":"button.buy","f":0,"dis":true}
      ],
      "text": "# Your cart\n\nTwo items, total 42 dollars."
    }
    """.trimIndent()

    @Test
    fun `a snapshot parses into a page the model can read`() {
        val page = PageModel.parse(snapshotJson)
        assertThat(page).isNotNull()
        page!!
        assertThat(page.url).isEqualTo("https://shop.example/cart")
        assertThat(page.title).isEqualTo("Your cart")
        assertThat(page.elements).hasSize(6)
        assertThat(page.consoleErrors).isEqualTo(2)
        assertThat(page.canScrollDown).isTrue()

        val email = page.element(2)!!
        assertThat(email.editable).isTrue()
        assertThat(email.password).isFalse()

        val pass = page.element(3)!!
        assertThat(pass.password).isTrue()
        // A password's value never crosses into the model's world: filled, not shown.
        assertThat(pass.value).isEqualTo("•")
        assertThat(pass.value).doesNotContain("hunter")

        val remember = page.element(4)!!
        assertThat(remember.checked).isTrue()

        val country = page.element(5)!!
        assertThat(country.options).isEqualTo(12)
        assertThat(country.selected).isEqualTo("India")

        assertThat(page.element(6)!!.disabled).isTrue()
    }

    @Test
    fun `a human check in a cross-origin frame is seen and marked for the user`() {
        val page = PageModel.parse(snapshotJson)!!
        assertThat(page.humanCheck).isEqualTo(HumanCheckKind.RECAPTCHA)
        assertThat(page.needsHuman).isTrue()
        assertThat(page.frames.single().crossOrigin).isTrue()
        assertThat(page.frames.single().humanCheck).isTrue()
        assertThat(page.render()).contains("HUMAN CHECK")
        assertThat(page.render()).contains("recaptcha")
    }

    @Test
    fun `a passed check is clear`() {
        val passed = PageModel.parse(snapshotJson)!!.copy(humanCheckPassed = true)
        assertThat(passed.needsHuman).isFalse()
    }

    @Test
    fun `the listing renders compact numbered lines`() {
        val page = PageModel.parse(snapshotJson)!!
        val out = page.render()
        assertThat(out).contains(PageState.PAGE_MARKER)
        assertThat(out).contains("Page: Your cart — https://shop.example/cart")
        assertThat(out).contains("[1] link \"Home\"")
        assertThat(out).contains("[2] textbox \"Email\"")
        assertThat(out).contains("[3] textbox \"Password\" (password, filled)")
        assertThat(out).contains("[4] checkbox \"Remember me\" (checked)")
        assertThat(out).contains("[5] combobox \"Country\" (12 options, selected \"India\")")
        assertThat(out).contains("[6] button \"Place order\" (disabled)")
        assertThat(out).contains("Scroll: 120/4200")
        assertThat(out).contains("more below")
        assertThat(out).contains("Console errors: 2")
        assertThat(out).contains("Two items, total 42 dollars.")
    }

    @Test
    fun `the render respects its budget and says what was cut`() {
        val many = (1..200).map {
            PageFixtures.el(it, "link", "Item number $it with a fairly long label to take space", top = it * 50)
        }.toTypedArray()
        val page = PageFixtures.page(*many, text = "x".repeat(5_000))
        val out = page.render(maxChars = 3_000)
        assertThat(out.length).isAtMost(3_600) // listing budget + marker lines, text squeezed out
        assertThat(out).contains("more elements")
    }

    @Test
    fun `the signature moves when the page means something changed, and not for value-only noise`() {
        val base = PageModel.parse(snapshotJson)!!
        // Same page, re-parsed: same signature.
        assertThat(PageModel.parse(snapshotJson)!!.signature).isEqualTo(base.signature)

        // A checked box toggled: changed.
        val toggledElements = base.elements.map { if (it.index == 4) it.copy(checked = false) else it }
        assertThat(
            PageModel.signatureOf(base.url, toggledElements, base.text, base.scrollY, base.viewportHeight, base.pageHeight, base.humanCheck, base.humanCheckPassed)
        ).isNotEqualTo(base.signature)

        // A scrolled page: changed (past the coarse bucket).
        assertThat(
            PageModel.signatureOf(base.url, base.elements, base.text, base.scrollY + 2_000, base.viewportHeight, base.pageHeight, base.humanCheck, base.humanCheckPassed)
        ).isNotEqualTo(base.signature)

        // Another URL: changed.
        assertThat(
            PageModel.signatureOf("https://other.example", base.elements, base.text, base.scrollY, base.viewportHeight, base.pageHeight, base.humanCheck, base.humanCheckPassed)
        ).isNotEqualTo(base.signature)
    }

    @Test
    fun `garbage in, null out`() {
        assertThat(PageModel.parse("not json at all")).isNull()
        assertThat(PageModel.parse("{}")).isNotNull() // an empty but parseable snapshot is an empty page
    }
}
