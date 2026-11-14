package com.cady.cadymarketing.domain.engine

import com.cady.cadymarketing.domain.model.Brand
import com.cady.cadymarketing.domain.model.Product
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout

/** Sends one request to the gateway and returns the raw answer text. The Android implementation lives in data/. */
interface GatewayTransport {
    @Throws(IOException::class)
    suspend fun post(url: String, body: String, timeoutMs: Int): String
}

/**
 * The client of the Apps Script gateway: POST {k: kind, p: prompt, j: json?, t: token},
 * answer {ok: true, text | img, m: model} or {ok: false, code, err}. Failures become GatewayException
 * with the same codes and messages as the original tool.
 */
class GatewayClient @Inject constructor(private val transport: GatewayTransport) {

    suspend fun call(config: GatewayConfig, kind: String, prompt: String, json: Boolean): GatewayResponse {
        if (!config.isConfigured) throw GatewayException("nourl", "الصق رابط البوابة أولًا")
        val body = "{\"k\":" + MiniJson.quote(kind) +
            ",\"p\":" + MiniJson.quote(prompt) +
            ",\"j\":" + (if (json) 1 else 0) +
            ",\"t\":" + MiniJson.quote(config.token) + "}"

        val raw = try {
            withTimeout(TIMEOUT_MS) { transport.post(config.url.trim(), body, TIMEOUT_MS.toInt()) }
        } catch (e: TimeoutCancellationException) {
            throw failure("time")
        } catch (e: CancellationException) {
            throw e
        } catch (e: SocketTimeoutException) {
            throw failure("time")
        } catch (e: IOException) {
            throw failure("net")
        }

        val parsed = try {
            MiniJson.parse(raw)
        } catch (e: MiniJson.ParseException) {
            throw failure("html")
        }
        val answer = parsed as? Map<*, *> ?: throw failure("html")

        if (answer["ok"] != true) {
            val serverError = answer["err"]?.toString()
            val code = GatewayMessages.codeFor(answer["code"] as? String, serverError)
            val message = GatewayMessages.byCode[code] ?: (serverError ?: "فشل الطلب").take(90)
            throw GatewayException(code, message, serverError)
        }
        return GatewayResponse(
            model = answer["m"] as? String,
            text = answer["text"] as? String,
            image = answer["img"] as? String,
        )
    }

    suspend fun testConnection(config: GatewayConfig): GatewayResponse =
        call(config, "text", GatewayPrompts.CONNECTION_TEST, false)

    /** Four posts written by the AI. Throws "empty" when the answer holds no text. */
    suspend fun generatePosts(config: GatewayConfig, product: Product, brand: Brand, tone: String): List<String> {
        val response = call(config, "text", GatewayPrompts.posts(product, brand, tone), true)
        val posts = GatewayParsing.list(response.text)
        if (posts.isEmpty()) throw failure("empty")
        return posts
    }

    /** Up to eight idea openers. May be empty; the caller then falls back to the local idea bank. */
    suspend fun generateIdeas(config: GatewayConfig, product: Product, brand: Brand, angle: String): List<String> {
        val response = call(config, "text", GatewayPrompts.ideas(product, brand, angle), true)
        return GatewayParsing.list(response.text)
    }

    private fun failure(code: String): GatewayException =
        GatewayException(code, GatewayMessages.byCode[code] ?: "فشل الطلب")

    companion object {
        const val TIMEOUT_MS = 70_000L
    }
}
