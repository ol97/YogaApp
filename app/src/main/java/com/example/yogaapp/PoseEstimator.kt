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
                    private val analyzerFragment: AnalyzerFragment
) : ImageAnalysis.Analyzer {

    private lateinit var model_I: EfficientPoseI
    private lateinit var model_II: EfficientPoseII
    private lateinit var model_III: EfficientPoseIII
    private lateinit var model_RT: EfficientPoseRT
    private lateinit var inputBufferEstimator: TensorBuffer
    private lateinit var outputBufferEstimator: TensorBuffer
    private var inputBufferClassifier: TensorBuffer
    private var outputBufferClassifier: TensorBuffer
    private var imageProcessor: ImageProcessor? = null
    private var yuvToRgbConverter: YuvToRgbConverter = YuvToRgbConverter(context)
    private lateinit var inputSize: Size
    private var confidenceThreshold = 0
    private var parsingJob: Job? = null
    private var classifier: FinalClassifier
    private var analysisInProgrss: Boolean = false
    private var rotation: Float = 90F

    private val mean  = floatArrayOf(-7.70853226e-03F, -6.11706507e-01F,  1.94246296e-03F,
        -3.76282272e-01F, -1.41938343e-01F, -3.45730721e-01F, -2.20449061e-01F,
        -1.73007281e-01F, -1.92002760e-01F, -9.46467036e-02F,  7.86759911e-05F, -3.42683745e-01F,
        1.53506381e-01F, -3.50076901e-01F,  2.08990530e-01F, -1.81081825e-01F, 1.63784914e-01F,
        -1.16658663e-01F,  7.81844748e-03F, 3.94896462e-02F, -9.25715828e-02F, 4.31316718e-02F,
        -2.02938627e-01F,  3.65051917e-01F, -1.79913716e-01F, 6.15379113e-01F, 1.02246695e-01F,
        4.48660454e-02F, 2.21954862e-01F, 3.57236156e-01F, 1.96818760e-01F,  6.22228608e-01F)
    private val std = floatArrayOf(0.61403802F, 0.56785943F, 0.40416081F, 0.38942636F,
        0.45410017F, 0.38416963F, 0.54795604F, 0.46984896F, 0.65981907F, 0.72798659F,
        0.38216023F, 0.38709971F, 0.45676917F, 0.3889833F,  0.54955573F, 0.47447626F, 0.6657808F,
        0.73946116F, 0.45309747F, 0.53090507F, 0.46906032F, 0.53128722F, 0.66006425F, 0.37657832F,
        0.65698298F, 0.56837161F, 0.47584919F, 0.52826396F, 0.65319942F, 0.3946331F,
        0.66315676F, 0.57348306F)


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


    @SuppressLint("UnsafeExperimentalUsageError")
    override fun analyze(image: ImageProxy) {
        analysisInProgrss = true
        if (imageProcessor == null){

            val maxDimension = max(image.width, image.height)
            imageProcessor = ImageProcessor.Builder()
                .add(ResizeWithCropOrPadOp(maxDimension,maxDimension))
                .add(ResizeOp(inputSize.height, inputSize.width, ResizeOp.ResizeMethod.BILINEAR))
                .add(NormalizeOp(0F,255F))
                .add(NormalizeOp(floatArrayOf(0.485F, 0.456F, 0.406F), floatArrayOf(0.229F, 0.224F, 0.225F)))
                .build()

        }
        var bitmap = Bitmap.createBitmap(image.width, image.height, Bitmap.Config.ARGB_8888)
        yuvToRgbConverter.yuvToRgb(image.image!!, bitmap)
        bitmap = yuvToRgbConverter.rotateBitmap(bitmap, rotation)
        val tensorImage = imageProcessor?.process(TensorImage.fromBitmap(bitmap))
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
        if (parsingJob == null)
        {
            parsingJob = CoroutineScope(Default).launch{
                parseOutput(inputSize, outputBufferEstimator, bitmap, confidenceThreshold,
                    analyzerFragment)
            }
        }
        else
        {
            while(!parsingJob!!.isCompleted){val pass = Unit}
            parsingJob = CoroutineScope(Default).launch{
                parseOutput(inputSize, outputBufferEstimator, bitmap, confidenceThreshold,
                    analyzerFragment)
            }
        }
        analysisInProgrss = false
        image.close()
    }

    private suspend fun parseOutput(inputSize: Size, outputBuffer: TensorBuffer, bitmap: Bitmap,
                                    confidenceThreshold: Int, analyzerFragment: AnalyzerFragment){
        val pointsArray = FloatArray(32)
        val xArray = FloatArray(16)
        val yArray = FloatArray(16)
        val scoresArray = FloatArray(16)
        for (i in 0 until 16){
            CoroutineScope(Default).launch{
                var maxValue = 0F
                var maxX = 0F
                var maxY = 0F
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
                scoresArray[i] = maxValue
            }.join()
        }

        val inputArray = normalizeInputArray(xArray, yArray, pointsArray,scoresArray)
        val classifierOutput = classifyPose(inputArray)


        analyzerFragment.update(drawPoints(bitmap,pointsArray,scoresArray),
            classifierOutput.first, classifierOutput.second, System.currentTimeMillis())

    }

    private fun classifyPose(inputArray: FloatArray): Pair<String,Float>{
        var indexOfMax = 0
        var maxConfidence = 0F
        if (inputArray.distinct().lastIndex != 0){
            inputBufferClassifier.loadArray(inputArray)
            outputBufferClassifier = classifier.process(inputBufferClassifier).outputFeature0AsTensorBuffer

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

        var pose = "Unknown"
        when (indexOfMax) {
            0 -> {pose = "Tree Pose"}
            1 -> {pose = "Warrior I Pose"}
            2 -> {pose = "Downward Dog Pose"}
            3 -> {pose = "Mountain Pose"}
            4 -> {pose = "Warrior II Pose"}
            5 -> {pose = "Bow Pose"}
            6 -> {pose = "Camel Pose"}
            7 -> {pose = "Plank Pose"}
            8 -> {pose = "Chair Pose"}
            9 -> {pose = "Garland Pose"}
            10 -> {pose = "Unknown Pose"}
        }
        return Pair(pose, maxConfidence)
    }

    private fun drawPoints(bitmap: Bitmap, pointsArray: FloatArray, scoresArray: FloatArray):Bitmap{
        val paint = Paint()
        val hsvColor = FloatArray(3)
        hsvColor[1] = 1F
        hsvColor[2] = 1F
        paint.strokeWidth = bitmap.width.toFloat()/224F*6F
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

    private fun normalizeInputArray(xArray: FloatArray, yArray: FloatArray, pointsArray: FloatArray,
                                    scoresArray:FloatArray):FloatArray {
        val inputArray = FloatArray(32)
        for (i in 0 until 16){
            xArray[i] = pointsArray[i*2]
            yArray[i] = pointsArray[i*2+1]
        }
        val xArrayMin = xArray.minOrNull()
        var xArrayMax = xArray.maxOrNull()
        val yArrayMin = yArray.minOrNull()
        var yArrayMax = yArray.maxOrNull()

        for (i in 0 until 16){
            xArray[i] -= (xArrayMax!! - xArrayMin!!)/2 + xArrayMin
            yArray[i] -= (yArrayMax!! - yArrayMin!!)/2 + yArrayMin
        }

        xArrayMax = xArray.maxOrNull()
        yArrayMax = yArray.maxOrNull()

        for (i in 0 until 16){
            xArray[i] /= xArrayMax!!
            yArray[i] /= yArrayMax!!
        }

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


    fun updateThreshold(newThreshold:Int){
        confidenceThreshold = newThreshold
    }


    fun updateRotation(rotation: Int){
        this.rotation = rotation.toFloat()
    }


    fun releaseResources(){
        while (analysisInProgrss){val pass = Unit}
        if (this::model_I.isInitialized){model_I.close()}
        if (this::model_II.isInitialized){model_II.close()}
        if (this::model_III.isInitialized){model_III.close()}
        if (this::model_RT.isInitialized){model_RT.close()}

        if (parsingJob == null){classifier.close()}
        else{
            while (!parsingJob!!.isCompleted){val pass: Unit = Unit}
            classifier.close()
        }
    }

}