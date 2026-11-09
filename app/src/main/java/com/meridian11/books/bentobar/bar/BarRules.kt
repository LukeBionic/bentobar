package com.meridian11.books.bentobar.bar

/**
 * A rectangle on the screen, in plain numbers (`android.graphics.Rect` is a stub in unit tests). It answers with the
 * names `Rect` does, so the code that places the strip reads the same either way.
 */
data class Box(val left: Int, val top: Int, val right: Int, val bottom: Int) {
    fun width() = right - left
    fun height() = bottom - top
    fun centerX() = (left + right) shr 1

    /** As `Rect.intersects`: strict on every side, so a box with no width still counts when it lies inside another. */
    fun intersects(other: Box) = left < other.right && other.left < right && top < other.bottom && other.top < bottom

    /** As `Rect.toShortString`, for the logs. */
    fun toShortString() = "[$left,$top][$right,$bottom]"
}

/**
 * A window as the window list gives it: what kind it is and where. Nothing of what is in it, so a list of these is all
 * [BarRules] ever sees of the windows, and a recorded dump replays through them.
 */
data class WindowFact(val id: Int, val kind: Kind, val box: Box) {
    enum class Kind { SYSTEM, APPLICATION, OVERLAY, OTHER }
}

/** One node of the status bar's tree, in the order the walk met it: the root first, with its depth below the root. */
data class BarNode(
    val id: String,
    val box: Box,
    val depth: Int,
    /** What it says. Looked at for the clock and dropped when the scan ends. */
    val text: String = "",
    /** Whether it has a content description. */
    val described: Boolean = false,
    val clickable: Boolean = false,
    val children: Int = 0,
)

/** Where the status bar is and which part of it is free: what one scan of its tree found. */
data class BarSnapshot(
    /** The status bar window's id, for window screenshots (colour sampling). */
    val windowId: Int,
    val bar: Box,
    /** Empty space BentoBar may use: the desktop bar's spacer, or the widest gap between items. */
    val free: Box,
    /** The clock text, whose colour BentoBar copies. */
    val clock: Box?,
    /** A short fingerprint of the bar's items, for logs. */
    val summary: String,
    /** Where the spacer node was at the scan. It is [free] unless the spacer had no width left. */
    val spacer: Box? = null,
)

/**
 * What the window list and the status bar's tree mean, as plain rules over plain facts. No Android types: the facts are
 * collected in `StatusBarScan` and `BarController.check`, and the cases the devices showed are unit-tested here.
 */
internal object BarRules {
    /** [BarSnapshot.summary] when the window had no node tree to read: its free area is unknown. */
    const val NO_TREE = "no-tree"
    private val timeRe = Regex("""\d{1,2}[:.]\d{2}""")

    /** The status bar window: a system window along the top edge, shorter than [maxHeight]. */
    fun findBar(listed: List<WindowFact>, screenWidth: Int, maxHeight: Int): WindowFact? =
        listed.firstOrNull { w -> w.kind == WindowFact.Kind.SYSTEM && w.box.top == 0 && w.box.height() in 1..maxHeight && w.box.width() >= screenWidth / 2 }

    /** Whether the node with this resource id is the desktop bar's spacer. */
    fun isSpacer(id: String) = id.endsWith("DesktopStatusBarSpacer")

    /**
     * What the tree of the status bar window [windowId], at [bar], says about the bar. [nodes] are in the walk's order;
     * none at all means the window had no tree to read.
     */
    fun parse(windowId: Int, bar: Box, nodes: List<BarNode>): BarSnapshot {
        if (nodes.isEmpty()) return BarSnapshot(windowId, bar, bar, null, NO_TREE)
        var spacer: Box? = null
        var clock: Box? = null
        val parts = ArrayList<Box>()
        val names = StringBuilder()
        for (n in nodes) {
            if (isSpacer(n.id)) spacer = n.box
            if (clock == null && n.text.isNotEmpty() && timeRe.containsMatchIn(n.text)) clock = n.box
            // Leaf-ish pieces that take room: anything clickable or labelled that isn't the whole bar.
            val labelled = n.clickable || n.described || n.text.isNotEmpty()
            if (labelled && n.box.width() in 1 until bar.width() * 6 / 10 && n.box.height() > 0) {
                parts += n.box
                if (n.depth <= 6 && n.id.isNotEmpty()) names.append(n.id.substringAfterLast('/')).append(' ')
            }
        }
        val free = spacer?.takeIf { it.width() > 0 } ?: widestGap(bar, parts)
        return BarSnapshot(windowId, bar, free, clock, names.toString().trim(), spacer)
    }

    /** The widest horizontal stretch of the bar that no item covers. */
    private fun widestGap(bar: Box, parts: List<Box>): Box {
        val spans = parts.map { it.left to it.right }.sortedBy { it.first }
        var best = Box(bar.left, bar.top, bar.left, bar.bottom)
        var cursor = bar.left
        for ((l, r) in spans) {
            if (l > cursor && l - cursor > best.width()) best = Box(cursor, bar.top, l, bar.bottom)
            cursor = maxOf(cursor, r)
        }
        if (bar.right - cursor > best.width()) best = Box(cursor, bar.top, bar.right, bar.bottom)
        return best
    }
}
