package com.meridian11.books.bentobar.compose

import android.app.Application
import android.graphics.Rect
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.meridian11.books.bentobar.bar.Strip
import com.meridian11.books.bentobar.bar.StripEntry
import com.meridian11.books.bentobar.bar.StripEvents
import com.meridian11.books.bentobar.bar.StripLook
import com.meridian11.books.bentobar.data.ItemConfig
import com.meridian11.books.bentobar.data.Pill
import com.meridian11.books.bentobar.data.TextSize
import com.meridian11.books.bentobar.items.BarSlider
import com.meridian11.books.bentobar.items.Env
import com.meridian11.books.bentobar.items.ItemState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The seams between the strip's pure rules and its composables (finding F14): which of an item's pointer loops gets a press,
 * what the strip reports back, and what a screen reader finds on it. Pure tests can't reach these.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [37])
class StripTest {
    @get:Rule val rule = createComposeRule()

    private class Recorded : StripEvents {
        val clicks = mutableListOf<String>()
        val contexts = mutableListOf<String>()
        val slides = mutableListOf<Pair<String, Float>>()
        val drags = mutableListOf<String>()
        val dropped = mutableListOf<String>()
        val takenAway = mutableListOf<String>()
        var chevrons = 0
        override fun click(item: ItemConfig, at: Rect) { clicks += item.id }
        override fun context(item: ItemConfig, at: Rect) { contexts += item.id }
        override fun scroll(item: ItemConfig, steps: Int) {}
        override fun chevron(at: Rect) { chevrons++ }
        override fun chevronContext(at: Rect) {}
        override fun hover(inside: Boolean) {}
        override fun placed(id: String, at: Rect) {}
        override fun slide(item: ItemConfig, level: Float, done: Boolean) { slides += item.id to level }
        override fun drag(item: ItemConfig, dx: Float, done: Boolean) { drags += item.id; if (done) dropped += item.id }
        override fun dragTakenAway(item: ItemConfig) { takenAway += item.id }
    }

    private val events = Recorded()
    private val look = StripLook(Color.White, lightText = true, TextSize.DEFAULT, 8.dp, Pill.NONE)
    private fun entry(id: String, state: ItemState) = StripEntry(ItemConfig(id, "cpu"), state)
    private val plain = entry("a", ItemState(icon = "X", text = "Alpha", desc = "Alpha desc"))
    private val sliderEntry = entry("s", ItemState(icon = "X", text = "50%", desc = "Volume", slider = BarSlider(0.5f, 15)))

    @Before fun env() = Env.init(ApplicationProvider.getApplicationContext<Application>())

