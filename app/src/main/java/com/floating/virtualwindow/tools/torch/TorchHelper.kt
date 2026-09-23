package com.floating.virtualwindow.tools.torch

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.widget.Toast

object TorchHelper {

    var isTorchOn: Boolean = false
        private set

    fun toggleTorch(context: Context): Boolean {
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
                ?: return false
            val cameraIds = cameraManager.cameraIdList

            var targetCameraId: String? = null
            for (id in cameraIds) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                val facing = characteristics.get(CameraCharacteristics.LENS_FACING)
                if (hasFlash && facing == CameraCharacteristics.LENS_FACING_BACK) {
                    targetCameraId = id
                    break
                }
            }

            if (targetCameraId == null && cameraIds.isNotEmpty()) {
                targetCameraId = cameraIds[0]
            }

            if (targetCameraId != null) {
                val newState = !isTorchOn
                cameraManager.setTorchMode(targetCameraId, newState)
                isTorchOn = newState
                val msg = if (isTorchOn) "🔦 Flashlight ON" else "Flashlight OFF"
                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                return isTorchOn
            } else {
                Toast.makeText(context, "No camera flash found", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(context, "Flashlight error: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
        return false
    }

    fun turnOff(context: Context) {
        if (!isTorchOn) return
        try {
            val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager ?: return
            for (id in cameraManager.cameraIdList) {
                val characteristics = cameraManager.getCameraCharacteristics(id)
                val hasFlash = characteristics.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) ?: false
                if (hasFlash) {
                    cameraManager.setTorchMode(id, false)
                }
            }
            isTorchOn = false
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
