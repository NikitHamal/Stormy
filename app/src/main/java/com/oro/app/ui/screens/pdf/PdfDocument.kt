package com.oro.app.ui.screens.pdf

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.os.Bundle
import android.os.CancellationSignal
import android.util.Base64
import android.util.LruCache
import android.widget.Toast
import androidx.core.content.FileProvider
import com.oro.app.data.preview.PdfSourceType
import com.oro.app.data.preview.PdfSources
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.cancellation.CancellationException

/** A problem opening a PDF whose message is fit to show the user as it is. */
internal class PdfSourceException(message: String) : Exception(message)

/**
 * Turns whatever the viewer was handed (a project file, a `content://` or
 * `file://` URI, an http(s) link, a `data:` URI) into a local file the
 * renderer can open. Everything that isn't already a local file lands in
 * `cacheDir/pdf-view`, which is pruned as it is used.
 */
internal object PdfSourceLoader {
    private const val MAX_BYTES = 150L * 1024 * 1024
    private const val CACHE_DIR = "pdf-view"
    private const val STALE_MS = 24L * 60 * 60 * 1000

    suspend fun load(context: Context, source: String): File = withContext(Dispatchers.IO) {
        val dir = cacheDir(context)
        prune(dir)
        val s = PdfSources.normalize(source)
        if (s.isEmpty()) throw PdfSourceException("There is no PDF to show.")
        val file = when (PdfSources.classify(s)) {
            PdfSourceType.FILE_PATH -> File(s)
            PdfSourceType.FILE_URI -> File(Uri.parse(s).path ?: throw PdfSourceException("That file path isn't valid."))
            PdfSourceType.CONTENT_URI -> {
                val input = try {
                    context.contentResolver.openInputStream(Uri.parse(s))
                } catch (e: Exception) {
                    null
                } ?: throw PdfSourceException("This PDF can't be read any more.")
                input.use { copyToCache(dir, it) }
            }
            PdfSourceType.HTTP_URL -> download(dir, s)
            PdfSourceType.DATA_URI -> decodeDataUri(dir, s)
            PdfSourceType.BLOB_URL -> throw PdfSourceException("This PDF lives inside the page and can't be opened from its link. Open it from the preview.")
        }
        if (!file.isFile) throw PdfSourceException("The file doesn't exist any more.")
        val head = ByteArray(1024)
        val read = try {
            FileInputStream(file).use { it.read(head) }
        } catch (e: IOException) {
            throw PdfSourceException("This file can't be read.")
        }
        if (read <= 0 || !PdfSources.looksLikePdf(head.copyOf(read))) {
            throw PdfSourceException("This file isn't a PDF.")
        }
        file
    }

    fun cacheDir(context: Context): File = File(context.cacheDir, CACHE_DIR).apply { mkdirs() }

    private fun prune(dir: File) {
        val cutoff = System.currentTimeMillis() - STALE_MS
        dir.listFiles()?.forEach { if (it.isFile && it.lastModified() < cutoff) it.delete() }
    }

    private fun copyToCache(dir: File, input: InputStream): File {
        val out = File.createTempFile("doc", ".pdf", dir)
        try {
            FileOutputStream(out).use { copyLimited(input, it) }
        } catch (e: Throwable) {
            out.delete()
            throw e
        }
        return out
    }

