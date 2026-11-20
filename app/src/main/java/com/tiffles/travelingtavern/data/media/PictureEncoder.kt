package com.tiffles.travelingtavern.data.media

/** Turns a picture picked on the phone into a small `data:` URL the page can upload. */
fun interface PictureEncoder {
    /** [uri] (a content URI) as a JPEG `data:` URL, or null if it couldn't be read. */
    suspend fun encode(uri: String): String?
}
