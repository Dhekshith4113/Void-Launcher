package com.example.voidui

import android.content.pm.ApplicationInfo
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.RecyclerView

class AppToggleAdapter(
    private var apps: List<ApplicationInfo>,
    private val onToggleChanged: () -> Unit
) : RecyclerView.Adapter<AppToggleAdapter.ViewHolder>() {

    fun updateData(newApps: List<ApplicationInfo>) {
        this.apps = newApps
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_app_toggle, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val appInfo = apps[position]
        val context = holder.itemView.context
        val pm = context.packageManager
        val packageName = appInfo.packageName

        holder.appName.text = appInfo.loadLabel(pm)

        // Remove previous listener before setting new one to avoid unwanted callbacks
        holder.appSwitch.setOnCheckedChangeListener(null)

        // Set the saved toggle state
        val isChecked = SharedPreferencesManager.isAppTimerEnabled(context, packageName)
        holder.appSwitch.isChecked = isChecked

        // Set new listener
        holder.appSwitch.setOnCheckedChangeListener { _, newState ->
            SharedPreferencesManager.setAppTimerEnabled(context, packageName, newState)
            onToggleChanged() // Notify parent to refresh the list
        }
    }

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val appName: TextView = view.findViewById(R.id.appNameToggle)
        val appSwitch: SwitchCompat = view.findViewById(R.id.appSwitch)
    }
}
