package com.syeddev.facedetectionmediapipe

import android.Manifest
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.google.common.util.concurrent.ListenableFuture
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.syeddev.facedetectionmediapipe.databinding.ActivityCameraBinding
import com.syeddev.facedetectionmediapipe.manager.FaceDetectorHelper
import com.syeddev.facedetectionmediapipe.manager.FaceDetectorHelper.Companion.THRESHOLD_DEFAULT
import com.syeddev.facedetectionmediapipe.utils.hasCameraPermission
import com.syeddev.facedetectionmediapipe.utils.showDialogPermission
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraActivity : AppCompatActivity(),FaceDetectorHelper.DetectorListener {

    private val TAG = "FaceDetection"

    private val binding : ActivityCameraBinding by lazy {
        ActivityCameraBinding.inflate(layoutInflater)
    }

    private val cameraPermission by lazy {
        registerForActivityResult(ActivityResultContracts.RequestPermission()){ isGranted->
            if (isGranted){
                Log.e(TAG,"Camera Permission Granted..")
                Toast.makeText(this,"Camera Permission Granted..",Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG,"Camera Permission Denied..")
                Toast.makeText(this,"Camera Permission Denied..",Toast.LENGTH_SHORT).show()
            }
        }
    }

    private lateinit var faceDetectorHelper: FaceDetectorHelper

    private val preview = Preview.Builder()
        .setTargetRotation(binding.uiCameraPreview.display.rotation)
        .build()
    private var cameraProvider: ProcessCameraProvider? = null
    private val cameraProviderFuture: ListenableFuture<ProcessCameraProvider> by lazy {
        ProcessCameraProvider.getInstance(this)
    }
    private val backgroundExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }

    private var cameraSelector: CameraSelector? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var imageCapture: ImageCapture? = null
    private lateinit var bitmapBuffer: Bitmap

    @RequiresApi(Build.VERSION_CODES.R)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cameraPermission.launch(Manifest.permission.CAMERA)
        setContentView(binding.root)
        if(this.hasCameraPermission()){
            setUpCamera()
        }else {
            showDialogPermission(
                "camera",
                !shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)
            ) { cameraPermission.launch(Manifest.permission.CAMERA) }
        }
    }

    override fun onResume() {
        super.onResume()
        backgroundExecutor.execute {
            if (faceDetectorHelper.isClosed()) {
                faceDetectorHelper.setUpFaceDetector()
            }
        }
    }

    override fun onPause() {
        super.onPause()
        if(this::faceDetectorHelper.isInitialized) {
            backgroundExecutor.execute { faceDetectorHelper.clearFaceDetector() }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun setUpCamera(){
        backgroundExecutor.execute {
            faceDetectorHelper = FaceDetectorHelper(
                context = this,
                threshold = THRESHOLD_DEFAULT,
                faceDetectorListener = this,
                runningMode = RunningMode.LIVE_STREAM
            )
            binding.uiCameraPreview.post {
                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    bindCamera()
                },ContextCompat.getMainExecutor(this@CameraActivity))
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.R)
    private fun bindCamera(){
        cameraSelector = CameraSelector.Builder()
            .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
            .build()

        imageCapture = ImageCapture.Builder()
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(binding.uiCameraPreview.display.rotation)
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
            .build()
            .also {
                it.setAnalyzer(
                    backgroundExecutor,
                    faceDetectorHelper::detectLiveStreamFrame
                )
            }

        cameraProvider?.unbindAll()

        try {
            cameraProvider?.bindToLifecycle(
                this as LifecycleOwner,
                cameraSelector!!,
                preview,
                imageAnalyzer
            )
            preview.setSurfaceProvider(binding.uiCameraPreview.surfaceProvider)
        } catch (exc: Exception){
            Log.e(TAG, "Use case binding failed", exc)
        }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        imageAnalyzer?.targetRotation = binding.uiCameraPreview.display.rotation
    }

    override fun onResults(resultBundle: FaceDetectorHelper.ResultBundle) {
        val detectionResult = resultBundle.results[0]
        binding.overlay.setResults(
            detectionResult,
            resultBundle.inputImageHeight,
            resultBundle.inputImageWidth
        )
        binding.overlay.invalidate()
    }

    override fun onError(error: String, errorCode: Int) {
        this.runOnUiThread {
            Toast.makeText(this,error,Toast.LENGTH_SHORT).show()
        }
    }
}