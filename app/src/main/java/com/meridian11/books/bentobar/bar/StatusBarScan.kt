package com.meridian11.books.bentobar.bar

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import android.view.accessibility.AccessibilityWindowInfo

/** Android's rectangle as a plain [Box], and back. */
internal fun Rect.box() = Box(left, top, right, bottom)
internal fun Box.rect() = Rect(left, top, right, bottom)

/**
 * Reads the accessibility tree into plain facts: the window list as [WindowFact]s, SystemUI's status bar window as
 * [BarNode]s, which [BarRules] turns into a [BarSnapshot]. The status bar is the only window BentoBar ever reads;
 * app windows are skipped.
 */
object StatusBarScan {
    /** What a scan brings back: the plain [snapshot], and the live spacer node (a cheap "did anything move?" check for [BarController]). */
    class Scanned(val snapshot: BarSnapshot, val spacerNode: AccessibilityNodeInfo?)

    /** What [BarRules] may know of [w]: its kind, its id and where it is. */
    fun fact(w: AccessibilityWindowInfo): WindowFact {
        val kind = when (w.type) {
            AccessibilityWindowInfo.TYPE_SYSTEM -> WindowFact.Kind.SYSTEM
            AccessibilityWindowInfo.TYPE_APPLICATION -> WindowFact.Kind.APPLICATION
            AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY -> WindowFact.Kind.OVERLAY
            else -> WindowFact.Kind.OTHER
        }
        return WindowFact(w.id, kind, Rect().also { w.getBoundsInScreen(it) }.box())
    }

    fun scan(w: AccessibilityWindowInfo): Scanned {
        val bar = Rect().also { w.getBoundsInScreen(it) }.box()
        // One round trip: prefetch the (small, ~35 node) tree instead of an IPC per getChild().
        val root = w.getRoot(AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST or
            AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE) ?: return Scanned(BarRules.parse(w.id, bar, emptyList()), null)
        var spacerNode: AccessibilityNodeInfo? = null
        val nodes = ArrayList<BarNode>()
        val stack = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        stack.add(root to 0)
        while (stack.isNotEmpty() && nodes.size < 400) {
            val (n, depth) = stack.removeLast()
            val id = n.viewIdResourceName.orEmpty()
            if (BarRules.isSpacer(id)) spacerNode = n
            nodes += BarNode(id, Rect().also { n.getBoundsInScreen(it) }.box(), depth, n.text?.toString().orEmpty(),
                described = !n.contentDescription.isNullOrEmpty(), clickable = n.isClickable, children = n.childCount)
            for (i in n.childCount - 1 downTo 0) n.getChild(i)?.let { stack.add(it to depth + 1) }
        }
        return Scanned(BarRules.parse(w.id, bar, nodes), spacerNode)
    }
}
