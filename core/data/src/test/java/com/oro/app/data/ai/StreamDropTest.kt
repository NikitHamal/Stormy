package com.oro.app.data.ai

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import java.net.ServerSocket
import kotlin.concurrent.thread

/**
 * A stream that breaks after the server already answered 200 (seen live on
 * VLM Run's gateway) is a dropped connection: it must come back retryable,
 * not as "request failed (200)", which ended whole agent runs.
 */
class StreamDropTest {

    @Test
    fun `a stream cut after a 200 is a retryable connection error`() = runBlocking {
        val server = ServerSocket(0)
        thread(isDaemon = true) {
            server.accept().use { socket ->
                val input = socket.getInputStream()
                // Read the request head (and whatever body has arrived), then answer and cut.
                val buf = ByteArray(65_536)
                input.read(buf)
                val out = socket.getOutputStream()
                out.write("HTTP/1.1 200 OK\r\nContent-Type: text/event-stream\r\nTransfer-Encoding: chunked\r\n\r\n".toByteArray())
                val chunk = "data: {\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hel\"}}]}\n\n"
                out.write((Integer.toHexString(chunk.toByteArray().size) + "\r\n" + chunk + "\r\n").toByteArray())
                out.flush()
                Thread.sleep(200)
            }
        }
        val provider = OpenAiCompatibleProvider(AiProvider.CUSTOM, "", baseUrlOverride = "http://127.0.0.1:${server.localPort}")
        val model = AiModel(id = "test-model", name = "Test", provider = AiProvider.CUSTOM)
        val events = withTimeout(20_000) {
            provider.streamChatCompletion(model, listOf(ChatRequestMessage(role = "user", content = "hi"))).toList()
        }
        server.close()
        val error = events.filterIsInstance<StreamEvent.Error>().single()
        assertThat(error.retryable).isTrue()
        assertThat(error.httpCode).isNull()
        assertThat(error.message).doesNotContain("(200)")
    }
}
