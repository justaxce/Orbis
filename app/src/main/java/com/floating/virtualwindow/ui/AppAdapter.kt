package com.floating.virtualwindow.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.AppInfo
import com.google.android.material.checkbox.MaterialCheckBox

class AppAdapter(
    private val onAppSelectionChanged: (AppInfo, Boolean) -> Unit
) : RecyclerView.Adapter<AppAdapter.AppViewHolder>() {

    private var allApps: List<AppInfo> = emptyList()
    private var displayedApps: List<AppInfo> = emptyList()

    fun submitList(apps: List<AppInfo>) {
        allApps = apps
        displayedApps = apps
        notifyDataSetChanged()
    }

    fun filter(query: String) {
        displayedApps = if (query.isBlank()) {
            allApps
        } else {
            val lower = query.lowercase().trim()
            allApps.filter {
                it.appName.lowercase().contains(lower) || it.packageName.lowercase().contains(lower)
            }
        }
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_selection, parent, false)
        return AppViewHolder(view)
    }

    override fun onBindViewHolder(holder: AppViewHolder, position: Int) {
        val app = displayedApps[position]
        holder.bind(app)
    }

    override fun getItemCount(): Int = displayedApps.size

    inner class AppViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivAppIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvAppName)
        private val tvPackage: TextView = itemView.findViewById(R.id.tvPackageName)
        private val cbSelect: MaterialCheckBox = itemView.findViewById(R.id.cbSelectApp)

        fun bind(app: AppInfo) {
            tvName.text = app.appName
            tvPackage.text = app.packageName
            if (app.icon != null) {
                ivIcon.setImageDrawable(app.icon)
            } else {
                ivIcon.setImageResource(R.mipmap.ic_launcher)
            }

            cbSelect.setOnCheckedChangeListener(null)
            cbSelect.isChecked = app.isSelected

            cbSelect.setOnCheckedChangeListener { _, isChecked ->
                app.isSelected = isChecked
                onAppSelectionChanged(app, isChecked)
            }

            itemView.setOnClickListener {
                cbSelect.isChecked = !cbSelect.isChecked
            }
        }
    }
}