    private fun copyLimited(input: InputStream, output: OutputStream) {
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val n = input.read(buffer)
            if (n < 0) break
            total += n
            if (total > MAX_BYTES) throw PdfSourceException("This PDF is too large to open here.")
            output.write(buffer, 0, n)
        }
    }

    private fun download(dir: File, url: String): File {
        val connection = try {
            URL(url).openConnection() as HttpURLConnection
        } catch (e: Exception) {
            throw PdfSourceException("That link isn't valid.")
        }
        try {
            connection.connectTimeout = 15_000
            connection.readTimeout = 30_000
            connection.instanceFollowRedirects = true
            connection.setRequestProperty("Accept", "application/pdf,*/*")
            val code = try {
                connection.responseCode
            } catch (e: IOException) {
                throw PdfSourceException("Couldn't reach ${connection.url.host}. Check the connection and try again.")
            }
            if (code !in 200..299) throw PdfSourceException("The server answered HTTP $code.")
            return connection.inputStream.use { copyToCache(dir, it) }
        } finally {
            connection.disconnect()
        }
    }

    private fun decodeDataUri(dir: File, source: String): File {
        val data = PdfSources.parseDataUri(source) ?: throw PdfSourceException("That data link isn't valid.")
        if (!data.isBase64) throw PdfSourceException("Only base64 PDF data is supported.")
        val payload = if ('%' in data.payload) {
            runCatching { URLDecoder.decode(data.payload.replace("+", "%2B"), "UTF-8") }.getOrDefault(data.payload)
        } else data.payload
        // Standard base64 first; web-safe base64 (- and _) is what some encoders emit.
        val bytes = try {
            Base64.decode(payload, Base64.DEFAULT)
        } catch (e: IllegalArgumentException) {
            try {
                Base64.decode(payload, Base64.URL_SAFE)
            } catch (e2: IllegalArgumentException) {
                throw PdfSourceException("The PDF data is damaged.")
            }
        }
        val out = File.createTempFile("doc", ".pdf", dir)
        out.writeBytes(bytes)
        return out
    }
}

/**
 * An open PDF. [PdfRenderer] allows one open page at a time and is not
 * thread-safe, so every render goes through one mutex; a render that has been
 * cancelled (its page scrolled away) simply never gets the lock.
 */
internal class PdfDocumentHandle private constructor(
    val file: File,
    private val descriptor: ParcelFileDescriptor,
    private val renderer: PdfRenderer,
    /** Each page's size in points (width, height), before any rotation. */
    val pageSizes: List<Pair<Float, Float>>
) : AutoCloseable {

    val id: Long = nextId.incrementAndGet()
    val pageCount: Int get() = pageSizes.size

    private val lock = Mutex()
    @Volatile private var closed = false

    /** Renders page [index] to a bitmap of [widthPx] x [heightPx] (the rotated page) turned by [rotation]. */
    suspend fun render(index: Int, widthPx: Int, heightPx: Int, rotation: Int): Bitmap = lock.withLock {
        if (closed) throw CancellationException("The document was closed.")
        withContext(Dispatchers.IO) {
            try {
                val quarterTurn = rotation % 180 != 0
                val w = if (quarterTurn) heightPx else widthPx
                val h = if (quarterTurn) widthPx else heightPx
                val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                renderer.openPage(index).use { page ->
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                }
                if (rotation == 0) bitmap else {
                    val turned = Bitmap.createBitmap(
                        bitmap, 0, 0, w, h, Matrix().apply { postRotate(rotation.toFloat()) }, true
                    )
                    if (turned !== bitmap) bitmap.recycle()
                    turned
                }
            } finally {
                if (closed) closeNow()
            }
        }
    }

    override fun close() {
        closed = true
        // A render in flight closes the document itself when it is done.
        if (lock.tryLock()) {
            try {
                closeNow()
            } finally {
                lock.unlock()
            }
        }
    }

    private fun closeNow() {
        runCatching { renderer.close() }
        runCatching { descriptor.close() }
    }

    companion object {
        private val nextId = AtomicLong()

        suspend fun open(file: File): PdfDocumentHandle = withContext(Dispatchers.IO) {
            val descriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                val renderer = PdfRenderer(descriptor)
                try {
                    if (renderer.pageCount == 0) throw PdfSourceException("This PDF has no pages.")
                    val sizes = ArrayList<Pair<Float, Float>>(renderer.pageCount)
                    for (i in 0 until renderer.pageCount) {
                        renderer.openPage(i).use { page -> sizes.add(page.width.toFloat() to page.height.toFloat()) }
                    }
                    PdfDocumentHandle(file, descriptor, renderer, sizes)
                } catch (e: Throwable) {
                    runCatching { renderer.close() }
                    throw e
                }
            } catch (e: SecurityException) {
                runCatching { descriptor.close() }
                throw PdfSourceException("This PDF is password protected. Open it in another app.")
            } catch (e: IOException) {
                runCatching { descriptor.close() }
                throw PdfSourceException("This PDF is damaged, or uses a feature the built-in viewer can't read.")
            } catch (e: Throwable) {
                runCatching { descriptor.close() }
                throw e
            }
        }
    }
}

