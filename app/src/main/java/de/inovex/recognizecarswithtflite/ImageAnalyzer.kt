package de.inovex.recognizecarswithtflite

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.RectF
import android.view.Surface
import android.view.WindowManager
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.palette.graphics.Palette
import de.inovex.recognizecarswithtflite.MainActivity.Companion.ASSOCIATED_AXIS_LABELS
import de.inovex.recognizecarswithtflite.MainActivity.Companion.HEIGHT
import de.inovex.recognizecarswithtflite.MainActivity.Companion.MAX_RESULT_DISPLAY
import de.inovex.recognizecarswithtflite.MainActivity.Companion.WIDTH
import de.inovex.recognizecarswithtflite.ml.Detect
import de.inovex.recognizecarswithtflite.utils.toBitmap
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.common.TensorProcessor
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.image.ops.Rot90Op
import org.tensorflow.lite.support.label.TensorLabel
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer

class ImageAnalyzer(
    private val ctx: Context,
    private val listener: RecognitionListener
) : ImageAnalysis.Analyzer {

    // Tensorflow Lite Model Instance
    private val carsModel = Detect.newInstance(ctx)

    // Associated labels from the txt file
    var associatedAxisLabels: List<String?>? = FileUtil.loadLabels(ctx, ASSOCIATED_AXIS_LABELS)

    /**
     * Calculate what rotation of an image is necessary before passing it to the model so as to
     * compensate for the device rotation.
     */
    private fun calculateNecessaryRotation(): Int {
        return when ((ctx.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay.rotation) {
            Surface.ROTATION_90 -> 0
            Surface.ROTATION_270 -> 2
            Surface.ROTATION_180 -> 4
            Surface.ROTATION_0 -> 3
            else -> 3
        }
    }

    /**
     * Analyze images from the camera stream using a Tensorflow Lite Model which performs
     * object detection of cars in images.
     * Takes an ImageProxy as argument and returns the recognition results to a listener.
     */
    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(imageProxy: ImageProxy) {
        val items: MutableList<Recognition> = mutableListOf()

        // LABELS
        val probabilityProcessor = TensorProcessor.Builder().build()
        val probabilityBuffer = TensorBuffer.createFixedSize(intArrayOf(1, 196), DataType.UINT8)
        val labels = TensorLabel(
            associatedAxisLabels!!,
            probabilityProcessor.process(probabilityBuffer)
        )

        // IMAGE PREPROCESSING
        val imageProcessor = ImageProcessor.Builder()
            // Center crop the image
            .add(ResizeWithCropOrPadOp(HEIGHT, WIDTH))
            // Rotate
            .add(Rot90Op(calculateNecessaryRotation()))
            .build()
        var tImage = TensorImage(DataType.UINT8)
        val bitmap = imageProxy.image!!.toBitmap()
        tImage.load(bitmap)
        tImage = imageProcessor.process(tImage)
        // Extract the dominant color from the image to compute what the most suitable color for the text would be
        val palette = Palette.from(bitmap).generate()
        val color = palette.dominantSwatch?.bodyTextColor

        // INFERENCE
        // Process the model
        val outputs = carsModel.process(tImage)
        // Extract the recognition results
        val numBoxes = outputs.numberOfDetectionsAsTensorBuffer.intArray[0]
        val detectionClasses = outputs.categoryAsTensorBuffer.intArray
        val detectionScores = outputs.scoreAsTensorBuffer.floatArray
        val boxes = outputs.locationAsTensorBuffer
        val detectionBoxes = Array(numBoxes) { FloatArray(4) }
        for (i in detectionBoxes.indices) {
            detectionBoxes[i] = boxes.floatArray.copyOfRange(
                4 * i,
                4 * i + 4
            )
        }
        for (i in 0 until MAX_RESULT_DISPLAY) {
            items.add(
                Recognition(
                    labels.categoryList[detectionClasses[i]].label,
                    detectionScores[i],
                    imageProcessor.inverseTransform(
                        RectF(
                            detectionBoxes[i][1] * WIDTH,
                            detectionBoxes[i][0] * HEIGHT,
                            detectionBoxes[i][3] * WIDTH,
                            detectionBoxes[i][2] * HEIGHT
                        ), imageProxy.height, imageProxy.width
                    )
                )
            )
        }

        // Sort the results by their confidence and return the three with the highest
        listener(items.apply {
            sortByDescending { it.confidence }
        }.take(MAX_RESULT_DISPLAY).toList(), imageProxy, color)

        // Close the image. This tells CameraX to feed the next image to the analyzer
        imageProxy.close()
    }
}