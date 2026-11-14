package com.cady.cadymarketing.data.gateway

import com.cady.cadymarketing.domain.engine.GatewayTransport
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * POSTs to the Apps Script web app. The body goes as text/plain (the gateway reads the raw text), and the
 * script's redirect to its answer is followed like a browser does.
 */
@Singleton
class HttpGatewayTransport @Inject constructor() : GatewayTransport {
    override suspend fun post(url: String, body: String, timeoutMs: Int): String =
        withContext(Dispatchers.IO) {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = "POST"
                connection.connectTimeout = CONNECT_TIMEOUT_MS
                connection.readTimeout = timeoutMs
                connection.doOutput = true
                connection.instanceFollowRedirects = true
                connection.setRequestProperty("Content-Type", "text/plain;charset=UTF-8")
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                val stream = if (connection.responseCode in 200..399) {
                    connection.inputStream
                } else {
                    connection.errorStream ?: connection.inputStream
                }
                stream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            } finally {
                connection.disconnect()
            }
        }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}
