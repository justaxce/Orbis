package com.floating.virtualwindow.tools

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.floating.virtualwindow.bridge.WebBridgeManager
import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Bitmap
import android.os.Build
import android.os.Message
import java.util.Locale
import android.util.AttributeSet
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.content.Intent
import android.net.Uri
import android.webkit.CookieManager
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.BrowserHistoryManager

@SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
class FloatingBrowserView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    private val webView: WebView
    private val etUrl: EditText
    private val pbLoading: ProgressBar
    private val btnBack: ImageButton
    private val btnClearUrl: ImageButton
    private val btnRefresh: ImageButton
    private val btnToggleDesktopMode: ImageButton
    private val flOAuthPopupContainer: FrameLayout
    private var popupWebView: WebView? = null
    private var isDesktopMode: Boolean = false

    // Text Selection Toolbar
    private val llSelectionToolbar: LinearLayout
    private val tvSelectedTextPreview: TextView
    private val btnCopySelection: TextView
    private val btnSearchSelection: TextView
    private val btnClearSelection: ImageButton

    private var currentSelectedText: String = ""

    var onFocusChanged: ((Boolean) -> Unit)? = null
    var onInputFocusChanged: ((Boolean) -> Unit)? = null
    var onKeyboardViewportChanged: ((isOpen: Boolean, keyboardHeightPx: Int) -> Unit)? = null
    var onNotificationReceived: (() -> Unit)? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_floating_browser, this, true)
        webView = view.findViewById(R.id.wvBrowser)
        etUrl = view.findViewById(R.id.etBrowserUrl)
        pbLoading = view.findViewById(R.id.pbBrowserLoading)
        btnBack = view.findViewById(R.id.btnBrowserBack)
        btnClearUrl = view.findViewById(R.id.btnClearUrl)
        btnRefresh = view.findViewById(R.id.btnBrowserRefresh)
        btnToggleDesktopMode = view.findViewById(R.id.btnToggleDesktopMode)
        flOAuthPopupContainer = view.findViewById(R.id.flOAuthPopupContainer)

        llSelectionToolbar = view.findViewById(R.id.llSelectionToolbar)
        tvSelectedTextPreview = view.findViewById(R.id.tvSelectedTextPreview)
        btnCopySelection = view.findViewById(R.id.btnCopySelection)
        btnSearchSelection = view.findViewById(R.id.btnSearchSelection)
        btnClearSelection = view.findViewById(R.id.btnClearSelection)

        updateDesktopButtonUi()
        setupWebView()
        setupListeners()
    }

    private fun setupWebView() {
        val settings: WebSettings = webView.settings
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.databaseEnabled = true
        settings.loadWithOverviewMode = true
        settings.useWideViewPort = true
        settings.setSupportZoom(true)
        settings.builtInZoomControls = true
        settings.displayZoomControls = false
        settings.mediaPlaybackRequiresUserGesture = false
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
        settings.setSupportMultipleWindows(true)
        settings.javaScriptCanOpenWindowsAutomatically = true
        settings.allowFileAccess = true
        settings.allowContentAccess = true
        settings.setGeolocationEnabled(true)
        settings.cacheMode = WebSettings.LOAD_DEFAULT
        applyThemeAndLanguage()

        // Enable horizontal & vertical scrollbars for desktop sites and preformatted code
        webView.isHorizontalScrollBarEnabled = true
        webView.isVerticalScrollBarEnabled = true
        webView.isScrollbarFadingEnabled = true
        webView.scrollBarStyle = View.SCROLLBARS_INSIDE_OVERLAY

        // Enable Cookies (including 3rd-party cookies for login sessions across all services)
        try {
            val cookieManager = CookieManager.getInstance()
            cookieManager.setAcceptCookie(true)
            cookieManager.setAcceptThirdPartyCookies(webView, true)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        webView.isLongClickable = true
        webView.isHapticFeedbackEnabled = true

        // Register JavaScript bridge for text selection detection
        webView.addJavascriptInterface(OrbisBridge(), "OrbisBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val uri = request?.url ?: return false
                val scheme = uri.scheme?.lowercase() ?: return false
                if (scheme == "http" || scheme == "https") {
                    return false
                }
                try {
                    val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Suppress unknown scheme crashes
                }
                return true
            }

            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pbLoading.visibility = View.VISIBLE
                etUrl.setText(url)
                clearSelection()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pbLoading.visibility = View.GONE
                injectSelectionListener()
                injectHorizontalScrollAndCodeFix()
                injectKeyboardAndNotificationListeners()
                try {
                    CookieManager.getInstance().flush()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                BrowserHistoryManager.recordVisit(context, url, view?.title)
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onReceivedTitle(view: WebView?, title: String?) {
                super.onReceivedTitle(view, title)
                checkNotificationInTitle(title)
            }

            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                pbLoading.progress = newProgress
                if (newProgress >= 100) {
                    pbLoading.visibility = View.GONE
                } else {
                    pbLoading.visibility = View.VISIBLE
                }
            }

            override fun onPermissionRequest(request: PermissionRequest?) {
                handlePermissionRequest(request)
            }

            override fun onGeolocationPermissionsShowPrompt(
                origin: String?,
                callback: GeolocationPermissions.Callback?
            ) {
                handleGeolocationPermission(origin, callback)
            }

            override fun onShowFileChooser(
                webView: WebView?,
                filePathCallback: ValueCallback<Array<Uri>>?,
                fileChooserParams: FileChooserParams?
            ): Boolean {
                return handleShowFileChooser(filePathCallback, fileChooserParams)
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message?
            ): Boolean {
                return handleOAuthPopupWindow(resultMsg)
            }

            override fun onCloseWindow(window: WebView?) {
                dismissOAuthPopup()
            }
        }

        // Detect touch down on WebView to immediately grant input focus so that HTML inputs pop up soft keyboard instantly
        webView.setOnTouchListener { v, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onFocusChanged?.invoke(true)
                v.requestFocus()
            } else if (event.action == MotionEvent.ACTION_UP) {
                postDelayed({
                    checkActiveSelection()
                }, 150)
            }
            false
        }
    }

    private fun injectSelectionListener() {
        val js = """
            (function() {
                if (window.__orbis_selection_injected) return;
                window.__orbis_selection_injected = true;
                document.addEventListener('selectionchange', function() {
                    var sel = window.getSelection();
                    var text = sel ? sel.toString().trim() : '';
                    if (window.OrbisBridge) {
                        window.OrbisBridge.onTextSelected(text);
                    }
                });
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun checkActiveSelection() {
        webView.evaluateJavascript("(function(){ return window.getSelection() ? window.getSelection().toString().trim() : ''; })();") { result ->
            val cleaned = result?.trim('"', ' ', '\\', '\n', '\r') ?: ""
            if (cleaned.isNotEmpty() && cleaned != "null") {
                handleSelectedText(cleaned)
            }
        }
    }

    private fun handleSelectedText(text: String) {
        val trimmed = text.trim()
        if (trimmed.isNotEmpty()) {
            currentSelectedText = trimmed
            val preview = if (trimmed.length > 25) "${trimmed.take(25)}..." else trimmed
            tvSelectedTextPreview.text = "\"$preview\""
            llSelectionToolbar.visibility = View.VISIBLE
        } else {
            llSelectionToolbar.visibility = View.GONE
        }
    }

    private fun clearSelection() {
        currentSelectedText = ""
        llSelectionToolbar.visibility = View.GONE
        webView.evaluateJavascript("window.getSelection() && window.getSelection().removeAllRanges();", null)
    }

    private fun setupListeners() {
        btnBack.setOnClickListener {
            if (webView.canGoBack()) {
                webView.goBack()
            }
        }

        btnRefresh.setOnClickListener {
            webView.reload()
        }

        btnToggleDesktopMode.setOnClickListener {
            toggleDesktopMode()
        }

        btnClearUrl.setOnClickListener {
            if (etUrl.text.isNotEmpty()) {
                etUrl.setText("")
                etUrl.requestFocus()
                onFocusChanged?.invoke(true)
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(etUrl, InputMethodManager.SHOW_IMPLICIT)
            } else if (pbLoading.visibility == View.VISIBLE) {
                webView.stopLoading()
                pbLoading.visibility = View.GONE
            }
        }

        etUrl.setOnTouchListener { _, event ->
            if (event.action == MotionEvent.ACTION_DOWN) {
                onFocusChanged?.invoke(true)
            }
            false
        }

        etUrl.setOnClickListener {
            onFocusChanged?.invoke(true)
            etUrl.requestFocus()
            etUrl.postDelayed({
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.showSoftInput(etUrl, InputMethodManager.SHOW_IMPLICIT)
            }, 100)
        }

        etUrl.setOnFocusChangeListener { _, hasFocus ->
            onFocusChanged?.invoke(hasFocus)
            onInputFocusChanged?.invoke(hasFocus)
            if (hasFocus) {
                etUrl.postDelayed({
                    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                    imm?.showSoftInput(etUrl, InputMethodManager.SHOW_IMPLICIT)
                }, 100)
            }
        }

        etUrl.setOnEditorActionListener { _, actionId, event ->
            if (actionId == EditorInfo.IME_ACTION_GO ||
                (event != null && event.keyCode == KeyEvent.KEYCODE_ENTER && event.action == KeyEvent.ACTION_DOWN)
            ) {
                loadFromInput()
                etUrl.clearFocus()
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                imm?.hideSoftInputFromWindow(etUrl.windowToken, 0)
                onFocusChanged?.invoke(false)
                true
            } else {
                false
            }
        }

        // Selection Toolbar Actions
        btnCopySelection.setOnClickListener {
            if (currentSelectedText.isNotEmpty()) {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("Copied Text", currentSelectedText)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, R.string.text_copied_toast, Toast.LENGTH_SHORT).show()
                clearSelection()
            }
        }

        btnSearchSelection.setOnClickListener {
            if (currentSelectedText.isNotEmpty()) {
                val query = currentSelectedText
                clearSelection()
                loadUrl("https://www.google.com/search?q=" + java.net.URLEncoder.encode(query, "UTF-8"))
            }
        }

        btnClearSelection.setOnClickListener {
            clearSelection()
        }
    }

    fun loadUrl(url: String, asDesktop: Boolean = false) {
        isDesktopMode = asDesktop
        applyThemeAndLanguage()
        updateDesktopButtonUi()
        val headers = createLanguageHeaders()
        webView.loadUrl(url, headers)
    }

    private fun toggleDesktopMode() {
        isDesktopMode = !isDesktopMode
        applyThemeAndLanguage()
        updateDesktopButtonUi()
        val msg = if (isDesktopMode) "Desktop site requested" else "Mobile site requested"
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
        webView.reload()
    }

    fun applyThemeAndLanguage() {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                webView.settings.forceDark = if (isSystemDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                webView.settings.isAlgorithmicDarkeningAllowed = isSystemDark
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val bgColor = context.getColor(R.color.background)
        webView.setBackgroundColor(bgColor)

        applyUserAgent()
    }

    private fun applyUserAgent() {
        if (isDesktopMode) {
            webView.settings.userAgentString = DESKTOP_USER_AGENT
        } else {
            try {
                webView.settings.userAgentString = WebSettings.getDefaultUserAgent(context)
            } catch (e: Exception) {
                webView.settings.userAgentString = ANDROID_MOBILE_USER_AGENT
            }
        }
    }

    private fun createLanguageHeaders(): Map<String, String> {
        val locale = Locale.getDefault()
        val tag = locale.toLanguageTag()
        val lang = locale.language
        return mapOf(
            "Accept-Language" to "$tag,$lang;q=0.9,en;q=0.8"
        )
    }

    private fun updateDesktopButtonUi() {
        if (isDesktopMode) {
            btnToggleDesktopMode.setColorFilter(context.getColor(R.color.accent))
        } else {
            btnToggleDesktopMode.setColorFilter(context.getColor(R.color.text_secondary))
        }
    }

    private fun injectHorizontalScrollAndCodeFix() {
        val js = """
            (function() {
                try {
                    var metas = document.querySelectorAll('meta[name="viewport"]');
                    metas.forEach(function(m) {
                        var content = m.getAttribute('content') || '';
                        if (content.indexOf('user-scalable=no') !== -1 || content.indexOf('user-scalable=0') !== -1) {
                            m.setAttribute('content', content.replace(/user-scalable=\s*(no|0)/gi, 'user-scalable=yes').replace(/maximum-scale=\s*1(\.0)?/gi, 'maximum-scale=5.0'));
                        }
                    });
                    var css = 'html, body { overflow-x: auto !important; -webkit-overflow-scrolling: touch !important; } ' +
                              'pre, code, table, .highlight, [class*="code"], [class*="table"], .blob-wrapper, .react-code-text { overflow-x: auto !important; -webkit-overflow-scrolling: touch !important; max-width: 100vw !important; } ' +
                              '#app, #app > div, ._aigv, ._aigs, .landing-wrapper { overflow-x: auto !important; min-width: 720px !important; width: auto !important; -webkit-overflow-scrolling: touch !important; }';
                    var head = document.head || document.getElementsByTagName('head')[0] || document.documentElement;
                    var style = document.getElementById('__orbis_scroll_style');
                    if (!style) {
                        style = document.createElement('style');
                        style.id = '__orbis_scroll_style';
                        style.type = 'text/css';
                        style.appendChild(document.createTextNode(css));
                        head.appendChild(style);
                    }
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    companion object {
        const val ANDROID_MOBILE_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
        const val DESKTOP_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    private fun loadFromInput() {
        var input = etUrl.text.toString().trim()
        if (input.isEmpty()) return

        if (!input.startsWith("http://") && !input.startsWith("https://")) {
            input = if (input.contains(".") && !input.contains(" ")) {
                "https://$input"
            } else {
                "https://www.google.com/search?q=" + java.net.URLEncoder.encode(input, "UTF-8")
            }
        }
        applyThemeAndLanguage()
        webView.loadUrl(input, createLanguageHeaders())
    }

    private fun handleOAuthPopupWindow(resultMsg: Message?): Boolean {
        if (resultMsg == null) return false
        flOAuthPopupContainer.removeAllViews()
        flOAuthPopupContainer.visibility = View.VISIBLE

        val popup = WebView(context).apply {
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.setSupportMultipleWindows(false)
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            val isSystemDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                try {
                    settings.forceDark = if (isSystemDark) WebSettings.FORCE_DARK_ON else WebSettings.FORCE_DARK_OFF
                } catch (e: Exception) {}
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    settings.isAlgorithmicDarkeningAllowed = isSystemDark
                } catch (e: Exception) {}
            }
            try {
                settings.userAgentString = WebSettings.getDefaultUserAgent(context)
            } catch (e: Exception) {
                settings.userAgentString = ANDROID_MOBILE_USER_AGENT
            }
            setBackgroundColor(context.getColor(R.color.background))
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    try {
                        CookieManager.getInstance().flush()
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                    BrowserHistoryManager.recordVisit(context, url, view?.title)
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    return false
                }
            }

            webChromeClient = object : WebChromeClient() {
                override fun onPermissionRequest(request: PermissionRequest?) {
                    handlePermissionRequest(request)
                }

                override fun onGeolocationPermissionsShowPrompt(
                    origin: String?,
                    callback: GeolocationPermissions.Callback?
                ) {
                    handleGeolocationPermission(origin, callback)
                }

                override fun onShowFileChooser(
                    webView: WebView?,
                    filePathCallback: ValueCallback<Array<Uri>>?,
                    fileChooserParams: FileChooserParams?
                ): Boolean {
                    return handleShowFileChooser(filePathCallback, fileChooserParams)
                }

                override fun onCloseWindow(window: WebView?) {
                    dismissOAuthPopup()
                }
            }
        }

        val popupLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
            setBackgroundColor(context.getColor(R.color.background))

            val density = resources.displayMetrics.density
            val topBar = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, (34 * density).toInt())
                setBackgroundColor(context.getColor(R.color.surface))
                gravity = Gravity.CENTER_VERTICAL
                setPadding((10 * density).toInt(), 0, (6 * density).toInt(), 0)

                val titleTv = TextView(context).apply {
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    text = "Sign in / Authentication"
                    setTextColor(context.getColor(R.color.text_primary))
                    textSize = 12f
                    setSingleLine(true)
                }

                val closeBtn = ImageButton(context).apply {
                    layoutParams = LinearLayout.LayoutParams((28 * density).toInt(), (28 * density).toInt())
                    setImageResource(R.drawable.ic_close)
                    setBackgroundResource(android.R.drawable.btn_default)
                    contentDescription = "Close Sign-In"
                    setOnClickListener {
                        dismissOAuthPopup()
                    }
                }

                addView(titleTv)
                addView(closeBtn)
            }

            addView(topBar)
            addView(popup, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f))
        }

        flOAuthPopupContainer.addView(popupLayout)
        popupWebView = popup

        val transport = resultMsg.obj as? WebView.WebViewTransport
        transport?.webView = popup
        resultMsg.sendToTarget()
        return true
    }

    private fun dismissOAuthPopup() {
        popupWebView?.stopLoading()
        popupWebView?.destroy()
        popupWebView = null
        flOAuthPopupContainer.removeAllViews()
        flOAuthPopupContainer.visibility = View.GONE
        try {
            CookieManager.getInstance().flush()
        } catch (e: Exception) {
            e.printStackTrace()
        }
        webView.reload()
    }

    private fun handlePermissionRequest(request: PermissionRequest?) {
        if (request == null) return
        val resources = request.resources ?: return

        val neededPermissions = mutableListOf<String>()
        for (res in resources) {
            when (res) {
                PermissionRequest.RESOURCE_AUDIO_CAPTURE -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.RECORD_AUDIO)
                    }
                }
                PermissionRequest.RESOURCE_VIDEO_CAPTURE -> {
                    if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                        neededPermissions.add(Manifest.permission.CAMERA)
                    }
                }
            }
        }

        if (neededPermissions.isEmpty()) {
            try {
                request.grant(resources)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        } else {
            WebBridgeManager.requestNativePermissions(context, neededPermissions.toTypedArray()) { granted ->
                post {
                    try {
                        if (granted) {
                            request.grant(resources)
                        } else {
                            request.deny()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun handleGeolocationPermission(
        origin: String?,
        callback: GeolocationPermissions.Callback?
    ) {
        if (callback == null) return
        val fineLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarseLocation = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)

        if (fineLocation == PackageManager.PERMISSION_GRANTED || coarseLocation == PackageManager.PERMISSION_GRANTED) {
            callback.invoke(origin, true, false)
        } else {
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            WebBridgeManager.requestNativePermissions(context, permissions) { granted ->
                post {
                    try {
                        callback.invoke(origin, granted, false)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    private fun handleShowFileChooser(
        filePathCallback: ValueCallback<Array<Uri>>?,
        fileChooserParams: WebChromeClient.FileChooserParams?
    ): Boolean {
        if (filePathCallback == null) return false

        val acceptTypes = fileChooserParams?.acceptTypes ?: emptyArray()
        val allowMultiple = fileChooserParams?.mode == WebChromeClient.FileChooserParams.MODE_OPEN_MULTIPLE
        val isCaptureEnabled = fileChooserParams?.isCaptureEnabled ?: false

        WebBridgeManager.startFileChooser(
            context = context,
            acceptTypes = acceptTypes,
            allowMultiple = allowMultiple,
            isCaptureEnabled = isCaptureEnabled,
            callback = filePathCallback
        )
        return true
    }

    private fun injectKeyboardAndNotificationListeners() {
        val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        val isSystemDark = currentNightMode == Configuration.UI_MODE_NIGHT_YES
        val locale = Locale.getDefault()
        val langTag = locale.toLanguageTag()
        val lang = locale.language

        val js = """
            (function() {
                if (window.__orbis_kb_notif_injected) return;
                window.__orbis_kb_notif_injected = true;

                document.addEventListener('focusin', function(e) {
                    var el = e.target;
                    if (!el) return;
                    var tag = (el.tagName || '').toLowerCase();
                    var isInput = tag === 'input' || tag === 'textarea' || el.isContentEditable;
                    if (isInput) {
                        if (window.OrbisBridge && window.OrbisBridge.onInputFocusState) {
                            window.OrbisBridge.onInputFocusState(true);
                        }
                        setTimeout(function() {
                            try {
                                el.scrollIntoView({ behavior: 'smooth', block: 'center' });
                            } catch(err){}
                        }, 300);
                    }
                }, true);

                document.addEventListener('focusout', function(e) {
                    var el = e.target;
                    if (!el) return;
                    var tag = (el.tagName || '').toLowerCase();
                    var isInput = tag === 'input' || tag === 'textarea' || el.isContentEditable;
                    if (isInput) {
                        setTimeout(function() {
                            var act = document.activeElement;
                            var actTag = (act && act.tagName ? act.tagName : '').toLowerCase();
                            var stillIn = actTag === 'input' || actTag === 'textarea' || (act && act.isContentEditable);
                            if (!stillIn && window.OrbisBridge && window.OrbisBridge.onInputFocusState) {
                                window.OrbisBridge.onInputFocusState(false);
                            }
                        }, 200);
                    }
                }, true);

                if (window.visualViewport) {
                    window.visualViewport.addEventListener('resize', function() {
                        var diff = window.innerHeight - window.visualViewport.height;
                        var isKb = diff > 100;
                        var kbPx = Math.round(diff * (window.devicePixelRatio || 1));
                        if (window.OrbisBridge && window.OrbisBridge.onViewportResized) {
                            window.OrbisBridge.onViewportResized(isKb, kbPx);
                        }
                    });
                }

                var checkTitle = function(str) {
                    if (window.OrbisBridge && window.OrbisBridge.onWebTitleChanged) {
                        window.OrbisBridge.onWebTitleChanged(str || document.title || '');
                    }
                };
                var titleEl = document.querySelector('title');
                if (titleEl) {
                    new MutationObserver(function() {
                        checkTitle(document.title);
                    }).observe(titleEl, { subtree: true, characterData: true, childList: true });
                }

                if (!window.Notification) {
                    window.Notification = function(title, options) {
                        if (window.OrbisBridge && window.OrbisBridge.onWebNotificationReceived) {
                            window.OrbisBridge.onWebNotificationReceived(title || '');
                        }
                    };
                    window.Notification.permission = 'granted';
                    window.Notification.requestPermission = function(cb) {
                        if (cb) cb('granted');
                        return Promise.resolve('granted');
                    };
                } else {
                    var origNotif = window.Notification;
                    window.Notification = function(title, options) {
                        if (window.OrbisBridge && window.OrbisBridge.onWebNotificationReceived) {
                            window.OrbisBridge.onWebNotificationReceived(title || '');
                        }
                        return new origNotif(title, options);
                    };
                    window.Notification.permission = 'granted';
                }

                if (navigator.setAppBadge) {
                    var origBadge = navigator.setAppBadge;
                    navigator.setAppBadge = function(count) {
                        if (window.OrbisBridge && window.OrbisBridge.onWebNotificationReceived) {
                            window.OrbisBridge.onWebNotificationReceived('badge:' + (count || 1));
                        }
                        return origBadge.apply(this, arguments);
                    };
                }

                // Enforce system theme (Dark/Light) and system language on webpage
                try {
                    var meta = document.querySelector('meta[name="color-scheme"]');
                    if (!meta) {
                        meta = document.createElement('meta');
                        meta.name = 'color-scheme';
                        (document.head || document.documentElement).appendChild(meta);
                    }
                    meta.content = ${if (isSystemDark) "'dark light'" else "'light dark'"};
                } catch(e) {}

                try {
                    Object.defineProperty(navigator, 'language', {
                        get: function() { return '$langTag'; },
                        configurable: true
                    });
                    Object.defineProperty(navigator, 'languages', {
                        get: function() { return ['$langTag', '$lang', 'en']; },
                        configurable: true
                    });
                } catch(e) {}
            })();
        """.trimIndent()
        webView.evaluateJavascript(js, null)
    }

    private fun checkNotificationInTitle(title: String?) {
        if (title.isNullOrBlank()) return
        val t = title.trim()
        val hasBadge = t.matches(Regex("""^[\(\[\{]\s*\d+\+?\s*[\)\]\}].*""")) ||
                t.startsWith("•") ||
                t.startsWith("*") ||
                t.contains(Regex("""\(\d+\)"""))
        if (hasBadge) {
            onNotificationReceived?.invoke()
        }
    }

    fun destroy() {
        dismissOAuthPopup()
        webView.stopLoading()
        webView.destroy()
    }

    inner class OrbisBridge {
        @JavascriptInterface
        fun onTextSelected(text: String) {
            post {
                handleSelectedText(text)
            }
        }

        @JavascriptInterface
        fun onInputFocusState(isFocused: Boolean) {
            post {
                onFocusChanged?.invoke(isFocused)
                onInputFocusChanged?.invoke(isFocused)
            }
        }

        @JavascriptInterface
        fun onViewportResized(keyboardOpen: Boolean, heightDiffPx: Int) {
            post {
                onKeyboardViewportChanged?.invoke(keyboardOpen, heightDiffPx)
            }
        }

        @JavascriptInterface
        fun onWebNotificationReceived(info: String) {
            post {
                onNotificationReceived?.invoke()
            }
        }

        @JavascriptInterface
        fun onWebTitleChanged(title: String) {
            post {
                checkNotificationInTitle(title)
            }
        }
    }
}
