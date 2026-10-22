package com.oro.app.device.overlay

import android.annotation.SuppressLint
import android.content.Context
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
 */
internal class DragFrame(
    context: Context,
    private val onDragStart: () -> Unit,
    private val onDrag: (dx: Float, dy: Float) -> Unit,
    private val onDragEnd: () -> Unit,
) : FrameLayout(context) {

    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private var downX = 0f
    private var downY = 0f
    private var tracking = false
    private var dragging = false

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
