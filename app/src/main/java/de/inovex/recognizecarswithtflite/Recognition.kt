package de.inovex.recognizecarswithtflite

import android.graphics.RectF

/**
 * Recognition item object with fields for the label, the probability and the location of the bounding box.
 */
class Recognition(
    val label: String,
    val confidence: Float,
    var location: RectF
) {
    val probabilityString = String.format("%.1f%%", confidence * 100.0f)
    override fun toString(): String {
        return "$label / $probabilityString"
    }
}