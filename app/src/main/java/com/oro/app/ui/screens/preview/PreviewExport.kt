package com.oro.app.ui.screens.preview

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import com.oro.app.data.preview.StandaloneBundler
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * The preview's export actions: a page as one self-contained HTML file, a
 * page with the files it uses as a zip, the code on the clipboard, and the
 * system print dialog (which also saves a PDF). Blocking file work: call it
 * off the main thread.
 */
internal object PreviewExport {

    private const val MAX_TEXT_BYTES = 5L * 1024 * 1024

    /** [rel] inside [root], not hidden and not escaping it; else null. */
    private fun safeFile(root: File, rel: String): File? {
        val clean = rel.replace('\\', '/').trimStart('/')
        if (clean.isEmpty() || clean.split('/').any { it.startsWith(".") || it == ".." }) return null
        val file = File(root, clean)
        val inside = runCatching {
            file.isFile && file.canonicalPath.startsWith(root.canonicalPath + File.separator)
        }.getOrDefault(false)
        return file.takeIf { inside }
    }

    fun readText(root: File, rel: String): String? =
        safeFile(root, rel)?.takeIf { it.length() <= MAX_TEXT_BYTES }?.let { runCatching { it.readText() }.getOrNull() }

    fun readBytes(root: File, rel: String): ByteArray? =
        safeFile(root, rel)?.takeIf { it.length() <= MAX_TEXT_BYTES }?.let { runCatching { it.readBytes() }.getOrNull() }

    fun bundle(root: File, page: String): StandaloneBundler.Result =
        StandaloneBundler.bundle(page, { readText(root, it) }, { readBytes(root, it) })

    fun standaloneName(page: String): String =
        page.substringAfterLast('/').substringBeforeLast('.').ifBlank { "page" } + ".standalone.html"

    fun zipName(page: String): String =
        page.substringAfterLast('/').substringBeforeLast('.').ifBlank { "page" } + "-files.zip"

    fun writeText(context: Context, uri: Uri, text: String): Boolean = runCatching {
        context.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) } != null
    }.getOrDefault(false)

    /** Writes [page] and everything it links to into [uri] as a zip; returns how many files went in, or -1. */
    fun writeZip(context: Context, uri: Uri, root: File, page: String): Int = runCatching {
        val files = StandaloneBundler.closure(page, { readText(root, it) }, { safeFile(root, it) != null })
        var count = 0
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: return -1
        ZipOutputStream(out.buffered()).use { zip ->
            for (path in files) {
                val file = safeFile(root, path) ?: continue
                zip.putNextEntry(ZipEntry(path))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
                count++
            }
        }
        count
    }.getOrDefault(-1)

    fun copy(context: Context, text: String, label: String = "Stormy") {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        clipboard.setPrimaryClip(ClipData.newPlainText(label, text.take(900_000)))
    }

    /** Opens the system print dialog for what [webView] shows; "Save as PDF" is one of its printers. */
    fun print(context: Context, webView: WebView, title: String): Boolean {
        val manager = context.getSystemService(Context.PRINT_SERVICE) as? PrintManager ?: return false
        val name = title.ifBlank { "Preview" }
        return runCatching {
            manager.print(name, webView.createPrintDocumentAdapter(name), PrintAttributes.Builder().build())
        }.isSuccess
    }
}
