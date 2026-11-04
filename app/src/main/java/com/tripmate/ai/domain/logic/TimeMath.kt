package com.tripmate.ai.domain.logic

object TimeMath {
    const val MINUTES_PER_DAY = 24 * 60
    fun hhmm(minutes: Int): String {
        val m = ((minutes % MINUTES_PER_DAY) + MINUTES_PER_DAY) % MINUTES_PER_DAY
        return "%02d:%02d".format(m / 60, m % 60)
    }
    fun roundUpTo5(minutes: Int): Int = ((minutes + 4) / 5) * 5
}
