package com.agispsi.asylumlocator

import android.graphics.Bitmap
import android.graphics.Rect
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

data class OcrLine(val text: String, val box: Rect)
class Ocr : AutoCloseable {
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    suspend fun lines(bitmap: Bitmap, region: Rect? = null): List<OcrLine> {
        val source = if (region == null) bitmap else Bitmap.createBitmap(bitmap, region.left, region.top, region.width(), region.height())
        // Upscale small crops for reliable digit recognition; never replace ambiguous letters with digits.
        val scale = if (region != null && source.height < 100) 3 else 1
        val input = if (scale > 1) Bitmap.createScaledBitmap(source, source.width * scale, source.height * scale, true) else source
        try {
            // ML Kit Tasks do not cancel with the coroutine. Wait for the native reader
            // before recycling its bitmap, then honor cancellation before further work.
            val text = withContext(NonCancellable) { recognizer.process(InputImage.fromBitmap(input, 0)).await() }
            currentCoroutineContext().ensureActive()
            return text.textBlocks.flatMap { it.lines }.mapNotNull { line ->
                line.boundingBox?.let { b -> OcrLine(line.text, Rect(b.left / scale + (region?.left ?: 0), b.top / scale + (region?.top ?: 0), b.right / scale + (region?.left ?: 0), b.bottom / scale + (region?.top ?: 0))) }
            }
        } finally {
            if (input !== source) input.recycle()
            if (source !== bitmap) source.recycle()
        }
    }
    suspend fun text(bitmap: Bitmap, region: Rect) = lines(bitmap, region).joinToString("\n") { it.text }
    override fun close() = recognizer.close()
}
