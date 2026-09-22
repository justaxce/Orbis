package com.floating.virtualwindow

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floating.virtualwindow.data.AppRepository
import com.floating.virtualwindow.data.PreferencesManager
import com.floating.virtualwindow.engine.GuestLauncher
import com.floating.virtualwindow.service.FloatingOverlayService
import com.floating.virtualwindow.ui.AppAdapter
import com.floating.virtualwindow.ui.QuickLaunchAdapter
import com.floating.virtualwindow.ui.UpdateDialog
import com.floating.virtualwindow.ui.WirelessGuideDialog
import com.floating.virtualwindow.updater.UpdateManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.materialswitch.MaterialSwitch
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import rikka.shizuku.Shizuku

class MainActivity : AppCompatActivity() {

    private lateinit var preferencesManager: PreferencesManager
    private lateinit var appRepository: AppRepository
    private lateinit var appAdapter: AppAdapter
    private lateinit var quickLaunchAdapter: QuickLaunchAdapter
    private lateinit var updateManager: UpdateManager

    private val shizukuBinderListener = Shizuku.OnBinderReceivedListener {
        if (preferencesManager.engineMode == PreferencesManager.MODE_ADVANCED) {
            if (GuestLauncher.isShizukuRunningWithoutPermission()) {
                GuestLauncher.requestShizukuPermission(SHIZUKU_PERMISSION_CODE)
            }
        }
    }

