/*
 * File chooser and permission handling ported from the Aegis SillyTavern Android wrapper
 * (https://github.com/aegis-plus/sillytavern-android), which is under the ISC License:
 *
 * Copyright (c) 2026
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted, provided that the above
 * copyright notice and this permission notice appear in all copies.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES
 * WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR
 * ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES
 * WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN
 * ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF
 * OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.
 */
package com.tiffles.travelingtavern.data.webview

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebView

/**
 * Handles the page's file uploads and mic/camera requests. The picker and the permission
 * dialog are started by the attached [host]; their results are handed back here.
 *
 * Unlike the Aegis original, mic/camera is only granted to the saved server's own origin
 * and only for what the user approved.
 */
class TavernWebChromeClient(
    private val serverBaseUrl: () -> String?
) : WebChromeClient() {

    var host: WebViewHost? = null

    private var filePathCallback: ValueCallback<Array<Uri>>? = null
    private var pendingPermissionRequest: PermissionRequest? = null

    override fun onShowFileChooser(
        webView: WebView,
        filePathCallback: ValueCallback<Array<Uri>>,
        fileChooserParams: FileChooserParams
    ): Boolean {
        // A picker that never reported back would leave the page's file input stuck.
        this.filePathCallback?.onReceiveValue(null)
        this.filePathCallback = filePathCallback

        val launched = host?.launchFileChooser(fileChooserParams.createIntent()) ?: false
        if (!launched) this.filePathCallback = null
        return launched
    }

    /** Delivers the picker result to the page; a cancelled picker yields null. */
    fun onFileChooserResult(resultCode: Int, data: Intent?) {
        filePathCallback?.onReceiveValue(FileChooserParams.parseResult(resultCode, data))
        filePathCallback = null
    }

    override fun onPermissionRequest(request: PermissionRequest) {
        val currentHost = host
        val permissions = request.resources.mapNotNull(::androidPermissionFor).distinct()
        if (currentHost == null || permissions.isEmpty() || !request.isFromServer()) {
            request.deny()
            return
        }
        pendingPermissionRequest?.deny()
        pendingPermissionRequest = request
        currentHost.requestPermissions(permissions.toTypedArray())
    }

    override fun onPermissionRequestCanceled(request: PermissionRequest) {
        if (pendingPermissionRequest == request) pendingPermissionRequest = null
    }

    /** Grants the page only the resources whose Android permission the user approved. */
    fun onPermissionsResult(granted: Map<String, Boolean>) {
        val request = pendingPermissionRequest ?: return
        pendingPermissionRequest = null
        val approved = request.resources.filter { resource ->
            androidPermissionFor(resource)?.let { granted[it] == true } ?: false
        }
        if (approved.isEmpty()) request.deny() else request.grant(approved.toTypedArray())
    }

    private fun PermissionRequest.isFromServer(): Boolean {
        val baseUrl = serverBaseUrl() ?: return false
        return isSameServer(url = origin.toString(), baseUrl = baseUrl)
    }

    private fun androidPermissionFor(resource: String): String? = when (resource) {
        PermissionRequest.RESOURCE_AUDIO_CAPTURE -> Manifest.permission.RECORD_AUDIO
        PermissionRequest.RESOURCE_VIDEO_CAPTURE -> Manifest.permission.CAMERA
        else -> null
    }
}
