package de.inovex.recognizecarswithtflite

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import de.inovex.recognizecarswithtflite.utils.RecognizedBoundingBox
import de.inovex.recognizecarswithtflite.utils.RecognizedLabelText
import de.inovex.recognizecarswithtflite.utils.addWidthCompensation
import de.inovex.recognizecarswithtflite.utils.computeImageToPreviewConversionMatrix
import java.util.concurrent.Executors

// Listener for the result of the ImageAnalyzer
typealias RecognitionListener = (recognition: List<Recognition>, image: ImageProxy, color: Int?) -> Unit

class MainActivity : AppCompatActivity() {

    // CameraX
    private lateinit var preview: Preview // Preview use case, fast, responsive view of the camera
    private lateinit var imageAnalyzer: ImageAnalysis // Analysis use case, for running ML code
    private lateinit var camera: Camera
    private val cameraExecutor = Executors.newSingleThreadExecutor()

    // Preview and results views
    private val previewView by lazy {
        findViewById<PreviewView>(R.id.previewView)
    }
    private val resultsView by lazy {
        findViewById<ImageView>(R.id.resultsView)
    }

    // ViewModel, where the recognition results will be stored and updated with each new image analyzed by the Tensorflow Lite Model.
    private val recognitionListViewModel: RecognitionListViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        setTheme(R.style.Theme_RecognizeCarsWithTFLite)
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Request camera permissions
        if (allPermissionsGranted()) {
            startCamera()
        } else {
            ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
        }

        // Update the results whenever there are changes in the recognized items
        recognitionListViewModel.recognitionList.observe(this, { items ->
            runOnUiThread {
                if (resultsView.height > 0 && resultsView.width > 0) {
                    showResults(resultsView, items)
                }
            }
        })
    }

    /**
     * Check all permissions are granted - use for Camera permission in this example.
     */
    private fun allPermissionsGranted(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    /**
     * This gets called after the Camera permission pop up is shown.
     */
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(this, getString(R.string.permission_deny_text), Toast.LENGTH_SHORT)
                    .show()
                finish()
            }
        }
    }

    /**
     * Start the Camera which involves:
     *
     * 1. Initialising the preview use case
     * 2. Initialising the image analyser use case
     * 3. Attach both to the lifecycle of this activity
     * 4. Pipe the output of the preview object to the PreviewView on the screen
     */
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // Used to bind the lifecycle of cameras to the lifecycle owner
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            preview = Preview.Builder().build()

            imageAnalyzer = ImageAnalysis.Builder()
                // This sets the ideal size for the image to be analysed, CameraX will choose the
                // the most suitable resolution which may not be exactly the same or hold the same
                // aspect ratio
                .setTargetResolution(Size(WIDTH, HEIGHT))
                // How the Image Analyser should pipe in input, 1. every frame but drop no frame, or
                // 2. go to the latest frame and may drop some frame. The default is 2.
                // STRATEGY_KEEP_ONLY_LATEST. The following line is optional, kept here for clarity
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { analysisUseCase: ImageAnalysis ->
                    analysisUseCase.setAnalyzer(
                        cameraExecutor, ImageAnalyzer(this) { items, image, color ->
                            // updating the list of recognised objects
                            recognitionListViewModel.updateData(items, image, color)
                        }
                    )
                }

            // Select camera, back is the default. If it is not available, choose front camera
            val cameraSelector = if (cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA))
                CameraSelector.DEFAULT_BACK_CAMERA else CameraSelector.DEFAULT_FRONT_CAMERA

            try {
                // Unbind use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to camera - try to bind everything at once and CameraX will find
                // the best combination.
                camera = cameraProvider.bindToLifecycle(
                    this, cameraSelector, preview, imageAnalyzer
                )

                // Attach the preview to preview view, aka View Finder
                preview.setSurfaceProvider(previewView.surfaceProvider)
            } catch (exc: Exception) {
                Log.e("@string/app_name", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    /**
     * Display the results of the image analysis over the preview.
     */
    private fun showResults(imageView: ImageView, items: List<Recognition>) {
        val widthCompensation = (recognitionListViewModel.image?.height?.minus(WIDTH)) ?: 0
        val matrix =
            computeImageToPreviewConversionMatrix(previewView, recognitionListViewModel.image!!)
        val recognizedBoundingBox = RecognizedBoundingBox(
            imageView, recognitionListViewModel.color ?: resources.getColor(R.color.blue, theme)
        )
        val recognizedLabelText = RecognizedLabelText(
            resources.displayMetrics,
            recognitionListViewModel.color ?: resources.getColor(R.color.blue, theme)
        )

        // Clear previous results
        imageView.setImageResource(0)

        // Create canvas on which to draw the results
        val canvas = Canvas(recognizedBoundingBox.mutableBitmap)

        // Draw results: bounding box and label of those with the highest confidence (a maximum of 3 items)
        for (i in items) {
            // Ignore those with very low confidence
            if (i.confidence < 0.4F)
                continue

            // Convert the area using a matrix, add a width compensation and draw the box
            matrix.mapRect(i.location)
            i.location =
                i.location.addWidthCompensation(widthCompensation, recognitionListViewModel.image!!)
            recognizedBoundingBox.drawRect(canvas, i.location)

            // Draw label
            recognizedLabelText.drawText(
                canvas,
                i.location.left,
                i.location.top,
                i.label,
                i.confidence
            )

            // Apply changes
            imageView.setImageBitmap(recognizedBoundingBox.mutableBitmap)
        }
    }

    companion object {
        const val MAX_RESULT_DISPLAY = 3
        const val WIDTH = 640
        const val HEIGHT = 640
        const val ASSOCIATED_AXIS_LABELS = "labelmap.txt"
        const val REQUEST_CODE_PERMISSIONS = 123
        private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    }
}