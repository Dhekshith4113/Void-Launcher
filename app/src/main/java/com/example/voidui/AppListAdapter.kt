package com.example.voidui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class AppListAdapter(
    private val context: Context,
    private var apps: MutableList<ApplicationInfo>,
    private val pm: PackageManager,
    val refreshList: () -> Unit,
    var onAppDragStarted: ((ApplicationInfo) -> Unit)? = null,
    private val onAppClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private var newAppPackages: Set<String> = emptySet()

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.appName)
        val newAppName: TextView = itemView.findViewById(R.id.newText)

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val app = apps[adapterPosition]
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
                showAppOptionsDialog(context, apps[adapterPosition])
                return true
            }
        })

        init {
            itemView.setOnTouchListener { _, event ->
                gestureDetector.onTouchEvent(event)
                false
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]
        holder.name.text = app.loadLabel(pm)

        if (newAppPackages.contains(app.packageName)) {
            holder.newAppName.visibility = View.VISIBLE
        } else {
            holder.newAppName.visibility = View.GONE
        }

        holder.itemView.setOnLongClickListener {
            val clipData = ClipData.newPlainText("packageName", app.packageName)
            val shadow = AppIconDragShadowBuilder(context, app)
            it.startDragAndDrop(clipData, shadow, app, 0)
            onAppDragStarted?.invoke(app)
            true
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
            val packageUri = Uri.parse("package:${appInfo.packageName}") // Replace with target package
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
        this.apps = newApps
        notifyDataSetChanged()
    }

    fun setApps(newApps: Set<String>) {
        this.newAppPackages = newApps
        notifyDataSetChanged()
    }

    fun addApp(app: ApplicationInfo) {
        if (apps.none { it.packageName == app.packageName }) {
            apps.add(app)
            apps.sortBy { it.loadLabel(pm).toString().lowercase() }
            notifyDataSetChanged()
        }
    }

    fun removeApp(app: ApplicationInfo) {
        apps.removeAll { it.packageName == app.packageName }
        notifyDataSetChanged()
    }

    fun getApps(): List<ApplicationInfo> {
        apps.sortBy { it.loadLabel(pm).toString().lowercase() }
        return apps.toList()
    }

}

class AppIconDragShadowBuilder(val context: Context, appInfo: ApplicationInfo) :
    View.DragShadowBuilder() {

    private val icon: Drawable
    private val sizeInDP: Int = 48

    init {
        val pm = context.packageManager
        icon = appInfo.loadIcon(pm)
        icon.setBounds(0, 0, sizeInDP.toPx(context), sizeInDP.toPx(context))
    }

    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        size.set(sizeInDP.toPx(context), sizeInDP.toPx(context))
        touch.set(size.x / 2, size.y / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        icon.draw(canvas)
    }

    private fun Int.toPx(context: Context): Int {
        return (this * context.resources.displayMetrics.density).toInt()
    }

}
