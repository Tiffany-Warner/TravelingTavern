package com.tiffles.travelingtavern.domain.repository

import com.tiffles.travelingtavern.domain.model.ConnectionResult

/** Checks whether a normalized base URL answers as SillyTavern. */
interface ConnectionTester {
    suspend fun test(baseUrl: String, username: String?, password: String?): ConnectionResult
}
