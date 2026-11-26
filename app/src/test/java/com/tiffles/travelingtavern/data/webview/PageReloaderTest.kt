package com.tiffles.travelingtavern.data.webview

import android.webkit.CookieManager
import android.webkit.WebStorage
import android.webkit.WebView
import io.mockk.confirmVerified
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.After
import org.junit.Before
import org.junit.Test

/** A2: after an extension install or update, only the HTTP cache is cleared before the reload. */
class PageReloaderTest {

    private val webView: WebView = mockk(relaxed = true)

    @Before
    fun setUp() {
        mockkStatic(CookieManager::class, WebStorage::class)
    }

    @After
    fun tearDown() {
        unmockkStatic(CookieManager::class, WebStorage::class)
    }

    @Test
    fun `clears the cache with disk files, then reloads, and nothing else`() {
        webView.clearCacheOnlyAndReload()

        verifyOrder {
            webView.clearCache(true)
            webView.reload()
        }
        confirmVerified(webView)
        verify(exactly = 0) { CookieManager.getInstance() }
        verify(exactly = 0) { WebStorage.getInstance() }
    }
}
