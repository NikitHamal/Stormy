package com.oro.app.share

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.oro.app.data.deploy.Deployer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Share a project: as a zip anyone can open in a browser or another editor,
 * or as its live link when it has been published.
 */
object ProjectSharing {

    private val SKIP = setOf(".oro", ".git", "node_modules", ".gradle", ".idea")

    suspend fun zip(context: Context, root: File, name: String): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "share").apply { mkdirs(); listFiles()?.forEach { it.delete() } }
        val out = File(dir, Deployer.slug(name) + ".zip")
        ZipOutputStream(out.outputStream().buffered()).use { z ->
            root.walkTopDown()
                .onEnter { it == root || it.name !in SKIP }
                .filter { it.isFile }
                .forEach { f ->
                    z.putNextEntry(ZipEntry(Deployer.slug(name) + "/" + f.relativeTo(root).invariantSeparatorsPath))
                    f.inputStream().use { it.copyTo(z) }
                    z.closeEntry()
                }
        }
        out
    }

    suspend fun shareZip(context: Context, root: File, name: String) {
        val file = zip(context, root, name)
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType("application/zip")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, "$name (made with Oro)")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        withContext(Dispatchers.Main) {
            context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }

    /** The published URL, if the project was deployed from Oro. */
    fun liveUrl(root: File): String? = Deployer(root).state().let { it.netlifyUrl ?: it.githubUrl }

    fun shareLink(context: Context, name: String, url: String) {
        val send = Intent(Intent.ACTION_SEND).setType("text/plain")
            .putExtra(Intent.EXTRA_TEXT, "$name — $url")
        context.startActivity(Intent.createChooser(send, "Share $name").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
