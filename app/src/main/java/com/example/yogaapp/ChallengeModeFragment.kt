package com.example.yogaapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.RectF
import android.hardware.SensorManager
import android.os.Bundle
import android.os.Parcelable
import android.os.SystemClock
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.*
import android.widget.*
import androidx.fragment.app.Fragment
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import kotlinx.coroutines.*
import kotlinx.coroutines.Dispatchers.Main
import java.util.ArrayList
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.round
import kotlin.random.Random.Default.nextInt


class ChallengeModeFragment : Fragment(), PoseEstimatorUser {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textureView: TextureView
    private lateinit var textViewFPS: TextView
    private lateinit var textViewPoseConfidence: TextView
    private lateinit var textViewPose: TextView
    private lateinit var imageButtonSettings: ImageButton
    private lateinit var orientationListener: OrientationEventListener
    private lateinit var analyzer:PoseEstimator
    private lateinit var targetSize: Size
    private lateinit var buttonNext: Button
    private var lastUpdated:Long = 0
    private lateinit var imageButtonSwitchCamera: ImageButton
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private val LENS_FACING_KEY: String = "lens_facing"
    private var rotation = 0
    private var displayHeight:Int = 0
    private var displayWidth:Int = 0
    private lateinit var preferences: SharedPreferences
    private var showFPS: Boolean = true
    private var confidenceThreshold:Int = 20
    private var filteringTimeThreshold:Int = 1
    private lateinit var modelType: String
    private val TAG = "CameraXBasic"
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private var listOfPoses: MutableList<TimestampedPose> = mutableListOf()
    private lateinit var targetPose: String
    private var holdTimeThreshold: Int = 1
    private lateinit var textViewTargetPose: TextView
    private val KEY_TARGET_POSE = "target_pose"
    private val KEY_LIST_OF_POSES = "list_of_poses"


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_challenge_mode, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadSettings()
        imageButtonSettings = view.findViewById(R.id.imageButtonSettings)
        imageButtonSettings.setOnClickListener {
            findNavController().navigate(R.id.action_challengeModeFragment_to_settingsFragment)
        }
        textViewFPS = view.findViewById(R.id.textViewFPS)
        if (showFPS){textViewFPS.visibility = View.VISIBLE}
        else {textViewFPS.visibility = View.GONE}
        textureView = view.findViewById(R.id.textureView)
        textViewPoseConfidence = view.findViewById(R.id.textViewPoseConfidence)
        imageButtonSwitchCamera = view.findViewById(R.id.imageButtonSwitchCamera)
        textViewPose = view.findViewById(R.id.textViewPose)
        textViewTargetPose = view.findViewById(R.id.textViewTargetPose)
        buttonNext = view.findViewById(R.id.buttonNext)
        buttonNext.setOnClickListener {
            changePose()
        }

