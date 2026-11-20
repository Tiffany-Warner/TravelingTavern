package com.tiffles.travelingtavern.data.bridge

import javax.inject.Inject
import javax.inject.Singleton

/**
 * The app's theme colors for the clean chat layout, set by the app shell whenever the theme
 * changes and re-sent to the page after every load. Main thread only.
 */
@Singleton
class PageThemeStore @Inject constructor() {
    var theme: PageTheme? = null
}

/**
 * [colors] maps a camelCase name (e.g. `primaryContainer`) to `#rrggbb`; the page turns it into
 * the CSS variable `--tt-primary-container`. [dark] is the app's light / dark mode.
 */
data class PageTheme(val dark: Boolean, val colors: Map<String, String>)
