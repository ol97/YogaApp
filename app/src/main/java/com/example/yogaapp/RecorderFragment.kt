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
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.round

class RecorderFragment : Fragment(), PoseEstimatorUser {

    private lateinit var cameraExecutor: ExecutorService
    private lateinit var textureView: TextureView
    private lateinit var textViewFPS: TextView
    private lateinit var textViewPoseConfidence: TextView
    private lateinit var textViewPose: TextView
    private lateinit var imageButtonSettings: ImageButton
    private lateinit var orientationListener: OrientationEventListener
    private lateinit var analyzer:PoseEstimator
    private lateinit var targetSize: Size
    private lateinit var toggleButtonRecord: ToggleButton
    private var lastUpdated:Long = 0
    private lateinit var imageButtonSwitchCamera:ImageButton
    private var lensFacing = CameraSelector.DEFAULT_BACK_CAMERA
    private val LENS_FACING_KEY: String = "lens_facing"
    private var rotation = 0
    private var displayHeight:Int = 0
    private var displayWidth:Int = 0
    private lateinit var preferences:SharedPreferences
    private var showFPS: Boolean = true
    private var confidenceThreshold:Int = 20
    private var timeThreshold:Int = 1
    private lateinit var modelType: String
    private val TAG = "CameraXBasic"
    private val REQUEST_CODE_PERMISSIONS = 10
    private val REQUIRED_PERMISSIONS = arrayOf(Manifest.permission.CAMERA)
    private var recordingFlag: Boolean = false
    private val listOfPoses: MutableList<Pair<String, Long>> = mutableListOf()


    override fun onCreateView(
            inflater: LayoutInflater, container: ViewGroup?,
            savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_recorder, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        loadSettings()
        imageButtonSettings = view.findViewById(R.id.imageButtonSettings)
        imageButtonSettings.setOnClickListener {
            findNavController().navigate(R.id.action_recorderFragment_to_settingsFragment)
        }
        textViewFPS = view.findViewById(R.id.textViewFPS)
        if (showFPS){textViewFPS.visibility = View.VISIBLE}
        else {textViewFPS.visibility = View.GONE}
        textureView = view.findViewById(R.id.textureView)
        textViewPoseConfidence = view.findViewById(R.id.textViewPoseConfidence)
        imageButtonSwitchCamera = view.findViewById(R.id.imageButtonSwitchCamera)
        textViewPose = view.findViewById(R.id.textViewPose)
        toggleButtonRecord = view.findViewById(R.id.toggleButtonRecord)
        toggleButtonRecord.setOnCheckedChangeListener { buttonView, isChecked ->

            recordingFlag = isChecked
            if (isChecked)
            {
                listOfPoses.clear()
            }
            if (!isChecked && listOfPoses.lastIndex > 2)
            {

                val alert = context?.let { it1 -> AlertDialog.Builder(it1) }
                alert?.setTitle("Set name")
                alert?.setMessage("Insert name")

                val input = EditText(context)
                alert?.setView(input)

                alert?.setPositiveButton("Save") { dialog, whichButton ->
                    val value = input.text.toString()
                    context?.let {
                        val archiveHelper = ArchiveHelper.getInstance(it)
                        val ok = archiveHelper?.insertSession(filterListOfPoses(listOfPoses), value)
                        if (!ok!!) {
                            val t = Toast.makeText(context, "Saving failed!", Toast.LENGTH_SHORT)
                            t.show()
                        } else {
                            val t = Toast.makeText(context, "Saving succeeded!", Toast.LENGTH_SHORT)
                            t.show()
                        }
                    }

                }

                alert?.setNegativeButton("Cancel"
                ) { dialog, which ->
                }
                alert?.show()
            }
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
        timeThreshold = preferences.getInt("timeThreshold", 1)
        modelType = preferences.getString("modelType", "RT").toString()
        showFPS = preferences.getBoolean("showFPS", true)

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val displayMetrics = DisplayMetrics()
        activity?.windowManager?.defaultDisplay?.getMetrics(displayMetrics)
        displayHeight = displayMetrics.heightPixels
        displayWidth = displayMetrics.widthPixels
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
    }

     override fun update(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){
        updateUI(bitmap, pose, confidence, timestamp)
        if (recordingFlag)
        {
            listOfPoses.add(Pair(pose, timestamp))
        }

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

            textViewFPS.text = "Time Per Frame: " +
                    (timestamp - lastUpdated).toString() + "ms"
            textViewPoseConfidence.text = "Confidence: " + (round(confidence * 10000) / 100).toString() + "%"
            textViewPose.text = "Pose: " + pose
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
                        "Permissions not granted by the user.",
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

    private fun filterListOfPoses(listOfPoses: List<Pair<String, Long>>):List<Pair<String, Long>>
    {
        val temporaryList1: MutableList<Pair<String, Long>> = mutableListOf()
        val temporaryList2: MutableList<Pair<String, Long>> = mutableListOf()
        var lastTimestamp = 0L
        if (listOfPoses.isNotEmpty())
        {
            lastTimestamp = listOfPoses.last().second
        }
        for (i in listOfPoses.indices)
        {
            if ( i > 0 )
            {
                if (listOfPoses[i].first != listOfPoses[i - 1].first)
                {
                    temporaryList1.add(listOfPoses[i])
                }
            }
            if (i == 0)
            {
                temporaryList1.add(listOfPoses[i])
            }
        }

        for (i in temporaryList1.indices)
        {
            if (i > 0) {
                if ((temporaryList1[i].second - temporaryList1[i - 1].second) >= timeThreshold * 1000) {
                    temporaryList2.add(temporaryList1[i])
                }
            }
            else if (i == 0)
            {
                temporaryList2.add(temporaryList1[i])
            }
        }

        temporaryList1.clear()
        for (i in temporaryList2.indices)
        {
            if ( i > 0 )
            {
                if (temporaryList2[i].first != temporaryList2[i - 1].first)
                {
                    temporaryList1.add(temporaryList2[i])
                }
            }
            if (i == 0)
            {
                temporaryList1.add(temporaryList2[i])
            }
        }

        val finalList: MutableList<Pair<String, Long>> = mutableListOf()
        for (i in temporaryList1.indices)
        {
            if (i < temporaryList1.lastIndex)
            {
                finalList.add(Pair(temporaryList1[i].first, temporaryList1[i + 1].second - temporaryList1[i].second))
            }
            else
            {
                finalList.add(Pair(temporaryList1[i].first, lastTimestamp - temporaryList1[i].second))
            }
        }
        return finalList
    }
}