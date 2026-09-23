package com.floating.virtualwindow.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.os.Build
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.AppInfo
import com.floating.virtualwindow.data.AppRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@SuppressLint("ClickableViewAccessibility")
class AllAppsDrawerView(
    private val context: Context,
    private val windowManager: WindowManager,
    private val onAppSelected: (packageName: String, appName: String, icon: Drawable?) -> Unit
) {

    val view: View = LayoutInflater.from(context).inflate(R.layout.view_all_apps_drawer, null)
    private val layoutParams: WindowManager.LayoutParams
    private val appRepository = AppRepository(context)
    private val scope = CoroutineScope(Dispatchers.Main)

    private val etSearch: EditText = view.findViewById(R.id.etSearchAllApps)
    private val rvAllApps: RecyclerView = view.findViewById(R.id.rvAllApps)
    private val btnClose: View = view.findViewById(R.id.btnCloseAllApps)

    private var allAppsList = listOf<AppInfo>()
    private var filteredList = mutableListOf<AppInfo>()
    private val adapter: AllAppsAdapter

    init {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val displayMetrics = context.resources.displayMetrics
        val drawerWidth = (displayMetrics.widthPixels * 0.85).toInt().coerceAtMost(480)
        val drawerHeight = (displayMetrics.heightPixels * 0.65).toInt().coerceAtMost(650)

        layoutParams = WindowManager.LayoutParams(
            drawerWidth,
            drawerHeight,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        rvAllApps.layoutManager = GridLayoutManager(context, 4)
        adapter = AllAppsAdapter()
        rvAllApps.adapter = adapter

        btnClose.setOnClickListener {
            hide()
        }

        view.setOnTouchListener { _, event ->
            if (event.action == android.view.MotionEvent.ACTION_OUTSIDE) {
                hide()
                true
            } else {
                false
            }
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                filterApps(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })
    }

    private fun filterApps(query: String) {
        val q = query.trim().lowercase()
        filteredList.clear()
        if (q.isEmpty()) {
            filteredList.addAll(allAppsList)
        } else {
            for (app in allAppsList) {
                if (app.appName.lowercase().contains(q) || app.packageName.lowercase().contains(q)) {
                    filteredList.add(app)
                }
            }
        }
        adapter.notifyDataSetChanged()
    }

    fun show() {
        scope.launch {
            val apps = appRepository.getInstalledApps()
            withContext(Dispatchers.Main) {
                allAppsList = apps
                filterApps(etSearch.text.toString())
                if (view.parent == null) {
                    try {
                        windowManager.addView(view, layoutParams)
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }
    }

    fun hide() {
        if (view.parent != null) {
            try {
                windowManager.removeView(view)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private inner class AllAppsAdapter : RecyclerView.Adapter<AllAppsAdapter.AppViewHolder>() {

        inner class AppViewHolder(val itemView: View) : RecyclerView.ViewHolder(itemView) {
            val ivIcon: ImageView = itemView.findViewById(R.id.ivSidebarIcon)
            val tvName: TextView = itemView.findViewById(R.id.tvSidebarName)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
            val v = LayoutInflater.from(context).inflate(R.layout.item_sidebar_app, parent, false)
            return AppViewHolder(v)
        }

        override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
            val app = filteredList[position]
            holder.tvName.text = app.appName
            val displayIcon = app.icon ?: com.floating.virtualwindow.data.WebIconHelper.getIconForApp(context, app.appName, app.packageName)
            holder.ivIcon.setImageDrawable(displayIcon)

            holder.itemView.setOnClickListener {
                onAppSelected(app.packageName, app.appName, displayIcon)
                hide()
            }
        }

        override fun getItemCount(): Int = filteredList.size
    }
}
