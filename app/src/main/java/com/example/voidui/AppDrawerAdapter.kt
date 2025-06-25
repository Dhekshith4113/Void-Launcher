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

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        parent = recyclerView
    }

    override fun onDetachedFromRecyclerView(recyclerView: RecyclerView) {
        super.onDetachedFromRecyclerView(recyclerView)
        parent = null
    }

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
        val miniDrawerLayout: LinearLayout = view.findViewById(R.id.miniDrawerLayout)
        val icon: ShapeableImageView = view.findViewById(R.id.appIcon)
        val name: TextView = view.findViewById(R.id.appName)

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

    // Add this method to your AppListAdapter class
    private fun loadThemedIcon(app: ApplicationInfo, holder: AppDrawerAdapter.ViewHolder) {
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

    private fun applyThemedIconStyling(holder: AppDrawerAdapter.ViewHolder, monochromeIcon: Drawable) {
        // Create a themed background
        val backgroundDrawable = if (SharedPreferencesManager.getAppIconShape(context) == "round") {
            ContextCompat.getDrawable(context, R.drawable.themed_icon_background_rounded)?: createThemedBackground()
        } else {
            ContextCompat.getDrawable(context, R.drawable.squricle_512_271)?: createThemedBackground()
        }
        backgroundDrawable.setTint(getColor(context, R.color.themed_icon_background))

        // Tint the monochrome icon with your desired color
        val tintedIcon = monochromeIcon.mutate()
        tintedIcon.setTint(getColor(context, R.color.themed_icon_foreground))

        // Create layered drawable with background and scaled foreground
        val layerDrawable = LayerDrawable(arrayOf(backgroundDrawable, tintedIcon))

        // Use negative padding to make the icon extend beyond the background bounds
        val negativePadding = (-18 * context.resources.displayMetrics.density).toInt() // -18dp
        layerDrawable.setLayerInset(1, negativePadding, negativePadding, negativePadding, negativePadding)

        holder.icon.setImageDrawable(layerDrawable)

        // Apply squircle corner radius to match the background
        holder.icon.shapeAppearanceModel = ShapeAppearanceModel.builder()
            .setAllCornerSizes(16.dp.toFloat()) // 16dp corner radius for squircle
            .build()
    }

    private fun resetIconStyling(holder: AppDrawerAdapter.ViewHolder) {
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

//    // Update your onBindViewHolder method
//    override fun onBindViewHolder(holder: com.example.voidui.AppListAdapter.ViewHolder, position: Int) {
//        val app = apps[position]
//        val appLabel = app.loadLabel(pm).toString()
//        val shouldDim = highlightedInitial != null && !appLabel.startsWith(highlightedInitial!!, ignoreCase = true)
//
//        holder.itemView.alpha = if (shouldDim) 0.2f else 1f
//        holder.icon.setBackgroundResource(0)
//
//        // Check if themed icons are enabled (you might want to add this preference)
//        if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
//            loadThemedIcon(app, holder)
//        } else {
//            holder.icon.setImageDrawable(app.loadIcon(pm))
//            resetIconStyling(holder)
//        }
//
//        holder.name.text = appLabel
//
//        if (SharedPreferencesManager.isMiniAppNameToggleEnabled(context)) {
//            holder.name.visibility = View.VISIBLE
//            val layoutParams = holder.icon.layoutParams as ViewGroup.MarginLayoutParams
//            layoutParams.topMargin = 0.dp
//            layoutParams.bottomMargin = 0.dp
//            holder.icon.layoutParams = layoutParams
//        } else {
//            holder.name.visibility = View.GONE
//            val layoutParams = holder.icon.layoutParams as ViewGroup.MarginLayoutParams
//            layoutParams.topMargin = 12.spToPx.pxToDp
//            layoutParams.bottomMargin = 12.spToPx.pxToDp
//            holder.icon.layoutParams = layoutParams
//        }
//
//        holder.itemView.setOnLongClickListener {
//            val clipData = ClipData.newPlainText("packageName", app.packageName)
//            val shadow = AppIconDragShadowBuilder(context, app)
//            it.startDragAndDrop(clipData, shadow, app, 0)
//            onAppDragStarted?.invoke(app)
//            true
//        }
//    }

    override fun onBindViewHolder(holder:AppDrawerAdapter.ViewHolder, position: Int) {
        val app = appList[position]
        if (app.packageName == DROP_INDICATOR_PACKAGE) {
            holder.icon.setImageResource(0)
            holder.icon.setBackgroundResource(R.drawable.drop_indicator)
            if (SharedPreferencesManager.isShowMiniAppNameEnabled(context)) {
                holder.name.visibility = View.VISIBLE
                holder.miniDrawerLayout.setPadding(0, 6.dp, 0, 6.dp)
            } else {
                holder.name.visibility = View.GONE
                holder.miniDrawerLayout.setPadding(0, 18.dp, 0, 0)
            }
            holder.itemView.setOnLongClickListener(null)
        } else {
            holder.name.text = app.loadLabel(pm)
            holder.icon.setBackgroundResource(0)

            // Check if themed icons are enabled (you might want to add this preference)
            if (SharedPreferencesManager.isThemedIconsEnabled(context)) {
                loadThemedIcon(app, holder)
            } else {
                holder.icon.setImageDrawable(app.loadIcon(pm))
                resetIconStyling(holder)
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
                onAppDragStarted?.invoke(app) // Notify removal if dragging from drawer
                true
            }
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
    private val Int.spToPx: Float get() = this * context.resources.displayMetrics.scaledDensity
    private val Float.pxToDp: Int get() = (this / context.resources.displayMetrics.density).toInt()

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