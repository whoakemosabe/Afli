package app.afli.ui

import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import app.afli.data.Repo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * A trip photo loaded off the main thread and scaled down to about [maxPx] on its longest side,
 * so a log full of photos scrolls smoothly and doesn't fill memory. Null while loading or if the
 * file is gone.
 */
@Composable
fun rememberPhoto(name: String?, maxPx: Int = 480): State<ImageBitmap?> = produceState<ImageBitmap?>(null, name, maxPx) {
    value = if (name == null) null else withContext(Dispatchers.IO) {
        runCatching {
            val path = Repo.photoFile(name).path
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(path, bounds)
            var sample = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxPx) sample *= 2
            val bmp = BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
            // Camera photos are often stored sideways with a note saying which way is up.
            val turn = when (android.media.ExifInterface(path).getAttributeInt(android.media.ExifInterface.TAG_ORIENTATION, android.media.ExifInterface.ORIENTATION_NORMAL)) {
                android.media.ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                android.media.ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                android.media.ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            (if (bmp != null && turn != 0f) android.graphics.Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, android.graphics.Matrix().apply { postRotate(turn) }, true) else bmp)?.asImageBitmap()
        }.getOrNull()
    }
}
