package com.tiffles.travelingtavern.data.remote

import com.tiffles.travelingtavern.data.repository.ServerConfigRepositoryImpl
import kotlinx.coroutines.runBlocking
import okhttp3.Credentials
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Adds HTTP basic auth from the saved credentials, but only for requests to the
 * configured server, and never when the caller already set an Authorization header
 * (the connection test sends candidate credentials explicitly).
 */
@Singleton
class BasicAuthInterceptor @Inject constructor(
    private val repository: ServerConfigRepositoryImpl
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (request.header(AUTHORIZATION) != null) return chain.proceed(request)

        // Runs on an OkHttp dispatcher thread; DataStore serves this from its cache after
        // the first read.
        val (baseUrl, credentials) = runBlocking { repository.baseUrl() to repository.credentials() }
        val serverUrl = baseUrl?.toHttpUrlOrNull()
        if (credentials == null || serverUrl == null || !request.url.isSameServer(serverUrl)) {
            return chain.proceed(request)
        }

        val authed = request.newBuilder()
            .header(AUTHORIZATION, Credentials.basic(credentials.username, credentials.password))
            .build()
        return chain.proceed(authed)
    }

    private fun HttpUrl.isSameServer(other: HttpUrl): Boolean =
        scheme == other.scheme && host.equals(other.host, ignoreCase = true) && port == other.port

    private companion object {
        const val AUTHORIZATION = "Authorization"
    }
}
