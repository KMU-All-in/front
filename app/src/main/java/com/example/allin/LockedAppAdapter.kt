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
    val icon: Drawable
)

class LockedAppAdapter(
    private var apps: List<LockedApp>,
    private val onToggle: (String, Boolean) -> Unit
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
        holder.swLock.isChecked = true // 리스트에 있다는 건 잠겨있다는 뜻

        holder.swLock.setOnCheckedChangeListener { _, isChecked ->
            onToggle(app.packageName, isChecked)
        }
    }

    override fun getItemCount() = apps.size

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val ivIcon: ImageView = view.findViewById(R.id.ivAppIcon)
        val tvName: TextView = view.findViewById(R.id.tvAppName)
        val swLock: SwitchCompat = view.findViewById(R.id.swItemLock)
    }
}
