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
import androidx.appcompat.app.AlertDialog
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.preference.PreferenceManager
import com.example.yogaapp.database.ArchiveHelper
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

// Fragment handling logic of Recording Mode
// PoseEstimatorUser is an interface used to pass data from PoseEstimator to whatever
// is using it ("Recording Mode" or "Challenge Mode").
class RecordingModeFragment : Fragment(), PoseEstimatorUser {

    // camera executor, basically a thread used by cameraX so it doesn't block the UI thread.
    private lateinit var cameraExecutor: ExecutorService

    // all widgets visible ob the screen in "Recording Mode"
    private lateinit var textureView: TextureView
    private lateinit var textViewFPS: TextView
    private lateinit var textViewPoseConfidence: TextView
    private lateinit var textViewPose: TextView
    private lateinit var imageButtonSettings: ImageButton
    private lateinit var toggleButtonRecord: ToggleButton
    private lateinit var imageButtonSwitchCamera:ImageButton

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
    private lateinit var preferences:SharedPreferences

    // Time per frame visibility
    private var showFPS: Boolean = true

    // Confidence threshold for keypoints detected by PoseEstimator. If confidence for a point is lower
    // than the threshold then it's coordinates are set to [0,0] and it is not displayed.
    private var confidenceThreshold:Int = 20

    // a minimal time for which a pose has to be held for to be written in database
    // threshold for filtering list of poses before saving it to database
    private var timeThreshold:Int = 1

