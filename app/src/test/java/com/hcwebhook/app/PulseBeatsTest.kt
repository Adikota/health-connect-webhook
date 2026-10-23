package com.hcwebhook.app

import org.junit.Assert.assertEquals
import org.junit.Test

class PulseBeatsTest {

    @Test
    fun oneSyncToTwoWebhooks_isOneBeatUpWithFailureDot() {
        val beats = pulseBeats(listOf(500L to true, 501L to false), start = 0, end = 1000, minGap = 0.01f)

        assertEquals(listOf(Beat(0.5f, anyOk = true, anyFailed = true)), beats)
    }

    @Test
    fun allFailed_isBeatDown() {
        val beats = pulseBeats(listOf(100L to false, 101L to false), start = 0, end = 1000, minGap = 0.01f)

        assertEquals(listOf(Beat(0.1f, anyOk = false, anyFailed = true)), beats)
    }

    @Test
    fun farApart_areSeparateBeats_andOldEventsClampToStart() {
        val beats = pulseBeats(listOf(-50L to true, 900L to true), start = 0, end = 1000, minGap = 0.01f)

        assertEquals(listOf(0f, 0.9f), beats.map { it.x })
    }
}
