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
import android.speech.tts.TextToSpeech
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
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.random.Random.Default.nextInt

// Fragment handling logic of "Challenge Mode"
// PoseEstimatorUser is an interface used to pass data from PoseEstimator to whatever
// is using it ("Recording Mode" or "Challenge Mode"). TextTosSpeech.OnInitListener to use TTS.

class ChallengeModeFragment : Fragment(), PoseEstimatorUser, TextToSpeech.OnInitListener {

    // camera executor, basically a thread used by cameraX so it doesn't block the UI thread.
    private lateinit var cameraExecutor: ExecutorService

    // all widgets visible on the screen in "Challenge Mode"
    private lateinit var textureView: TextureView
    private lateinit var textViewFPS: TextView
    private lateinit var textViewPoseConfidence: TextView
    private lateinit var textViewPose: TextView
    private lateinit var textViewScore: TextView
    private lateinit var imageButtonSettings: ImageButton
    private lateinit var imageButtonSwitchCamera: ImageButton
    private lateinit var buttonNext: Button
    private lateinit var textViewTargetPose: TextView

    // orientation listener to check orientation of the device.
    // Resources.Configuration.orientation only tells whether it's portrait or landscape, but doesn't
    // tell which ("left" or "right") landscape it is.
    private lateinit var orientationListener: OrientationEventListener

    // PoseEstimator - class handling all image processing.
    // modelType - RT, I, II, III
    // variable "analyzer" will be sometimes regarded to as PoseEstimator
    private lateinit var analyzer: PoseEstimator
    private lateinit var modelType: String

    // target image resolution for cameraX based on selected pose estimation model.
    // (input resolution of pose estimation model)
    private lateinit var targetSize: Size

    // timestamp of when was the last frame received, used to calculate time per frame
    private var lastUpdated:Long = 0

    // indicates whether back or front camera is used, image from front camera is mirrored
    // so it has to be flipped, also rotation is different
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA

    // current device rotation
    private var rotation = 0

    // display height and width, changes with orientation (portrait/landscape)
    private var displayHeight:Int = 0
    private var displayWidth:Int = 0

    // variable necessary to retrieve settings from settings screen,
    // I'm using predefined Settings Fragment.
    private lateinit var preferences: SharedPreferences

    // Time per frame visibility
    private var showFPS: Boolean = true

    // Confidence threshold for keypoints detected by PoseEstimator. If confidence for a point is lower
    // than the threshold then it's coordinates are set to [0,0] and it is not displayed.
    private var confidenceThreshold:Int = 20

    // how long (in seconds) user has to hold the pose to get a point
    private var holdTimeThreshold: Int = 1

    // Keys for asking for camera permissions, based on "Getting Started with CameraX"
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    // keys to retrieve values from settings or savedInstanceState
    private val KEY_TARGET_POSE = "target_pose"
    private val KEY_LIST_OF_POSES = "list_of_poses"
    private val KEY_CORRECT_POSES = "correct_poses"
    private val KEY_TOTAL_POSES = "total_poses"
    private val LENS_FACING_KEY: String = "lens_facing"

    // list where poses with timestamps are saved, used to determine if user held a pose for
    // long enough
    private var listOfPoses: MutableList<TimestampedPose> = mutableListOf()

    // the pose user has to do
    private lateinit var targetPose: String

    // enable/disable TTS
    private var enableVoiceMessages: Boolean = false

    // TextToSpeech instance
    private lateinit var tts: TextToSpeech

    // multiplier for keypoint marker size
    private var pointSize: Int = 5

    // score
    private var correctPoses = 0
    private var totalPoses = 0


    // load settings and initialize everything
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSettings()

        // create TTS instance
        tts = TextToSpeech(context, this)

        // get display size
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        displayHeight = displayMetrics.heightPixels
        displayWidth = displayMetrics.widthPixels

        // select random pose as target
        targetPose = randomPose()
        totalPoses += 1

        // set target size based on model selected in settings
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

        // create instance of PoseEstimator, pass confidenceThreshold for point detection and
        // point marker size (multiplier)
        analyzer = PoseEstimator(
                requireContext(),
                modelType, this)
        analyzer.updateThreshold(confidenceThreshold)
        analyzer.setPointSize(pointSize)

        // create cameraExecutor (basically start a thread for the camera)
        cameraExecutor = Executors.newSingleThreadExecutor()

        // set orientationListener so it automatically sends orientation to PoseEstimator,
        // amount for which the image has to be rotated is different for front and back cameras
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

