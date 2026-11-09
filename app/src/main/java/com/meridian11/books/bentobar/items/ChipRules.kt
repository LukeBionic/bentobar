package com.meridian11.books.bentobar.items

/** When the Live Update chip's meeting changes and what it needs for that. Pure: [Chips] does the Android part. */
internal object ChipRules {
    /** A meeting gets its chip this long before it starts. */
    const val LEAD_MS = 15 * 60_000L

    /** Whether the chip can be a meeting's right now, so it needs the calendar. The chip works while the service is off, so it is one of the calendar's users. */
    fun needsCalendar(notifies: Boolean, timerRuns: Boolean, meetingItem: Boolean): Boolean = notifies && !timerRuns && meetingItem

    /** The next time the meeting chip would change: a meeting entering the 15-minute window, starting or ending. */
    fun nextChange(meetings: List<Calendar.Event>, now: Long): Long? =
        meetings.flatMap { listOf(it.begin - LEAD_MS, it.begin, it.end) }.filter { it > now }.minOrNull()

    /** After how long a meeting's chip goes by itself: at its end, even with no process left to take it away. Never 0, which would mean never. */
    fun timeoutAfter(end: Long, now: Long): Long = (end - now).coerceAtLeast(1)
}