        val layoutTop = view.findViewById<LinearLayout>(R.id.layout_top)
        layoutTop.bringToFront()
        layoutTop.invalidate()
        val layoutBottom = view.findViewById<LinearLayout>(R.id.layout_bottom)
        layoutBottom.bringToFront()
        layoutBottom.invalidate()
    }

    private fun loadSettings(){
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        confidenceThreshold = preferences.getInt("confidenceThreshold", 20)
        filteringTimeThreshold = preferences.getInt("timeThreshold", 1)
        modelType = preferences.getString("modelType", "RT").toString()
        showFPS = preferences.getBoolean("showFPS", true)
        holdTimeThreshold = preferences.getInt("holdPoseThreshold", 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        displayHeight = displayMetrics.heightPixels
        displayWidth = displayMetrics.widthPixels
        targetPose = randomPose()

        if (savedInstanceState != null)
        {
            targetPose = savedInstanceState.getString(KEY_TARGET_POSE).toString()
            listOfPoses = savedInstanceState.getParcelableArrayList<TimestampedPose>(KEY_LIST_OF_POSES) as MutableList<TimestampedPose>
            if (savedInstanceState.getBoolean(LENS_FACING_KEY)) {
                lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
            }
        }
    }

    override fun onResume() {
        super.onResume()

        cameraExecutor = Executors.newSingleThreadExecutor()

        if (modelType == "I"){
            targetSize = Size(256, 256)
        }
        if (modelType == "II"){
            targetSize = Size(368, 368)
        }
        if (modelType == "III"){
            targetSize = Size(480, 480)
        }
        if (modelType == "RT"){
            targetSize = Size(224, 224)
        }

        analyzer = PoseEstimator(
            requireContext(),
            modelType, this)
        analyzer.updateThreshold(confidenceThreshold)

        if (allPermissionsGranted()) {
            startCamera()
        }
        else{
            requestPermissions(
                REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
        lastUpdated = SystemClock.uptimeMillis()
        imageButtonSwitchCamera.setOnClickListener {
            if (lensFacing ==  CameraSelector.DEFAULT_BACK_CAMERA) {
                lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA
                when (rotation) {
                    in 46..135 -> {
                        analyzer.updateRotation(180)
                    }
                    in 136..225 -> {
                        analyzer.updateRotation(90)
                    }
                    in 226..315 -> {
                        analyzer.updateRotation(0)
                    }
                    in 316..359 -> {
                        analyzer.updateRotation(270)
                    }
                    in 0..45 -> {
                        analyzer.updateRotation(270)
                    }
                }
            } else {
                lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
                when (rotation) {
                    in 46..135 -> {
                        analyzer.updateRotation(180)
                    }
                    in 136..225 -> {
                        analyzer.updateRotation(270)
                    }
                    in 226..315 -> {
                        analyzer.updateRotation(0)
                    }
                    in 316..359 -> {
                        analyzer.updateRotation(90)
                    }
                    in 0..45 -> {
                        analyzer.updateRotation(90)
                    }
                }
            }
            startCamera()
        }

        orientationListener = object : OrientationEventListener(context,
            SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                if(lensFacing == CameraSelector.DEFAULT_BACK_CAMERA){
                    rotation = orientation
                    when (orientation) {
                        in 46..135 -> {
                            analyzer.updateRotation(180)
                        }
                        in 136..225 -> {
                            analyzer.updateRotation(270)
                        }
                        in 226..315 -> {
                            analyzer.updateRotation(0)
                        }
                        in 316..359 -> {
                            analyzer.updateRotation(90)
                        }
                        in 0..45 -> {
                            analyzer.updateRotation(90)
                        }
                    }
                }
                else{
                    when (orientation) {
                        in 46..135 -> {
                            analyzer.updateRotation(180)
                        }
                        in 136..225 -> {
                            analyzer.updateRotation(90)
                        }
                        in 226..315 -> {
                            analyzer.updateRotation(0)
                        }
                        in 316..359 -> {
                            analyzer.updateRotation(270)
                        }
                        in 0..45 -> {
                            analyzer.updateRotation(270)
                        }
                    }
                }

            }
        }
        orientationListener.enable()

    }

    override fun onPause() {
        super.onPause()

        orientationListener.disable()
        cameraExecutor.shutdown()
        cameraExecutor.awaitTermination(3000, TimeUnit.MILLISECONDS)
        analyzer.releaseResources()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        if (lensFacing == CameraSelector.DEFAULT_FRONT_CAMERA){
            outState.putBoolean(LENS_FACING_KEY, true)
        }
        else{
            outState.putBoolean(LENS_FACING_KEY, false)
        }
        outState.putString(KEY_TARGET_POSE, targetPose)
        outState.putParcelableArrayList(KEY_LIST_OF_POSES, listOfPoses as ArrayList<out Parcelable>)
    }

    override fun update(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){
        listOfPoses.add(TimestampedPose(pose, timestamp))
        if (checkPose())
        {
            CoroutineScope(Main).launch {
                greenSignal()
            }
            changePose()
        }
        updateUI(bitmap, pose, confidence, timestamp)
    }

    private fun updateUI(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){
        requireActivity().runOnUiThread {
            try{
                val canvas = textureView.lockCanvas()
                canvas.drawColor(Color.BLACK)
                if (displayWidth < displayHeight){
                    val scale = canvas.width.toFloat()/bitmap.width.toFloat()
                    val dst = RectF(0F, (canvas.height - bitmap.height.toFloat() * scale) / 2, displayWidth.toFloat(),
                        canvas.height - (canvas.height - bitmap.height.toFloat() * scale) / 2)
                    canvas.drawBitmap(bitmap, null, dst, null)
                }
                if (displayWidth >= displayHeight){
                    val scale = canvas.height.toFloat()/bitmap.height.toFloat()
                    val dst = RectF((canvas.width - bitmap.width * scale) / 2, 0F,
                        canvas.width - (canvas.width - bitmap.width * scale) / 2, canvas.height.toFloat())
                    canvas.drawBitmap(bitmap, null, dst, null)
                }
                textureView.unlockCanvasAndPost(canvas)
            } catch (e: Exception){
                Log.d("TextureView", e.message)
            }
            if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
                textureView.scaleX = 1F
            } else {
                textureView.scaleX = -1F
            }

            textViewFPS.text = getString(R.string.timePerFrameTextView, (timestamp - lastUpdated).toInt())
            textViewPoseConfidence.text = getString(R.string.confidenceTextView, (confidence * 10000 / 100).toInt())
            textViewPose.text = getString(R.string.poseTextView, pose)
            textViewTargetPose.text = getString(R.string.targetPoseTextView, targetPose)
            lastUpdated = timestamp
        }
    }


    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireActivity(), it) == PackageManager.PERMISSION_GRANTED
    }


    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<String>, grantResults:
        IntArray
    ) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                startCamera()
            } else {
                Toast.makeText(
                    context,
                    getString(R.string.permissionNotGranted),
                    Toast.LENGTH_SHORT
                ).show()
                activity?.finish()
            }
        }
    }

    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = context?.let { ProcessCameraProvider.getInstance(it) }

        cameraProviderFuture?.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetResolution(targetSize)
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, lensFacing, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    private fun randomPose(): String
    {
        val randomNumber = nextInt(10)
        var pose = getString(R.string.unknownPose)
        when (randomNumber) {
            0 -> {pose = getString(R.string.treePose)}
            1 -> {pose = getString(R.string.warriorIPose)}
            2 -> {pose = getString(R.string.downwardDogPose)}
            3 -> {pose = getString(R.string.mountainPose)}
            4 -> {pose = getString(R.string.warriorIIPose)}
            5 -> {pose = getString(R.string.bowPose)}
            6 -> {pose = getString(R.string.camelPose)}
            7 -> {pose = getString(R.string.plankPose)}
            8 -> {pose = getString(R.string.chairPose)}
            9 -> {pose = getString(R.string.garlandPose)}
        }
        return pose
    }

    private fun checkPose(): Boolean
    {
        var requirementMet: Boolean = true
        val reversedList: MutableList<TimestampedPose> = mutableListOf()
        reversedList.addAll(listOfPoses.asReversed())
        if (reversedList[0].timestamp - reversedList.lastOrNull()!!.timestamp < holdTimeThreshold)
        {
            requirementMet = false
            return requirementMet
        }
        for (pose in reversedList)
        {
            if (reversedList[0].timestamp - pose.timestamp < holdTimeThreshold * 1000 &&
                    pose.poseName != targetPose)
            {
                requirementMet = false
            }

            if (reversedList[0].timestamp - pose.timestamp >= holdTimeThreshold * 1000)
            {
                listOfPoses.remove(pose)
            }
        }
        return requirementMet
    }

    private fun changePose()
    {
        var newPose: String
        do
        {
            newPose = randomPose()
        }
        while(newPose == targetPose)
        targetPose = newPose
        listOfPoses.clear()
    }

    private suspend fun greenSignal()
    {
        withContext(Main)
        {
            textViewTargetPose.setBackgroundColor(Color.GREEN)
            delay(500)
            textViewTargetPose.setBackgroundColor(Color.BLACK)
        }
    }
}