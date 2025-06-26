package com.example.voidui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Point
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView

class AppListAdapter(
    private val context: Context,
    private var apps: MutableList<ApplicationInfo>,
    private val pm: PackageManager,
    val refreshList: () -> Unit,
    var onAppDragStarted: ((ApplicationInfo) -> Unit)? = null,
    private val onAppClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppListAdapter.ViewHolder>() {

    private var newAppPackages: Set<String> = emptySet()

    companion object {
        private const val VIEW_TYPE_LIST = 0
        private const val VIEW_TYPE_GRID = 1
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val icon: ShapeableImageView = itemView.findViewById(R.id.appIcon)
        val name: TextView = itemView.findViewById(R.id.appName)
        val newAppName: TextView? = itemView.findViewById(R.id.newText)

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

    override fun getItemViewType(position: Int): Int {
        return if (SharedPreferencesManager.isAppDrawerEnabled(context)) VIEW_TYPE_GRID else VIEW_TYPE_LIST
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layoutId = if (viewType == VIEW_TYPE_GRID) R.layout.item_app_grid else R.layout.item_app
        val view = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = apps.size

    private fun loadThemedIcon(app: ApplicationInfo): Drawable {
        return try {
            val monochromeIcon = getMonochromeIcon(app)
            if (monochromeIcon != null) {
                applyThemedIconStyling(monochromeIcon)
            } else {
                // Fallback to regular icon
                app.loadIcon(pm)
            }
        } catch (e: Exception) {
            app.loadIcon(pm)
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun getMonochromeIcon(app: ApplicationInfo): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val icon = pm.getApplicationIcon(app)
                if (icon is AdaptiveIconDrawable) {
                    icon.monochrome
                } else null
            } else {
                null
            } ?: run {
                // Fallback: Check if app has ic_launcher_monochrome drawable
                val resources = pm.getResourcesForApplication(app)
                val id = resources.getIdentifier("ic_launcher_monochrome", "drawable", app.packageName)
                if (id != 0) ContextCompat.getDrawable(context, id) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun applyThemedIconStyling(monochromeIcon: Drawable): Drawable {
        // Create a themed background
        val backgroundDrawable = if (SharedPreferencesManager.getAppIconShape(context) == "round") {
            ContextCompat.getDrawable(context, R.drawable.themed_icon_background_rounded)
        } else {
            ContextCompat.getDrawable(context, R.drawable.squricle_512_271)
        }
        backgroundDrawable?.setTint(ContextCompat.getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(ContextCompat.getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        return layerDrawable
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        holder.name.text = MainActivity().normalizeAppName(app.loadLabel(pm).toString())
        holder.icon.setBackgroundResource(0)

        if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
            holder.icon.setImageDrawable(loadThemedIcon(app))
        } else {
            holder.icon.setImageDrawable(app.loadIcon(pm))
        }

        if (SharedPreferencesManager.isAppDrawerEnabled(context)) {
            holder.icon.visibility = View.VISIBLE
            if (SharedPreferencesManager.isHideAppNameEnabled(context)) {
                holder.name.text = ""
            }
        } else {
            if (SharedPreferencesManager.isShowAppIconEnabled(context)) {
                holder.icon.visibility = View.VISIBLE
            } else {
                holder.icon.visibility = View.GONE
            }
        }

        if (newAppPackages.contains(app.packageName)) {
            holder.newAppName?.visibility = View.VISIBLE
        } else {
            holder.newAppName?.visibility = View.GONE
        }

        holder.itemView.setOnLongClickListener {
            val clipData = ClipData.newPlainText("packageName", app.packageName)
            val shadow = AppIconDragShadowBuilder(context, app, pm)
            it.startDragAndDrop(clipData, shadow, app, 0)
            onAppDragStarted?.invoke(app)
            true
        }
    }

    private fun showAppOptionsDialog(context: Context, appInfo: ApplicationInfo) {
        val packageManager = context.packageManager
        val appName = MainActivity().normalizeAppName(appInfo.loadLabel(packageManager).toString())

        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_app_options, null)
        val nameTextView = dialogView.findViewById<TextView>(R.id.appNameText)
        val iconImageView = dialogView.findViewById<ImageView>(R.id.appIconImage)

        nameTextView.text = appName
        if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
            iconImageView.setImageDrawable(loadThemedIcon(appInfo))
        } else {
            iconImageView.setImageDrawable(appInfo.loadIcon(pm))
        }

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
            apps.sortBy { MainActivity().normalizeAppName(it.loadLabel(pm).toString()).lowercase() }
            notifyDataSetChanged()
        }
    }

    fun removeApp(app: ApplicationInfo) {
        apps.removeAll { it.packageName == app.packageName }
        notifyDataSetChanged()
    }

    fun getApps(): List<ApplicationInfo> {
        apps.sortBy { MainActivity().normalizeAppName(it.loadLabel(pm).toString()).lowercase() }
        return apps.toList()
    }

}

class AppIconDragShadowBuilder(val context: Context, appInfo: ApplicationInfo, private val pm: PackageManager) : View.DragShadowBuilder() {

    private val icon: Drawable

    init {
        val pm = context.packageManager
        icon = if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
            loadThemedIcon(appInfo)
        } else appInfo.loadIcon(pm)
        icon.setBounds(0, 0, 48.dp, 48.dp)
    }

    override fun onProvideShadowMetrics(size: Point, touch: Point) {
        size.set(48.dp, 48.dp)
        touch.set(size.x / 2, size.y / 2)
    }

    override fun onDrawShadow(canvas: Canvas) {
        icon.draw(canvas)
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    private fun loadThemedIcon(app: ApplicationInfo): Drawable {
        return try {
            val monochromeIcon = getMonochromeIcon(app)
            if (monochromeIcon != null) {
                applyThemedIconStyling(monochromeIcon)
            } else {
                // Fallback to regular icon
                app.loadIcon(pm)
            }
        } catch (e: Exception) {
            app.loadIcon(pm)
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun getMonochromeIcon(app: ApplicationInfo): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val icon = pm.getApplicationIcon(app)
                if (icon is AdaptiveIconDrawable) {
                    icon.monochrome
                } else null
            } else {
                null
            } ?: run {
                // Fallback: Check if app has ic_launcher_monochrome drawable
                val resources = pm.getResourcesForApplication(app)
                val id = resources.getIdentifier("ic_launcher_monochrome", "drawable", app.packageName)
                if (id != 0) ContextCompat.getDrawable(context, id) else null
            }
        } catch (e: Exception) {
            null
        }
    }

    private fun applyThemedIconStyling(monochromeIcon: Drawable): Drawable {
        // Create a themed background
        val backgroundDrawable = if (SharedPreferencesManager.getAppIconShape(context) == "round") {
            ContextCompat.getDrawable(context, R.drawable.themed_icon_background_rounded)
        } else {
            ContextCompat.getDrawable(context, R.drawable.squricle_512_271)
        }
        backgroundDrawable?.setTint(ContextCompat.getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(ContextCompat.getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        return layerDrawable
    }
}