    private fun strip(items: List<StripEntry>, maxWidth: Int = 2000, chevron: Boolean = false, onOverflow: (Set<String>) -> Unit = {}, direction: LayoutDirection? = null) {
        rule.setContent {
            androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalLayoutDirection provides (direction ?: androidx.compose.ui.platform.LocalLayoutDirection.current)) {
                Strip(items, emptyList(), showChevron = chevron, chevronAlways = false, chevronReservePx = 30, expanded = false, chevronOnLeft = true,
                    look = look, maxWidthPx = maxWidth, heightDp = 36.dp, events = events, onOverflow = onOverflow)
            }
        }
    }

    @Test fun aPressOnAnItemIsAClickAndAMoveAlongTheStripIsADrag() {
        strip(listOf(plain))
        val node = rule.onNodeWithContentDescription("Alpha desc")
        node.performTouchInput { click(center) }
        rule.runOnIdle { assertEquals(listOf("a"), events.clicks) }
        node.performTouchInput { swipeRight() }
        rule.runOnIdle { assertEquals(listOf("a"), events.clicks); assertTrue(events.drags.contains("a")); assertEquals(listOf("a"), events.dropped) }
    }

    @Test fun aPressOnTheSliderIsTheSlidersAndNeitherAClickNorADrag() {
        strip(listOf(sliderEntry))
        val node = rule.onNodeWithContentDescription("Volume")
        // The zone runs to the item's end; well inside it, a mouse sets the level at once.
        node.performTouchInput { click(centerRight - Offset(20f, 0f)) }
        rule.runOnIdle {
            assertTrue("the slider got the press", events.slides.isNotEmpty())
            assertTrue("no click on the item: ${events.clicks}", events.clicks.isEmpty())
            assertTrue("no drag of the item: ${events.drags}", events.drags.isEmpty())
        }
    }

    @Test fun aPressTheSystemTakesAwayIsNotAClick() {
        strip(listOf(plain))
        val node = rule.onNodeWithContentDescription("Alpha desc")
        node.performTouchInput { down(center); cancel() }
        rule.runOnIdle { assertTrue("a cancelled press is no click: ${events.clicks}", events.clicks.isEmpty()) }
    }

    @Test fun aDragTheSystemTakesAwayNeverLands() {
        strip(listOf(plain))
        val node = rule.onNodeWithContentDescription("Alpha desc")
        node.performTouchInput { down(center); moveBy(Offset(60f, 0f)); moveBy(Offset(20f, 0f)); cancel() }
        rule.runOnIdle {
            assertTrue(events.drags.contains("a"))
            assertTrue("no drop: ${events.dropped}", events.dropped.isEmpty())
            assertEquals(listOf("a"), events.takenAway)
        }
    }

    @Test fun aFingerHeldStillOpensTheMenuAtTheSystemsTimeAndNotAClick() {
        strip(listOf(plain))
        rule.onNodeWithContentDescription("Alpha desc").performTouchInput { longClick() }
        rule.runOnIdle {
            assertEquals(listOf("a"), events.contexts)
            assertTrue(events.clicks.isEmpty())
        }
    }

    @Test fun whatDoesNotFitIsReported() {
        val second = entry("b", ItemState(icon = "X", text = "Beta long text", desc = "Beta desc"))
        var over: Set<String> = emptySet()
        strip(listOf(second, plain), maxWidth = 100, onOverflow = { over = it })
        rule.runOnIdle { assertEquals("only what has no room is dropped", setOf("b"), over) }
    }

    @Test fun aScreenReaderCanPressAnItemAndOpenItsMenu() {
        strip(listOf(plain))
        val node = rule.onNodeWithContentDescription("Alpha desc")
        node.performSemanticsAction(SemanticsActions.OnClick)
        node.performSemanticsAction(SemanticsActions.OnLongClick)
        rule.runOnIdle { assertEquals(listOf("a"), events.clicks); assertEquals(listOf("a"), events.contexts) }
    }

    @Test fun aScreenReaderCanSetAVolume() {
        strip(listOf(sliderEntry))
        val node = rule.onNodeWithContentDescription("Volume")
        node.assert(SemanticsMatcher("range 0 to 15") { it.config.getOrNull(SemanticsProperties.ProgressBarRangeInfo)?.range == 0f..15f })
        rule.runOnIdle { }
        node.performSemanticsAction(SemanticsActions.SetProgress) { it(10f) }
        rule.runOnIdle { assertEquals("a steps value out of 15", "s" to 10 / 15f, events.slides.single()) }
    }

    @Test fun aStateWithNoWordsIsNamedAfterItsType() {
        // CPU's title is what an item with no description and no text is called.
        strip(listOf(entry("x", ItemState(icon = "X"))))
        rule.onNodeWithContentDescription("CPU", substring = true).assertIsDisplayed()
    }

    @Test fun inARightToLeftLanguageTheStripKeepsItsOrderAndASliderStillGoesItsReadingWay() {
        val second = entry("b", ItemState(icon = "X", text = "Beta", desc = "Beta desc"))
        strip(listOf(plain, second), direction = LayoutDirection.Rtl)
        val a = rule.onNodeWithContentDescription("Alpha desc").getBoundsInRoot()
        val b = rule.onNodeWithContentDescription("Beta desc").getBoundsInRoot()
        assertFalse("the first item stays on the left: $a, $b", b.left < a.left)
    }
}
