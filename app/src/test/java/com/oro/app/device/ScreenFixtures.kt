package com.oro.app.device

/** Small builders for screens in tests: no Android types involved. */
internal object ScreenFixtures {
    fun el(
        index: Int,
        role: String,
        label: String,
        top: Int = index * 100,
        left: Int = 0,
        right: Int = 1080,
        height: Int = 90,
        clickable: Boolean = role != ScreenElement.ROLE_TEXT,
        editable: Boolean = role == "input",
        value: String? = null,
        window: String? = null,
        checkable: Boolean = false,
        checked: Boolean = false,
        scrollable: Boolean = role == ScreenElement.ROLE_LIST,
        password: Boolean = false,
        viewId: String? = null,
    ) = ScreenElement(
        index = index, role = role, label = label, bounds = Bounds(left, top, right, top + height),
        clickable = clickable, editable = editable, value = value, window = window,
        checkable = checkable, checked = checked, scrollable = scrollable, password = password, viewId = viewId,
    )

    fun screen(vararg elements: ScreenElement, pkg: String = "com.example", label: String = "Example", keyboard: Boolean = false) =
        ScreenState(pkg, label, 1080, 2400, keyboard, elements.toList(), truncated = 0)
}
