package com.oro.app.data.ai.agent

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TextToolCallParserTest {

    private val known = setOf("read_file", "write_file", "edit_file", "ask_user", "list_files")

    @Test
    fun `hermes tool_call tags with nested braces in content`() {
        val content = """
            I'll create the stylesheet.
            <tool_call>
            {"name": "write_file", "arguments": {"path": "style.css", "content": "body { margin: 0; }\n.a { color: red; }"}}
            </tool_call>
        """.trimIndent()
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls).hasSize(1)
        assertThat(parsed.calls[0].function.name).isEqualTo("write_file")
        assertThat(parsed.calls[0].function.arguments).contains(".a { color: red; }")
        assertThat(parsed.visibleText).isEqualTo("I'll create the stylesheet.")
    }

    @Test
    fun `qwen3 coder xml function format`() {
        val content = """
            <function=edit_file>
            <parameter=path>
            index.html
            </parameter>
            <parameter=old_string>
            <h1>Hi</h1>
            </parameter>
            <parameter=new_string>
            <h1>Hello</h1>
            </parameter>
            </function>
        """.trimIndent()
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(call.function.name).isEqualTo("edit_file")
        assertThat(call.function.arguments).contains("\"path\":\"index.html\"")
        assertThat(call.function.arguments).contains("<h1>Hello</h1>")
    }

    @Test
    fun `mistral marker and fenced json and aliases`() {
        val mistral = TextToolCallParser.parse("""[TOOL_CALLS][{"name":"read_file","arguments":{"path":"a.js"}}]""", known)
        assertThat(mistral.calls.single().function.name).isEqualTo("read_file")

        val fenced = TextToolCallParser.parse("Reading it:\n```json\n{\"name\": \"view\", \"arguments\": {\"path\": \"b.js\"}}\n```", known)
        // "view" is an alias of read_file, so it is accepted.
        assertThat(fenced.calls.single().function.name).isEqualTo("view")
        assertThat(fenced.visibleText).isEqualTo("Reading it:")

        val openAiShape = TextToolCallParser.parse("""{"type":"function","function":{"name":"list_files","arguments":"{}"}}""", known)
        assertThat(openAiShape.calls.single().function.arguments).isEqualTo("{}")
    }

    @Test
    fun `prose and unknown tools are not executed`() {
        val prose = "You could call delete_file(path='index.html') to remove it, or use read_file."
        assertThat(TextToolCallParser.parse(prose, known).calls).isEmpty()

        val example = "Here is JSON:\n```json\n{\"name\": \"Alice\", \"age\": 3}\n```"
        assertThat(TextToolCallParser.parse(example, known).calls).isEmpty()

        val unknown = "<tool_call>{\"name\": \"format_disk\", \"arguments\": {}}</tool_call>"
        assertThat(TextToolCallParser.parse(unknown, known).calls).isEmpty()
    }

    @Test
    fun `balanced json extraction ignores braces in strings`() {
        assertThat(TextToolCallParser.extractJson("x {\"a\": \"}{\", \"b\": [1, {\"c\": 2}]} tail"))
            .isEqualTo("{\"a\": \"}{\", \"b\": [1, {\"c\": 2}]}")
    }

    @Test
    fun `k2 horizon namespaced key-value tool calls`() {
        // Recorded from chat.ifm.ai on 2026-10-17, including the malformed first key.
        val content = """
            <ifm|tool_calls>
            <ifm|tool_call>read_file
            <ifm|arg_key>path":
            <ifm|arg_value>index.html</ifm|arg_value>
            </ifm|tool_call>
            <ifm|tool_call>read_file
            <ifm|arg_key>path</ifm|arg_key>
            <ifm|arg_value>style.css</ifm|arg_value>
            </ifm|tool_call>
            </ifm|tool_calls>
        """.trimIndent()
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls.map { it.function.arguments })
            .containsExactly("{\"path\":\"index.html\"}", "{\"path\":\"style.css\"}").inOrder()
        assertThat(parsed.visibleText).isEmpty()
    }

    @Test
    fun `glm key-value form with multi-line html value`() {
        val content = "<tool_call>write_file<arg_key>path</arg_key><arg_value>a.html</arg_value>" +
            "<arg_key>content</arg_key><arg_value><p>\n  hi\n</p></arg_value></tool_call>"
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(call.function.name).isEqualTo("write_file")
        assertThat(call.function.arguments).contains("<p>\\n  hi\\n</p>")
    }

    @Test
    fun `k2 horizon json-in-key-value hybrids are repaired`() {
        // Recorded from chat.ifm.ai on 2026-10-17.
        val content = """
            <ifm|tool_calls>
            <ifm|tool_call>read_file
            <ifm|arg_key>path": "index.html"}}</ifm|tool_call>
            <ifm|tool_call>grep
            <ifm|arg_key>pattern</ifm|arg_key> ".*", "path": "style.css", "include": "*.css"}}</ifm|tool_call>
            <ifm|tool_call>read_file
            <ifm|arg_key>path": "style.css</ifm|arg_value>
            </ifm|tool_call>
            </ifm|tool_calls>
        """.trimIndent()
        val calls = TextToolCallParser.parse(content, known + "grep").calls
        assertThat(calls).hasSize(3)
        assertThat(calls[0].function.arguments).isEqualTo("{\"path\":\"index.html\"}")
        assertThat(calls[2].function.arguments).isEqualTo("{\"path\":\"style.css\"}")
        assertThat(calls[1].function.name).isEqualTo("grep")
        assertThat(calls[1].function.arguments).isEqualTo("{\"pattern\":\".*\",\"path\":\"style.css\",\"include\":\"*.css\"}")
    }

    /** Recorded live from K2 Horizon 375B on 2026-10-17 under the real Orobi prompt. */
    @Test
    fun `k2 horizon native template with two calls`() {
        val content = """
            I'll look at the existing project first.
            <ifm|tool_calls>
            <ifm|tool_call>read_file
            <ifm|arg_key>path</ifm|arg_key>
            <ifm|arg_value>index.html</ifm|arg_value>
            </ifm|tool_call>
            <ifm|tool_call>read_file
            <ifm|arg_key>path</ifm|arg_key>
            <ifm|arg_value>style.css</ifm|arg_value>
            </ifm|tool_call>
            </ifm|tool_calls>
        """.trimIndent()
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls.map { it.function.arguments })
            .containsExactly("{\"path\":\"index.html\"}", "{\"path\":\"style.css\"}").inOrder()
        assertThat(parsed.visibleText).isEqualTo("I'll look at the existing project first.")
    }

    @Test
    fun `key-value file bodies stay strings even when they look like JSON or numbers`() {
        val content = "<ifm|tool_call>write_file\n<ifm|arg_key>path</ifm|arg_key>\n<ifm|arg_value>manifest.json</ifm|arg_value>\n" +
            "<ifm|arg_key>content</ifm|arg_key>\n<ifm|arg_value>{\"name\": \"Cafe\"}</ifm|arg_value>\n</ifm|tool_call>"
        val args = kotlinx.serialization.json.Json.parseToJsonElement(
            TextToolCallParser.parse(content, known).calls.single().function.arguments
        ) as kotlinx.serialization.json.JsonObject
        val body = args["content"] as kotlinx.serialization.json.JsonPrimitive
        assertThat(body.isString).isTrue()
        assertThat(body.content).isEqualTo("{\"name\": \"Cafe\"}")
    }

    @Test
    fun `kimi k2 and deepseek special-token sections`() {
        val kimi = "Reading.<|tool_calls_section_begin|><|tool_call_begin|>functions.read_file:0" +
            "<|tool_call_argument_begin|>{\"path\": \"a.html\"}<|tool_call_end|><|tool_calls_section_end|>"
        val k = TextToolCallParser.parse(kimi, known)
        assertThat(k.calls.single().function.name).isEqualTo("read_file")
        assertThat(k.visibleText).isEqualTo("Reading.")

        val deepseek = "<｜tool▁calls▁begin｜><｜tool▁call▁begin｜>function<｜tool▁sep｜>list_files\n```json\n{}\n```<｜tool▁call▁end｜><｜tool▁calls▁end｜>"
        assertThat(TextToolCallParser.parse(deepseek, known).calls.single().function.name).isEqualTo("list_files")
    }

    @Test
    fun `streaming prefix hides tool markup and partial openers`() {
        assertThat(TextToolCallParser.visiblePrefixLength("Let me read it.\n<ifm|tool_call>read_file")).isEqualTo(16)
        assertThat(TextToolCallParser.visiblePrefixLength("Plain prose")).isEqualTo(11)
        // "<tool_c" could still become "<tool_call>": hold it back.
        assertThat(TextToolCallParser.visiblePrefixLength("Ok <tool_c")).isEqualTo(3)
        // A finished HTML tag in prose is not held back.
        assertThat(TextToolCallParser.visiblePrefixLength("Use <b>bold</b>")).isEqualTo(15)
    }

    // ---------------------------------------------------------------- canonical raw-parameter format

    private fun argsOf(call: com.oro.app.data.ai.ToolCallResponse) =
        kotlinx.serialization.json.Json.parseToJsonElement(call.function.arguments) as kotlinx.serialization.json.JsonObject

    private fun text(call: com.oro.app.data.ai.ToolCallResponse, key: String) =
        (argsOf(call)[key] as kotlinx.serialization.json.JsonPrimitive).content

    @Test
    fun `canonical format keeps raw file bodies exactly, quotes and newlines included`() {
        val html = "<!DOCTYPE html>\n<html lang=\"en\">\n  <p class='a'>\"Hi\" \\n literally</p>\n</html>"
        val content = "Creating the page.\n<tool_call>\n<function=write_file>\n<parameter=path>index.html</parameter>\n" +
            "<parameter=content>\n$html\n</parameter>\n</function>\n</tool_call>"
        val parsed = TextToolCallParser.parse(content, known)
        val call = parsed.calls.single()
        assertThat(call.function.name).isEqualTo("write_file")
        assertThat(text(call, "path")).isEqualTo("index.html")
        assertThat(text(call, "content")).isEqualTo(html)
        assertThat(parsed.visibleText).isEqualTo("Creating the page.")
        assertThat(parsed.openEnded).isEmpty()
        assertThat(parsed.incomplete).isEmpty()
    }

    @Test
    fun `missing closing tags inside a closed tool_call block are tolerated`() {
        // Weak models drop </parameter> and </function>; the block itself is closed.
        val content = "<tool_call>\n<function=edit_file>\n<parameter=path>a.html\n<parameter=old_string><b>x</b></parameter>\n" +
            "<parameter=new_string><b>y</b>\n</tool_call>"
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(text(call, "path")).isEqualTo("a.html")
        assertThat(text(call, "old_string")).isEqualTo("<b>x</b>")
        assertThat(text(call, "new_string")).isEqualTo("<b>y</b>")
    }

    @Test
    fun `several calls in one reply keep their order`() {
        val content = """
            <tool_call>
            <function=read_file>
            <parameter=path>index.html</parameter>
            </function>
            </tool_call>
            <tool_call>
            <function=read_file>
            <parameter=path>style.css</parameter>
            </function>
            </tool_call>
        """.trimIndent()
        val calls = TextToolCallParser.parse(content, known).calls
        assertThat(calls.map { text(it, "path") }).containsExactly("index.html", "style.css").inOrder()
    }

    @Test
    fun `a call cut off mid-value is reported, never run`() {
        val content = "Writing it.\n<tool_call>\n<function=write_file>\n<parameter=path>index.html</parameter>\n" +
            "<parameter=content>\n<!DOCTYPE html>\n<html>\n<body>\n<section class=\"hero\">"
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls).isEmpty()
        assertThat(parsed.incomplete).containsExactly("write_file")
        assertThat(parsed.visibleText).isEqualTo("Writing it.")
    }

    @Test
    fun `an unclosed block whose last value closed is accepted but marked open-ended`() {
        val content = "<tool_call>\n<function=read_file>\n<parameter=path>a.html</parameter>"
        val parsed = TextToolCallParser.parse(content, known)
        val call = parsed.calls.single()
        assertThat(text(call, "path")).isEqualTo("a.html")
        assertThat(parsed.openEnded).containsExactly(call.id)
    }

    @Test
    fun `json cut off inside an unclosed tool_call is not repaired into a call`() {
        val content = "<tool_call>\n{\"name\": \"write_file\", \"arguments\": {\"path\": \"a.html\", \"content\": \"<html><body>"
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls).isEmpty()
        assertThat(parsed.incomplete).containsExactly("write_file")
    }

    @Test
    fun `bare functions and name-attribute spellings`() {
        val content = "<function name=\"read_file\">\n<parameter name=\"path\">x.js</parameter>\n</function>\n" +
            "<function=list_files>\n</function>"
        val calls = TextToolCallParser.parse(content, known).calls
        assertThat(calls.map { it.function.name }).containsExactly("read_file", "list_files").inOrder()
        assertThat(text(calls[0], "path")).isEqualTo("x.js")
        assertThat(calls[1].function.arguments).isEqualTo("{}")
    }

    @Test
    fun `values are typed except text keys`() {
        val content = "<tool_call>\n<function=ask_user>\n" +
            "<parameter=questions>[{\"question\": \"Which colour?\", \"options\": [{\"label\": \"Blue\"}]}]</parameter>\n" +
            "</function>\n</tool_call>\n" +
            "<tool_call>\n<function=read_file>\n<parameter=path>2048</parameter>\n<parameter=offset>12</parameter>\n" +
            "<parameter=limit>40</parameter>\n</function>\n</tool_call>"
        val calls = TextToolCallParser.parse(content, known).calls
        val questions = argsOf(calls[0])["questions"]
        assertThat(questions).isInstanceOf(kotlinx.serialization.json.JsonArray::class.java)
        val read = argsOf(calls[1])
        assertThat((read["path"] as kotlinx.serialization.json.JsonPrimitive).isString).isTrue()
        assertThat((read["offset"] as kotlinx.serialization.json.JsonPrimitive).isString).isFalse()
        assertThat((read["limit"] as kotlinx.serialization.json.JsonPrimitive).content).isEqualTo("40")
    }

    @Test
    fun `a formatted call parses back to the same arguments`() {
        val args = """{"path":"app.js","content":"const s = \"a\\nb\";\nconsole.log(s);","append":true}"""
        val formatted = TextToolProtocol.formatCall("write_file", args)
        val call = TextToolCallParser.parse(formatted, known).calls.single()
        assertThat(text(call, "path")).isEqualTo("app.js")
        assertThat(text(call, "content")).isEqualTo("const s = \"a\\nb\";\nconsole.log(s);")
        assertThat((argsOf(call)["append"] as kotlinx.serialization.json.JsonPrimitive).content).isEqualTo("true")
    }

    @Test
    fun `fabricated results are found only after a closed call`() {
        val call = "<tool_call>\n<function=read_file>\n<parameter=path>a</parameter>\n</function>\n</tool_call>\n"
        val faked = call + "<tool_result name=\"read_file\">made up</tool_result>"
        assertThat(TextToolCallParser.fakeResultStart(faked)).isEqualTo(call.length)
        // Inside a call that is still being written: a file may mention the tag.
        val inside = "<tool_call>\n<function=write_file>\n<parameter=content>docs: <tool_result> is"
        assertThat(TextToolCallParser.fakeResultStart(inside)).isEqualTo(-1)
        // Prose with no call yet.
        assertThat(TextToolCallParser.fakeResultStart("Hi\nUser: hello")).isEqualTo(-1)
        // A second call after the first is not a fake result.
        assertThat(TextToolCallParser.fakeResultStart(call + call)).isEqualTo(-1)
    }

    @Test
    fun `a call wrapped in a code fence leaves no empty fence behind`() {
        val content = "Let me read it.\n```xml\n<tool_call>\n<function=read_file>\n<parameter=path>a.html</parameter>\n" +
            "</function>\n</tool_call>\n```"
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls.single().function.name).isEqualTo("read_file")
        assertThat(parsed.visibleText).isEqualTo("Let me read it.")
    }

    // ---------------------------------------------------------------- raw values are opaque

    private val withDelete = known + "delete_file"

    @Test
    fun `a README showing an example call is written whole and the example never runs`() {
        val readme = "# Tool format\n```\n<function=delete_file>\n<parameter=path>index.html</parameter>\n</function>\n```\nDone."
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>README.md</parameter>\n" +
            "<parameter=content>\n$readme\n</parameter>\n</function>\n</tool_call>"
        val parsed = TextToolCallParser.parse(content, withDelete)
        val call = parsed.calls.single()
        assertThat(call.function.name).isEqualTo("write_file")
        assertThat(text(call, "content")).isEqualTo(readme)
        assertThat(parsed.incomplete).isEmpty()
    }

    @Test
    fun `an example wrapped in its own tool_call stays content too`() {
        val doc = "Example:\n<tool_call>\n<function=delete_file>\n<parameter=path>a</parameter>\n</function>\n</tool_call>"
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>docs.md</parameter>\n" +
            "<parameter=content>\n$doc\n</parameter>\n</function>\n</tool_call>"
        val call = TextToolCallParser.parse(content, withDelete).calls.single()
        assertThat(call.function.name).isEqualTo("write_file")
        assertThat(text(call, "content")).isEqualTo(doc)
    }

    @Test
    fun `a literal closing tag inside code is content`() {
        val js = "const CLOSE = '</parameter>';\nconst F = \"</function>\";\nconst T = `</tool_call>`;\nexport { CLOSE, F, T };"
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>tags.js</parameter>\n" +
            "<parameter=content>\n$js\n</parameter>\n</function>\n</tool_call>"
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(text(call, "content")).isEqualTo(js)
    }

    @Test
    fun `a closing tag on its own line followed by prose is content`() {
        val doc = "Close a call with\n</function>\nand you are done."
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>a.md</parameter>\n" +
            "<parameter=content>\n$doc\n</parameter>\n</function>\n</tool_call>"
        assertThat(text(TextToolCallParser.parse(content, known).calls.single(), "content")).isEqualTo(doc)
    }

    @Test
    fun `html that mentions tool_result is neither cut nor truncated`() {
        val html = "<p>The app answers with a <tool_result> block.</p>\n<p>User: hello</p>"
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>docs.html</parameter>\n" +
            "<parameter=content>\n$html\n</parameter>\n</function>\n</tool_call>"
        assertThat(TextToolCallParser.fakeResultStart(content)).isEqualTo(-1)
        assertThat(text(TextToolCallParser.parse(content, known).calls.single(), "content")).isEqualTo(html)
    }

    @Test
    fun `an invented result and the fake call after it are cut from a one-piece reply`() {
        val first = "<tool_call>\n<function=read_file>\n<parameter=path>a.html</parameter>\n</function>\n</tool_call>\n"
        val reply = first + "<tool_result name=\"read_file\">\n<html></html>\n</tool_result>\n" +
            "<tool_call>\n<function=delete_file>\n<parameter=path>a.html</parameter>\n</function>\n</tool_call>"
        val parsed = TextToolCallParser.parse(reply, withDelete)
        assertThat(parsed.calls.map { it.function.name }).containsExactly("read_file")
        assertThat(TextToolCallParser.fakeResultStart(reply)).isEqualTo(first.length)
    }

    @Test
    fun `a marker inside the first file does not hide a later invented result`() {
        val first = "<tool_call>\n<function=write_file>\n<parameter=path>a.md</parameter>\n<parameter=content>\n" +
            "Replies look like <tool_result> blocks.\n</parameter>\n</function>\n</tool_call>\n"
        val reply = first + "<tool_result name=\"write_file\">ok</tool_result>\nAll done!"
        assertThat(TextToolCallParser.fakeResultStart(reply)).isEqualTo(first.length)
        assertThat(TextToolCallParser.parse(reply, known).visibleText).doesNotContain("All done")
    }

    @Test
    fun `json inside tool_call ends at its brace, not at a closer in a string`() {
        val content = "<tool_call>\n{\"name\": \"write_file\", \"arguments\": {\"path\": \"a.md\", " +
            "\"content\": \"wrap calls in </tool_call> tags\"}}\n</tool_call>"
        val call = TextToolCallParser.parse(content, known).calls.single()
        assertThat(text(call, "content")).isEqualTo("wrap calls in </tool_call> tags")
    }

    @Test
    fun `a complete call followed by a cut one runs only the first`() {
        val content = "<tool_call>\n<function=read_file>\n<parameter=path>a.html</parameter>\n</function>\n</tool_call>\n" +
            "<tool_call>\n<function=write_file>\n<parameter=path>b.html</parameter>\n<parameter=content>\n<html><bo"
        val parsed = TextToolCallParser.parse(content, known)
        assertThat(parsed.calls.map { it.function.name }).containsExactly("read_file")
        assertThat(parsed.incomplete).containsExactly("write_file")
        assertThat(parsed.incompleteCalls.single().function.name).isEqualTo("write_file")
    }

    @Test
    fun `an unclosed read-only call keeps the arguments that arrived`() {
        val parsed = TextToolCallParser.parse("<tool_call>\n<function=read_file>\n<parameter=path>a.html", known)
        assertThat(parsed.calls).isEmpty()
        assertThat(text(parsed.incompleteCalls.single(), "path")).isEqualTo("a.html")
    }

    @Test
    fun `with a schema, a bare parameter line that is not the tool's own stays content`() {
        val schemas = com.oro.app.data.ai.tools.AgentTools.parameterSchemas
        val doc = "To edit, send\n<parameter=old_string>the old text</parameter>\nand the new one."
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>help.md</parameter>\n" +
            "<parameter=content>\n$doc\n</parameter>\n</function>\n</tool_call>"
        val call = TextToolCallParser.parse(content, known, schemas).calls.single()
        assertThat(text(call, "content")).isEqualTo(doc)
        assertThat(argsOf(call).keys).containsExactly("path", "content")
    }

    // ---------------------------------------------------------------- typing

    private fun schema(json: String) =
        kotlinx.serialization.json.Json.parseToJsonElement(json) as kotlinx.serialization.json.JsonObject

    @Test
    fun `values follow the tool's schema when it is known`() {
        val search = schema("""{"type":"object","properties":{"page":{"type":"integer"},"zip":{"type":"string"},"exact":{"type":"boolean"},"tags":{"type":"array","items":{"type":"string"}}}}""")
        val content = "<tool_call>\n<function=mcp__shop__search>\n<parameter=page>2</parameter>\n<parameter=zip>02134</parameter>\n" +
            "<parameter=exact>true</parameter>\n<parameter=tags>[\"a\", \"b\"]</parameter>\n</function>\n</tool_call>"
        val call = TextToolCallParser.parse(content, setOf("mcp__shop__search"), mapOf("mcp__shop__search" to search)).calls.single()
        val args = argsOf(call)
        assertThat((args["page"] as kotlinx.serialization.json.JsonPrimitive).isString).isFalse()
        assertThat((args["zip"] as kotlinx.serialization.json.JsonPrimitive).content).isEqualTo("02134")
        assertThat((args["zip"] as kotlinx.serialization.json.JsonPrimitive).isString).isTrue()
        assertThat((args["exact"] as kotlinx.serialization.json.JsonPrimitive).content).isEqualTo("true")
        assertThat(args["tags"]).isInstanceOf(kotlinx.serialization.json.JsonArray::class.java)
    }

    @Test
    fun `without a schema, numbers with a leading zero and huge numbers stay text`() {
        val content = "<tool_call>\n<function=ask_user>\n<parameter=code>02134</parameter>\n" +
            "<parameter=big>123456789012345678901234</parameter>\n<parameter=n>7</parameter>\n</function>\n</tool_call>"
        val args = argsOf(TextToolCallParser.parse(content, known).calls.single())
        assertThat((args["code"] as kotlinx.serialization.json.JsonPrimitive).isString).isTrue()
        assertThat((args["big"] as kotlinx.serialization.json.JsonPrimitive).isString).isTrue()
        assertThat((args["n"] as kotlinx.serialization.json.JsonPrimitive).isString).isFalse()
        // The key-value form used to crash on a 20-digit value.
        val kv = "<tool_call>ask_user<arg_key>big</arg_key><arg_value>123456789012345678901234</arg_value></tool_call>"
        assertThat(TextToolCallParser.parse(kv, known).calls).hasSize(1)
    }

    // ------------------------------------------------ template drift (live eval, Tencent Hy3)

    private val hy3Tools = setOf("use_skill", "add_ui_kit", "list_files", "update_plan", "write_file")

    @Test
    fun `hy3 id-tagged calls with the name and no function tag are read`() {
        // Verbatim from a live run: the first two calls are canonical, the rest carry Hy3's own template.
        val content = "I'll build this premium coffee shop site. Let me load the right playbooks and the UI kit first.\n\n" +
            "<tool_call>\n<function=use_skill>\n<parameter=name>design-system</parameter>\n</function>\n</tool_call>\n" +
            "<tool_call>\n<function=use_skill>\n<parameter=name>landing-page</parameter>\n</function>\n</tool_call>\n" +
            "<tool_call:6124c78e>use_skill>\n<parameter=name>ui-states</parameter>\n</function>\n</tool_call>\n" +
            "<tool_call:6124c78e>use_skill>\n<parameter=name>forms</parameter>\n</function>\n</tool_call>\n" +
            "<tool_call:6124c78e>add_ui_kit>\n<parameter=brand_hue>22</parameter>\n</function>\n</tool_call>\n" +
            "</tool_calls:6124c78e>"
        val parsed = TextToolCallParser.parse(content, hy3Tools)
        assertThat(parsed.calls.map { it.function.name }).containsExactly("use_skill", "use_skill", "use_skill", "use_skill", "add_ui_kit").inOrder()
        assertThat(parsed.calls[2].function.arguments).contains("ui-states")
        assertThat(parsed.calls[4].function.arguments).contains("22")
        assertThat(parsed.visibleText).isEqualTo("I'll build this premium coffee shop site. Let me load the right playbooks and the UI kit first.")
    }

    @Test
    fun `a function tag missing its equals sign is read`() {
        val content = "Let me see what the UI kit created and plan the build.\n\n" +
            "<tool_call>\n<function>list_files>\n<parameter=path>.</parameter>\n<parameter=depth>2</parameter>\n" +
            "<parameter=symbols>true</parameter>\n</function>\n</tool_call>\n" +
            "<tool_call:6124c78e>update_plan>\n<parameter=tasks>[{\"title\":\"Set up index.html\",\"state\":\"active\"}]</parameter>\n" +
            "</function>\n</tool_call>\n</tool_calls:6124c78e>"
        val parsed = TextToolCallParser.parse(content, hy3Tools)
        assertThat(parsed.calls.map { it.function.name }).containsExactly("list_files", "update_plan").inOrder()
        assertThat(parsed.calls[0].function.arguments).contains("\"depth\":2")
        assertThat(parsed.calls[1].function.arguments).contains("Set up index.html")
        assertThat(TextToolCallParser.looksLikeToolCall("x\n<function>list_files>\n")).isTrue()
    }

    @Test
    fun `a parameter tag missing its equals sign is read, value on the same line`() {
        // Verbatim shape from a live run: before this, write_file arrived without its content.
        val content = "app.js still didn't save. Retrying the write.\n\n" +
            "<tool_call>\n<function>update_plan>\n<parameter>tasks>[{\"title\":\"Load design system\", \"state\":\"active\"}]</parameter>\n" +
            "</function>\n</tool_call>\n\n" +
            "<tool_call>\n<function>write_file>\n<parameter=path>app.js</parameter>\n<parameter>content>/* Himalayan Brew - interactions */\n" +
            "(function () {\n  \"use strict\";\n})();\n</parameter>\n</function>\n</tool_call>"
        val parsed = TextToolCallParser.parse(content, hy3Tools)
        assertThat(parsed.calls.map { it.function.name }).containsExactly("update_plan", "write_file").inOrder()
        assertThat(parsed.calls[0].function.arguments).contains("Load design system")
        val args = argsOf(parsed.calls[1])
        assertThat((args["path"] as kotlinx.serialization.json.JsonPrimitive).content).isEqualTo("app.js")
        assertThat((args["content"] as kotlinx.serialization.json.JsonPrimitive).content).startsWith("/* Himalayan Brew - interactions */\n(function () {")
    }

    @Test
    fun `drifted openers inside a file body stay content`() {
        // Only line-start markup naming a real tool is rewritten; prose that mentions the tags is not.
        val body = "Docs: the model may write <function>list_files> by mistake.\nAnd <tool_call:abc>nothing> too."
        val content = "<tool_call>\n<function=write_file>\n<parameter=path>NOTES.md</parameter>\n<parameter=content>\n" +
            body + "\n</parameter>\n</function>\n</tool_call>"
        val parsed = TextToolCallParser.parse(content, hy3Tools)
        assertThat(parsed.calls).hasSize(1)
        assertThat(parsed.calls[0].function.arguments).contains("<function>list_files> by mistake")
        assertThat(parsed.calls[0].function.arguments).contains("<tool_call:abc>nothing>")
    }
}
