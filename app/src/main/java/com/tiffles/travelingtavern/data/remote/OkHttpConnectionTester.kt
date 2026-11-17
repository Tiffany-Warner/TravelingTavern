package com.tiffles.travelingtavern.data.remote

import android.util.Log
import com.tiffles.travelingtavern.domain.model.ConnectionResult
import com.tiffles.travelingtavern.domain.repository.ConnectionTester
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.IOException
import java.net.HttpURLConnection
import java.util.concurrent.TimeUnit
import javax.inject.Inject

/**
 * `GET {baseUrl}/` with a 10 s timeout. SillyTavern is recognized by its page title,
 * verified against SillyTavern 1.19.0 whose index page carries `<title>SillyTavern</title>`.
 */
class OkHttpConnectionTester @Inject constructor(
    sharedClient: OkHttpClient
) : ConnectionTester {

    private val client: OkHttpClient = sharedClient.newBuilder()
        .callTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .build()

    override suspend fun test(baseUrl: String, username: String?, password: String?): ConnectionResult =
        withContext(Dispatchers.IO) {
            val url = baseUrl.toHttpUrlOrNull() ?: return@withContext ConnectionResult.InvalidUrl
            val request = buildRequest(url, username, password)
            val hasCredentials = username != null && password != null

            try {
                client.newCall(request).execute().use { response -> classify(response, hasCredentials) }
            } catch (e: IOException) {
                Log.d(TAG, "Connection test failed: ${e.javaClass.simpleName}")
                ConnectionResult.Unreachable
            }
        }

    private fun buildRequest(url: okhttp3.HttpUrl, username: String?, password: String?): Request {
        val builder = Request.Builder().url(url.newBuilder().encodedPath("/").build())
        if (username != null && password != null) {
            builder.header("Authorization", Credentials.basic(username, password))
        }
        return builder.build()
    }

    private fun classify(response: Response, hasCredentials: Boolean): ConnectionResult = when {
        response.code == HttpURLConnection.HTTP_UNAUTHORIZED ->
            if (hasCredentials) ConnectionResult.AuthFailed else ConnectionResult.AuthRequired

        response.isSuccessful ->
            if (looksLikeSillyTavern(response)) ConnectionResult.Success else ConnectionResult.NotSillyTavern

        else -> ConnectionResult.NotSillyTavern
    }

    private fun looksLikeSillyTavern(response: Response): Boolean {
        val html = response.peekBody(MAX_BODY_BYTES).string()
        return TITLE_REGEX.containsMatchIn(html)
    }

    private companion object {
        const val TAG = "ConnectionTester"
        const val TIMEOUT_SECONDS = 10L
        const val MAX_BODY_BYTES = 64L * 1024
        val TITLE_REGEX = Regex("<title>\\s*SillyTavern\\s*</title>", RegexOption.IGNORE_CASE)
    }
}
