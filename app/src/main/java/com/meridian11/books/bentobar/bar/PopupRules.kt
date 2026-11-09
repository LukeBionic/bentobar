package com.meridian11.books.bentobar.bar

/** Which popup is meant: the ‹ menu, an item's popup, or an item's right-click menu. [toString] is what the debug hooks print. */
sealed interface MenuKey {
    /** What the popup belongs to, under the name the strip knows it by: an item's id, or "chevron" for ‹. */
    val owner: String

    data object Bar : MenuKey {
        override val owner = "chevron"
        override fun toString() = "bentobar"
    }

    data class Item(val id: String) : MenuKey {
        override val owner get() = id
        override fun toString() = "item:$id"
    }

    data class ItemContext(val id: String) : MenuKey {
        override val owner get() = id
        override fun toString() = "ctx:$id"
    }
}

/** When pointing at an item opens its popup. Pure, so the rules are tested (`PopupRulesTest`). */
internal object PopupRules {
    /**
     * Whether the pointer coming onto item [hovered] opens its popup: only with "Switch popups on hover" [on] (off by
     * default: nothing opens on hover by itself), only while an item's popup or the ‹ menu is [open], never for the
     * item already open, never for a right-click menu, and only for an item that [hasPopup]: one that acts on a click
     * (keep awake, a Shortcut) must not act on a hover.
     */
    fun hoverOpens(open: MenuKey?, hovered: String, hasPopup: Boolean, on: Boolean): Boolean {
        if (!on || open == null || !hasPopup) return false
        if (open is MenuKey.ItemContext) return false
        return open != MenuKey.Item(hovered)
    }

    /**
     * The item a press at [x] on the strip belongs to, of the items [drawn] now: the highlight's own rule, a gap going to
     * the item on its side of the middle. An item that has gone must not be in [drawn]: its old box would take the press.
     */
    fun pressedItem(x: Float, drawn: List<Span>): String? = StripHighlight.target(x, drawn.sortedBy { it.left }, null)
}
