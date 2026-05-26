package com.example.allin

import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

data class LockedApp(
    val packageName: String,
    val name: String,
    val icon: Drawable,
    val isActive: Boolean = true
)

class LockedAppAdapter(
    var apps: List<LockedApp> = emptyList(),
    private val onToggle: (String, Boolean) -> Unit,
    private val onMoreClick: (View, LockedApp) -> Unit
) : RecyclerView.Adapter<LockedAppAdapter.ViewHolder>() {

    fun updateData(newApps: List<LockedApp>) {
        apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_locked_app, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.ivIcon.setImageDrawable(app.icon)
        holder.tvName.text = app.name
        holder.swLock.setOnCheckedChangeListener(null)
        holder.swLock.isChecked = app.isActive
        holder.swLock.setOnCheckedChangeListener { _, isChecked ->
            onToggle(app.packageName, isChecked)
        }
        holder.btnOptions.setOnClickListener { view ->
            onMoreClick(view, app)
        }
    }

    override fun getItemCount() = apps.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val swLock: SwitchCompat = view.findViewById(R.id.swItemLock)
        val btnOptions: ImageView = view.findViewById(R.id.btnAppOptions)
    }
}
