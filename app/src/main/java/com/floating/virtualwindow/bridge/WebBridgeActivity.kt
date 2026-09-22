package com.floating.virtualwindow.bridge

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import java.io.File

/**
 * Transparent trampoline activity used by the overlay floating window service to execute
 * native Android operations that require an Activity context:
 * 1. Requesting native runtime permissions (Camera, Microphone, Location, Media) with official OS dialogs.
 * 2. Launching native system file choosers, document pickers, and camera capture intents.
 */
class WebBridgeActivity : AppCompatActivity() {

    private var currentCameraUri: Uri? = null
    private var currentCameraFile: File? = null
    private var isResultHandled = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val action = intent.action
        when (action) {
            ACTION_REQUEST_PERMISSIONS -> {
                handlePermissionRequest()
            }
            ACTION_CHOOSE_FILE -> {
                handleFileChooser()
            }
            else -> {
                finish()
            }
        }
    }

    private fun handlePermissionRequest() {
        val permissions = intent.getStringArrayExtra(EXTRA_PERMISSIONS) ?: emptyArray()
        val missingPermissions = permissions.filter { perm ->
            ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED
        }.toTypedArray()

        if (missingPermissions.isEmpty()) {
            isResultHandled = true
            WebBridgeManager.onPermissionsResult(true)
            finish()
        } else {
            ActivityCompat.requestPermissions(this, missingPermissions, REQ_PERMISSIONS)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQ_PERMISSIONS) {
            isResultHandled = true
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }
            WebBridgeManager.onPermissionsResult(allGranted)
            finish()
        }
    }

    private fun handleFileChooser() {
        val acceptTypes = intent.getStringArrayExtra(EXTRA_ACCEPT_TYPES) ?: emptyArray()
        val allowMultiple = intent.getBooleanExtra(EXTRA_ALLOW_MULTIPLE, false)
        val isCaptureEnabled = intent.getBooleanExtra(EXTRA_CAPTURE_ENABLED, false)

        val isImageAcceptable = acceptTypes.isEmpty() || acceptTypes.any {
            it.contains("image", ignoreCase = true) || it == "*/*"
        }

        // Prepare camera photo intent if images are acceptable
        var cameraIntent: Intent? = null
        if (isImageAcceptable) {
            try {
                val cameraDir = File(cacheDir, "camera").apply { mkdirs() }
                val photoFile = File(cameraDir, "img_camera_${System.currentTimeMillis()}.jpg")
                val photoUri = FileProvider.getUriForFile(this, "${packageName}.fileprovider", photoFile)
                currentCameraFile = photoFile
                currentCameraUri = photoUri

                cameraIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                    addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION or Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Prepare document / file picker intent
        val pickIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            val filteredTypes = acceptTypes.filter { it.isNotBlank() }
            if (filteredTypes.size == 1 && filteredTypes[0] != "*/*") {
                type = filteredTypes[0]
            } else if (filteredTypes.size > 1) {
                type = "*/*"
                putExtra(Intent.EXTRA_MIME_TYPES, filteredTypes.toTypedArray())
            } else {
                type = "*/*"
            }
            if (allowMultiple) {
                putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            }
        }

        try {
            if (isCaptureEnabled && cameraIntent != null && cameraIntent.resolveActivity(packageManager) != null) {
                // If capture attribute is strictly requested, launch camera directly
                startActivityForResult(cameraIntent, REQ_FILE_PICKER)
            } else {
                val chooserIntent = Intent.createChooser(pickIntent, "Attach File or Take Photo").apply {
                    if (cameraIntent != null && cameraIntent.resolveActivity(packageManager) != null) {
                        putExtra(Intent.EXTRA_INITIAL_INTENTS, arrayOf(cameraIntent))
                    }
                }
                startActivityForResult(chooserIntent, REQ_FILE_PICKER)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            isResultHandled = true
            WebBridgeManager.onFilePickerResult(null)
            finish()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQ_FILE_PICKER) {
            isResultHandled = true
            if (resultCode == Activity.RESULT_OK) {
                val results = mutableListOf<Uri>()

                // Check if camera captured photo
                val camFile = currentCameraFile
                val camUri = currentCameraUri
                if (data == null || (data.data == null && data.clipData == null)) {
                    if (camFile != null && camFile.exists() && camFile.length() > 0L && camUri != null) {
                        results.add(camUri)
                    }
                }

                // Check multiple files from picker
                val clipData: ClipData? = data?.clipData
                if (clipData != null) {
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i)?.uri?.let { results.add(it) }
                    }
                } else if (data?.data != null) {
                    results.add(data.data!!)
                }

                val finalUris = if (results.isNotEmpty()) results.toTypedArray() else null
                WebBridgeManager.onFilePickerResult(finalUris)
            } else {
                // User cancelled file selection
                currentCameraFile?.let {
                    if (it.exists() && it.length() == 0L) {
                        it.delete()
                    }
                }
                WebBridgeManager.onFilePickerResult(null)
            }
            finish()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!isResultHandled) {
            // Safety guard: Unblock WebView callback if activity was dismissed or terminated prematurely
            WebBridgeManager.onFilePickerResult(null)
            WebBridgeManager.onPermissionsResult(false)
        }
    }

    companion object {
        const val ACTION_REQUEST_PERMISSIONS = "com.floating.virtualwindow.bridge.ACTION_REQUEST_PERMISSIONS"
        const val ACTION_CHOOSE_FILE = "com.floating.virtualwindow.bridge.ACTION_CHOOSE_FILE"

        const val EXTRA_PERMISSIONS = "extra_permissions"
        const val EXTRA_ACCEPT_TYPES = "extra_accept_types"
        const val EXTRA_ALLOW_MULTIPLE = "extra_allow_multiple"
        const val EXTRA_CAPTURE_ENABLED = "extra_capture_enabled"

        private const val REQ_PERMISSIONS = 1001
        private const val REQ_FILE_PICKER = 1002
    }
}