    // Keys for asking for camera permissions, based on "Getting Started with CameraX"
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)

    // keys for reading saved data from savedInstanceState
    private val LENS_FACING_KEY: String = "lens_facing"
    private val KEY_RECORDING = "recording_flag"
    private val KEY_LIST_OF_POSES = "list_of_poses"

    // flag indicating whether recording is active
    private var recordingFlag: Boolean = false

    // list which holds names of all poses with timestamps during recording
    private var listOfPoses: MutableList<TimestampedPose> = mutableListOf()

    // multiplier for keypoint marker size
    private var pointSize: Int = 5


    // load settings and initialize everything
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadSettings()

        //get display size
        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        displayHeight = displayMetrics.heightPixels
        displayWidth = displayMetrics.widthPixels

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
        if (savedInstanceState != null) {
            recordingFlag = savedInstanceState.getBoolean(KEY_RECORDING)
            if (savedInstanceState.getBoolean(LENS_FACING_KEY)) {
                lensFacing = CameraSelector.DEFAULT_FRONT_CAMERA
            } else {
                lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
            }
            if (recordingFlag)
            {
                listOfPoses = savedInstanceState.getParcelableArrayList<TimestampedPose>(KEY_LIST_OF_POSES) as MutableList<TimestampedPose>
            }
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
        return inflater.inflate(R.layout.fragment_recorder, container, false)
    }

    // when layout is inflated find all widgets, set onClickListeners etc. (configure UI)
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        imageButtonSettings = view.findViewById(R.id.imageButtonSettings)

        // navigate to settings after clicking "Settings" button
        imageButtonSettings.setOnClickListener {
            findNavController().navigate(R.id.action_recorderFragment_to_settingsFragment)
        }

        // setting visibility of timePerFrame textView. Setting visibility to View.GONE
        // frees up the slot in LinearLayout
        textViewFPS = view.findViewById(R.id.textViewFPS)
        if (showFPS){textViewFPS.visibility = View.VISIBLE}
        else {textViewFPS.visibility = View.GONE}

        textureView = view.findViewById(R.id.textureView)
        textViewPoseConfidence = view.findViewById(R.id.textViewPoseConfidence)

        // after switching lenses update the rotation for the first frame manually and restart the camera,
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

        // set onClickListener for the toggle button that starts/stops the recording
        // when the recording starts then clear the list
        // if the recording is stops then show a dialog box in which user can name
        // the training session being saved
        toggleButtonRecord = view.findViewById(R.id.toggleButtonRecord)
        toggleButtonRecord.isChecked = recordingFlag
        toggleButtonRecord.setOnCheckedChangeListener { buttonView, isChecked ->
            recordingFlag = isChecked
            // if new recording is starting then clear the list
            if (isChecked)
            {
                listOfPoses.clear()
            }
            // if a recording is stopped show dialog
            // there is no point in saving a session if only one frame was saved, duration can't be calculated
            if (!isChecked && listOfPoses.lastIndex >= 2)
            {
                // build and show dialog
                val alert = context?.let { it1 -> AlertDialog.Builder(it1) }
                alert?.setTitle(getString(R.string.setName))
                alert?.setMessage(getString(R.string.insertName))
                val input = EditText(context)
                alert?.setView(input)
                // load all currently taken names to later check if the name entered by user is valid
                val names = context?.let { ArchiveHelper.getInstance(it) }!!.readSessionNames()
                alert?.setPositiveButton(getString(R.string.save)) { dialog, whichButton -> }
                alert?.setNegativeButton(getString(R.string.cancel)) { dialog, which -> }
                val dialog = alert!!.create()
                dialog.show()

                // positive button - "Save"
                // negative button - "Cancel" - closes the dialog
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                    val name = input.text.toString()
                    // check if name is already taken, if yes show Toast
                    if (names.contains(name)) {
                        val toast = Toast.makeText(context, getString(R.string.nameInUse), Toast.LENGTH_SHORT)
                        toast.show()
                    }
                    // check if name is too long, if yes show Toast
                    else if (name.length >= 20){
                        val toast = Toast.makeText(context, getString(R.string.nameTooLong), Toast.LENGTH_SHORT)
                        toast.show()
                    }
                    // if name is ok save session to database. display Toast with confirmation
                    else {
                        context?.let {
                            val archiveHelper = ArchiveHelper.getInstance(it)
                            val ok = archiveHelper?.insertSession(filterListOfPoses(listOfPoses), name)
                            if (!ok!!) {
                                val toast = Toast.makeText(context, getString(R.string.savingFailed), Toast.LENGTH_SHORT)
                                toast.show()
                            } else {
                                val toast = Toast.makeText(context, getString(R.string.savingSucceeded), Toast.LENGTH_SHORT)
                                toast.show()
                                dialog.dismiss()
                            }
                        }
                    }
                }

            }
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

    // Shutdown thread handling camera, wait for its termination
    // and release resources used by pose estimation model. Resources can be only released
    // after the pose estimation model stops receiving and processing frames.
    override fun onDestroy() {
        super.onDestroy()

        cameraExecutor.shutdown()
        cameraExecutor.awaitTermination(3000, TimeUnit.MILLISECONDS)
        analyzer.releaseResources()
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
        outState.putBoolean(KEY_RECORDING, recordingFlag)
        if (recordingFlag)
        {
            outState.putParcelableArrayList(KEY_LIST_OF_POSES, listOfPoses as ArrayList<out Parcelable>)
        }
    }

    // load settings from preferences, preferences are basically all the settings in "Settings" screen
    private fun loadSettings(){
        preferences = PreferenceManager.getDefaultSharedPreferences(context)
        confidenceThreshold = preferences.getInt("confidenceThreshold", 20)
        timeThreshold = preferences.getInt("timeThreshold", 1)
        modelType = preferences.getString("modelType", "RT").toString()
        showFPS = preferences.getBoolean("showFPS", true)
        pointSize = preferences.getInt("pointSize", 5)

    }

    // method defined by PoseEstimatorUser interface. PoseEstimator returns bitmap, name of the pose,
    // confidence for that pose, and timestamp of when was the frame passed to fragment.
    // if recording is active add pose name with timestamp to the list
     override fun update(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){
        updateUI(bitmap, pose, confidence, timestamp)
        if (recordingFlag)
        {
            listOfPoses.add(TimestampedPose(pose, timestamp))
        }

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
            lastUpdated = timestamp
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
                    .setTargetResolution(targetSize) // input size of pose estimation model
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer) // frames will be sent to analyzer (Pose Estimator)
                    }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                        this, lensFacing, imageAnalyzer)

            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(context))
    }

    // filters list of poses from all entries that lasted shorter than the threshold and
    // calculate duration of each pose based on timestamps
    private fun filterListOfPoses(listOfPoses: List<TimestampedPose>):List<TimestampedPose>
    {
        // two temporary list which will be used during the filtering operation
        // a lot of elements will be "deleted" so it is easier to copy elements between two lists
        // rather than use only original list and keep track of all the changing values and iterators
        val temporaryList1: MutableList<TimestampedPose> = mutableListOf()
        val temporaryList2: MutableList<TimestampedPose> = mutableListOf()

        // last element will be lost so it's timestamp has to be kept in separate variable to
        // calculate duration of the last pose correctly
        var lastTimestamp = 0L
        if (listOfPoses.isNotEmpty())
        {
            lastTimestamp = listOfPoses.last().timestamp
        }

        // step one - detect and save only the elements where the poses change.
        for (i in listOfPoses.indices)
        {
            if ( i > 0 )
            {
                if (listOfPoses[i].poseName != listOfPoses[i - 1].poseName)
                {
                    temporaryList1.add(listOfPoses[i])
                }
            }
            if (i == 0)
            {
                temporaryList1.add(listOfPoses[i])
            }
        }

        // step 2 - delete all the poses that lasted shorter than the threshold
        // threshold is in seconds and timestamps in milliseconds, threshold has to be multiplied by 1000
        for (i in temporaryList1.indices)
        {
            if (i > 0) {
                if ((temporaryList1[i].timestamp - temporaryList1[i - 1].timestamp) >= timeThreshold * 1000) {
                    temporaryList2.add(temporaryList1[i])
                }
            }
            else if (i == 0)
            {
                temporaryList2.add(temporaryList1[i])
            }
        }

        // clear temporaryList1 so it can be used again, at this point all the valuable data is
        // stored in temporaryList2
        // step 3 - once again detect and save only the elements where the poses change
        temporaryList1.clear()
        for (i in temporaryList2.indices)
        {
            if ( i > 0 )
            {
                if (temporaryList2[i].poseName != temporaryList2[i - 1].poseName)
                {
                    temporaryList1.add(temporaryList2[i])
                }
            }
            if (i == 0)
            {
                temporaryList1.add(temporaryList2[i])
            }
        }

        // step 4 - calculate duration of each pose based on timestamps
        val finalList: MutableList<TimestampedPose> = mutableListOf()
        for (i in temporaryList1.indices)
        {
            if (i < temporaryList1.lastIndex)
            {
                finalList.add(TimestampedPose(temporaryList1[i].poseName,
                        temporaryList1[i + 1].timestamp - temporaryList1[i].timestamp))
            }
            else
            {
                finalList.add(TimestampedPose(temporaryList1[i].poseName,
                        lastTimestamp - temporaryList1[i].timestamp))
            }
        }
        return finalList
    }
}