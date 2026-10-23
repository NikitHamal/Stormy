package com.oro.app.device

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import android.os.Build
import android.util.Base64
import android.view.Display
import androidx.annotation.RequiresApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.Executor
import kotlin.coroutines.resume

/**
 * A screenshot for vision models, with each element's number drawn on its
 * box ("set-of-mark"), so the model can look at a game, a map, a photo or a
 * canvas and still act by number -- the listing and the picture use the same
 * indexes.
 *
 * Android 11+ only (AccessibilityService.takeScreenshot). Downscaled to
 * [MAX_SIDE] and JPEG-encoded: a full-resolution PNG costs several times the
 * tokens and the memory a budget phone can spare.
 */
object ScreenshotTaker {

    private const val MAX_SIDE = 1280
    private const val MAX_MARKS = 90

    val supported: Boolean get() = Build.VERSION.SDK_INT >= 30

    /** Base64 JPEG of the screen with element numbers drawn on, or null if unavailable. */
    suspend fun capture(service: AccessibilityService, screen: ScreenState?): String? {
        if (Build.VERSION.SDK_INT < 30) return null
        val bitmap = take(service) ?: return null
        return withContext(Dispatchers.Default) {
            try {
                annotateAndEncode(bitmap, screen)
            } finally {
                bitmap.recycle()
            }
        }
    }

    @RequiresApi(30)
    private suspend fun take(service: AccessibilityService): Bitmap? = suspendCancellableCoroutine { cont ->
        val direct = Executor { it.run() }
        runCatching {
            service.takeScreenshot(Display.DEFAULT_DISPLAY, direct, object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    val buffer = result.hardwareBuffer
                    val soft = runCatching {
                        val hw = Bitmap.wrapHardwareBuffer(buffer, result.colorSpace)
                        val copy = hw?.copy(Bitmap.Config.ARGB_8888, false)
                        hw?.recycle()
                        copy
                    }.getOrNull()
                    buffer.close()
                    if (cont.isActive) cont.resume(soft)
                }

                override fun onFailure(errorCode: Int) {
                    if (cont.isActive) cont.resume(null)
                }
            })
        }.onFailure { if (cont.isActive) cont.resume(null) }
    }

    private fun annotateAndEncode(source: Bitmap, screen: ScreenState?): String {
        val scale = minOf(1f, MAX_SIDE.toFloat() / maxOf(source.width, source.height))
        val w = (source.width * scale).toInt().coerceAtLeast(1)
        val h = (source.height * scale).toInt().coerceAtLeast(1)
        val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        canvas.drawBitmap(source, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))

        if (screen != null) {
            // Coordinates are in screen pixels; the screenshot may differ in size (cutouts, scaling).
            val sx = w.toFloat() / screen.width.coerceAtLeast(1)
            val sy = h.toFloat() / screen.height.coerceAtLeast(1)
            val box = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE; strokeWidth = 2.5f; color = MARK
            }
            val tagFill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = MARK }
            val tagText = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.WHITE; textSize = 22f * maxOf(0.7f, scale * 1.4f).coerceAtMost(1.2f)
                typeface = Typeface.DEFAULT_BOLD
            }
            screen.elements.asSequence()
                .filter { it.actionable || it.role == "image" || it.role == "icon" || it.role == ScreenElement.ROLE_SLIDER }
                .take(MAX_MARKS)
                .forEach { e ->
                    val r = RectF(e.bounds.left * sx, e.bounds.top * sy, e.bounds.right * sx, e.bounds.bottom * sy)
                    canvas.drawRect(r, box)
                    val label = e.index.toString()
                    val tw = tagText.measureText(label) + 8f
                    val th = tagText.textSize + 6f
                    val tag = RectF(r.left, r.top, r.left + tw, r.top + th)
                    canvas.drawRoundRect(tag, 4f, 4f, tagFill)
                    canvas.drawText(label, tag.left + 4f, tag.bottom - 5f, tagText)
                }
        }

        val bytes = ByteArrayOutputStream()
        out.compress(Bitmap.CompressFormat.JPEG, 70, bytes)
        out.recycle()
        return Base64.encodeToString(bytes.toByteArray(), Base64.NO_WRAP)
    }

    /** Oro magenta, slightly deepened so white numbers stay legible on light screens. */
    private val MARK = Color.rgb(0xD0, 0x00, 0xD0)
}
