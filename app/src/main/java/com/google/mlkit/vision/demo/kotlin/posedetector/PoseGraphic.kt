/*
 * Copyright 2020 Google LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.mlkit.vision.demo.kotlin.posedetector

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.os.Build
import android.speech.tts.TextToSpeech
import android.widget.Toast
import androidx.annotation.RequiresApi
import com.google.mlkit.vision.demo.GraphicOverlay
import com.google.mlkit.vision.demo.GraphicOverlay.Graphic
import com.google.mlkit.vision.demo.kotlin.CameraXLivePreviewActivity
import com.google.mlkit.vision.pose.Pose
import com.google.mlkit.vision.pose.PoseLandmark
import java.lang.Math.max
import java.lang.Math.min
import java.util.*

enum class SkeletalJoint { EYE, SHOULDER, ELBOW, WRIST, HIP, KNEE, ANKLE }

/** Draw the detected pose in preview. */
class PoseGraphic
internal constructor(
  overlay: GraphicOverlay,
  private val pose: Pose,
  private val showInFrameLikelihood: Boolean,
  private val visualizeZ: Boolean,
  private val rescaleZForVisualization: Boolean,
  private val poseClassification: List<String>,
  private val singleSideSelectedLandmarksIsRight: Boolean,
) : Graphic(overlay) {

  companion object { // == global vars / consts
    private val DOT_RADIUS = 8.0f
    private val IN_FRAME_LIKELIHOOD_TEXT_SIZE = 30.0f
    private val STROKE_WIDTH = 10.0f
    private val POSE_CLASSIFICATION_TEXT_SIZE = 60.0f
    val MIN_LIKELYHOOD = .8F
    val jointNAME = arrayOf ( "eye", "shoulder", "elbow", "wrist", "hip", "knee", "ankle")
  }

  private var zMin = java.lang.Float.MAX_VALUE
  private var zMax = java.lang.Float.MIN_VALUE
  private val classificationTextPaint: Paint
  private val leftPaint: Paint
  private val rightPaint: Paint
  private val whitePaint: Paint

  init {
    classificationTextPaint = Paint()
    classificationTextPaint.color = Color.WHITE
    classificationTextPaint.textSize = POSE_CLASSIFICATION_TEXT_SIZE
    classificationTextPaint.setShadowLayer(5.0f, 0f, 0f, Color.BLACK)

    whitePaint = Paint()
    whitePaint.strokeWidth = STROKE_WIDTH
    whitePaint.color = Color.WHITE
    whitePaint.textSize = IN_FRAME_LIKELIHOOD_TEXT_SIZE
    leftPaint = Paint()
    leftPaint.strokeWidth = STROKE_WIDTH
    leftPaint.color = Color.GREEN
    rightPaint = Paint()
    rightPaint.strokeWidth = STROKE_WIDTH
    rightPaint.color = Color.YELLOW
  }

  internal fun calculateAngleDegFloat(P1X: Float, P1Y: Float, P2X: Float, P2Y: Float, P3X: Float, P3Y: Float): Double {
    // angle at P1 is computed..
    val numerator = P2Y * (P1X - P3X) + P1Y * (P3X - P2X) + P3Y * (P2X - P1X)
    val denominator = (P2X - P1X) * (P1X - P3X) + (P2Y - P1Y) * (P1Y - P3Y)
    val ratio = numerator / denominator
    val angleRad: Double = Math.atan(ratio.toDouble())
    var angleDeg = angleRad * 180 / Math.PI
    if (angleDeg < 0) {
      angleDeg += 180
    }
    return angleDeg
  }

  internal fun sayAdvice( jointIndex: Int, increaseAction: String, decreaseAction: String): String{
    // start w/ knee angle, how far is it from ideal 95--135 ?
    var toSay: String = "";
    val jointAngleNow = CameraXLivePreviewActivity.currentAngles[jointIndex]
    toSay += " at %.0f degrees, your %s angle is".format( jointAngleNow, jointNAME[jointIndex])
    if( jointAngleNow < (CameraXLivePreviewActivity.optimumAngles[jointIndex] - 5.0)) {
      toSay += " too narrow, please %s a little bit. ".format( increaseAction)
    } else if( jointAngleNow > (CameraXLivePreviewActivity.optimumAngles[jointIndex] + 5.0)) {
      toSay += " too wide, please %s a little bit. ".format( decreaseAction)
    } else {
      toSay += " already good! ".format( jointNAME[jointIndex])
    }
    return toSay
  }

  internal fun drawAngle(canvas: Canvas, jointIndex: Int, lm1: PoseLandmark, lm2 : PoseLandmark, lm3: PoseLandmark){
    if( lm1.inFrameLikelihood < MIN_LIKELYHOOD)
      return
    if( lm2.inFrameLikelihood < MIN_LIKELYHOOD)
      return
    if( lm3.inFrameLikelihood < MIN_LIKELYHOOD)
      return
    CameraXLivePreviewActivity.currentAngles[jointIndex] = calculateAngleDegFloat(
      lm1.position.x, lm1.position.y,
      lm2.position.x, lm2.position.y,
      lm3.position.x, lm3.position.y,
    )
    if( CameraXLivePreviewActivity.numAngleVals[jointIndex] == 0) {
      CameraXLivePreviewActivity.currentAngles[jointIndex] = CameraXLivePreviewActivity.currentAngles[jointIndex]
    } else {
      CameraXLivePreviewActivity.currentAngles[jointIndex] = (CameraXLivePreviewActivity.currentAngles[jointIndex] * CameraXLivePreviewActivity.numAngleVals[jointIndex] + CameraXLivePreviewActivity.currentAngles[jointIndex]) / (CameraXLivePreviewActivity.numAngleVals[jointIndex]+1)
    }
    ++CameraXLivePreviewActivity.numAngleVals[jointIndex]
    if( (jointIndex == SkeletalJoint.KNEE.ordinal) && (CameraXLivePreviewActivity.numAngleVals[jointIndex] == 40) ){
      var toSay = "OK! here is my advice to get you to an optimal posture : "
      toSay += sayAdvice( SkeletalJoint.KNEE.ordinal, "move backward", "move forward")
      toSay += sayAdvice( SkeletalJoint.HIP.ordinal, "recline backward", "recline forward")
//      sayAdvice( SkeletalJoint.KNEE.ordinal, "move backward", "move forward")
      CameraXLivePreviewActivity.tts!!.speak(toSay, TextToSpeech.QUEUE_FLUSH, null,"")
    }
    canvas.drawText(
      "%d %s %.0f°".format( CameraXLivePreviewActivity.numAngleVals[jointIndex], CameraXLivePreviewActivity.jointsNames[jointIndex], CameraXLivePreviewActivity.currentAngles[jointIndex]),
      translateX(lm1.position.x),
      translateY(lm1.position.y),
      whitePaint
    )
  }

  override fun draw(canvas: Canvas) {
// Get all PoseLandmarks. If no person was detected, the list will be empty
    val allLandmarks = pose.allPoseLandmarks
    if (allLandmarks.isEmpty()) {
      return
    }
    var landmarks = mutableListOf(pose.getPoseLandmark(PoseLandmark.NOSE))
// Or get specific PoseLandmarks individually, ordered, from 0 to 32
//    val nose = pose.getPoseLandmark(PoseLandmark.NOSE)
//    val lefyEyeInner = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_INNER)
    val leftEye = pose.getPoseLandmark(PoseLandmark.LEFT_EYE)
//    val leftEyeOuter = pose.getPoseLandmark(PoseLandmark.LEFT_EYE_OUTER)
//    val rightEyeInner = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_INNER)
    val rightEye = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE)
//    val rightEyeOuter = pose.getPoseLandmark(PoseLandmark.RIGHT_EYE_OUTER)
//    val leftEar = pose.getPoseLandmark(PoseLandmark.LEFT_EAR)
//    val rightEar = pose.getPoseLandmark(PoseLandmark.RIGHT_EAR)
//    val leftMouth = pose.getPoseLandmark(PoseLandmark.LEFT_MOUTH)
//    val rightMouth = pose.getPoseLandmark(PoseLandmark.RIGHT_MOUTH)
    val leftShoulder = pose.getPoseLandmark(PoseLandmark.LEFT_SHOULDER)
    val rightShoulder = pose.getPoseLandmark(PoseLandmark.RIGHT_SHOULDER)
    val leftElbow = pose.getPoseLandmark(PoseLandmark.LEFT_ELBOW)
    val rightElbow = pose.getPoseLandmark(PoseLandmark.RIGHT_ELBOW)
    val leftWrist = pose.getPoseLandmark(PoseLandmark.LEFT_WRIST)
    val rightWrist = pose.getPoseLandmark(PoseLandmark.RIGHT_WRIST)
    val leftHip = pose.getPoseLandmark(PoseLandmark.LEFT_HIP)
    val rightHip = pose.getPoseLandmark(PoseLandmark.RIGHT_HIP)
    val leftKnee = pose.getPoseLandmark(PoseLandmark.LEFT_KNEE)
    val rightKnee = pose.getPoseLandmark(PoseLandmark.RIGHT_KNEE)
    val leftAnkle = pose.getPoseLandmark(PoseLandmark.LEFT_ANKLE)
    val rightAnkle = pose.getPoseLandmark(PoseLandmark.RIGHT_ANKLE)
    val leftPinky = pose.getPoseLandmark(PoseLandmark.LEFT_PINKY)
    val rightPinky = pose.getPoseLandmark(PoseLandmark.RIGHT_PINKY)
    val leftIndex = pose.getPoseLandmark(PoseLandmark.LEFT_INDEX)
    val rightIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_INDEX)
    val leftThumb = pose.getPoseLandmark(PoseLandmark.LEFT_THUMB)
    val rightThumb = pose.getPoseLandmark(PoseLandmark.RIGHT_THUMB)
    val leftHeel = pose.getPoseLandmark(PoseLandmark.LEFT_HEEL)
    val rightHeel = pose.getPoseLandmark(PoseLandmark.RIGHT_HEEL)
    val leftFootIndex = pose.getPoseLandmark(PoseLandmark.LEFT_FOOT_INDEX)
    val rightFootIndex = pose.getPoseLandmark(PoseLandmark.RIGHT_FOOT_INDEX)

    // Draw pose classification text.
    val classificationX = POSE_CLASSIFICATION_TEXT_SIZE * 0.5f
    for (i in poseClassification.indices) {
      val classificationY =
        canvas.height - (POSE_CLASSIFICATION_TEXT_SIZE * 1.5f * (poseClassification.size - i).toFloat())
      canvas.drawText(
        poseClassification[i],
        classificationX,
        classificationY,
        classificationTextPaint
      )
    }

    // IVN Draw selected points
    if( singleSideSelectedLandmarksIsRight){
      landmarks.add(rightEye)
      landmarks.add(rightShoulder)
      landmarks.add(rightElbow)
      landmarks.add(rightWrist)
      landmarks.add(rightHip)
      landmarks.add(rightKnee)
      landmarks.add(rightAnkle)
    } else {
      landmarks.add(leftEye)
      landmarks.add(leftShoulder)
      landmarks.add(leftElbow)
      landmarks.add(leftWrist)
      landmarks.add(leftHip)
      landmarks.add(leftKnee)
      landmarks.add(leftAnkle)
    }
    for (landmark in landmarks) {
      if(landmark == null)
        continue
      drawPoint(canvas, landmark!!, whitePaint)
      if (visualizeZ && rescaleZForVisualization) {
        zMin = min(zMin, landmark!!.position3D.z)
        zMax = max(zMax, landmark!!.position3D.z)
      }
      if (showInFrameLikelihood) {
        // Draw inFrameLikelihood for selected landmarks
        canvas.drawText(
          String.format(Locale.US, "%.2f", landmark!!.inFrameLikelihood),
          translateX(landmark.position.x),
          translateY(landmark.position.y),
          whitePaint
        )
      }

    }
    //IVN, no Face lines..