/** Rendered pages kept while they are near the screen; the least recently used go first. */
internal object PdfPageCache {
    private val cache = object : LruCache<String, Bitmap>(
        (Runtime.getRuntime().maxMemory() / 1024 / 6).toInt().coerceIn(8 * 1024, 96 * 1024)
    ) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount / 1024
    }

    fun key(documentId: Long, page: Int, width: Int, height: Int, rotation: Int) =
        "$documentId:$page:$width:$height:$rotation"

    fun get(key: String): Bitmap? = cache.get(key)?.takeIf { !it.isRecycled }
    fun put(key: String, bitmap: Bitmap) { cache.put(key, bitmap) }
}

/** Print, share, open elsewhere and save: what the viewer does with the file itself. */
internal object PdfActions {
    private const val AUTHORITY_SUFFIX = ".fileprovider"

    fun findActivity(context: Context): android.app.Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is android.app.Activity) return c
            c = c.baseContext
        }
        return null
    }

    fun print(context: Context, file: File, name: String) {
        val manager = findActivity(context)?.getSystemService(Context.PRINT_SERVICE) as? PrintManager
        if (manager == null) {
            Toast.makeText(context, "Printing isn't available here", Toast.LENGTH_SHORT).show()
            return
        }
        manager.print(name, PdfPrintAdapter(file, name), null)
    }

    fun open(context: Context, file: File, name: String) {
        val uri = shareUri(context, file, name) ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/pdf")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "No app here can open PDFs", Toast.LENGTH_SHORT).show()
        }
    }

    fun share(context: Context, file: File, name: String) {
        val uri = shareUri(context, file, name) ?: return
        val intent = Intent(Intent.ACTION_SEND)
            .setType("application/pdf")
            .putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, name)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(Intent.createChooser(intent, "Share $name"))
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(context, "Nothing here can receive a PDF", Toast.LENGTH_SHORT).show()
        }
    }

    /** Copies the PDF to a place the user picked (a document-create result). */
    suspend fun saveTo(context: Context, file: File, target: Uri): Boolean = withContext(Dispatchers.IO) {
        try {
            val out = context.contentResolver.openOutputStream(target) ?: return@withContext false
            out.use { o -> FileInputStream(file).use { it.copyTo(o) } }
            true
        } catch (e: Exception) {
            false
        }
    }

    /** A content:// URI another app may read; a file outside the app's shared folders is copied into the cache first. */
    private fun shareUri(context: Context, file: File, name: String): Uri? {
        val authority = context.packageName + AUTHORITY_SUFFIX
        return try {
            FileProvider.getUriForFile(context, authority, file)
        } catch (e: IllegalArgumentException) {
            try {
                val safeName = name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "document.pdf" }
                val copy = File(PdfSourceLoader.cacheDir(context), safeName)
                file.copyTo(copy, overwrite = true)
                FileProvider.getUriForFile(context, authority, copy)
            } catch (e: Exception) {
                Toast.makeText(context, "Couldn't share this file", Toast.LENGTH_SHORT).show()
                null
            }
        }
    }
}

/** Hands the original PDF bytes to the system print service, which does its own rendering. */
private class PdfPrintAdapter(private val file: File, private val name: String) : PrintDocumentAdapter() {

    override fun onLayout(
        oldAttributes: PrintAttributes?,
        newAttributes: PrintAttributes?,
        cancellationSignal: CancellationSignal?,
        callback: LayoutResultCallback?,
        extras: Bundle?
    ) {
        if (cancellationSignal?.isCanceled == true) {
            callback?.onLayoutCancelled()
            return
        }
        val info = PrintDocumentInfo.Builder(name)
            .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
            .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
            .build()
        callback?.onLayoutFinished(info, true)
    }

    override fun onWrite(
        pages: Array<out PageRange>?,
        destination: ParcelFileDescriptor?,
        cancellationSignal: CancellationSignal?,
        callback: WriteResultCallback?
    ) {
        if (destination == null) {
            callback?.onWriteFailed("No destination")
            return
        }
        try {
            FileInputStream(file).use { input ->
                FileOutputStream(destination.fileDescriptor).use { output -> input.copyTo(output) }
            }
            callback?.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
        } catch (e: Exception) {
            callback?.onWriteFailed(e.message)
        }
    }
}
