package com.meridian11.books.bentobar.data

import com.meridian11.books.bentobar.SourceScan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** One command places an item one way, whichever surface asks. */
class LayoutIntentsTest {
    private val shown = ItemConfig("s", "clock", Section.SHOWN)
    private val shown2 = ItemConfig("s2", "clock", Section.SHOWN)
    private val hidden = ItemConfig("h", "clock", Section.HIDDEN)
    private val off = ItemConfig("o", "clock", Section.OFF)
    private val layout = BarConfig(items = listOf(shown, shown2, hidden, off))
    private fun BarConfig.ids() = items.map { it.id }
    private fun BarConfig.at(id: String) = items.single { it.id == id }

    @Test fun hidingPutsAnItemAtTheEndOfHidden() {
        val c = layout.hide("s")
        assertEquals(Section.HIDDEN, c.at("s").section)
        assertEquals(listOf("s2", "h", "s", "o"), c.ids())
    }

    @Test fun showingAndTurningOffPutAnItemAtTheEndOfTheirSections() {
        val back = layout.show("h")
        assertEquals(Section.SHOWN, back.at("h").section)
        assertEquals(listOf("s", "s2", "h", "o"), back.ids())
        val gone = layout.turnOff("s2")
        assertEquals(Section.OFF, gone.at("s2").section)
        assertEquals(listOf("s", "h", "o", "s2"), gone.ids())
    }

    @Test fun anEmptySectionTakesTheItemAtTheEndOfTheList() {
        val c = BarConfig(items = listOf(shown, off)).hide("s")
        assertEquals(listOf("o", "s"), c.ids())
    }

    @Test fun aCommandForTheSectionAnItemIsAlreadyInMovesNothing() {
        // The settings' Where chooser asks for the section whichever chip is clicked: the selected one must not reorder.
        assertSame(layout, layout.show("s"))
        assertSame(layout, layout.hide("h"))
        assertSame(layout, layout.turnOff("o"))
    }

    @Test fun anIdThatIsNotInTheLayoutChangesNothing() {
        assertSame(layout, layout.hide("nope"))
        assertEquals(layout, layout.setWhenActive("nope", true))
    }

    @Test fun theRuleBringsAShownOrTurnedOffItemIntoHiddenWhereItIs() {
        // Both the bar's menu and settings do this: where it is, because it pops out at the place it had in the bar.
        val fromShown = layout.setWhenActive("s", true)
        assertEquals(Section.HIDDEN, fromShown.at("s").section)
        assertTrue(fromShown.at("s").whenActive)
        assertEquals(layout.ids(), fromShown.ids())
        // Only hidden items pop out, so a turned-off one comes too (the bar's menu used to leave it Off).
        val fromOff = layout.setWhenActive("o", true)
        assertEquals(Section.HIDDEN, fromOff.at("o").section)
        assertTrue(fromOff.at("o").whenActive)
    }

    @Test fun turningTheRuleOffLeavesAnItemWhereItIs() {
        val c = layout.setWhenActive("h", true).setWhenActive("h", false)
        assertEquals(Section.HIDDEN, c.at("h").section)
        assertEquals(false, c.at("h").whenActive)
        val stayed = layout.copy(items = layout.items.map { if (it.id == "s") it.copy(whenActive = true) else it }).setWhenActive("s", false)
        assertEquals(Section.SHOWN, stayed.at("s").section)
    }

    @Test fun anItemsRuleStaysOnThroughACommandThatMovesIt() {
        val rule = layout.setWhenActive("h", true)
        assertTrue(rule.turnOff("h").at("h").whenActive)
        assertTrue(rule.show("h").at("h").whenActive)
    }

    @Test fun noScreenWritesASectionOrARuleOfItsOwn() {
        val handlers = SourceScan.sources.filter { val p = it.path.replace('\\', '/'); "/bentobar/ui/" in p || "/bentobar/bar/" in p }
        assertTrue(handlers.size > 10)
        val byHand = Regex("""\.copy\([^)]*\b(section|whenActive)\s*=|,\s*999\s*\)""")
        val found = handlers.filter { byHand.containsMatchIn(SourceScan.code(it)) }.map { it.name }
        assertEquals("a section or a Show when rule is written in a screen: add a Layout intent instead", emptyList<String>(), found)
    }
}