//    drawLine(canvas, nose, lefyEyeInner, whitePaint)
//    drawLine(canvas, lefyEyeInner, lefyEye, whitePaint)
//    drawLine(canvas, lefyEye, leftEyeOuter, whitePaint)
//    drawLine(canvas, leftEyeOuter, leftEar, whitePaint)
//    drawLine(canvas, nose, rightEyeInner, whitePaint)
//    drawLine(canvas, rightEyeInner, rightEye, whitePaint)
//    drawLine(canvas, rightEye, rightEyeOuter, whitePaint)
//    drawLine(canvas, rightEyeOuter, rightEar, whitePaint)
//    drawLine(canvas, leftMouth, rightMouth, whitePaint)
//    drawLine(canvas, leftShoulder, rightShoulder, whitePaint)
    if( singleSideSelectedLandmarksIsRight){
      drawLine(canvas, rightShoulder, rightElbow, rightPaint)
      drawLine(canvas, rightElbow, rightWrist, rightPaint)
      drawLine(canvas, rightShoulder, rightHip, rightPaint)
      drawLine(canvas, rightHip, rightKnee, rightPaint)
      drawLine(canvas, rightKnee, rightAnkle, rightPaint)
      drawAngle( canvas, SkeletalJoint.KNEE.ordinal, rightKnee!!, rightHip!!, rightAnkle!!)
      drawAngle( canvas, SkeletalJoint.HIP.ordinal, rightHip!!, rightKnee!!, rightShoulder!!)
      drawAngle( canvas, SkeletalJoint.ELBOW.ordinal, rightElbow!!, rightWrist!!, rightShoulder!!)
//      canvas.drawText("knee angle is %.0f°".format( tmp), 10f, 200F, rightPaint)
    }else{
      drawLine(canvas, leftShoulder, leftElbow, leftPaint)
      drawLine(canvas, leftElbow, leftWrist, leftPaint)
      drawLine(canvas, leftShoulder, leftHip, leftPaint)
      drawLine(canvas, leftHip, leftKnee, leftPaint)
      drawLine(canvas, leftKnee, leftAnkle, leftPaint)
      drawAngle( canvas, SkeletalJoint.KNEE.ordinal, leftKnee!!, leftHip!!, leftAnkle!!)
      drawAngle( canvas, SkeletalJoint.HIP.ordinal, leftHip!!, leftKnee!!, leftShoulder!!)
      drawAngle( canvas, SkeletalJoint.ELBOW.ordinal, leftElbow!!, leftWrist!!, leftShoulder!!)
    }
