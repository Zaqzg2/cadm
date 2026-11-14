package com.cady.cadymarketing.domain.engine

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class InfrastructureTest {

    // ---------------------------------------------------------------- text helpers
    @Test
    fun digitsAreConvertedWithoutChangingTheLength() {
        val text = "٠١٢٣٤٥٦٧٨٩ و ۰۱۲۳۴۵۶۷۸۹ و 5"
        val converted = ArabicText.toWesternDigits(text)
        assertEquals("0123456789 و 0123456789 و 5", converted)
        assertEquals(text.length, converted.length)
    }

    @Test
    fun uriComponentsAreEncodedLikeJavaScript() {
        assertEquals("a%20b(c)!~*'", ArabicText.encodeUriComponent("a b(c)!~*'"))
        assertEquals("%D8%A3%D9%87%D9%84%D9%8B%D8%A7", ArabicText.encodeUriComponent("أهلًا"))
        assertEquals("%2B%26%3D%3F%2F", ArabicText.encodeUriComponent("+&=?/"))
    }

    @Test
    fun datesRoundTripThroughUtcMillis() {
        val millis = DateUtil.toUtcMillis("2026-11-12")
        assertEquals("2026-11-12", DateUtil.fromUtcMillis(millis!!))
        assertNull(DateUtil.toUtcMillis("not a date"))
        assertNull(DateUtil.toUtcMillis("2026-11"))
        assertEquals(10, DateUtil.today().length)
    }

    // ---------------------------------------------------------------- json
    @Test
    fun jsonIsParsedAndQuoted() {
        val parsed = MiniJson.parse("""{"ok":true,"n":-1.5e1,"s":"a\"b\\c\nd\u0645","l":[1,"x",null,false],"o":{}}""")
        val map = parsed as Map<*, *>
        assertEquals(true, map["ok"])
        assertEquals(-15.0, map["n"])
        assertEquals("a\"b\\c\nd\u0645", map["s"])
        assertEquals(listOf(1.0, "x", null, false), map["l"])
        assertEquals(emptyMap<String, Any?>(), map["o"])
        val roundTrip = MiniJson.parse(MiniJson.quote("سطر \"أول\"\n\tثان \\ \u0001"))
        assertEquals("سطر \"أول\"\n\tثان \\ \u0001", roundTrip)
    }

    @Test
    fun badJsonThrows() {
        for (bad in listOf("", "{", "[1,]", "{\"a\":}", "tru", "\"abc", "[1 2]", "{'a':1}", "1 2")) {
            try {
                MiniJson.parse(bad)
                fail("should not parse: $bad")
            } catch (e: MiniJson.ParseException) {
                // expected
            }
        }
    }

    // ---------------------------------------------------------------- gateway client
    private class FakeTransport(val answer: () -> String) : GatewayTransport {
        var lastUrl: String? = null
        var lastBody: String? = null

        override suspend fun post(url: String, body: String, timeoutMs: Int): String {
            lastUrl = url
            lastBody = body
            return answer()
        }
    }

    private val config = GatewayConfig(" https://script.google.com/macros/s/x/exec ", "tok")

    private fun failureOf(transport: FakeTransport, block: suspend (GatewayClient) -> Unit): GatewayException {
        try {
            runBlocking { block(GatewayClient(transport)) }
        } catch (e: GatewayException) {
            return e
        }
        fail("expected a GatewayException")
        throw IllegalStateException()
    }

    @Test
    fun aSuccessfulCallReturnsTheTextAndTheModel() {
        val transport = FakeTransport { """{"ok":true,"m":"gemini-x","text":"[\"a\",\"b\"]"}""" }
        val response = runBlocking { GatewayClient(transport).call(config, "text", "hello \"q\"", true) }
        assertEquals("gemini-x", response.model)
        assertEquals("[\"a\",\"b\"]", response.text)
        assertEquals("https://script.google.com/macros/s/x/exec", transport.lastUrl)
        val sent = MiniJson.parse(transport.lastBody!!) as Map<*, *>
        assertEquals("text", sent["k"])
        assertEquals("hello \"q\"", sent["p"])
        assertEquals(1.0, sent["j"])
        assertEquals("tok", sent["t"])
    }

    @Test
    fun theJsonFlagIsZeroWhenNotRequested() {
        val transport = FakeTransport { """{"ok":true,"text":"x"}""" }
        runBlocking { GatewayClient(transport).testConnection(config) }
        assertEquals(0.0, (MiniJson.parse(transport.lastBody!!) as Map<*, *>)["j"])
    }

    @Test
    fun aGatewayErrorKeepsItsCodeAndShowsTheKnownMessage() {
        val error = failureOf(FakeTransport { """{"ok":false,"code":"busy","err":"x"}""" }) { it.testConnection(config) }
        assertEquals("busy", error.code)
        assertEquals(GatewayMessages.byCode.getValue("busy"), error.message)
    }

    @Test
    fun anUnknownServerErrorIsClassifiedFromItsText() {
        val quota = failureOf(FakeTransport { """{"ok":false,"err":"RESOURCE_EXHAUSTED: slow down"}""" }) { it.testConnection(config) }
        assertEquals("quota", quota.code)
        val odd = failureOf(FakeTransport { """{"ok":false,"err":"something strange happened"}""" }) { it.testConnection(config) }
        assertEquals("x", odd.code)
        assertEquals("something strange happened", odd.message)
        val long = failureOf(FakeTransport { """{"ok":false,"err":"${"z".repeat(200)}"}""" }) { it.testConnection(config) }
        assertEquals(90, long.message!!.length)
    }

    @Test
    fun aPageThatIsNotJsonMeansTheGatewayLinkIsWrong() {
        val error = failureOf(FakeTransport { "<html>sign in</html>" }) { it.testConnection(config) }
        assertEquals("html", error.code)
    }

    @Test
    fun networkProblemsAreMappedToNetAndTime() {
        val net = failureOf(FakeTransport { throw IOException("no route") }) { it.testConnection(config) }
        assertEquals("net", net.code)
        val time = failureOf(FakeTransport { throw SocketTimeoutException("slow") }) { it.testConnection(config) }
        assertEquals("time", time.code)
    }

    @Test
    fun withoutAnUrlNothingIsSent() {
        val transport = FakeTransport { """{"ok":true}""" }
        val error = failureOf(transport) { it.testConnection(GatewayConfig("", "")) }
        assertEquals("nourl", error.code)
        assertNull(transport.lastBody)
    }

    @Test
    fun generatedPostsAreParsedAndAnEmptyAnswerIsAnError() {
        val product = TestFixtures.offer
        val brand = TestFixtures.brand
        val good = FakeTransport { """{"ok":true,"text":"[\"منشور أول طويل\",\"منشور ثان\"]"}""" }
        val posts = runBlocking { GatewayClient(good).generatePosts(config, product, brand, "ودّي") }
        assertEquals(listOf("منشور أول طويل", "منشور ثان"), posts)

        val empty = failureOf(FakeTransport { """{"ok":true,"text":"[]"}""" }) { it.generatePosts(config, product, brand, "ودّي") }
        assertEquals("empty", empty.code)

        val noIdeas = runBlocking { GatewayClient(FakeTransport { """{"ok":true,"text":""}""" }).generateIdeas(config, product, brand, "عرض") }
        assertTrue(noIdeas.isEmpty())
    }

    @Test
    fun promptsCarryTheFactsAndTheBannedList() {
        val posts = GatewayPrompts.posts(TestFixtures.offerWithEnd, TestFixtures.brand, "ودّي")
        assertTrue(posts.contains("السعر 6,800 ريال بدل 7,000"))
        assertTrue(posts.contains("ينتهي العرض 2099-12-31"))
        assertTrue(posts.contains("يقتل,جراثيم"))
        assertTrue(posts.contains("{الرابط}"))
        val ideas = GatewayPrompts.ideas(TestFixtures.offer, TestFixtures.brand, "نصيحة")
        assertTrue(ideas.contains("«نصيحة»"))
        assertTrue(ideas.contains("سائل جلي كادي — ليمون"))
    }
}
