package com.oro.app.device.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.widget.FrameLayout
import kotlin.math.abs

/**
 * The island window's root: lets taps through to the Compose content, and
 * takes over as a drag once the finger travels past the touch slop.
 *
 * Dragging is done here rather than in Compose because the window moves under
 * the finger: pointer positions inside the window stay put while it follows,
 * so only raw screen coordinates describe the gesture.
 *
 * It also reports what only the window sees: Back and touches outside the
 * island while the reply field has the keyboard (both hand focus back to the
 * app underneath), and configuration changes such as rotation.
 */
internal class DragFrame(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onDragEnd: () -> Unit,
    private val onDismissKeyboard: () -> Unit = {},
    private val onConfigChanged: () -> Unit = {},
) : FrameLayout(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var dragging = false

    /** True while a drag is under the finger, so nothing else moves the window meanwhile. */
    val isDragging: Boolean get() = dragging

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        // Only seen while the window is focusable (the reply field is in
        // use): Back closes the keyboard and returns focus, nothing more.
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            if (event.action == KeyEvent.ACTION_UP && !event.isCanceled) onDismissKeyboard()
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        // FLAG_WATCH_OUTSIDE_TOUCH is set only while typing.
        if (ev.actionMasked == MotionEvent.ACTION_OUTSIDE) {
            onDismissKeyboard()
            return false
        }
        return super.dispatchTouchEvent(ev)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        onConfigChanged()
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> if (tracking && !dragging && pastSlop(ev)) startDrag()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> tracking = false
        }
        return dragging
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            // Nothing in the content took the press (empty card space): this
            // view gets the gesture directly, so it can still become a drag.
            MotionEvent.ACTION_DOWN -> begin(ev)
            MotionEvent.ACTION_MOVE -> {
                if (tracking && !dragging && pastSlop(ev)) startDrag()
                if (dragging) onDrag(ev.rawX - downX, ev.rawY - downY)
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (dragging) onDragEnd()
                dragging = false
                tracking = false
            }
        }
        return tracking || dragging
    }

    private fun begin(ev: MotionEvent) {
        downX = ev.rawX
        downY = ev.rawY
        tracking = true
        dragging = false
    }

    private fun pastSlop(ev: MotionEvent) = abs(ev.rawX - downX) > slop || abs(ev.rawY - downY) > slop

    private fun startDrag() {
        dragging = true
        parent?.requestDisallowInterceptTouchEvent(true)
        onDragStart()
    }
}
