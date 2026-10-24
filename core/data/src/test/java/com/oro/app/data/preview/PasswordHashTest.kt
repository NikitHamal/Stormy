package com.oro.app.data.preview

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PasswordHashTest {

    @Test
    fun `pbkdf2 hashes verify, are salted and never hold the password`() {
        val a = PasswordHash.create("correct horse")
        val b = PasswordHash.create("correct horse")
        assertThat(a).startsWith("pbkdf2:")
        assertThat(a).isNotEqualTo(b)
        assertThat(a).doesNotContain("correct")
        assertThat(PasswordHash.verify(a, "correct horse")).isTrue()
        assertThat(PasswordHash.verify(a, "Correct horse")).isFalse()
        assertThat(PasswordHash.needsUpgrade(a)).isFalse()
    }

    @Test
    fun `old sha256 hashes still verify and ask for an upgrade`() {
        val hex = java.security.MessageDigest.getInstance("SHA-256").digest("s4ltpw".toByteArray()).joinToString("") { "%02x".format(it) }
        val old = "sha256:s4lt:$hex"
        assertThat(PasswordHash.verify(old, "pw")).isTrue()
        assertThat(PasswordHash.verify(old, "px")).isFalse()
        assertThat(PasswordHash.needsUpgrade(old)).isTrue()
        assertThat(PasswordHash.verify("garbage", "pw")).isFalse()
        assertThat(PasswordHash.verify("pbkdf2:x:zz:yy", "pw")).isFalse()
    }
}
