package com.oro.app.data.ai.checkpoint

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CheckpointsTest {
    @Test
    fun `restoring from a message undoes that run and every later one`() {
        val root = Files.createTempDirectory("cp").toFile()
        File(root, "index.html").writeText("v0")
        val cps = Checkpoints(root)

        val run1 = cps.begin("s", "m1", "first")
        run1.capture("index.html"); File(root, "index.html").writeText("v1")
        run1.capture("new.css"); File(root, "new.css").writeText("x")
        run1.capture("index.html") // second touch in the same run is free
        assertThat(run1.finish()!!.files).hasSize(2)
        Thread.sleep(5)
        val run2 = cps.begin("s", "m2", "second")
        run2.capture("index.html"); File(root, "index.html").writeText("v2")
        run2.finish()
        assertThat(cps.begin("s", "m3", "read only").finish()).isNull()

        assertThat(cps.messageIds("s")).containsExactly("m1", "m2")
        val r = cps.restoreFrom("s", "m1")
        assertThat(r.checkpoints).isEqualTo(2)
        assertThat(File(root, "index.html").readText()).isEqualTo("v0")
        assertThat(File(root, "new.css").exists()).isFalse()
        assertThat(cps.list("s")).isEmpty()
    }
}
