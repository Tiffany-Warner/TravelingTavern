package com.tiffles.travelingtavern.data.remote

import android.util.Log
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

/** A5 / R42: the release upload, its CSRF token and what reaches the logs. */
class OkHttpReleaseUploaderTest {

    private val logs = mutableListOf<String>()
    private val requests = mutableListOf<Request>()
    private val bodies = mutableListOf<String?>()

    /** Answers by path: each entry is used once, the last one repeats. */
    private val answers = mutableMapOf<String, ArrayDeque<Answer>>()

    private data class Answer(val code: Int, val body: String = "", val fail: Boolean = false)

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } answers { logs += secondArg<String>(); 0 }
        every { Log.i(any(), any()) } answers { logs += secondArg<String>(); 0 }
        every { Log.w(any(), any<String>()) } answers { logs += secondArg<String>(); 0 }
    }

    @After
    fun tearDown() {
        unmockkStatic(Log::class)
    }

    private fun answer(path: String, vararg list: Answer) {
        answers[path] = ArrayDeque(list.toList())
    }

    private val fakeServer = Interceptor { chain ->
        val request = chain.request()
        requests += request
        bodies += request.body?.let { body -> Buffer().also(body::writeTo).readUtf8() }
        val queue = answers.getValue(request.url.encodedPath)
        val next = if (queue.size > 1) queue.removeFirst() else queue.first()
        if (next.fail) throw IOException("connection refused")
        Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(next.code)
            .message("")
            .body(next.body.toResponseBody())
            .build()
    }

    private fun uploader(baseUrl: String? = SERVER) = OkHttpReleaseUploader(
        client = OkHttpClient.Builder().addInterceptor(fakeServer).build(),
        baseUrl = { baseUrl },
        cookies = { COOKIE }
    )

    private fun paths() = requests.map { it.method + " " + it.url.encodedPath }

    @Test
    fun `fetches the token with the cookies, then posts the body with cookies and CSRF header`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"$TOKEN"}"""))
        answer("/api/files/upload", Answer(200))

        assertTrue(uploader().upload(SERVER, BODY))

        assertEquals(listOf("GET /csrf-token", "POST /api/files/upload"), paths())
        val (csrf, upload) = requests
        assertEquals(COOKIE, csrf.header("Cookie"))
        assertEquals("no-cache", csrf.header("Cache-Control"))
        assertEquals(COOKIE, upload.header("Cookie"))
        assertEquals(TOKEN, upload.header("X-CSRF-Token"))
        assertEquals("application/json; charset=utf-8", upload.body?.contentType().toString())
        assertEquals(BODY, bodies[1])
        assertEquals("$SERVER/api/files/upload", upload.url.toString())
    }

    @Test
    fun `the token is reused`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"$TOKEN"}"""))
        answer("/api/files/upload", Answer(200))
        val uploader = uploader()

        uploader.upload(SERVER, BODY)
        uploader.upload(SERVER, BODY)

        assertEquals(listOf("GET /csrf-token", "POST /api/files/upload", "POST /api/files/upload"), paths())
    }

    @Test
    fun `a 403 fetches the token again once and resends`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"old"}"""), Answer(200, """{"token":"new"}"""))
        answer("/api/files/upload", Answer(403), Answer(200))

        assertTrue(uploader().upload(SERVER, BODY))

        assertEquals(
            listOf("GET /csrf-token", "POST /api/files/upload", "GET /csrf-token", "POST /api/files/upload"),
            paths()
        )
        assertEquals("new", requests[3].header("X-CSRF-Token"))
    }

    @Test
    fun `a second 403 fails the attempt without another fetch`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"$TOKEN"}"""))
        answer("/api/files/upload", Answer(403))

        assertFalse(uploader().upload(SERVER, BODY))
        assertEquals(4, requests.size)
    }

    @Test
    fun `a 500 fails the attempt and keeps the token`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"$TOKEN"}"""))
        answer("/api/files/upload", Answer(500), Answer(200))
        val uploader = uploader()

        assertFalse(uploader.upload(SERVER, BODY))
        assertTrue(uploader.upload(SERVER, BODY))
        assertEquals(1, paths().count { it == "GET /csrf-token" })
    }

    @Test
    fun `no token means no upload`() = runTest {
        answer("/csrf-token", Answer(500))
        answer("/api/files/upload", Answer(200))

        assertFalse(uploader().upload(SERVER, BODY))
        assertEquals(listOf("GET /csrf-token"), paths())
    }

    @Test
    fun `a malformed token answer means no upload`() = runTest {
        answer("/csrf-token", Answer(200, "not json"))

        assertFalse(uploader().upload(SERVER, BODY))
    }

    @Test
    fun `a network error fails the attempt`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"$TOKEN"}"""))
        answer("/api/files/upload", Answer(0, fail = true))

        assertFalse(uploader().upload(SERVER, BODY))
    }

    @Test
    fun `another origin than the saved server is never sent to`() = runTest {
        assertFalse(uploader().upload("http://192.0.2.9:8000", BODY))
        assertFalse(uploader(baseUrl = null).upload(SERVER, BODY))
        assertTrue(requests.isEmpty())
    }

    @Test
    fun `logs never carry the token, cookies, the URL or the body`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":"$TOKEN"}"""), Answer(200, """{"token":"$TOKEN"}"""))
        answer("/api/files/upload", Answer(403), Answer(500), Answer(0, fail = true))
        val uploader = uploader()
        uploader.upload(SERVER, BODY)
        uploader.upload(SERVER, BODY)
        uploader.upload("http://192.0.2.9:8000", BODY)

        assertTrue(logs.isNotEmpty())
        for (line in logs) {
            for (secret in listOf(TOKEN, "session-cookie", "192.0.2", "/api/files", "/csrf-token", BODY, "dHQ")) {
                assertFalse("log line leaks: $line", line.contains(secret))
            }
        }
    }

    @Test
    fun `a token that isn't a string means no upload`() = runTest {
        answer("/csrf-token", Answer(200, """{"token":123}"""))

        assertFalse(uploader().upload(SERVER, BODY))
        assertEquals(listOf("GET /csrf-token"), paths())
    }

    private companion object {
        const val SERVER = "http://192.0.2.1:8000"
        const val TOKEN = "csrf-secret-123"
        const val COOKIE = "session-cookie=abc"
        const val BODY = """{"name":"tt-session-release.json","data":"dHQ="}"""
    }
}
