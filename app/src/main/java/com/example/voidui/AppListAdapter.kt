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
import android.graphics.drawable.GradientDrawable
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
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel

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

    // Add this method to your AppListAdapter class
    private fun loadThemedIcon(app: ApplicationInfo, holder: ViewHolder) {
        try {
            // First, try to get the monochrome icon
            val monochromeIcon = getMonochromeIcon(app)

            if (monochromeIcon != null) {
                // Apply themed icon styling
                applyThemedIconStyling(holder, monochromeIcon)
            } else {
                // Fallback to regular icon
                holder.icon.setImageDrawable(app.loadIcon(pm))
                // Reset any previous themed styling
                resetIconStyling(holder)
            }
        } catch (e: Exception) {
            // Fallback to regular icon if anything goes wrong
            holder.icon.setImageDrawable(app.loadIcon(pm))
            resetIconStyling(holder)
        }
    }

    @TargetApi(Build.VERSION_CODES.TIRAMISU)
    private fun getMonochromeIcon(app: ApplicationInfo): Drawable? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // For Android 13+ (API 33+), try to get adaptive icon
                val icon = pm.getApplicationIcon(app)
                if (icon is AdaptiveIconDrawable) {
                    // Try to get monochrome layer
                    val monochromeDrawable = icon.monochrome
                    if (monochromeDrawable != null) {
                        return monochromeDrawable
                    }
                }
            }

            // Fallback method: try to get monochrome resource directly
            val resources = pm.getResourcesForApplication(app)
            val monochromeId = resources.getIdentifier("ic_launcher_monochrome", "drawable", app.packageName)

            if (monochromeId != 0) {
                return ContextCompat.getDrawable(context, monochromeId)
            }

            null
        } catch (e: Exception) {
            null
        }
    }

    private fun applyThemedIconStyling(holder: ViewHolder, monochromeIcon: Drawable) {
        // Create a themed background
        val backgroundDrawable = if (SharedPreferencesManager.getAppIconShape(context) == "round") {
            ContextCompat.getDrawable(context, R.drawable.themed_icon_background_rounded)?: createThemedBackground()
        } else {
            ContextCompat.getDrawable(context, R.drawable.squricle_512_271)?: createThemedBackground()
        }
        backgroundDrawable.setTint(ContextCompat.getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(ContextCompat.getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        holder.icon.setImageDrawable(layerDrawable)

        // Apply squircle corner radius to match the background
        holder.icon.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.22f))
            .build()
    }

    private fun resetIconStyling(holder: ViewHolder) {
        // Reset to default appearance
        holder.icon.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(RelativeCornerSize(0.22f))
            .build()
    }

    private fun createThemedBackground(): Drawable {
        // Create a programmatic background if you don't have a drawable resource
        val shape = GradientDrawable()
        shape.shape = GradientDrawable.OVAL
        shape.setColor(ContextCompat.getColor(context, R.color.themed_icon_background))
        return shape
    }

    // Update your onBindViewHolder method
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val app = apps[position]

        holder.name.text = MainActivity().normalizeAppName(app.loadLabel(pm).toString())
        holder.icon.setBackgroundResource(0)

        // Check if themed icons are enabled (you might want to add this preference)
        if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
            loadThemedIcon(app, holder)
        } else {
            holder.icon.setImageDrawable(app.loadIcon(pm))
            resetIconStyling(holder)
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
            val shadow = AppIconDragShadowBuilder(context, app)
            it.startDragAndDrop(clipData, shadow, app, 0)
            onAppDragStarted?.invoke(app)
            true
        }
    }

    private fun showAppOptionsDialog(context: Context, appInfo: ApplicationInfo) {
        val packageManager = context.packageManager
        val appName = MainActivity().normalizeAppName(appInfo.loadLabel(packageManager).toString())
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