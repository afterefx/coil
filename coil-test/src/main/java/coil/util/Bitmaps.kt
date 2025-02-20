package coil.util

import android.graphics.Bitmap
import androidx.annotation.FloatRange
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import coil.size.Size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking

val Bitmap.size: Size
    get() = Size(width, height)

/**
 * Returns an [Array] of 4 [IntArray]s, the alpha, red, green, and blue pixel values.
 */
fun Bitmap.getPixels(): Array<IntArray> {
    val size = width * height
    val pixels = IntArray(size)
    getPixels(pixels, 0, width, 0, 0, width, height)

    val alpha = IntArray(size)
    val red = IntArray(size)
    val green = IntArray(size)
    val blue = IntArray(size)

    pixels.forEachIndexed { index, pixel ->
        alpha[index] = pixel.alpha
        red[index] = pixel.red
        green[index] = pixel.green
        blue[index] = pixel.blue
    }

    return arrayOf(alpha, red, green, blue)
}

@FloatRange(from = -1.0, to = 1.0)
fun Bitmap.computeSimilarity(other: Bitmap): Double = runBlocking(Dispatchers.Default) {
    val pixels1 = async { getPixels() }
    val pixels2 = async { other.getPixels() }

    suspend fun computeThresholdAsync(index: Int) = async {
        val channel1 = pixels1.await()[index]
        val channel2 = pixels2.await()[index]
        val crossCorrelation = crossCorrelation(channel1, channel2)
        if (crossCorrelation.isNaN()) {
            if (channel1.contentEquals(channel2)) 1.0 else 0.0
        } else {
            crossCorrelation
        }
    }

    val alphaThreshold = computeThresholdAsync(0)
    val redThreshold = computeThresholdAsync(1)
    val greenThreshold = computeThresholdAsync(2)
    val blueThreshold = computeThresholdAsync(3)

    minOf(
        alphaThreshold.await(),
        redThreshold.await(),
        greenThreshold.await(),
        blueThreshold.await()
    )
}

/**
 * Compares two [Bitmap]s by ensuring that they are the same size and that
 * the cross correlation of their ARGB channels is >= [threshold].
 */
fun Bitmap.isSimilarTo(
    expected: Bitmap,
    @FloatRange(from = -1.0, to = 1.0) threshold: Double = 0.99
): Boolean {
    require(threshold in -1.0..1.0) { "Invalid threshold: $threshold" }
    require(width == expected.width && height == expected.height) {
        "The actual image ($width, $height) is not the same size as the " +
            "expected image (${expected.width}, ${expected.height})."
    }

    return computeSimilarity(expected) >= threshold
}

/**
 * Asserts that [this] and [expected] are the same size and that
 * the cross correlation of their ARGB channels is >= [threshold].
 */
fun Bitmap.assertIsSimilarTo(
    expected: Bitmap,
    @FloatRange(from = -1.0, to = 1.0) threshold: Double = 0.99
) {
    require(threshold in -1.0..1.0) { "Invalid threshold: $threshold" }
    require(width == expected.width && height == expected.height) {
        "The actual image ($width, $height) is not the same size as the " +
            "expected image (${expected.width}, ${expected.height})."
    }

    val similarity = computeSimilarity(expected)
    check(similarity >= threshold) {
        "The images are not visually similar. Expected: $threshold; Actual: $similarity."
    }
}
