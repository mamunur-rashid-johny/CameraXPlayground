package com.example.cameraxplayground

import android.Manifest
import android.animation.ObjectAnimator
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.airbnb.lottie.LottieDrawable
import com.example.cameraxplayground.databinding.ActivityMainBinding
import com.example.cameraxplayground.utils.CameraActionEnum.*
import com.google.android.material.snackbar.Snackbar
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.Face
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val viewModel: PhotoViewModel by viewModels()
    private lateinit var detector: FaceDetector

    //camerax related
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var cameraProvider: ProcessCameraProvider
    private var imageCapture: ImageCapture? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var preview: Preview? = null
    private lateinit var imgCaptureExecutor: ExecutorService
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    //image file related
    private lateinit var outputDirectory: File
    private var savedUri: Uri? = null

    //time manage for eye blink
    private var startTimeStamp:Long = 0L
    private var blinkState = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initFaceDetectionClient()
        initCameraClient()
        checkCameraPermission()
    }

    private fun initFaceDetectionClient() {
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_ALL)
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_ALL)
            .build()
        detector = FaceDetection.getClient(options)
        binding.faceLottieAnim.speed = 0.5f
        viewModel.shuffleAndResetList()
        viewModel.cameraActionLiveData.observe(this) {
            when (it) {
                NODE_LEFT -> {
                    binding.faceLottieAnim.setMinAndMaxFrame(44, 78)
                    binding.faceLottieAnim.playAnimation()
                    binding.faceLottieAnim.repeatCount = LottieDrawable.INFINITE
                }
                NODE_RIGHT -> {

                    binding.faceLottieAnim.setMinAndMaxFrame(0, 43)
                    binding.faceLottieAnim.playAnimation()
                    binding.faceLottieAnim.repeatCount = LottieDrawable.INFINITE
                }
                EYE_BLINK -> {
                    binding.faceLottieAnim.setMinAndMaxFrame(80, 157)
                    binding.faceLottieAnim.playAnimation()
                    binding.faceLottieAnim.repeatCount = LottieDrawable.INFINITE
                }
                SMILE -> {
                    binding.faceLottieAnim.setMinAndMaxFrame(165, 233)
                    binding.faceLottieAnim.playAnimation()
                    binding.faceLottieAnim.repeatCount = LottieDrawable.INFINITE
                }
            }
        }
        viewModel.cameraActionText.observe(this){
            binding.textCameraActionText.text = "$it"
        }
        viewModel.progressValue.observe(this){
            ObjectAnimator.ofInt(binding.progressBar,"progress",binding.progressBar.progress,it)
                .setDuration(300)
                .start()
            binding.textProgressText.text = "Competed $it%"
        }
    }

    private fun initCameraClient() {

        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProvider = cameraProviderFuture.get()
        outputDirectory = getOutputDirectory()
        imgCaptureExecutor = Executors.newSingleThreadExecutor()
    }

    private fun getOutputDirectory(): File {
        val mediaDir = externalMediaDirs.firstOrNull().let {
            File(it, "Camerax").apply { mkdirs() }
        }
        return if (mediaDir.exists())
            mediaDir else filesDir
    }

    //permission manager for camera
    private val requestPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                openCamera()
            } else if (!shouldShowRequestPermissionRationale(Manifest.permission.CAMERA)) {
                binding.parentLayout.showSnackBar(
                    binding.parentLayout,
                    "Camera access is permanently denied. You need to enable the permission from app setting.",
                    Snackbar.LENGTH_INDEFINITE,
                    getString(R.string.settings)
                ) {
                    openAppSetting()
                }
            } else {
                binding.parentLayout.showSnackBar(
                    binding.parentLayout,
                    "Camera access is required to display the camera preview.",
                    Snackbar.LENGTH_INDEFINITE,
                    getString(R.string.dismiss)
                ) {}
            }
        }

    private fun checkCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED -> {
                openCamera()
            }
            ActivityCompat.shouldShowRequestPermissionRationale(
                this,
                Manifest.permission.CAMERA
            ) -> {
                binding.parentLayout.showSnackBar(
                    binding.parentLayout,
                    "Camera access is permanently denied. You need to enable the permission from app setting.",
                    Snackbar.LENGTH_INDEFINITE,
                    getString(R.string.settings)
                ) {
                    openAppSetting()
                }
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun openCamera() {
        cameraProviderFuture.addListener({

            val viewFinder = binding.preview


            //lens builder
            val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

            //preview builder
            preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(binding.preview.surfaceProvider)
                }

            //image capture builder
            imageCapture = ImageCapture.Builder()
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()

            //image analysis
            imageAnalysis = ImageAnalysis.Builder().apply {
                setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            }.build()

            imageAnalysis?.setAnalyzer(imgCaptureExecutor) { imageProxy ->
                val baseImage = imageProxy.image
                val inpImage =
                    InputImage.fromMediaImage(baseImage!!, imageProxy.imageInfo.rotationDegrees)
                detector.process(inpImage)
                    .addOnSuccessListener { faces ->
                        if (faces.size == 1) {
                            binding.textNoFaceText.visibility = View.GONE
                            binding.faceLottieAnim.visibility = View.VISIBLE
                            binding.textCameraActionText.visibility = View.VISIBLE
                            when(viewModel.cameraActionEnum){
                                SMILE -> {
                                    faces[0].smilingProbability?.let {
                                        if (it>= SMILE_THRESHOLD){
                                            viewModel.successExecution()
                                        }
                                    }
                                }
                                NODE_LEFT -> {
                                    if (faces[0].headEulerAngleY>= HEAD_ANGEL_RIGHT){
                                      viewModel.successExecution()
                                    }
                                    }
                                NODE_RIGHT -> {
                                    if (faces[0].headEulerAngleY<= HEAD_ANGEL_LEFT){
                                        viewModel.successExecution()
                                    }
                                }
                                EYE_BLINK -> {
                                     when(blinkState){
                                         0->{
                                             if ((faces[0]?.leftEyeOpenProbability?:0f) >= OPEN_THRESHOLD && (faces[0]?.rightEyeOpenProbability?:0f) >= OPEN_THRESHOLD ){
                                                 blinkState = 1
                                                 startTimeStamp = System.currentTimeMillis()
                                             }
                                         }
                                         1->{
                                             if ((faces[0]?.leftEyeOpenProbability?:0f)< CLOSE_THRESHOLD && ((faces[0]?.rightEyeOpenProbability?:0f)< CLOSE_THRESHOLD)){
                                                 blinkState = 2
                                             }
                                         }
                                         2->{
                                             if ((faces[0]?.leftEyeOpenProbability?:0f) >= OPEN_THRESHOLD && (faces[0]?.rightEyeOpenProbability?:0f) >= OPEN_THRESHOLD ){
                                                val currentTime = System.currentTimeMillis()
                                                 blinkState = if (((currentTime-startTimeStamp)/1000.0)<5){
                                                     3
                                                 }else{
                                                     0
                                                 }
                                             }
                                         }
                                         3->{
                                             //update call success thing on view Model
                                             blinkState = 4
                                             viewModel.successExecution()
                                         }
                                     }


                                }
                                else -> {
                                    //no need for change
                                }
                            }
                            imageProxy.close()
                        }else{
                            binding.textCameraActionText.visibility = View.GONE
                            binding.faceLottieAnim.visibility = View.GONE
                            binding.textNoFaceText.visibility = View.VISIBLE
                            viewModel.shuffleAndResetList()
                            val msg = if (faces.isEmpty()) "No face detected!" else "${faces.size} faces detected!"
                            binding.textNoFaceText.text = msg
                            imageProxy.close()
                        }

                        if (viewModel.counterValue==4){
                            imageProxy.close()
                            detector.close()
                          //  cameraProvider.unbindAll()
                            viewModel.counterValue = 0
                            capturePhoto()
                            blinkState = 0
                        }
                    }
                    .addOnFailureListener { exp ->
                        Log.e(TAG, "openCamera: $exp")
                        imageProxy.close()
                    }
            }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis)
            } catch (exp: Exception) {
                Log.e(TAG, "error in bind section: $exp")
            }
        }, ContextCompat.getMainExecutor(this))

    }


    private fun openAppSetting() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        val uri = Uri.fromParts("package", packageName, null)
        intent.data = uri
        startActivity(intent)
    }

    private fun View.showSnackBar(view: View, msg: String, length: Int, actionMsg: CharSequence?, action: (View) -> Unit) {
        val snackBar = Snackbar.make(view, msg, length)
        if (actionMsg != null) {
            snackBar.setAction(actionMsg) { action(this) }.show()
        } else {
            snackBar.show()
        }
    }

    private fun capturePhoto() {
        val imageCapture = imageCapture ?: return
        val photoFile = File(
            outputDirectory,
            SimpleDateFormat(
                FILENAME_FORMAT, Locale.US
            ).format(System.currentTimeMillis()) + ".jpg"
        )

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
        imageCapture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onError(exc: ImageCaptureException) {
                    Log.e(TAG, "Photo capture failed: " + exc.message)
                }

                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    binding.constraint.visibility = View.GONE
                    binding.constraintCapture.visibility = View.VISIBLE
                    savedUri = Uri.fromFile(photoFile)
                    loadImage(savedUri)
                }
            })
    }

    private fun loadImage(savedUri: Uri?) {
        Log.e(TAG, "Image uri: $savedUri" )
        binding.ImgFrontSideOfNid.setImageURI(savedUri)
    }

    companion object {
        private const val SMILE_THRESHOLD = 0.20f
        private const val HEAD_ANGEL_LEFT = -20f
        private const val HEAD_ANGEL_RIGHT = 20f
        private const val OPEN_THRESHOLD  = 0.60f
        private const val CLOSE_THRESHOLD  = 0.15f
        private const val TAG = "CameraXDemo"
        const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss-SSS"
    }
}