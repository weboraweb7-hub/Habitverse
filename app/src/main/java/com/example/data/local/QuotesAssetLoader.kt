package com.example.data.local

import android.content.Context
import com.example.domain.model.Quote
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class QuotesAssetLoader @Inject constructor(
    @ApplicationContext private val context: Context,
    private val json: Json
) {
    private val quotes: List<Quote> by lazy {
        val raw = context.assets.open("quotes.json").bufferedReader().use { it.readText() }
        json.decodeFromString<List<Quote>>(raw)
    }

    fun getAllQuotes(): List<Quote> = quotes

    fun getQuoteForDate(date: LocalDate, tapOffset: Int = 0): Quote {
        val list = quotes
        if (list.isEmpty()) {
            return Quote(
                text = "Habits are the compound interest of self-improvement.",
                book = "Atomic Habits",
                author = "James Clear"
            )
        }
        val baseIndex = (date.year * 366 + date.dayOfYear) % list.size
        val index = Math.floorMod(baseIndex + tapOffset, list.size)
        return list[index]
    }
}
