package de.inovex.recognizecarswithtflite.utils

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.util.DisplayMetrics
import android.util.TypedValue


/**
 * Helper Class to draw a text label over an object over the preview.
 */
class RecognizedLabelText(displayMetrics: DisplayMetrics, color: Int) {
    private val interiorPaint: Paint = Paint()
    private val exteriorPaint: Paint = Paint()
    private var textSize: Float

    fun drawText(canvas: Canvas, posX: Float, posY: Float, label: String, confidence: Float) {
        val text = label + "(" + "%.2f".format(confidence * 100) + "%)"
        canvas.drawText(text, posX, posY - 8, exteriorPaint)
        canvas.drawText(text, posX, posY - 8, interiorPaint)
    }

    init {
        interiorPaint.typeface = Typeface.DEFAULT_BOLD
        exteriorPaint.typeface = Typeface.DEFAULT_BOLD
        interiorPaint.color = color
        exteriorPaint.color = color
        interiorPaint.style = Paint.Style.FILL
        exteriorPaint.style = Paint.Style.FILL
        interiorPaint.isAntiAlias = false
        exteriorPaint.isAntiAlias = false
        interiorPaint.alpha = 255
        exteriorPaint.alpha = 255
        textSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            22F,
            displayMetrics
        )
        interiorPaint.textSize = textSize
        exteriorPaint.textSize = textSize
        interiorPaint.strokeWidth = textSize / 8
        exteriorPaint.strokeWidth = textSize / 8
    }
}