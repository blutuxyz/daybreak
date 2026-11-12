package com.bdersolutions.daybreak.data.account

import android.content.Context
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.IOException

/** The name and the monthly price of a paid plan, in cents of euro. */
data class PlanPrice(val label: String, val monthlyCents: Int)

/** Reads the answer of `GET /v1/plans`. Null unless both paid plans are there with sensible prices. */
object PlanPriceJson {
    fun parse(body: String?): Map<Plan, PlanPrice>? {
        if (body.isNullOrBlank()) return null
        return try {
            val root = JsonParser.parseString(body).asJsonObject
            fun read(key: String): PlanPrice? {
                val item = root.getAsJsonObject(key) ?: return null
                val cents = item.get("monthlyCents").asInt
                val label = item.get("label").asString
                return if (cents in 0..100_000 && label.isNotBlank()) PlanPrice(label, cents) else null
            }
            val plus = read("plus") ?: return null
            val pro = read("pro") ?: return null
            mapOf(Plan.PLUS to plus, Plan.PRO to pro)
        } catch (_: Exception) {
            null
        }
    }

    /** "2,99 €" in German, "€2.99" otherwise: the same form as the website. */
    fun format(cents: Int, german: Boolean): String {
        val euros = cents / 100
        val rest = (cents % 100).toString().padStart(2, '0')
        return if (german) "$euros,$rest €" else "€$euros.$rest"
    }
}

/** Keeps the last prices the server sent. Plain values, no secrets. */
interface PlanPriceCache {
    fun cached(): String?
    fun save(body: String)
}

class SharedPrefsPlanPriceCache(context: Context) : PlanPriceCache {
    private val preferences = context.getSharedPreferences("daybreak_plans", Context.MODE_PRIVATE)

    override fun cached(): String? = preferences.getString("prices", null)

    override fun save(body: String) {
        preferences.edit().putString("prices", body).apply()
    }
}

/**
 * The prices of Plus and Pro for the plans sheet. The last answer is kept, so prices still show offline.
 * Nothing is shown for a plan without a price. [fetch] reads `GET /v1/plans` and throws an IOException
 * when the server cannot be reached.
 */
class PlanPriceRepository(
    private val fetch: (suspend () -> String)?,
    private val cache: PlanPriceCache,
    private val enabled: Boolean = true,
) {
    private val _current = MutableStateFlow(compute())

    /** The price of each paid plan, or nothing when no price is known yet. */
    val current: StateFlow<Map<Plan, PlanPrice>> = _current.asStateFlow()

    suspend fun refresh() {
        val fetch = fetch ?: return
        if (!enabled) return
        val body = try {
            fetch()
        } catch (_: IOException) {
            return
        }
        val prices = PlanPriceJson.parse(body) ?: return
        cache.save(body)
        _current.value = prices
    }

    private fun compute(): Map<Plan, PlanPrice> =
        if (enabled) PlanPriceJson.parse(cache.cached()).orEmpty() else emptyMap()
}
