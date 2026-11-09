package com.meridian11.books.bentobar.data

// What the user can ask of an item's place, as steps from one layout to the next: the one rule for each command, whichever
// surface asks (the rows and the chooser in settings, the bar's right-click menu). A screen calls `Store.update { it.hide(id) }`
// and writes no section or Show when rule of its own, so one command can't place an item one way here and another way there
// (`LayoutIntentsTest` holds the screens to it). Pure; an id that isn't in the layout changes nothing.

/**
 * Into [section], at the end of it: where a command puts an item (a drag says where it goes, with [moved]). Already in
 * [section]: nothing moves, so the settings' chooser can ask for whichever chip was clicked.
 */
fun BarConfig.putIn(id: String, section: Section): BarConfig =
    if (items.none { it.id == id && it.section != section }) this else copy(items = items.moved(id, section, Int.MAX_VALUE))

fun BarConfig.show(id: String): BarConfig = putIn(id, Section.SHOWN)

fun BarConfig.hide(id: String): BarConfig = putIn(id, Section.HIDDEN)

fun BarConfig.turnOff(id: String): BarConfig = putIn(id, Section.OFF)

/**
 * The "Show when…" rule on or off. Only hidden items pop into the bar, so turning it on brings a shown or turned-off item
 * into Hidden, where it stands in the list: it pops out at the place it had in the bar, which a move to the end would lose.
 * Turning it off moves nothing.
 */
fun BarConfig.setWhenActive(id: String, on: Boolean): BarConfig = copy(items = items.map {
    if (it.id == id) it.copy(whenActive = on, section = if (on) Section.HIDDEN else it.section) else it
})
