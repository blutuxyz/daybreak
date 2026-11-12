package com.bdersolutions.daybreak.data.account

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class PlanPriceTest {
    private val answer = """{"plus":{"label":"Plus","monthlyCents":299},"pro":{"label":"Pro","monthlyCents":599}}"""

    @Test
    fun theAnswerGivesThePricesOfBothPaidPlans() {
        val prices = PlanPriceJson.parse(answer)
        assertEquals(PlanPrice("Plus", 299), prices?.get(Plan.PLUS))
        assertEquals(PlanPrice("Pro", 599), prices?.get(Plan.PRO))
    }

    @Test
    fun anIncompleteOrBrokenAnswerGivesNothing() {
        assertNull(PlanPriceJson.parse("""{"plus":{"label":"Plus","monthlyCents":299}}"""))
        assertNull(PlanPriceJson.parse("""{"plus":{"label":"","monthlyCents":299},"pro":{"label":"Pro","monthlyCents":599}}"""))
        assertNull(PlanPriceJson.parse("not json"))
        assertNull(PlanPriceJson.parse(null))
    }

    @Test
    fun pricesAreShownLikeTheWebsite() {
        assertEquals("2,99 €", PlanPriceJson.format(299, german = true))
        assertEquals("€2.99", PlanPriceJson.format(299, german = false))
        assertEquals("5,05 €", PlanPriceJson.format(505, german = true))
        assertEquals("€0.00", PlanPriceJson.format(0, german = false))
    }

    @Test
    fun aRepositoryShowsTheCachedPricesOfflineAndTheFetchedOnesWhenOnline() = kotlinx.coroutines.runBlocking {
        val cache = object : PlanPriceCache {
            var body: String? = null
            override fun cached() = body
            override fun save(body: String) {
                this.body = body
            }
        }
        val repository = PlanPriceRepository(fetch = { answer }, cache = cache)
        assertEquals(emptyMap<Plan, PlanPrice>(), repository.current.value)
        repository.refresh()
        assertEquals(PlanPrice("Pro", 599), repository.current.value[Plan.PRO])

        val offline = PlanPriceRepository(fetch = { throw java.io.IOException("offline") }, cache = cache)
        offline.refresh()
        assertEquals(PlanPrice("Plus", 299), offline.current.value[Plan.PLUS])
    }
}
