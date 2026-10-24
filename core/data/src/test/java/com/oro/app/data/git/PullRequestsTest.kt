package com.oro.app.data.git

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.io.File
import java.nio.file.Files

class PullRequestsTest {
    @Test
    fun `blob ids match git`() {
        assertThat(PullRequests.blobSha(ByteArray(0))).isEqualTo("e69de29bb2d1d6434b8b29ae775ad8c2e48c5391")
        assertThat(PullRequests.blobSha("hello\n".toByteArray())).isEqualTo("ce013625030ba8dba906f756967f9e9ca394464a")
    }

    @Test
    fun `origin repo is read from git config`() {
        val root = Files.createTempDirectory("pr").toFile()
        File(root, ".git").mkdirs()
        File(root, ".git/config").writeText(
            "[core]\n\tbare = false\n[remote \"upstream\"]\n\turl = https://github.com/other/thing.git\n" +
                "[remote \"origin\"]\n\turl = git@github.com:me/site.git\n\tfetch = +refs/heads/*:refs/remotes/origin/*\n"
        )
        assertThat(PullRequests.originRepo(root)).isEqualTo("me/site")
        assertThat(PullRequests.branchName("Add a pricing table!")).isEqualTo("oro/add-a-pricing-table")
    }
}
