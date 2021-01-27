package com.example.yogaapp

import android.graphics.Bitmap

// interface created to make passing data from PoseEstimator to fragments (recording and Challenge mode)
// easier. This way PoseEstimator only needs method for sending data to PoseEstimatorUser and
// not two methods (one for each fragment).
interface PoseEstimatorUser {
    fun update(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){}
}