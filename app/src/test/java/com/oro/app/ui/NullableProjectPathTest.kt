package com.oro.app.ui

import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Test

/**
 * Guards the crash reported from a real device:
 *
 *     java.lang.NullPointerException
 *       at java.io.File.<init>(File.java:283)
 *       at GitDrawer$1$1.invokeSuspend
 *
 * The Git and Asset drawers are composed *before* the project loads, so that
 * their sheets have a stable width -- without that the drawer's open/closed
 * anchors collapse onto the same offset and it re-opens itself. The cost of
 * that fix is a window where projectPath is null, and `File(null)` throws
 * inside java.io.File rather than returning null.
 *
 * These pin the shape of the guard, not the composable: the rule is that a
 * nullable path must be resolved to a non-null root before it reaches File().
 */
class NullableProjectPathTest {

    /** Mirrors the guard used in the drawers. */
    private fun openRepository(projectPath: String?): File? {
        val root = projectPath ?: return null
        return File(root)
    }

    @Test
    fun `a null project path is skipped rather than passed to File`() {
        assertThat(openRepository(null)).isNull()
    }

    @Test
    fun `a real path still opens`() {
        assertThat(openRepository("/data/projects/site")?.path)
            .isEqualTo("/data/projects/site")
    }

    @Test
    fun `File rejects null, which is why the guard exists`() {
        // Documents the actual failure: this is not a defensive null check for
        // its own sake, it is the only thing standing between a null path and
        // a crash on the main thread.
        val thrown = runCatching {
            @Suppress("DEPRECATION")
            File(null as String?)
        }.exceptionOrNull()
        assertThat(thrown).isInstanceOf(NullPointerException::class.java)
    }
}
