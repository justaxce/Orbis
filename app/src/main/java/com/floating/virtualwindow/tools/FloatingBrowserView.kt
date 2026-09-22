package com.floating.virtualwindow.tools

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.util.AttributeSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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
    private val btnRefresh: ImageButton

    // Text Selection Toolbar
    private val llSelectionToolbar: LinearLayout
    private val tvSelectedTextPreview: TextView
    private val btnCopySelection: TextView
    private val btnSearchSelection: TextView
    private val btnClearSelection: ImageButton

    private var currentSelectedText: String = ""

    var onFocusChanged: ((Boolean) -> Unit)? = null

    init {
        val view = LayoutInflater.from(context).inflate(R.layout.view_floating_browser, this, true)
        webView = view.findViewById(R.id.wvBrowser)
        etUrl = view.findViewById(R.id.etBrowserUrl)
        pbLoading = view.findViewById(R.id.pbBrowserLoading)
        btnBack = view.findViewById(R.id.btnBrowserBack)
        btnRefresh = view.findViewById(R.id.btnBrowserRefresh)

        llSelectionToolbar = view.findViewById(R.id.llSelectionToolbar)
        tvSelectedTextPreview = view.findViewById(R.id.tvSelectedTextPreview)
        btnCopySelection = view.findViewById(R.id.btnCopySelection)
        btnSearchSelection = view.findViewById(R.id.btnSearchSelection)
        btnClearSelection = view.findViewById(R.id.btnClearSelection)

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
        settings.builtInZoomControls = true
        settings.displayZoomControls = false

        webView.isLongClickable = true
        webView.isHapticFeedbackEnabled = true

        // Register JavaScript bridge for text selection detection
        webView.addJavascriptInterface(OrbisBridge(), "OrbisBridge")

        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                pbLoading.visibility = View.VISIBLE
                etUrl.setText(url)
                clearSelection()
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                pbLoading.visibility = View.GONE
                injectSelectionListener()
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                pbLoading.progress = newProgress
                if (newProgress >= 100) {
                    pbLoading.visibility = View.GONE
                } else {
                    pbLoading.visibility = View.VISIBLE
                }
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
        if (asDesktop) {
            val desktopUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
            webView.settings.userAgentString = desktopUserAgent
        } else {
            webView.settings.userAgentString = null
        }
        webView.loadUrl(url)
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
        webView.loadUrl(input)
    }

    fun destroy() {
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
    }
}
