package com.tiffles.travelingtavern.data.remote

import android.util.Log
import android.webkit.CookieManager
import com.tiffles.travelingtavern.data.bridge.LeaseFileParser
import com.tiffles.travelingtavern.data.bridge.LeaseFileRead
import com.tiffles.travelingtavern.data.bridge.LeaseFileReader
import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import com.tiffles.travelingtavern.data.webview.isSameServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Reads `tt-session-lease.json` (E2) through the app's [OkHttpClient] (basic auth for the saved
 * server), with the WebView's cookies (SillyTavern's session) and `Cache-Control: no-cache`.
 * Only the saved server's origin is read. Logs never carry the URL, cookies or the file.
 */
@Singleton
class OkHttpLeaseFileReader @Inject constructor(
    client: OkHttpClient,
    private val serverConfig: ServerConfigRepositoryImpl
) : LeaseFileReader {

    /** Bounded, so a command is never left waiting long on an unreachable server. */
    private val client = client.newBuilder().callTimeout(READ_TIMEOUT_S, TimeUnit.SECONDS).build()

    override suspend fun read(origin: String): LeaseFileRead {
        val baseUrl = serverConfig.baseUrl()
        if (baseUrl == null || !isSameServer(origin, baseUrl)) {
            Log.w(TAG, "Lease origin isn't the saved server; treated as unreadable")
            return LeaseFileRead.Unreadable
        }
        val url = origin.trimEnd('/') + LEASE_PATH
        val cookies = withContext(Dispatchers.Main) { CookieManager.getInstance().getCookie(url) }
        val request = Request.Builder()
            .url(url)
            .header("Cache-Control", "no-cache")
            .apply { if (!cookies.isNullOrEmpty()) header("Cookie", cookies) }
            .build()
        return withContext(Dispatchers.IO) {
            try {
                client.newCall(request).execute().use { response ->
                    LeaseFileParser.parse(response.code, response.body?.string())
                }
            } catch (e: IOException) {
                Log.w(TAG, "Lease file read failed: ${e.javaClass.simpleName}")
                LeaseFileRead.Unreadable
            }
        }
    }

    private companion object {
        const val TAG = "LeaseFileReader"
        const val LEASE_PATH = "/user/files/tt-session-lease.json"
        const val READ_TIMEOUT_S = 5L
    }
}
