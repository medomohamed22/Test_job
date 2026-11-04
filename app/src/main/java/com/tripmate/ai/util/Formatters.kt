package com.tripmate.ai.util

import android.content.Context
import com.tripmate.ai.R
import java.text.NumberFormat
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Currency
import java.util.Locale

/** Locale-aware formatting. Uses the app's current locale so Arabic shows Arabic formatting. */
object Formatters {
    fun locale(context: Context): Locale = context.resources.configuration.locales[0]

    fun time(context: Context, minutes: Int): String {
        val m = ((minutes % 1440) + 1440) % 1440
        return LocalTime.of(m / 60, m % 60).format(DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT).withLocale(locale(context)))
    }

    fun date(context: Context, date: LocalDate, style: FormatStyle = FormatStyle.MEDIUM): String =
        date.format(DateTimeFormatter.ofLocalizedDate(style).withLocale(locale(context)))

    fun dayShort(context: Context, date: LocalDate): String =
        date.format(DateTimeFormatter.ofPattern("EEE d MMM", locale(context)))

    fun money(context: Context, amount: Double, currencyCode: String): String = runCatching {
        NumberFormat.getCurrencyInstance(locale(context)).apply {
            currency = Currency.getInstance(currencyCode)
            maximumFractionDigits = if (amount % 1.0 == 0.0) 0 else 2
        }.format(amount)
    }.getOrElse { "%.2f %s".format(amount, currencyCode) }

    fun number(context: Context, value: Number): String = NumberFormat.getInstance(locale(context)).format(value)

    fun duration(context: Context, minutes: Int): String {
        val h = minutes / 60
        val m = minutes % 60
        return when {
            h > 0 && m > 0 -> context.getString(R.string.duration_h_m, h, m)
            h > 0 -> context.getString(R.string.duration_h, h)
            else -> context.getString(R.string.duration_m, m)
        }
    }

    val currencies = listOf("EGP", "USD", "EUR", "GBP", "SAR", "AED")
}
