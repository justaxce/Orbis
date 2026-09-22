package com.floating.virtualwindow.bridge

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.webkit.ValueCallback

/**
 * Singleton coordinator facilitating communication between overlay WebViews (hosted in FloatingOverlayService)
 * and the transparent trampoline [WebBridgeActivity] (which executes native Android Activity tasks like
 * requesting runtime permissions and launching system file/camera choosers).
 */
object WebBridgeManager {

    private var activeFileChooserCallback: ValueCallback<Array<Uri>>? = null
    private var activePermissionCallback: ((Boolean) -> Unit)? = null

    /**
     * Launches the system file picker / camera chooser on behalf of an overlay WebView.
     */
    @Synchronized
    fun startFileChooser(
        context: Context,
        acceptTypes: Array<String>,
        allowMultiple: Boolean,
        isCaptureEnabled: Boolean,
        callback: ValueCallback<Array<Uri>>
    ) {
        // Cancel any existing pending callback to avoid blocking the WebView engine
        activeFileChooserCallback?.onReceiveValue(null)
        activeFileChooserCallback = callback

        val intent = Intent(context, WebBridgeActivity::class.java).apply {
            action = WebBridgeActivity.ACTION_CHOOSE_FILE
            putExtra(WebBridgeActivity.EXTRA_ACCEPT_TYPES, acceptTypes)
            putExtra(WebBridgeActivity.EXTRA_ALLOW_MULTIPLE, allowMultiple)
            putExtra(WebBridgeActivity.EXTRA_CAPTURE_ENABLED, isCaptureEnabled)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    /**
     * Delivers the selected file or photo URI(s) back to the pending WebView FilePathCallback.
     */
    @Synchronized
    fun onFilePickerResult(uris: Array<Uri>?) {
        try {
            activeFileChooserCallback?.onReceiveValue(uris)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activeFileChooserCallback = null
        }
    }

    /**
     * Requests native Android runtime permissions (e.g. RECORD_AUDIO, CAMERA, ACCESS_FINE_LOCATION)
     * using the official Android system permission dialog.
     */
    @Synchronized
    fun requestNativePermissions(
        context: Context,
        permissions: Array<String>,
        callback: (Boolean) -> Unit
    ) {
        activePermissionCallback?.invoke(false)
        activePermissionCallback = callback

        val intent = Intent(context, WebBridgeActivity::class.java).apply {
            action = WebBridgeActivity.ACTION_REQUEST_PERMISSIONS
            putExtra(WebBridgeActivity.EXTRA_PERMISSIONS, permissions)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        context.startActivity(intent)
    }

    /**
     * Delivers the native Android permission grant/deny result back to the requesting WebView.
     */
    @Synchronized
    fun onPermissionsResult(allGranted: Boolean) {
        try {
            activePermissionCallback?.invoke(allGranted)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            activePermissionCallback = null
        }
    }
}