    private val shizukuPermissionListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode == SHIZUKU_PERMISSION_CODE && grantResult == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Shizuku permission granted! Native apps enabled.", Toast.LENGTH_SHORT).show()
        }
    }

    // Top Bar
    private lateinit var llStatusToggle: View
    private lateinit var dotStatus: View
    private lateinit var tvStatusText: TextView

    // Pages
    private lateinit var pageWorkspace: View
    private lateinit var pageApps: View
    private lateinit var pageSettings: View
    private lateinit var bottomNav: BottomNavigationView
    private lateinit var layoutSplash: View

    // Workspace Views
    private lateinit var bannerPermissions: View
    private lateinit var btnQuickGrant: Button
    private lateinit var cardSidebarToggle: View
    private lateinit var tvSidebarToggleSubtitle: TextView
    private lateinit var switchSidebarToggle: MaterialSwitch
    private lateinit var rvQuickLaunch: RecyclerView
    private lateinit var tvCurrentEngine: TextView
    private lateinit var tvEngineDesc: TextView

    // Apps Views
    private lateinit var etSearchApps: EditText
    private lateinit var rvAppsList: RecyclerView
    private lateinit var tvPinnedCount: TextView

    // Settings Views
    private lateinit var switchAutoMinimize: MaterialSwitch
    private lateinit var rgDockSide: RadioGroup
    private lateinit var rgEngineMode: RadioGroup
    private lateinit var btnShizukuGuide: Button
    private lateinit var tvAppVersionDisplay: TextView
    private lateinit var tvAppVersionStatus: TextView
    private lateinit var btnCheckUpdates: Button
    private lateinit var btnVisitPortfolio: Button
    private lateinit var btnMessageDiscord: Button

    private val overlayPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        updatePermissionStates()
        updateServiceState()
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionStates()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        preferencesManager = PreferencesManager(this)
        appRepository = AppRepository(this)
        updateManager = UpdateManager(this)

        try {
            Shizuku.addBinderReceivedListenerSticky(shizukuBinderListener)
            Shizuku.addRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }

        initViews()
        setupSplashScreen()
        setupListeners()
        loadInstalledApps()
        checkSilentUpdateOnLaunch()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionStates()
        if (preferencesManager.isServiceEnabled && Settings.canDrawOverlays(this)) {
            FloatingOverlayService.start(this)
        }
        updateServiceState()
        syncDockSideSettings()
        switchAutoMinimize.isChecked = preferencesManager.autoMinimizeOnOutsideTap
    }

    override fun onDestroy() {
        try {
            Shizuku.removeBinderReceivedListener(shizukuBinderListener)
            Shizuku.removeRequestPermissionResultListener(shizukuPermissionListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
        super.onDestroy()
    }

    private fun initViews() {
        layoutSplash = findViewById(R.id.layoutSplash)
        llStatusToggle = findViewById(R.id.llStatusToggle)
        dotStatus = findViewById(R.id.dotStatus)
        tvStatusText = findViewById(R.id.tvStatusText)

        pageWorkspace = findViewById(R.id.pageWorkspace)
        pageApps = findViewById(R.id.pageApps)
        pageSettings = findViewById(R.id.pageSettings)
        bottomNav = findViewById(R.id.bottomNav)

        bannerPermissions = findViewById(R.id.bannerPermissions)
        btnQuickGrant = findViewById(R.id.btnQuickGrant)
        cardSidebarToggle = findViewById(R.id.cardSidebarToggle)
        tvSidebarToggleSubtitle = findViewById(R.id.tvSidebarToggleSubtitle)
        switchSidebarToggle = findViewById(R.id.switchSidebarToggle)

        rvQuickLaunch = findViewById(R.id.rvQuickLaunch)
        tvCurrentEngine = findViewById(R.id.tvCurrentEngine)
        tvEngineDesc = findViewById(R.id.tvEngineDesc)

        etSearchApps = findViewById(R.id.etSearchApps)
        rvAppsList = findViewById(R.id.rvAppsList)
        tvPinnedCount = findViewById(R.id.tvPinnedCount)

        switchAutoMinimize = findViewById(R.id.switchAutoMinimize)
        switchAutoMinimize.isChecked = preferencesManager.autoMinimizeOnOutsideTap

        rgDockSide = findViewById(R.id.rgDockSide)
        rgEngineMode = findViewById(R.id.rgEngineMode)
        btnShizukuGuide = findViewById(R.id.btnShizukuGuide)

        tvAppVersionDisplay = findViewById(R.id.tvAppVersionDisplay)
        tvAppVersionStatus = findViewById(R.id.tvAppVersionStatus)
        btnCheckUpdates = findViewById(R.id.btnCheckUpdates)
        btnVisitPortfolio = findViewById(R.id.btnVisitPortfolio)
        btnMessageDiscord = findViewById(R.id.btnMessageDiscord)

        tvAppVersionDisplay.text = getString(R.string.app_version_label, updateManager.getCurrentVersionName())

        // Quick Launch RecyclerView
        quickLaunchAdapter = QuickLaunchAdapter { app ->
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Enable 'Display over other apps' first", Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
                return@QuickLaunchAdapter
            }
            if (!preferencesManager.isServiceEnabled) {
                preferencesManager.isServiceEnabled = true
                FloatingOverlayService.start(this)
                updateServiceState()
            }
            Toast.makeText(this, "Swipe edge handle to open ${app.appName}", Toast.LENGTH_SHORT).show()
        }
        rvQuickLaunch.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        rvQuickLaunch.adapter = quickLaunchAdapter

        // Apps List RecyclerView
        appAdapter = AppAdapter { app, isSelected ->
            preferencesManager.toggleAppSelection(app.packageName, isSelected)
            updatePinnedCount()
            refreshQuickLaunch()
            if (preferencesManager.isServiceEnabled) {
                FloatingOverlayService.refreshApps(this)
            }
        }
        rvAppsList.layoutManager = LinearLayoutManager(this)
        rvAppsList.adapter = appAdapter

        syncDockSideSettings()

        preferencesManager.engineMode = PreferencesManager.MODE_ZERO_SETUP
        updateEngineSummary()
    }

    private fun syncDockSideSettings() {
        if (preferencesManager.dockSide == "LEFT") {
            findViewById<android.widget.RadioButton>(R.id.rbLeftEdge)?.isChecked = true
        } else {
            findViewById<android.widget.RadioButton>(R.id.rbRightEdge)?.isChecked = true
        }
    }

    private fun setupListeners() {
        // Bottom Navigation
        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_workspace -> showPage(pageWorkspace)
                R.id.nav_apps -> showPage(pageApps)
                R.id.nav_settings -> showPage(pageSettings)
            }
            true
        }

        // Top App Bar Status Toggle
        llStatusToggle.setOnClickListener {
            toggleSidebarService()
        }

        // Master Workspace Sidebar Card & Switch
        cardSidebarToggle.setOnClickListener {
            toggleSidebarService()
        }

        switchSidebarToggle.setOnClickListener {
            toggleSidebarService()
        }

        btnQuickGrant.setOnClickListener {
            requestOverlayPermission()
        }

        switchAutoMinimize.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.autoMinimizeOnOutsideTap = isChecked
        }

        // Fix: Update dock side dynamically without stopping/restarting the service!
        rgDockSide.setOnCheckedChangeListener { _, checkedId ->
            val newSide = if (checkedId == R.id.rbLeftEdge) "LEFT" else "RIGHT"
            preferencesManager.dockSide = newSide
            if (preferencesManager.isServiceEnabled) {
                FloatingOverlayService.updateDockSide(this)
            }
        }

        btnCheckUpdates.setOnClickListener {
            checkUpdateManual()
        }

        btnVisitPortfolio.setOnClickListener {
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.infernodev.fun/")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            } catch (e: Exception) {
                Toast.makeText(this, "Opening portfolio: https://www.infernodev.fun/", Toast.LENGTH_SHORT).show()
            }
        }

        btnMessageDiscord.setOnClickListener {
            val discordUserId = "1316335421394780262"
            val discordUsername = "zx.fy"

            // Copy Discord username to clipboard for instant convenience
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Discord Username", discordUsername))
            Toast.makeText(this, "Discord: @$discordUsername (Copied to clipboard)", Toast.LENGTH_SHORT).show()

            // Open Discord profile / DM (redirects to Discord app if installed, else web browser)
            try {
                val discordIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/users/$discordUserId")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(discordIntent)
            } catch (e: Exception) {
                try {
                    val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://discord.com/users/$discordUserId"))
                    startActivity(webIntent)
                } catch (ex: Exception) {
                    Toast.makeText(this, "Discord: @$discordUsername", Toast.LENGTH_LONG).show()
                }
            }
        }

        etSearchApps.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                appAdapter.filter(s?.toString() ?: "")
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Request notification permission on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    private fun checkSilentUpdateOnLaunch() {
        lifecycleScope.launch {
            val update = updateManager.checkForUpdate()
            if (update != null) {
                UpdateDialog(this@MainActivity, update, updateManager).show()
            }
        }
    }

    private fun checkUpdateManual() {
        btnCheckUpdates.isEnabled = false
        tvAppVersionStatus.text = getString(R.string.update_checking)

        lifecycleScope.launch {
            val update = updateManager.checkForUpdate()
            btnCheckUpdates.isEnabled = true

            if (update != null) {
                tvAppVersionStatus.text = "Update Available: v${update.versionName} (${update.apkSize})"
                UpdateDialog(this@MainActivity, update, updateManager).show()
            } else {
                val currentVer = updateManager.getCurrentVersionName()
                val latestMsg = "You are on the latest version (v$currentVer)"
                tvAppVersionStatus.text = latestMsg
                Toast.makeText(this@MainActivity, latestMsg, Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun toggleSidebarService() {
        if (!preferencesManager.isServiceEnabled) {
            if (!Settings.canDrawOverlays(this)) {
                Toast.makeText(this, "Enable 'Display over other apps' to start Orbis", Toast.LENGTH_SHORT).show()
                requestOverlayPermission()
                updateServiceState()
                return
            }
            preferencesManager.isServiceEnabled = true
            FloatingOverlayService.start(this)
        } else {
            preferencesManager.isServiceEnabled = false
            FloatingOverlayService.stop(this)
        }
        updateServiceState()
    }

    private fun showPage(targetPage: View) {
        pageWorkspace.visibility = if (targetPage == pageWorkspace) View.VISIBLE else View.GONE
        pageApps.visibility = if (targetPage == pageApps) View.VISIBLE else View.GONE
        pageSettings.visibility = if (targetPage == pageSettings) View.VISIBLE else View.GONE
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        overlayPermissionLauncher.launch(intent)
    }

    private fun updatePermissionStates() {
        val hasOverlay = Settings.canDrawOverlays(this)
        bannerPermissions.visibility = if (hasOverlay) View.GONE else View.VISIBLE
    }

    private fun updateServiceState() {
        val isRunning = preferencesManager.isServiceEnabled && Settings.canDrawOverlays(this)
        switchSidebarToggle.isChecked = isRunning

        if (isRunning) {
            dotStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.success))
            tvStatusText.text = getString(R.string.status_active)
            tvStatusText.setTextColor(getColor(R.color.text_primary))
            tvSidebarToggleSubtitle.text = getString(R.string.sidebar_master_toggle_active)
        } else {
            dotStatus.backgroundTintList = android.content.res.ColorStateList.valueOf(getColor(R.color.text_muted))
            tvStatusText.text = getString(R.string.status_idle)
            tvStatusText.setTextColor(getColor(R.color.text_muted))
            tvSidebarToggleSubtitle.text = getString(R.string.sidebar_master_toggle_inactive)
        }
    }

    private fun updateEngineSummary() {
        tvCurrentEngine.text = getString(R.string.mode_zero_setup)
        tvEngineDesc.text = getString(R.string.mode_zero_setup_desc)
    }

    private fun updatePinnedCount() {
        val count = preferencesManager.getSelectedPackages().size
        tvPinnedCount.text = getString(R.string.selected_apps_count, count)
    }

    private fun setupSplashScreen() {
        val ivSplashLogo = findViewById<ImageView>(R.id.ivSplashLogo)
        val tvSplashTitle = findViewById<TextView>(R.id.tvSplashTitle)
        val tvSplashTagline = findViewById<TextView>(R.id.tvSplashTagline)
        val tvSplashVersion = findViewById<TextView>(R.id.tvSplashVersion)

        tvSplashVersion.text = "v${updateManager.getCurrentVersionName()}"

        ivSplashLogo.alpha = 0f
        ivSplashLogo.scaleX = 0.75f
        ivSplashLogo.scaleY = 0.75f

        tvSplashTitle.alpha = 0f
        tvSplashTitle.translationY = 24f

        tvSplashTagline.alpha = 0f
        tvSplashTagline.translationY = 24f

        ivSplashLogo.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(550)
            .setInterpolator(android.view.animation.DecelerateInterpolator())
            .start()

        tvSplashTitle.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(180)
            .setDuration(450)
            .start()

        tvSplashTagline.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(300)
            .setDuration(450)
            .start()
    }

    private fun dismissSplashScreen() {
        layoutSplash.animate()
            .alpha(0f)
            .setDuration(350)
            .withEndAction {
                layoutSplash.visibility = View.GONE
            }
            .start()
    }

    private fun loadInstalledApps() {
        lifecycleScope.launch {
            val startTime = System.currentTimeMillis()
            val apps = appRepository.getInstalledApps()
            appAdapter.submitList(apps)
            updatePinnedCount()
            refreshQuickLaunch()

            val elapsed = System.currentTimeMillis() - startTime
            val remaining = (1200 - elapsed).coerceAtLeast(0)
            delay(remaining)

            dismissSplashScreen()
        }
    }

    private fun refreshQuickLaunch() {
        lifecycleScope.launch {
            val pinnedApps = appRepository.getSelectedApps()
            quickLaunchAdapter.submitList(pinnedApps)
        }
    }

    companion object {
        private const val SHIZUKU_PERMISSION_CODE = 8001
    }
}
