package com.example.agricultureoffline.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.os.Build
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter

internal object LeafImageValidator {
    private const val IMAGE_SIZE = 160
    private const val LEAF_THRESHOLD = 0.525f

    // Call off the main thread. A decode or model error propagates to the scan screen.
    fun isLeaf(context: Context, file: File): Boolean {
        val input = imageInput(file)
        val weights = context.assets.open("leaf_validator.tflite").use { it.readBytes() }
        val model = ByteBuffer.allocateDirect(weights.size).order(ByteOrder.nativeOrder())
        model.put(weights).rewind()
        Interpreter(model, Interpreter.Options().setNumThreads(2)).use { classifier ->
            val inputTensor = classifier.getInputTensor(0)
            val outputTensor = classifier.getOutputTensor(0)
            check(inputTensor.dataType() == DataType.FLOAT32)
            check(inputTensor.shape().contentEquals(intArrayOf(1, IMAGE_SIZE, IMAGE_SIZE, 3)))
            check(outputTensor.dataType() == DataType.FLOAT32)
            check(outputTensor.shape().contentEquals(intArrayOf(1, 1)))
            val output = Array(1) { FloatArray(1) }
            classifier.run(input, output)
            val score = output[0][0]
            check(score.isFinite() && score in 0f..1f)
            return score >= LEAF_THRESHOLD
        }
    }

    private fun imageInput(file: File): ByteBuffer {
        val decoded = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, _, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                decoder.setTargetSize(IMAGE_SIZE, IMAGE_SIZE)
            }
        } else {
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeFile(file.absolutePath, bounds)
            check(bounds.outWidth > 0 && bounds.outHeight > 0)
            var sampleSize = 1
            while (maxOf(bounds.outWidth, bounds.outHeight) / sampleSize > 640) {
                sampleSize *= 2
            }
            val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            BitmapFactory.decodeFile(file.absolutePath, options) ?: error("Cannot decode image")
        }
        val bitmap = Bitmap.createScaledBitmap(decoded, IMAGE_SIZE, IMAGE_SIZE, true)
        val pixels = IntArray(IMAGE_SIZE * IMAGE_SIZE)
        try {
            bitmap.getPixels(pixels, 0, IMAGE_SIZE, 0, 0, IMAGE_SIZE, IMAGE_SIZE)
        } finally {
            if (bitmap !== decoded) bitmap.recycle()
            decoded.recycle()
        }

        // The model includes scaling from RGB values in [0, 255] to [-1, 1].
        val input = ByteBuffer.allocateDirect(pixels.size * 3 * Float.SIZE_BYTES)
            .order(ByteOrder.nativeOrder())
        for (pixel in pixels) {
            input.putFloat(((pixel shr 16) and 255).toFloat())
            input.putFloat(((pixel shr 8) and 255).toFloat())
            input.putFloat((pixel and 255).toFloat())
        }
        input.rewind()
        return input
    }
}
