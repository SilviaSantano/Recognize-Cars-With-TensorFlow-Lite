package de.inovex.recognizecarswithtflite.utils

import android.graphics.*
import android.media.Image
import androidx.camera.core.ImageProxy
import androidx.camera.view.PreviewView
import java.io.ByteArrayOutputStream

/**
 * Compute a matrix to transform the coordinates of an object in the image from image analysis to the preview.
 */
fun computeImageToPreviewConversionMatrix(previewView: PreviewView, image: ImageProxy): Matrix {
    val cropRect = image.cropRect
    val rotationDegrees = image.imageInfo.rotationDegrees
    val matrix = Matrix()

    // A float array of the source vertices (crop rect) in clockwise order.
    val source = floatArrayOf(
        cropRect.left.toFloat(),
        cropRect.top.toFloat(),
        cropRect.right.toFloat(),
        cropRect.top.toFloat(),
        cropRect.right.toFloat(),
        cropRect.bottom.toFloat(),
        cropRect.left.toFloat(),
        cropRect.bottom.toFloat()
    )

    // A float array of the destination vertices in clockwise order.
    val destination = floatArrayOf(
        0f,
        0f,
        previewView.width.toFloat(),
        0f,
        previewView.width.toFloat(),
        previewView.height.toFloat(),
        0f,
        previewView.height.toFloat()
    )

    // The destination vertexes need to be shifted based on rotation degrees. The
    // rotation degree represents the clockwise rotation needed to correct the image.
    // Each vertex is represented by 2 float numbers in the vertices array.
    val vertexSize = 2
    // The destination needs to be shifted 1 vertex for every 90° rotation.
    val shiftOffset = rotationDegrees / 90 * vertexSize;
    val tempArray = destination.clone()
    for (toIndex in source.indices) {
        val fromIndex = (toIndex + shiftOffset) % source.size
        destination[toIndex] = tempArray[fromIndex]
    }
    matrix.setPolyToPoly(source, 0, destination, 0, 4)
    return matrix
}

/**
 * Add some width compensation to the sides (orientation-aware) to a box to improve the fitting
 * if the aspect ratio of the coordinate systems from image analysis and preview differ.
 */
fun RectF.addWidthCompensation(widthCompensation: Int, image: ImageProxy): RectF {
    if (image.imageInfo.rotationDegrees == 90) {
        this.left = this.left - widthCompensation
        this.right = this.right + widthCompensation
    } else {
        this.top = this.top - widthCompensation
        this.bottom = this.bottom + widthCompensation
    }
    return this
}

/**
 * Convert from Image of format NV21 to Bitmap format.
 */
fun Image.toBitmap(): Bitmap {
    val yBuffer = planes[0].buffer
    val vuBuffer = planes[2].buffer

    val ySize = yBuffer.remaining()
    val vuSize = vuBuffer.remaining()

    val nv21 = ByteArray(ySize + vuSize)

    yBuffer.get(nv21, 0, ySize)
    vuBuffer.get(nv21, ySize, vuSize)

    val yuvImage = YuvImage(nv21, ImageFormat.NV21, this.width, this.height, null)
    val out = ByteArrayOutputStream()
    yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 50, out)
    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}