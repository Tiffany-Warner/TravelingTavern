package com.tiffles.travelingtavern.data.bridge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Page appearance the app wants, re-applied after every page load. Clean mode is on by
 * default; Advanced (1.8) turns it off to show SillyTavern's full UI. Main thread only.
 */
@Singleton
class PageModeStore @Inject constructor() {
    var cleanMode: Boolean = true
}
