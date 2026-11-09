package com.meridian11.books.bentobar.bar

import com.meridian11.books.bentobar.data.Display
import com.meridian11.books.bentobar.data.Pill
import com.meridian11.books.bentobar.items.BarRoute
import com.meridian11.books.bentobar.items.BarSlider
import com.meridian11.books.bentobar.items.ItemState
import com.meridian11.books.bentobar.items.Tone

/**
 * Whether the strip has anything on the screen. One rule, used by [BarController] for its window: never from how many
 * entries it has, because an entry can draw nothing (a spacer without its line, a text item shown as an icon it doesn't
 * have). An empty strip's window is made invisible rather than left up as a transparent one, which is what Play Protect's
 * threat detection flagged (AGENTS.md); the window keeps its exact width and keeps being measured. Pure, so the rule is
 * unit-tested; what an item draws here is what [ItemView] draws.
 */
internal object StripContent {
    /**
     * The parts of an item in the strip. A slider takes the text's place ("Icon and text" draws icon and slider, "Text" the slider
     * alone; shown as an icon only, the item has none). A route line takes the icon's place ("Icon and text" draws line and
     * words, "Icon" the line alone; shown as text only, the item has none). [icon] and [text] say whether ItemView lays those out.
     */
    data class Parts(val slider: BarSlider?, val route: BarRoute?, val icon: Boolean, val text: Boolean)

    fun parts(state: ItemState, display: Display): Parts {
        val slider = state.slider?.takeIf { display != Display.ICON }
        val route = state.route?.takeIf { display.line }
        return Parts(slider, route,
            icon = route == null && (display != Display.TEXT || state.text.isNullOrEmpty()),
            text = slider == null && display != Display.ICON && !state.text.isNullOrEmpty())
    }

    /** Whether an item of [type], shown as [display], puts anything on the screen in this [state]. */
    fun draws(type: String, display: Display, state: ItemState): Boolean {
        if (type == "spacer") return state.divider
        if (state.tone == Tone.ALERT) return true // its red capsule, around whatever is in it
        val parts = parts(state, display)
        return parts.slider != null || parts.route != null ||
            (parts.icon && (state.image != null || state.dayNumber != null || !state.icon.isNullOrEmpty())) ||
            (parts.text && !state.text.isNullOrBlank())
    }

    /** Whether the strip draws anything: an item that does, ‹, or the pill (which stays up as an empty capsule, as it always has). */
    fun drawn(entries: List<StripEntry>, chevron: Boolean, pill: Pill): Boolean =
        chevron || pill != Pill.NONE || entries.any { draws(it.item.type, it.item.display, it.state) }

    /** The strip's window is made invisible: nothing is drawn. [width] is only for sizing; 0 means nothing was measured to show. */
    fun empty(width: Int, drawn: Boolean) = width <= 0 || !drawn

    /** What Settings says about a strip that is on screen. */
    fun status(empty: Boolean, noRoom: Boolean) = when {
        empty -> BarStatus.EMPTY
        noRoom -> BarStatus.NO_ROOM
        else -> BarStatus.SHOWN
    }
}
