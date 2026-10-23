package com.oro.app.ui.components.inspector

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class LinkedStylesheetsTest {

    @Test
    fun `on-screen local stylesheets in cascade order, without CDNs, alternates or print`() {
        val html = """
            <link rel="preconnect" href="https://fonts.googleapis.com">
            <link href="https://fonts.googleapis.com/css2?family=Inter" rel="stylesheet">
            <link rel="stylesheet" href="oro-ui/oro.css">
            <link rel=stylesheet href=css/site.css?v=2>
            <LINK REL='alternate stylesheet' HREF='/css/contrast.css'>
            <link rel="stylesheet" media="print" href="/css/print.css">
            <link rel="stylesheet" href="/css/extra.css">
            <link rel="icon" href="favicon.svg">
        """.trimIndent()
        assertThat(DirectEditService.linkedStylesheets(html))
            .containsExactly("oro-ui/oro.css", "css/site.css?v=2", "/css/extra.css").inOrder()
    }
}
