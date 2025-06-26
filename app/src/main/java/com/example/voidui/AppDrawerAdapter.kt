package com.example.voidui

import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.AlertDialog
import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.GestureDetector
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.content.ContextCompat.getColor
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.imageview.ShapeableImageView
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel

class AppDrawerAdapter(
    private val context: Context,
    private val pm: PackageManager,
    private var appList: MutableList<ApplicationInfo>,
    private val onSave: (List<ApplicationInfo>) -> Unit,
    val refreshList: () -> Unit,
    var onAppDragStarted: ((ApplicationInfo) -> Unit)? = null,
    private val onAppClick: (ApplicationInfo) -> Unit
) : RecyclerView.Adapter<AppDrawerAdapter.ViewHolder>() {

    private val drawerAppSize: Int = SharedPreferencesManager.getMiniAppDrawerCount(context)
    private var parent: RecyclerView? = null
    private var spacingDecoration: CenterSpacingDecoration? = null

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        parent = recyclerView

        // Find the spacing decoration
        for (i in 0 until recyclerView.itemDecorationCount) {
            val decoration = recyclerView.getItemDecorationAt(i)
            if (decoration is CenterSpacingDecoration) {
                spacingDecoration = decoration
                break
            }
        }
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        parent = null
        spacingDecoration = null
    }

    companion object {
        const val DROP_INDICATOR_PACKAGE = "__DROP_INDICATOR__"
        private var dropIndicatorItem: ApplicationInfo? = null

        fun getDropIndicatorItem(): ApplicationInfo {
            if (dropIndicatorItem == null) {
                dropIndicatorItem = ApplicationInfo().apply {
                    packageName = DROP_INDICATOR_PACKAGE
                }
            }
            return dropIndicatorItem!!
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val miniDrawerLayout: LinearLayout = view.findViewById(R.id.miniDrawerLayout)
        val icon: ShapeableImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)

        private val gestureDetector = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION || position >= appList.size) return false

                val app = appList[position]
                if (app.packageName == DROP_INDICATOR_PACKAGE) return false

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
                val position = adapterPosition
                if (position == RecyclerView.NO_POSITION || position >= appList.size) return false

                val app = appList[position]
                if (app.packageName != DROP_INDICATOR_PACKAGE) {
                    showAppOptionsDialog(context, app)
                }
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
        return if (position < appList.size && appList[position].packageName == DROP_INDICATOR_PACKAGE) 1 else 0
    }

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
        if (position >= appList.size) return

        val app = appList[position]

        if (app.packageName == DROP_INDICATOR_PACKAGE) {
            holder.icon.setImageResource(0)
            if (SharedPreferencesManager.getAppIconShape(context) == "round") {
                holder.icon.setBackgroundResource(R.drawable.drop_indicator_round)
            } else {
                holder.icon.setBackgroundResource(R.drawable.drop_indicator)
            }
            holder.name.text = ""

            if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                holder.name.visibility = View.VISIBLE
                holder.miniDrawerLayout.setPadding(0, 6.dp, 0, 6.dp)
            } else {
                holder.name.visibility = View.GONE
                holder.miniDrawerLayout.setPadding(0, 18.dp, 0, 0)
            }
            holder.itemView.setOnLongClickListener(null)
        } else {
            holder.name.text = MainActivity().normalizeAppName(app.loadLabel(pm).toString())
            holder.icon.setBackgroundResource(0)

            if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
                holder.icon.setImageDrawable(loadThemedIcon(app))
            } else {
                holder.icon.setImageDrawable(app.loadIcon(pm))
            }

            if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                holder.name.visibility = View.VISIBLE
                holder.miniDrawerLayout.setPadding(0, 6.dp, 0, 6.dp)
            } else {
                holder.name.visibility = View.GONE
                holder.miniDrawerLayout.setPadding(0, 18.dp, 0, 18.dp)
            }

            holder.itemView.setOnLongClickListener {
                val clipData = ClipData.newPlainText("packageName", app.packageName)
                val shadow = View.DragShadowBuilder(it)
                it.startDragAndDrop(clipData, shadow, app, 0)
                onAppDragStarted?.invoke(app)
                true
            }
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

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

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun hasDropIndicator(): Boolean {
        return appList.any { it.packageName == DROP_INDICATOR_PACKAGE }
    }

    fun insertDropIndicator(position: Int) {
        if (hasDropIndicator()) return

        val safePosition = position.coerceIn(0, appList.size)
        appList.add(safePosition, getDropIndicatorItem())
        notifyItemInserted(safePosition)

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun moveDropIndicator(toPosition: Int) {
        val dropIndex = appList.indexOfFirst { it.packageName == DROP_INDICATOR_PACKAGE }
        if (dropIndex == -1) return

        val safePosition = toPosition.coerceIn(0, appList.size - 1)
        if (dropIndex == safePosition) return

        // Use notifyItemMoved for smooth animation
        appList.removeAt(dropIndex)
        appList.add(safePosition, getDropIndicatorItem())
        notifyItemMoved(dropIndex, safePosition)

        // Force spacing recalculation after animation
        parent?.postDelayed({
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }, 250) // Wait for animation to complete
    }

    fun removeDropIndicator() {
        val index = appList.indexOfFirst { it.packageName == DROP_INDICATOR_PACKAGE }
        if (index == -1) return

        appList.removeAt(index)
        notifyItemRemoved(index)

        // Force spacing recalculation after removal
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun addAppAtPosition(app: ApplicationInfo, position: Int) {
        if (appList.any { it.packageName == app.packageName } ||
            appList.count { it.packageName != DROP_INDICATOR_PACKAGE } >= drawerAppSize) {
            return
        }

        val safePosition = position.coerceIn(0, appList.size)
        appList.add(safePosition, app)
        notifyItemInserted(safePosition)
        onSave(appList.filter { it.packageName != DROP_INDICATOR_PACKAGE })

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun removeApp(app: ApplicationInfo) {
        val indices = appList.mapIndexedNotNull { index, appInfo ->
            if (appInfo.packageName == app.packageName) index else null
        }

        indices.reversed().forEach { index ->
            appList.removeAt(index)
            notifyItemRemoved(index)
        }

        // Force spacing recalculation
        parent?.post {
            spacingDecoration?.invalidateSpacing()
            parent?.invalidateItemDecorations()
        }
    }

    fun getApps(): List<ApplicationInfo> = appList.toList()
}