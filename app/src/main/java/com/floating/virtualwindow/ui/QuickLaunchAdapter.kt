package com.floating.virtualwindow.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.floating.virtualwindow.R
import com.floating.virtualwindow.data.AppInfo

class QuickLaunchAdapter(
    private val onAppTapped: (AppInfo) -> Unit
) : RecyclerView.Adapter<QuickLaunchAdapter.QuickViewHolder>() {

    private var apps: List<AppInfo> = emptyList()

    fun submitList(newApps: List<AppInfo>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): QuickViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_quick_launch, parent, false)
        return QuickViewHolder(view)
    }

    override fun onBindViewHolder(holder: QuickViewHolder, position: Int) {
        val app = apps[position]
        holder.bind(app)
    }

    override fun getItemCount(): Int = apps.size

    inner class QuickViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val ivIcon: ImageView = itemView.findViewById(R.id.ivQuickIcon)
        private val tvName: TextView = itemView.findViewById(R.id.tvQuickName)

        fun bind(app: AppInfo) {
            tvName.text = app.appName
            if (app.icon != null) {
                ivIcon.setImageDrawable(app.icon)
            } else {
                ivIcon.setImageResource(R.mipmap.ic_launcher)
            }
            itemView.setOnClickListener {
                onAppTapped(app)
            }
        }
    }
}
