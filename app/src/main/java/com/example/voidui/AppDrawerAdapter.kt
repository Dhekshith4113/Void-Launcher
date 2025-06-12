package com.example.voidui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppDrawerAdapter(
    private val context: Context,
    private val pm: PackageManager,
    private var appList: MutableList<ApplicationInfo>,
    private val onSave: (List<ApplicationInfo>) -> Unit,
    val refreshList: () -> Unit,
    var onAppDragStarted: ((ApplicationInfo) -> Unit)? = null,
    private val onAppClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>() {

    private val drawerAppSize: Int = MainActivity().drawerSize

    companion object {
        const val DROP_INDICATOR_PACKAGE = "__DROP_INDICATOR__"

        fun getDropIndicatorItem(): ApplicationInfo {
            val dummy = ApplicationInfo()
            dummy.packageName = DROP_INDICATOR_PACKAGE
            return dummy
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.appIcon)

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val app = appList[adapterPosition]
                val globalToggle = SharedPreferencesManager.isGlobalTimerEnabled(context)
                val appToggle = SharedPreferencesManager.isAppToggleEnabled(context, app.packageName)

                if (globalToggle && appToggle) {
                    onAppClick(app)
                } else {
                    val intent = pm.getLaunchIntentForPackage(app.packageName)
                    if (intent != null) {
                        context.startActivity(intent)
                    }
                }
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                showAppOptionsDialog(context, appList[adapterPosition])
                return true
            }
        })

        init {
            view.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(context).inflate(R.layout.item_icon_only, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = appList.size

    override fun getItemViewType(position: Int): Int {
        return if (appList[position].packageName == DROP_INDICATOR_PACKAGE) 1 else 0
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = appList[position]
        if (app.packageName == DROP_INDICATOR_PACKAGE) {
            holder.icon.setImageResource(0)
            holder.icon.setBackgroundResource(R.drawable.drop_indicator)
            holder.itemView.setOnLongClickListener(null)
        } else {
            holder.icon.setBackgroundResource(0)
            holder.icon.setImageDrawable(app.loadIcon(pm))
            holder.itemView.setOnLongClickListener {
                val clipData = ClipData.newPlainText("packageName", app.packageName)
                val shadow = View.DragShadowBuilder(it)
                it.startDragAndDrop(clipData, shadow, app, 0)
                onAppDragStarted?.invoke(app) // Notify removal if dragging from drawer
                true
            }
        }
    }

    fun insertDropIndicator(position: Int) {
        if (appList.any { it.packageName == DROP_INDICATOR_PACKAGE }) return
        val dummy = ApplicationInfo().apply { packageName = DROP_INDICATOR_PACKAGE }
        appList.add(position, dummy)
        notifyItemInserted(position)
    }

    fun moveDropIndicator(toPosition: Int) {
        val dropIndex = appList.indexOfFirst { it.packageName == DROP_INDICATOR_PACKAGE }

        if (dropIndex == toPosition) return // already at right spot

        if (dropIndex != -1) {
            appList.removeAt(dropIndex)
            notifyItemRemoved(dropIndex)
        }

        val safePosition = toPosition.coerceIn(0, appList.size)
        appList.add(safePosition, getDropIndicatorItem())
        notifyItemInserted(safePosition)
    }

    fun removeDropIndicator() {
        val index = appList.indexOfFirst { it.packageName == DROP_INDICATOR_PACKAGE }
        if (index != -1) {
            appList.removeAt(index)
            notifyItemRemoved(index)

            // Force sync
            Handler(Looper.getMainLooper()).post {
                notifyDataSetChanged()
            }
        }
    }

    private fun showAppOptionsDialog(context: Context, appInfo: ApplicationInfo) {
        val packageManager = context.packageManager
        val appName = appInfo.loadLabel(packageManager).toString()
        val appIcon = appInfo.loadIcon(packageManager)

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_options, null)
        val nameTextView = dialogView.findViewById<TextView>(R.id.appNameText)
        val iconImageView = dialogView.findViewById<ImageView>(R.id.appIconImage)

        nameTextView.text = appName
        iconImageView.setImageDrawable(appIcon)

        val dialog = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialogView.findViewById<TextView>(R.id.uninstallBtn).setOnClickListener {
            val packageUri = Uri.parse("package:${appInfo.packageName}")
            val intent = Intent(Intent.ACTION_DELETE, packageUri)
            context.startActivity(intent)
            refreshList()
            dialog.dismiss()
        }

        dialogView.findViewById<TextView>(R.id.appInfoBtn).setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = Uri.parse("package:${appInfo.packageName}")
            context.startActivity(intent)
            dialog.dismiss()
        }

        dialog.show()
    }

    fun updateData(newApps: MutableList<ApplicationInfo>) {
        this.appList = newApps
        notifyDataSetChanged()
    }

    fun addAppAtPosition(app: ApplicationInfo, position: Int) {
        if (appList.any { it.packageName == app.packageName } || appList.size >= drawerAppSize) return
        appList.add(position.coerceIn(0, appList.size), app)
        notifyItemInserted(position)
        onSave(appList)
    }

    fun removeApp(app: ApplicationInfo) {
        appList.removeAll { it.packageName == app.packageName }
        notifyDataSetChanged()
    }

    fun getApps(): List<ApplicationInfo> = appList

}