        // check if there is a savedInstanceState of this fragment (returning from settings, configuration changed etc.)
        // if yes then restore data.
        if (savedInstanceState != null)
        {
            targetPose = savedInstanceState.getString(KEY_TARGET_POSE).toString()
            listOfPoses = savedInstanceState.getParcelableArrayList<TimestampedPose>(KEY_LIST_OF_POSES) as MutableList<TimestampedPose>
            if (savedInstanceState.getBoolean(LENS_FACING_KEY)) {
                lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
            }
            totalPoses = savedInstanceState.getInt(KEY_TOTAL_POSES)
            correctPoses = savedInstanceState.getInt(KEY_CORRECT_POSES)
        }

        // if camera permissions are granted then start camera, if no then ask for them
        if (allPermissionsGranted()) {
            startCamera()
        }
        else{
            requestPermissions(
                    REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS
            )
        }
    }

    // inflate fragment layout
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_challenge_mode, container, false)
    }

    // when layout is inflated find all widgets, set onClickListeners (configure UI)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        imageButtonSettings = view.findViewById(R.id.imageButtonSettings)

        // navigate to settings after clicking "Settings" button
        imageButtonSettings.setOnClickListener {
            findNavController().navigate(R.id.action_challengeModeFragment_to_settingsFragment)
        }

        // setting visibility of timePerFrame textView. Setting visibility to View.GONE
        //  frees up the slot in LinearLayout
        textViewFPS = view.findViewById(R.id.textViewFPS)
        if (showFPS){textViewFPS.visibility = View.VISIBLE}
        else {textViewFPS.visibility = View.GONE}

        textureView = view.findViewById(R.id.textureView)
        textViewPoseConfidence = view.findViewById(R.id.textViewPoseConfidence)

        // after switching lenses update the rotation for the first frame manually and restart camera,
        // orientationListener only updates after detecting change in orientation,
        // if device was stationary then the rotation passed to PoseEstimator wouldn't be updated.
        imageButtonSwitchCamera = view.findViewById(R.id.imageButtonSwitchCamera)
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
        textViewPose = view.findViewById(R.id.textViewPose)
        textViewTargetPose = view.findViewById(R.id.textViewTargetPose)
        textViewScore = view.findViewById(R.id.textViewScore)
        buttonNext = view.findViewById(R.id.buttonNext)

        // skip pose after clicking "Next" button
        buttonNext.setOnClickListener {
            changePose()
        }
    }

    override fun onResume() {
        super.onResume()

        orientationListener.enable()

        // check to see if a different model was selected in the settings.
        // if yes then reload model and all parameters associated with it and restart camera
        // (different target resolution for different models),
        // check to see if time per frame textView visibility was changed and change if needed.
        val oldModelType = modelType
        val oldShowFPS = showFPS
        loadSettings()

        if (oldModelType != modelType)
        {
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
        }
        analyzer.setPointSize(pointSize)
        analyzer.updateThreshold(confidenceThreshold)

        if (oldShowFPS != showFPS)
        {
            if (showFPS){textViewFPS.visibility = View.VISIBLE}
            else {textViewFPS.visibility = View.GONE}
        }
        lastUpdated = SystemClock.uptimeMillis()
        startCamera()
    }

    override fun onPause() {
        super.onPause()

        orientationListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()

        // Shutdown thread handling camera, wait for its termination
        // and release resources used by pose estimation model. Resources can be only released
        // after the pose estimation model stops receiving and processing frames.
        // Shutdown TTS.
        cameraExecutor.shutdown()
        cameraExecutor.awaitTermination(3000, TimeUnit.MILLISECONDS)
        analyzer.releaseResources()
        try
        {
            tts.stop()
            tts.shutdown()
        }catch (e:Exception)
        {
            Log.d("TTS", e.message.toString())
        }
    }

    // Write to Bundle (save) all necessary parameters on rotation, when locking screen,
    // navigating to settings etc.
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
        outState.putInt(KEY_CORRECT_POSES, correctPoses)
        outState.putInt(KEY_TOTAL_POSES, totalPoses)
    }

    // load settings from preferences, preferences are basically all the settings in "Settings" screen
    private fun loadSettings(){
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        confidenceThreshold = preferences.getInt("confidenceThreshold", 20)
        modelType = preferences.getString("modelType", "RT").toString()
        showFPS = preferences.getBoolean("showFPS", true)
        holdTimeThreshold = preferences.getInt("holdPoseThreshold", 1)
        enableVoiceMessages = preferences.getBoolean("enableVoiceMessages", false)
        pointSize = preferences.getInt("pointSize", 5)
    }

    // method defined by PoseEstimatorUser interface. PoseEstimator returns bitmap, name of the pose,
    // confidence for that pose, and timestamp of when was the frame passed to fragment.
    override fun update(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){

        // add pose name with timestamp to list so its possible to check for how long its being held
        listOfPoses.add(TimestampedPose(pose, timestamp))

        // check if pose is correct and is being held for long enough to award point. If yes then
        // make background of target pose textView green for 0.5 second, change target pose, clear list of poses.
        // In the end update UI.
        if (checkPose())
        {
            CoroutineScope(Main).launch {
                greenSignal()
            }
            correctPoses += 1
            changePose()
            listOfPoses.clear()
        }
        updateUI(bitmap, pose, confidence, timestamp)

    }


    private fun updateUI(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){

        // all actions regarding UI must be performed on UI thread
        requireActivity().runOnUiThread {

            // display image from PoseEstimator on TextureView,
            // check orientation of the screen and scale bitmap
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
                Log.d("TextureView", e.message.toString())
            }

            // image from front facing camera is mirrored, it has to be flipped
            if (lensFacing == CameraSelector.DEFAULT_BACK_CAMERA) {
                textureView.scaleX = 1F
            } else {
                textureView.scaleX = -1F
            }

            // update textViews
            textViewFPS.text = activity?.getString(R.string.timePerFrameTextView, (timestamp - lastUpdated).toInt())
            textViewPoseConfidence.text = activity?.getString(R.string.confidenceTextView, (confidence * 10000 / 100).toInt())
            textViewPose.text = activity?.getString(R.string.poseTextView, pose)
            textViewTargetPose.text = activity?.getString(R.string.targetPoseTextView, targetPose)
            lastUpdated = timestamp
            textViewScore.text = activity?.getString(R.string.scoreTextView, correctPoses, totalPoses)
        }

    }

    // from "Getting Started with CameraX"
    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(requireActivity(), it) == PackageManager.PERMISSION_GRANTED
    }

    // from "Getting Started with CameraX"
    // checks results of permission request
    // starts camera if permissions were granted
    // closes activity if not (returns to main menu)
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

    // based on "Getting Started with CameraX", using image analysis use case
    @SuppressLint("RestrictedApi")
    private fun startCamera() {
        val cameraProviderFuture = context?.let { ProcessCameraProvider.getInstance(it) }

        cameraProviderFuture?.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val imageAnalyzer = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST) // keeps only latest frame in buffer
                .setTargetResolution(targetSize)  // input size of pose estimation model
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, analyzer)  // frames will be sent to analyzer (Pose Estimator)
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this, lensFacing, imageAnalyzer) // starts camera session

            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    // returns random pose
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

    // checks if correct pose was held long enough. if yes then it returns true
    private fun checkPose(): Boolean
    {
        var poseCorrect = true
        val reversedList: MutableList<TimestampedPose> = mutableListOf()

        // checks if enough time has even passed since last correctly done pose
        reversedList.addAll(listOfPoses.asReversed())
        if (reversedList[0].timestamp - reversedList.lastOrNull()!!.timestamp < holdTimeThreshold
                || reversedList.size == 0 || reversedList.size == 1)
        {
            poseCorrect = false
            return poseCorrect
        }

        // checks if the correct pose was held long enough.
        // timestamps are in milliseconds and threshold is in seconds so it has to be multiplied
        for (pose in reversedList)
        {
            if (reversedList[0].timestamp - pose.timestamp < holdTimeThreshold * 1000 &&
                    pose.poseName != targetPose)
            {
                poseCorrect = false
            }

            // delete all records that no longer are needed.
            if (reversedList[0].timestamp - pose.timestamp >= holdTimeThreshold * 1000)
            {
                listOfPoses.remove(pose)
            }
        }
        return poseCorrect
    }

    // selects new target pose, new pose can't be the same as the old one
    // if TTS is enabled it is activated
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
        if (enableVoiceMessages)
        {
            tts.speak(targetPose, TextToSpeech.QUEUE_FLUSH, null, "")
        }
        totalPoses += 1
    }

    // sets the background of textvView displaying target pose green fr 0.5 second.
    private suspend fun greenSignal()
    {
        withContext(Main)
        {
            textViewTargetPose.setBackgroundColor(Color.GREEN)
            delay(500)
            textViewTargetPose.setBackgroundColor(Color.TRANSPARENT)
        }
    }

    // initializes TTS
    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val result = tts.setLanguage(Locale.ENGLISH)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.e("TTS","The Language specified is not supported!")
                val toast = Toast.makeText(context, R.string.ttsInitFailed, Toast.LENGTH_LONG)
                toast.show()
            }
        } else {
            Log.e("TTS", "Initialization Failed!")
        }
    }
}