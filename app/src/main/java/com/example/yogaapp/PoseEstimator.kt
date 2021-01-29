    package com.example.yogaapp

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.util.Size
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.example.yogaapp.ml.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.Default
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.tensorflow.lite.DataType
import org.tensorflow.lite.support.common.ops.NormalizeOp
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp
import org.tensorflow.lite.support.image.ops.ResizeWithCropOrPadOp
import org.tensorflow.lite.support.model.Model
import org.tensorflow.lite.support.tensorbuffer.TensorBuffer
import kotlin.math.max

class PoseEstimator(context: Context, private val type:String,
                    private val poseEstimatorUser: PoseEstimatorUser) : ImageAnalysis.Analyzer {

    // tflite models, only one is active at any given time
    private lateinit var model_I: EfficientPoseI
    private lateinit var model_II: EfficientPoseII
    private lateinit var model_III: EfficientPoseIII
    private lateinit var model_RT: EfficientPoseRT

    // I/O buffers for pose estimation model and classifier
    private lateinit var inputBufferEstimator: TensorBuffer
    private lateinit var outputBufferEstimator: TensorBuffer
    private var inputBufferClassifier: TensorBuffer
    private var outputBufferClassifier: TensorBuffer

    // ImageProcessor is a TensorFlow Lite class that can be used to perform basic operations
    // on image, here it is used for cropping/padding, scaling, normalizing to ImageNet standards.
    private var imageProcessor: ImageProcessor? = null

    // converts images from YUV color space to RGB
    private var yuvToRgbConverter: YuvToRgbConverter = YuvToRgbConverter(context)

    // input size of the currently used pose estimation model
    private lateinit var inputSize: Size

    // confidence threshold for keypoints, if their confidence is lower than the threshold then
    // such points are not marked on the image and their coordinates are set to [0,0],
    // passed from parent Fragment
    private var confidenceThreshold = 0

    // Job returned when launching coroutine in which output of the pose estimation is parsed,
    // points are marked on the image and pose is classified. Used to
    // check if processing of the previous frame has completed before launching coroutine for the next one.
    private var parsingJob: Job? = null

    // classifier model
    private var classifier: FinalClassifier

    // flag which indicates if a frame is being processed, used to check if all frames were processed
    // before releasing resources.
    private var analysisInProgress: Boolean = false

    // angle by which image has to be rotated, passed from parent Fragment
    private var rotation: Float = 90F

    // loads strings from resources (R.strings. ...)
    private var treePoseString: String = context.getString(R.string.treePose)
    private var mountainPoseString: String = context.getString(R.string.mountainPose)
    private var warriorIPoseString: String = context.getString(R.string.warriorIPose)
    private var warriorIIPoseString: String = context.getString(R.string.warriorIIPose)
    private var plankPoseString: String = context.getString(R.string.plankPose)
    private var chairPoseString: String = context.getString(R.string.chairPose)
    private var garlandPoseString: String = context.getString(R.string.garlandPose)
    private var downwardDogPoseString: String = context.getString(R.string.downwardDogPose)
    private var bowPoseString: String = context.getString(R.string.bowPose)
    private var camelPoseString: String = context.getString(R.string.camelPose)
    private var unknownPoseString: String = context.getString(R.string.unknownPose)

    // keypoint marker size multiplier
    private var pointSize: Int = 5

    // mean and standard deviations by which input parameters of classifier have to be normalized
    // before being processed
    private val mean  = floatArrayOf(-0.00833482F, -0.62727004F,  0.00146041F,
        -0.38550275F, -0.15284604F, -0.3669925F, -0.23114573F, -0.18421048F, -0.20711644F,
        -0.10777842F,  0.0023417F,  -0.36816991F, 0.16468407F, -0.36896587F,  0.22537469F,
        -0.1934807F,   0.19541056F, -0.11196623F, 0.0080589F,   0.03163596F, -0.10252364F,
        0.03748514F, -0.22574751F,  0.38669622F, -0.18820711F,  0.65753298F,  0.11263413F,
        0.03234019F,  0.22194424F,  0.38143497F, 0.19538015F,  0.66395481F)
    private val std = floatArrayOf(0.6196335F,  0.55582361F, 0.39790076F, 0.3799745F,
            0.45055568F, 0.37277209F, 0.54869939F, 0.46920492F, 0.66238514F, 0.73845478F, 0.372124F,
            0.36660152F, 0.45435866F, 0.37639802F, 0.55255384F, 0.46776781F, 0.66711001F,
            0.75293096F, 0.44145981F, 0.51929917F, 0.45996235F, 0.52027278F, 0.64905945F,
            0.3691878F, 0.66570541F, 0.54344156F, 0.46506851F, 0.51986545F, 0.64680984F,
            0.37614502F, 0.66577831F, 0.54828766F)


    // initialize pose estimation model, classifier, I/O buffers, set inputSize
    init {
        val options = Model.Options.Builder()
            .setDevice(Model.Device.GPU)
            .build()
        if (type == "I"){
            model_I = EfficientPoseI.newInstance(context,options)
            inputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3), DataType.FLOAT32)
            outputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 256, 256, 3), DataType.FLOAT32)
            inputSize = Size(256,256)
        }
        if (type == "II"){
            model_II = EfficientPoseII.newInstance(context,options)
            inputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 368, 368, 3), DataType.FLOAT32)
            outputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 368, 368, 3), DataType.FLOAT32)
            inputSize = Size(368,368)
        }
        if (type == "III"){
            model_III = EfficientPoseIII.newInstance(context,options)
            inputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 480, 480, 3), DataType.FLOAT32)
            outputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 480, 480, 3), DataType.FLOAT32)
            inputSize = Size(480,480)
        }
        if (type == "RT"){
            model_RT = EfficientPoseRT.newInstance(context,options)
            inputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            outputBufferEstimator = TensorBuffer.createFixedSize(intArrayOf(1, 224, 224, 3), DataType.FLOAT32)
            inputSize = Size(224,224)
        }

        classifier = FinalClassifier.newInstance(context)
        inputBufferClassifier = TensorBuffer.createFixedSize(intArrayOf(1,32), DataType.FLOAT32)
        outputBufferClassifier = TensorBuffer.createFixedSize(intArrayOf(1,11), DataType.FLOAT32)

    }

    // The most important method. It is first called when camera session is created and first
    // frame is sent to PoseEstimator. After that, this method is only called after the previous frame
    // is disposed with image.close() (parameter image is the processed frame).
    // In this method the entire image processing algorithm is implemented.
    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(image: ImageProxy) {
        analysisInProgress = true

        // ImageProcessor cannot be initialized in init{} block, because at that point image
        // dimensions are not known, it has to be initialized here
        if (imageProcessor == null){
            val maxDimension = max(image.width, image.height)

            // pad to 1:1 aspect ratio
            // scale to input size of pose estimation model
            // normalize to [0,1]
            // normalize to ImageNet specs
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(maxDimension,maxDimension))
                .add(ResizeOp(inputSize.height, inputSize.width, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0F,255F))
                .add(NormalizeOp(floatArrayOf(0.485F, 0.456F, 0.406F), floatArrayOf(0.229F, 0.224F, 0.225F)))
                .build()
        }

        // create bitmap in which RGB version of the image will be stored,
        // convert image to RGB and rotate it (camera is rotated by 90 degrees + device orientation
        var bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(image.image!!, bitmap)
        bitmap = yuvToRgbConverter.rotateBitmap(bitmap, rotation)

        // perform all operations specified during initializations of ImageProcessor and convert to TensorImage
        val tensorImage = imageProcessor?.process(TensorImage.fromBitmap(bitmap))

        // write image to buffer and run inference of pose estimation model
        inputBufferEstimator.loadBuffer(tensorImage!!.buffer)
        when (type) {
            "I" -> {
                outputBufferEstimator = (model_I.process(inputBufferEstimator)).outputFeature3AsTensorBuffer
            }
            "II" -> {
                outputBufferEstimator = (model_II.process(inputBufferEstimator)).outputFeature3AsTensorBuffer
            }
            "III" -> {
                outputBufferEstimator = (model_III.process(inputBufferEstimator)).outputFeature3AsTensorBuffer
            }
            "RT" -> {
                outputBufferEstimator = (model_RT.process(inputBufferEstimator)).outputFeature3AsTensorBuffer
            }
        }

        // output buffer from pose estimation is passed to another method that
        // is executed asynchronously in coroutine. To ensure correct order of frames, new coroutine
        // is launched only if the previous one has finished (object parsingJob is used to check that).
        // If the previously launched coroutine has finished then launch a new one, else wait for it's completion.
        if (parsingJob == null)
        {
            parsingJob = CoroutineScope(Default).launch{
                parseOutput(inputSize, outputBufferEstimator, bitmap, poseEstimatorUser)
            }
        }
        else
        {
            while(!parsingJob!!.isCompleted){val pass = Unit}
            parsingJob = CoroutineScope(Default).launch{
                parseOutput(inputSize, outputBufferEstimator, bitmap, poseEstimatorUser)
            }
        }

        // after image.close() call, tha analyze(image: ImageProxy) is called again but with new frame
        analysisInProgress = false
        image.close()
    }

    // method in which output of pose estimation model is analyzed, keypoints are located,
    // pose is classified etc.
    private suspend fun parseOutput(inputSize: Size, outputBuffer: TensorBuffer, bitmap: Bitmap,
                                    poseEstimatorUser: PoseEstimatorUser){
        // array in which coordinates of keypoints are stored (16 keypoints, (x,y) coordinates for each)
        val pointsArray = FloatArray(32)

        // confidence scores for each keypoint
        val scoresArray = FloatArray(16)

        // search for indices of highest value in each heatmap returned by pose estimation model
        // each map is processed in separate coroutine to parallelize and speed up the process
        // all operations are performed directly on the buffer returned by pose estimation model
        for (i in 0 until 16){
            CoroutineScope(Default).launch{
                var maxValue = 0F
                var maxX = 0F
                var maxY = 0F

                //search for indices of the highest value in a heatmap
                for (row in 0 until inputSize.height) {
                    for (col in 0 until inputSize.width) {
                        val value = outputBuffer.getFloatValue(i + 16 * col + 16 * inputSize.width * row)
                        if (value > maxValue) {
                            maxValue = value
                            maxX = col.toFloat()
                            maxY = row.toFloat()
                        }
                    }
                }

                // scaling keypoint coordinates from pose estimation model output resolution
                // to camera frame resolution,
                // keypoints also have to be translated because image was padded to 1:1 aspect ratio
                var xOffset = 0
                var yOffset = 0
                var ratio = 0F
                if (bitmap.height > bitmap.width) {
                    xOffset = (bitmap.height - bitmap.width) / 2
                    yOffset = 0
                    ratio = bitmap.height.toFloat() / inputSize.height.toFloat()
                } else {
                    xOffset = 0
                    yOffset = (bitmap.width - bitmap.height) / 2
                    ratio = bitmap.width.toFloat() / inputSize.width.toFloat()
                }
                pointsArray[2 * i] = maxX * ratio - xOffset
                pointsArray[2 * i + 1] = maxY * ratio - yOffset
                scoresArray[i] = maxValue // values in heatmaps are per-pixel confidence scores
            }.join() // blocks the thread and waits for all coroutines to finish
        }

        // normalize the keypoint coordinates before classifying pose
        val inputArray = normalizeInputArray(pointsArray,scoresArray)

        // classifies pose based on normalized keypoint coordinates
        val classifierOutput = classifyPose(inputArray)

        // sends image with marked points, name of the pose with confidence score and timestamp to
        // fragment (ChallengeMode or RecordingMode) in which everything will be further processed
        // and displayed.
        poseEstimatorUser.update(drawPoints(bitmap,pointsArray,scoresArray),
            classifierOutput.first, classifierOutput.second, System.currentTimeMillis())

    }

    // classifies the pose based on array of normalized keypoint coordinates
    private fun classifyPose(inputArray: FloatArray): Pair<String,Float>{
        var indexOfMax = 0
        var maxConfidence = 0F

        // checks if the array contains non zero values, zeroes mean that keypoint had confidence
        // score lower than the threshold and was rejected,
        // if all coordinates are zeroes then it's an unknown pose
        if (inputArray.distinct().lastIndex != 0){
            // run classifier inference
            inputBufferClassifier.loadArray(inputArray)
            outputBufferClassifier = classifier.process(inputBufferClassifier).outputFeature0AsTensorBuffer

            // find index of class with highest confidence score
            for (i in outputBufferClassifier.floatArray.indices){
                if (outputBufferClassifier.floatArray[i] > maxConfidence){
                    indexOfMax = i
                    maxConfidence = outputBufferClassifier.floatArray[i]
                }

            }
        }
        else{
            indexOfMax = 10
            maxConfidence = 1F
        }

        // find name of the class with highest confidence score and return that name with the score
        var pose = "Unknown Pose"
        when (indexOfMax) {
            0 -> {pose = treePoseString}
            1 -> {pose = warriorIPoseString}
            2 -> {pose = downwardDogPoseString}
            3 -> {pose = mountainPoseString}
            4 -> {pose = warriorIIPoseString}
            5 -> {pose = bowPoseString}
            6 -> {pose = camelPoseString}
            7 -> {pose = plankPoseString}
            8 -> {pose = chairPoseString}
            9 -> {pose = garlandPoseString}
            10 -> {pose = unknownPoseString}
        }
        return Pair(pose, maxConfidence)
    }

    // helper method for drawing keypoint markers on image of original size
    private fun drawPoints(bitmap: Bitmap, pointsArray: FloatArray, scoresArray: FloatArray):Bitmap{

        // color of each keypoint marker indicates it's confidence score (red - low, green - high)
        // to ensure even sweep between red and green, color is calculated in HSV color space
        // and then converted to RGB
        // size of markers is calculated based on "pointSize" variable which acts as a multiplier
        // since for each pose estimation model image resolution is different it cant be a set value
        val paint = Paint()
        val hsvColor = FloatArray(3)
        hsvColor[1] = 1F
        hsvColor[2] = 1F
        paint.strokeWidth = bitmap.width.toFloat()/224F*this.pointSize
        val canvas = Canvas(bitmap)
        for (i in 0 until 16){
            hsvColor[0] = 120F*scoresArray[i]
            paint.color = Color.HSVToColor(hsvColor)
            if (scoresArray[i]*100 >= confidenceThreshold){
                canvas.drawPoint(pointsArray[2*i], pointsArray[2*i+1], paint)
            }
        }
        return bitmap
    }

    // normalizes coordinates of keypoints before they are passed to classifier
    private fun normalizeInputArray(pointsArray: FloatArray,
                                    scoresArray:FloatArray):FloatArray {
        val inputArray = FloatArray(32)
        val xArray = FloatArray(16)
        val yArray = FloatArray(16)
        for (i in 0 until 16){
            xArray[i] = pointsArray[i*2]
            yArray[i] = pointsArray[i*2+1]
        }

        // find min, max values for x and y coordinates
        val xArrayMin = xArray.minOrNull()
        var xArrayMax = xArray.maxOrNull()
        val yArrayMin = yArray.minOrNull()
        var yArrayMax = yArray.maxOrNull()

        // move points so that the center of a rectangle bounding them is in [0,0]
        for (i in 0 until 16){
            xArray[i] -= (xArrayMax!! - xArrayMin!!)/2 + xArrayMin
            yArray[i] -= (yArrayMax!! - yArrayMin!!)/2 + yArrayMin
        }

        // scale coordinates so that they are all between -1 and 1
        for (i in 0 until 16){
            xArray[i] /= xArrayMax!!
            yArray[i] /= yArrayMax!!
        }

        // normalize coordinates with mean and std values of dataset used to train classifier
        // if confidence score for a keypoint is lower than the threshold then it's coordinates
        // are set to [0,0]
        for (i in 0 until 16){
            if (scoresArray[i]*100 >= confidenceThreshold){
                inputArray[i*2] = (xArray[i] - mean[2*i])/std[2*i]
                inputArray[i*2+1] = (yArray[i] - mean[2*i+1])/std[2*i+1]
            }
            else{
                inputArray[i*2] = 0F
                inputArray[i*2+1] = 0F
            }
        }
        return inputArray
    }

    // update threshold for keypoint confidence scores, value passed from fragment
    fun updateThreshold(newThreshold:Int){
        confidenceThreshold = newThreshold
    }

    // update angle by which image has to be rotated, based on device rotation and selected camera lens
    // has to be passed from fragment
    fun updateRotation(rotation: Int){
        this.rotation = rotation.toFloat()
    }

    // value passed from fragment
    fun setPointSize(pointSize: Int){
        this.pointSize = pointSize
    }

    // wait for all frames to be processed and then releases resources
    fun releaseResources(){
        while (analysisInProgress){val pass = Unit} // wait
        if (this::model_I.isInitialized){model_I.close()}
        if (this::model_II.isInitialized){model_II.close()}
        if (this::model_III.isInitialized){model_III.close()}
        if (this::model_RT.isInitialized){model_RT.close()}

        if (parsingJob == null){classifier.close()}
        else{
            while (!parsingJob!!.isCompleted){val pass = Unit}
            classifier.close()
        }
    }

}