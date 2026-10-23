package com.hcwebhook.app

/** One beat of the 24-hour pulse line: its position (0 to 1) and how its deliveries went. */
data class Beat(val x: Float, val anyOk: Boolean, val anyFailed: Boolean)

/**
 * The beats of the pulse line for deliveries ([time] ms, success) between [start] and [end].
 * Deliveries closer than [minGap] (a fraction of the width) make one beat, so a busy hour stays
 * readable. One sync goes to every webhook at the same time: the beat goes up when any delivery
 * went through, with a dot below when some failed, and down only when all of them failed.
 */
fun pulseBeats(events: List<Pair<Long, Boolean>>, start: Long, end: Long, minGap: Float): List<Beat> {
    if (end <= start) return emptyList()
    val beats = mutableListOf<Beat>()
    events.sortedBy { it.first }.forEach { (time, ok) ->
        val x = ((time - start).toFloat() / (end - start)).coerceIn(0f, 1f)
        val last = beats.lastOrNull()
        if (last == null || x - last.x > minGap) beats += Beat(x, anyOk = ok, anyFailed = !ok)
        else beats[beats.lastIndex] = last.copy(anyOk = last.anyOk || ok, anyFailed = last.anyFailed || !ok)
    }
    return beats
}
