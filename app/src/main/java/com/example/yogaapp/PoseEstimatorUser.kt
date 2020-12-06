package com.example.yogaapp

import android.graphics.Bitmap

interface PoseEstimatorUser {
    fun update(bitmap: Bitmap, pose: String, confidence: Float, timestamp: Long){}
}