package com.eight87.whisperboy.ui.coverart

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import com.eight87.whisperboy.R
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * cover-art.md Phase C — square-crop step that wedges between the DDG picker (Phase B) and
 * [com.eight87.whisperboy.data.library.BookSource.setCustomCover].
 *
 * Layout: a full-screen [Dialog] with a top app bar (Cancel) and bottom Save button. The
 * body is a square [Box] sized to the available width; [imageUrl] is rendered via Coil's
 * [AsyncImage] and the **image** is what the user pans + pinch-zooms. The crop window is
 * the displayed square itself — what you see is what gets saved. (Voice's pattern: fixed
 * window, image moves underneath; simpler than draggable corner handles, no per-handle
 * hit testing.)
 *
 * On Save, the image is decoded at full resolution via the [SingletonImageLoader], the
 * user's pan + zoom is converted into a source-bitmap crop rect, and the cropped region
 * is JPEG-encoded at quality 90 before being handed back via [onConfirm].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditCoverDialog(
    imageUrl: String,
    onCancel: () -> Unit,
    onConfirm: (croppedBytes: ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    Dialog(
        onDismissRequest = onCancel,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
        ),
    ) {
        EditCoverContent(
            imageUrl = imageUrl,
            onCancel = onCancel,
            onConfirm = onConfirm,
            modifier = modifier,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun EditCoverContent(
    imageUrl: String,
    onCancel: () -> Unit,
    onConfirm: (croppedBytes: ByteArray) -> Unit,
    modifier: Modifier = Modifier,
) {
    // User-controlled transform of the IMAGE underneath the fixed crop window. The crop
    // window itself never moves; the image translates + scales beneath it. Scale clamped
    // to [1, 6] — scale=1 means "image fills the square exactly" (ContentScale.Crop);
    // anything smaller would expose blank background under the crop window which we
    // never want to encode.
    var scale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }

    var imageLoaded by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    // Track the displayed square's pixel side so the save-time math can convert the
    // user's display-space pan into a source-pixel crop offset.
    var squarePx by remember { mutableFloatStateOf(0f) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    val transformableState = rememberTransformableState { zoomChange, panChange, _ ->
        val newScale = (scale * zoomChange).coerceIn(1f, 6f)
        // Clamp pan so the image always covers the crop window at the new scale.
        val maxOffset = if (squarePx > 0f) ((newScale - 1f) / 2f) * squarePx else 0f
        scale = newScale
        offsetX = (offsetX + panChange.x).coerceIn(-maxOffset, maxOffset)
        offsetY = (offsetY + panChange.y).coerceIn(-maxOffset, maxOffset)
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.coverart_crop_title)) },
                navigationIcon = {
                    IconButton(onClick = onCancel) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = stringResource(R.string.coverart_crop_cancel),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                val sidePx = with(density) { min(maxWidth.toPx(), maxHeight.toPx()) }
                squarePx = sidePx
                val sideDp = with(density) { sidePx.toDp() }

                Box(
                    modifier = Modifier
                        .size(sideDp)
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                        .transformable(state = transformableState),
                    contentAlignment = Alignment.Center,
                ) {
                    AsyncImage(
                        model = ImageRequest.Builder(context)
                            .data(imageUrl)
                            .listener(
                                onSuccess = { _, _ -> imageLoaded = true },
                                onError = { _, _ -> imageLoaded = false },
                            )
                            .build(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = scale,
                                scaleY = scale,
                                translationX = offsetX,
                                translationY = offsetY,
                            ),
                        contentScale = ContentScale.Crop,
                    )

                    if (!imageLoaded) {
                        CircularProgressIndicator()
                    }
                }
            }

            TextButton(
                onClick = {
                    if (!imageLoaded || saving) return@TextButton
                    saving = true
                    scope.launch {
                        runCatching {
                            cropImage(
                                context = context,
                                imageUrl = imageUrl,
                                scale = scale,
                                offsetX = offsetX,
                                offsetY = offsetY,
                                squarePx = squarePx,
                            )
                        }
                            .onSuccess { bytes -> onConfirm(bytes) }
                            .onFailure { saving = false }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 24.dp),
            ) {
                Text(
                    text = stringResource(
                        if (saving) R.string.coverart_crop_loading else R.string.coverart_crop_confirm,
                    ),
                    color = Color.White,
                )
            }
        }
    }
}

/**
 * Decode [imageUrl] at full resolution via the Coil singleton, apply the user's pan + zoom
 * as a source-bitmap rect, and JPEG-encode at quality 90.
 *
 * Geometry: the displayed view is a `squarePx` square; `ContentScale.Crop` at `scale=1`
 * makes the source bitmap fill the square along its short side, centred. At `scale=s`
 * the visible region covers `(shortSide / s)` source-pixels along the short side, and
 * a positive `offsetX` display-pixel pan moves the image RIGHT in the view, which means
 * the crop window's source-centre moves LEFT by `(offsetX / s) * (shortSide / squarePx)`
 * source-pixels.
 */
private suspend fun cropImage(
    context: Context,
    imageUrl: String,
    scale: Float,
    offsetX: Float,
    offsetY: Float,
    squarePx: Float,
): ByteArray = withContext(Dispatchers.IO) {
    val loader = SingletonImageLoader.get(context)
    val request = ImageRequest.Builder(context)
        .data(imageUrl)
        .allowHardware(false)
        .build()
    val result = loader.execute(request)
    if (result !is SuccessResult) {
        error("Failed to decode image for cropping")
    }
    val image = result.image
    val srcBitmap: Bitmap = when (image) {
        is BitmapImage -> image.bitmap
        else -> image.toBitmap()
    }
    val srcW = srcBitmap.width
    val srcH = srcBitmap.height
    val shortSide = min(srcW, srcH).toFloat()
    val safeScale = scale.coerceAtLeast(1f)
    val safeSquarePx = if (squarePx > 0f) squarePx else 1f

    val cropSideSrc = (shortSide / safeScale).coerceAtLeast(1f)
    val displayToSrc = shortSide / safeSquarePx
    val panSrcX = (offsetX * displayToSrc) / safeScale
    val panSrcY = (offsetY * displayToSrc) / safeScale

    val centreX = srcW / 2f - panSrcX
    val centreY = srcH / 2f - panSrcY

    val left = (centreX - cropSideSrc / 2f).roundToInt().coerceIn(0, max(0, srcW - 1))
    val top = (centreY - cropSideSrc / 2f).roundToInt().coerceIn(0, max(0, srcH - 1))
    val sideInt = cropSideSrc.roundToInt()
        .coerceAtMost(srcW - left)
        .coerceAtMost(srcH - top)
        .coerceAtLeast(1)

    val cropped = Bitmap.createBitmap(srcBitmap, left, top, sideInt, sideInt)
    val bytes = ByteArrayOutputStream().use { stream ->
        cropped.compress(Bitmap.CompressFormat.JPEG, 90, stream)
        stream.toByteArray()
    }
    if (cropped !== srcBitmap) cropped.recycle()
    bytes
}
