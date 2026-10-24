package com.oro.app.device

import kotlinx.serialization.json.JsonObject

/**
 * What a call acts on, resolved once from the model's arguments.
 *
 * The safety guard and the executor both work from this one value, so the
 * thing the user is asked to approve is exactly the thing that gets tapped.
 * (They used to resolve separately: the guard read the index on the newest
 * screen while the executor re-mapped it from the screen the model saw, so a
 * row shifting under a multi-call turn could slip "Place order" past the
 * guard.)
 */
sealed interface Target {
    /** An element the model named, by number or by words. [receiver] is what really takes the tap. */
    data class OnElement(
        val element: ScreenElement,
        val receiver: ScreenElement,
        /** The x,y the model gave, when the tap was moved onto this small control ([TapPlan.snap]). */
        val snappedFrom: Pair<Int, Int>? = null,
    ) : Target

    /** A screen point. [element] is what a finger there would press, [text] what is written there. */
    data class AtPoint(val x: Int, val y: Int, val element: ScreenElement?, val text: ScreenElement?) : Target

    /** type_text without an index: the focused field, if one is focused. */
    data class Focused(val field: ScreenElement?) : Target

    /** The screen changed since the model chose this (or the app did). Nothing must be done. */
    data class Stale(val appChanged: Boolean = false) : Target

    /** The arguments name nothing usable. */
    data class Missing(val message: String) : Target

    /** tap_text found several equal matches, or ([weak]) only poor ones. */
    data class Choices(val query: String, val candidates: List<ScreenElement>, val weak: Boolean) : Target

    data class NotFound(val query: String) : Target
}

/**
 * Resolves targets. Pure: every rule here runs in JVM tests.
 *
 * [seen] is the screen the model decided on (its numbers refer to it), [now]
 * the screen as it is right before acting. Rules:
 *  - a different app in front means every target is stale;
 *  - a number is found again by what it was ([ElementRemap]), never by position;
 *  - a number that went stale is never replaced by the x,y that came with it;
 *  - a point is resolved to whatever a finger there would press, so it can be
 *    guarded like a named element.
 */
object Targets {

    /**
     * tap, double_tap, long_press: an index, else x,y. A point on, or a
     * fingertip from, one small plain control taps that control by its node
     * ([TapPlan.snap]); anything else keeps the point.
     */
    fun tap(args: JsonObject, seen: ScreenState?, now: ScreenState, long: Boolean = false): Target {
        appChange(seen, now)?.let { return it }
        if (DeviceArgs.has(args, "index")) return byIndex(args, "index", seen, now, long)
        val at = point(args, "x", "y", now)
            ?: return Target.Missing("Give an element index from the screen listing, or x and y.")
        val control = TapPlan.snap(now, at.x, at.y, at.element) ?: return at
        return Target.OnElement(control, control, snappedFrom = at.x to at.y)
    }

    /** tap_text: the element showing these words (`text`, or `label` as models also write it). */
    fun text(args: JsonObject, seen: ScreenState?, now: ScreenState): Target {
        appChange(seen, now)?.let { return it }
        val query = DeviceArgs.str(args, "text")?.takeIf { it.isNotBlank() }
            ?: DeviceArgs.str(args, "label")?.takeIf { it.isNotBlank() }
            ?: return Target.Missing("Missing text: the words shown on the button, row or link.")
        return when (val m = TextMatch.find(query, now, DeviceArgs.int(args, "nth"))) {
            is TextMatch.Result.Found -> Target.OnElement(m.element, now.receiverFor(m.element))
            is TextMatch.Result.Ambiguous -> Target.Choices(query, m.candidates, m.weak)
            TextMatch.Result.NotFound -> Target.NotFound(query)
        }
    }

    /** type_text: the field at `index`, else the focused one. */
    fun field(args: JsonObject, seen: ScreenState?, now: ScreenState): Target {
        appChange(seen, now)?.let { return it }
        if (DeviceArgs.has(args, "index")) return byIndex(args, "index", seen, now, long = false)
        return Target.Focused(now.elements.firstOrNull { it.editable && it.focused })
    }

    /** set_value: the slider at `index` (no coordinates: a value needs the slider's own range). */
    fun slider(args: JsonObject, seen: ScreenState?, now: ScreenState): Target {
        appChange(seen, now)?.let { return it }
        if (!DeviceArgs.has(args, "index")) return Target.Missing("Give the slider's element number from the screen listing.")
        return byIndex(args, "index", seen, now, long = false)
    }

    /** An optional element (scroll's list, drag's ends): null when the key is absent. */
    fun optional(args: JsonObject, key: String, seen: ScreenState?, now: ScreenState): Target? {
        if (!DeviceArgs.has(args, key)) return null
        appChange(seen, now)?.let { return it }
        return byIndex(args, key, seen, now, long = false)
    }

    /** A gesture's starting point given as coordinates (swipe, drag), for the guard. */
    fun start(args: JsonObject, xKey: String, yKey: String, seen: ScreenState?, now: ScreenState): Target? {
        appChange(seen, now)?.let { return it }
        return point(args, xKey, yKey, now)
    }

    /**
     * Whether [after] (resolved again once the user approved) still names what
     * the user approved in [before]. Anything else is not acted on.
     */
    fun same(before: Target, after: Target): Boolean = when {
        before is Target.OnElement && after is Target.OnElement ->
            sameThing(before.element, after.element) && sameThing(before.receiver, after.receiver)
        before is Target.AtPoint && after is Target.AtPoint ->
            before.x == after.x && before.y == after.y &&
                sameThing(before.element, after.element) && sameThing(before.text, after.text)
        before is Target.Focused && after is Target.Focused -> sameThing(before.field, after.field)
        else -> false
    }

    private fun sameThing(a: ScreenElement?, b: ScreenElement?): Boolean = when {
        a == null || b == null -> a == null && b == null
        else -> a.role == b.role && a.label == b.label && a.window == b.window && a.viewId == b.viewId
    }

    private fun appChange(seen: ScreenState?, now: ScreenState): Target? =
        if (seen != null && seen !== now && seen.packageName.isNotEmpty() && now.packageName.isNotEmpty() &&
            seen.packageName != now.packageName
        ) Target.Stale(appChanged = true) else null

    private fun byIndex(args: JsonObject, key: String, seen: ScreenState?, now: ScreenState, long: Boolean): Target {
        val index = DeviceArgs.int(args, key)
            ?: return Target.Missing("\"$key\" must be an element number from the screen listing.")
        val (match, stale) = ElementRemap.resolve(seen ?: now, now, index)
        return when {
            match != null -> Target.OnElement(match, now.receiverFor(match, long))
            stale -> Target.Stale()
            else -> Target.Missing(
                "There is no element [$index]. Valid numbers are 0-${(now.elements.size - 1).coerceAtLeast(0)}."
            )
        }
    }

    private fun point(args: JsonObject, xKey: String, yKey: String, now: ScreenState): Target.AtPoint? {
        val x = DeviceArgs.int(args, xKey) ?: return null
        val y = DeviceArgs.int(args, yKey) ?: return null
        return Target.AtPoint(x, y, now.hitTest(x, y), now.textAt(x, y))
    }
}
