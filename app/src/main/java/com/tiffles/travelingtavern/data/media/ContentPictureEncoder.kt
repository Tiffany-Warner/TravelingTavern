package com.tiffles.travelingtavern.data.media

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.util.Log
import androidx.core.net.toUri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Base64
import javax.inject.Inject
import kotlin.math.max

/**
 * [PictureEncoder] for content URIs. Scales the picture down to [MAX_EDGE_PX] on its long edge
 * first: SillyTavern crops and resizes it to its avatar size on the server anyway, and a
 * full-size photo would make the script handed to the WebView several megabytes long.
 */
class ContentPictureEncoder @Inject constructor(
    @ApplicationContext private val context: Context
) : PictureEncoder {

    override suspend fun encode(uri: String): String? = withContext(Dispatchers.IO) {
        try {
            val bitmap = decodeScaled(uri)
            val bytes = ByteArrayOutputStream().use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)
                out.toByteArray()
            }
            bitmap.recycle()
            "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(bytes)
        } catch (e: IOException) {
            Log.w(TAG, "Couldn't read the picked picture", e)
            null
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Couldn't read the picked picture", e)
            null
        } catch (e: SecurityException) {
            Log.w(TAG, "No permission to read the picked picture", e)
            null
        }
    }

    private fun decodeScaled(uri: String): Bitmap {
        val source = ImageDecoder.createSource(context.contentResolver, uri.toUri())
        return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longEdge = max(info.size.width, info.size.height)
            if (longEdge > MAX_EDGE_PX) {
                val scale = MAX_EDGE_PX.toFloat() / longEdge
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            // Hardware bitmaps can't be compressed.
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
    }

    private companion object {
        const val TAG = "PictureEncoder"
        const val MAX_EDGE_PX = 1024
        const val JPEG_QUALITY = 90
    }
}
