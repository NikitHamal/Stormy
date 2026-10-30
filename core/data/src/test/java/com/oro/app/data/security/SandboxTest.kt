package com.oro.app.data.security

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class SandboxTest {

    @Test
    fun `language names normalise to the runner vocabulary`() {
        assertThat(Sandbox.normalizeLanguage("py")).isEqualTo("python")
        assertThat(Sandbox.normalizeLanguage("Node.js")).isEqualTo("javascript")
        assertThat(Sandbox.normalizeLanguage("sh")).isEqualTo("bash")
        assertThat(Sandbox.normalizeLanguage("C++")).isEqualTo("cpp")
        assertThat(Sandbox.normalizeLanguage("brainfuck")).isNull()
    }

    @Test
    fun `judge0 body carries the language and the code`() {
        val body = Sandbox.judge0Body(71, "print(6*7)", "", )
        assertThat(body).contains("\"language_id\":71")
        assertThat(body).contains("print(6*7)")
        assertThat(Sandbox.judge0LanguageId("bash")).isEqualTo(46)
        assertThat(Sandbox.judge0LanguageId("cobol")).isNull()
    }

    @Test
    fun `judge0 accepted output is a success`() {
        val json = """{"stdout":"42\n","stderr":null,"compile_output":null,"exit_code":0,
            "status":{"id":3,"description":"Accepted"}}"""
        val result = Sandbox.parseJudge0(json)
        assertThat(result.ok).isTrue()
        assertThat(result.stdout).isEqualTo("42\n")
        assertThat(result.exitCode).isEqualTo(0)
    }

    @Test
    fun `a compile error fails with the compiler text`() {
        val json = """{"stdout":null,"stderr":null,"compile_output":"main.py:1: invalid syntax\n",
            "exit_code":1,"status":{"id":6,"description":"Compilation Error"}}"""
        val result = Sandbox.parseJudge0(json)
        assertThat(result.ok).isFalse()
        assertThat(result.stderr).contains("invalid syntax")
        assertThat(result.detail).contains("Compilation Error")
    }

    @Test
    fun `wandbox success and failure parse`() {
        val ok = Sandbox.parseWandbox("""{"status":"0","program_output":"42\n","program_error":"","compiler_error":""}""")
        assertThat(ok.ok).isTrue()
        assertThat(ok.stdout).isEqualTo("42\n")
        val bad = Sandbox.parseWandbox("""{"status":"1","program_output":"","program_error":"boom","compiler_error":""}""")
        assertThat(bad.ok).isFalse()
        assertThat(bad.stderr).contains("boom")
        assertThat(Sandbox.wandboxCompiler("bash")).isNull()
        assertThat(Sandbox.wandboxCompiler("python")).isNotNull()
    }

    @Test
    fun `a custom runner uses the shared json contract`() {
        val body = Sandbox.customBody("python", "print(1)", "")
        assertThat(body).contains("\"language\":\"python\"")
        val result = Sandbox.parseCustom("""{"stdout":"ok\n","stderr":"","exit_code":0}""")
        assertThat(result.ok).isTrue()
        assertThat(result.engine).isEqualTo(Sandbox.Engine.CUSTOM.label)
    }
}
