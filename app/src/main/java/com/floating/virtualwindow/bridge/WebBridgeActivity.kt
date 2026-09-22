package com.floating.virtualwindow.bridge

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
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

        val cleanTypes = acceptTypes.filter { it.isNotBlank() }
        val isOnlyImage = cleanTypes.isNotEmpty() && cleanTypes.all { it.startsWith("image/", ignoreCase = true) }
        val isOnlyVideo = cleanTypes.isNotEmpty() && cleanTypes.all { it.startsWith("video/", ignoreCase = true) }
        val isMedia = isOnlyImage || isOnlyVideo || (cleanTypes.isNotEmpty() && cleanTypes.all {
            it.startsWith("image/", ignoreCase = true) || it.startsWith("video/", ignoreCase = true)
        })
        val isImageAcceptable = cleanTypes.isEmpty() || isMedia || cleanTypes.any {
            it.contains("image", ignoreCase = true) || it == "*/*"
        }

        // 1. Prepare camera capture intent if images or videos are acceptable
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

        // If capture attribute is strictly requested by website (e.g. <input capture="camera">), launch camera directly
        if (isCaptureEnabled && cameraIntent != null) {
            try {
                startActivityForResult(cameraIntent, REQ_FILE_PICKER)
                return
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // 2. Prepare Chooser and Intent List
        val initialIntents = mutableListOf<Intent>()
        if (cameraIntent != null) {
            initialIntents.add(cameraIntent)
        }

        val primaryIntent: Intent
        val chooserTitle: String

        if (isMedia) {
            chooserTitle = if (isOnlyVideo) "Select Video or Record" else "Select Photos or Camera"

            // On Android 13+ (API 33+), check for modern Photo Picker
            var photoPicker: Intent? = null
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    val pIntent = Intent(MediaStore.ACTION_PICK_IMAGES).apply {
                        if (isOnlyVideo) type = "video/*"
                        else if (isOnlyImage) type = "image/*"
                        if (allowMultiple) {
                            putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit())
                        }
                    }
                    if (pIntent.resolveActivity(packageManager) != null) {
                        photoPicker = pIntent
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }

            // Fallback / standard Gallery picker (Google Photos, Samsung Gallery, Device Gallery)
            val mediaUri = if (isOnlyVideo) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val galleryIntent = Intent(Intent.ACTION_PICK, mediaUri).apply {
                type = if (isOnlyVideo) "video/*" else "image/*"
                if (allowMultiple) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

            // Also provide file browser option in chooser in case user explicitly wants raw files
            val filesIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                type = if (isOnlyVideo) "video/*" else "image/*"
                if (cleanTypes.size > 1) {
                    putExtra(Intent.EXTRA_MIME_TYPES, cleanTypes.toTypedArray())
                }
                if (allowMultiple) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }
            initialIntents.add(filesIntent)

            primaryIntent = photoPicker ?: galleryIntent
        } else {
            chooserTitle = "Attach File or Take Photo"

            // Generic documents or mixed files
            val getFilesIntent = Intent(Intent.ACTION_GET_CONTENT).apply {
                if (cleanTypes.size == 1 && cleanTypes[0] != "*/*") {
                    type = cleanTypes[0]
                } else if (cleanTypes.size > 1) {
                    type = "*/*"
                    putExtra(Intent.EXTRA_MIME_TYPES, cleanTypes.toTypedArray())
                } else {
                    type = "*/*"
                }
                if (allowMultiple) {
                    putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
                }
            }

            // If images could be acceptable, add Gallery as an option in chooser too
            if (isImageAcceptable) {
                val galleryOpt = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI).apply {
                    type = "image/*"
                }
                initialIntents.add(galleryOpt)
            }

            primaryIntent = getFilesIntent
        }

        try {
            val chooserIntent = Intent.createChooser(primaryIntent, chooserTitle).apply {
                if (initialIntents.isNotEmpty()) {
                    putExtra(Intent.EXTRA_INITIAL_INTENTS, initialIntents.toTypedArray())
                }
            }
            startActivityForResult(chooserIntent, REQ_FILE_PICKER)
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