//    drawLine(canvas, leftHip, rightHip, whitePaint)
// Left body lines
//    drawLine(canvas, leftKnee, leftAnkle, leftPaint)
//    drawLine(canvas, leftWrist, leftThumb, leftPaint)
//    drawLine(canvas, leftWrist, leftPinky, leftPaint)
//    drawLine(canvas, leftWrist, leftIndex, leftPaint)
//    drawLine(canvas, leftIndex, leftPinky, leftPaint)
//    drawLine(canvas, leftAnkle, leftHeel, leftPaint)
//    drawLine(canvas, leftHeel, leftFootIndex, leftPaint)
// Right body lines:
//    drawLine(canvas, rightKnee, rightAnkle, rightPaint)
//    drawLine(canvas, rightWrist, rightThumb, rightPaint)
//    drawLine(canvas, rightWrist, rightPinky, rightPaint)
//    drawLine(canvas, rightWrist, rightIndex, rightPaint)
//    drawLine(canvas, rightIndex, rightPinky, rightPaint)
//    drawLine(canvas, rightAnkle, rightHeel, rightPaint)
//..IVN    drawLine(canvas, rightHeel, rightFootIndex, rightPaint)
  }

  internal fun drawPoint(canvas: Canvas, landmark: PoseLandmark, paint: Paint) {
    val point = landmark.position3D
    updatePaintColorByZValue(
      paint,
      canvas,
      visualizeZ,
      rescaleZForVisualization,
      point.z,
      zMin,
      zMax
    )
    canvas.drawCircle(translateX(point.x), translateY(point.y), DOT_RADIUS, paint)
  }

  internal fun drawLine(
    canvas: Canvas,
    startLandmark: PoseLandmark?,
    endLandmark: PoseLandmark?,
    paint: Paint
  ) {
    val start = startLandmark!!.position3D
    val end = endLandmark!!.position3D
    // Gets average z for the current body line
    if( startLandmark.inFrameLikelihood < MIN_LIKELYHOOD)
      return
    if( endLandmark.inFrameLikelihood < MIN_LIKELYHOOD)
      return
    val avgZInImagePixel = (start.z + end.z) / 2
    updatePaintColorByZValue(
      paint,
      canvas,
      visualizeZ,
      rescaleZForVisualization,
      avgZInImagePixel,
      zMin,
      zMax
    )
    canvas.drawLine(
      translateX(start.x),
      translateY(start.y),
      translateX(end.x),
      translateY(end.y),
      paint
    )
  }

}
