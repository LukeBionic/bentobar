package com.meridian11.books.bentobar.bar

/**
 * One press on an item of the strip, or on ‹: from the pointer going down to its letting go, what comes of it. Apart from
 * the pointer events, so that the rules are unit-tested (`ClickGestureTest`), as [SliderGesture]'s are.
 *
 * A primary press is a click when it lets go inside the item, and moved past the touch slop it becomes a drag, if the item can
 * be dragged. A secondary press is the item's menu. A finger held still for the system's long-press time is the menu too, at
 * that moment and not when it lifts, so the hold says what it did; what the finger does after that is nothing to this press.
 *
 * A press that something inside the item took for itself (the slider's track) is none of the item's: no click and no drag
 * come of it. A press the system takes away arrives as a Release already consumed: never a click and never a drop.
 */
internal class ClickGesture(private val longPressMs: Long, private val slop: Float) {
    /** What the strip does about what just happened. */
    sealed interface Did {
        data object Nothing : Did
        data object Click : Did
        data object Menu : Did
        /** The drag follows the pointer, which is at [x]. The first of these is its beginning. */
        data class Drag(val x: Float) : Did
        /** The pointer let go of the drag at [x]: where the item lands. */
        data class Drop(val x: Float) : Did
        /** The drag was taken away: nothing lands. */
        data object DragTakenAway : Did
    }

    /** A press is under way that is this item's. */
    var armed = false; private set
    /** It became a drag. */
    var dragging = false; private set
    /** Where the press went down. */
    var downX = 0f; private set
    private var secondary = false
    private var touch = false
    private var moved = false
    private var downAt = 0L
    private var lastAt = 0L

    /**
     * How long the finger may stay as it is before it counts as a hold, from the last event seen; null when no hold is being
     * waited for (nothing pressed, a mouse, a finger that has moved).
     */
    val holdMs: Long? get() = if (!armed || !touch || secondary || moved) null else (longPressMs - (lastAt - downAt)).coerceAtLeast(0)

    /** The pointer went down at [x]. [taken]: something inside the item has it already. */
    fun press(taken: Boolean, secondary: Boolean, touch: Boolean, x: Float, at: Long) {
        reset()
        if (taken) return
        armed = true
        this.secondary = secondary
        this.touch = touch
        downX = x
        downAt = at
        lastAt = at
    }

    /** The pointer moved. Only a primary press that has gone past the slop drags, and only an item that [draggable] says can. */
    fun move(x: Float, pressed: Boolean, taken: Boolean, draggable: Boolean, at: Long): Did {
        if (!armed) return Did.Nothing
        lastAt = at
        if (!pressed || taken) return Did.Nothing
        if (kotlin.math.abs(x - downX) > slop) moved = true
        if (secondary || !draggable) return Did.Nothing
        if (moved) dragging = true
        return if (dragging) Did.Drag(x) else Did.Nothing
    }

    /** The pointer let go at [x]. [taken]: the system took the press away, which arrives as a Release already consumed. */
    fun release(x: Float, inside: Boolean, taken: Boolean): Did {
        if (!armed) return Did.Nothing
        val drag = dragging
        val menu = secondary
        reset()
        return when {
            taken -> if (drag) Did.DragTakenAway else Did.Nothing
            drag -> Did.Drop(x)
            !inside -> Did.Nothing
            menu -> Did.Menu
            else -> Did.Click
        }
    }

    /** The finger stayed for [holdMs]: the item's menu, now. Lifting it afterwards does nothing more. */
    fun held(): Did {
        if (holdMs == null) return Did.Nothing
        reset()
        return Did.Menu
    }

    /** The gesture ended with a press under way (the item left the strip): a drag in progress must not stay behind. */
    fun cancel(): Did {
        val drag = dragging
        reset()
        return if (drag) Did.DragTakenAway else Did.Nothing
    }

    private fun reset() { armed = false; dragging = false; secondary = false; touch = false; moved = false }
}
