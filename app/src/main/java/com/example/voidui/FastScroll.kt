package com.example.voidui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class FastScroll @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var recyclerView: RecyclerView? = null
    private var isDragging = false
    private var scrollBarHeight = 200 // px, fixed height
    private var scrollYPosition = 0f
    private val thumbWidth = 24f
    private val thumbCornerRadius = 8f
    private val thumbRestColor = Color.DKGRAY
    private val thumbDragColor = Color.LTGRAY
    private var currentColor = thumbRestColor

    private val thumbPaint = android.graphics.Paint().apply {
        color = currentColor
        isAntiAlias = true
    }

    init {
        isClickable = true
    }

    override fun onDraw(canvas: android.graphics.Canvas) {
        super.onDraw(canvas)
        thumbPaint.color = currentColor
        val top = scrollYPosition.coerceIn(0f, height - scrollBarHeight.toFloat())
        canvas.drawRoundRect(
            width - thumbWidth,
            top,
            width.toFloat(),
            top + scrollBarHeight,
            thumbCornerRadius,
            thumbCornerRadius,
            thumbPaint
        )
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                if (isInsideThumb(event.y)) {
                    isDragging = true
                    currentColor = thumbDragColor
                    ViewCompat.postInvalidateOnAnimation(this)
                    return true
                }
            }
            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    scrollYPosition = (event.y - scrollBarHeight / 2).coerceIn(0f, height - scrollBarHeight.toFloat())
                    syncRecyclerViewScroll()
                    ViewCompat.postInvalidateOnAnimation(this)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    currentColor = thumbRestColor
                    ViewCompat.postInvalidateOnAnimation(this)
                    return true
                }
            }
        }
        return super.onTouchEvent(event)
    }

    private fun isInsideThumb(y: Float): Boolean {
        return y in scrollYPosition..(scrollYPosition + scrollBarHeight)
    }

    private fun syncRecyclerViewScroll() {
        recyclerView?.let { rv ->
            val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
            val totalItems = layoutManager.itemCount
            val visibleItems = layoutManager.childCount - 1
            if (totalItems == 0 || visibleItems == 0) return

            val scrollableRange = (totalItems - visibleItems).coerceAtLeast(1)
            val proportion = scrollYPosition / (height - scrollBarHeight).toFloat()
            val targetPosition = (scrollableRange * proportion).toInt()

            layoutManager.scrollToPositionWithOffset(targetPosition, 0)
        }
    }

    fun attachToRecyclerView(rv: RecyclerView) {
        recyclerView = rv
        rv.setOnScrollChangeListener { _, _, _, _, _ ->
            if (!isDragging) {
                updateScrollThumb()
            }
        }
    }

    private fun updateScrollThumb() {
        recyclerView?.let { rv ->
            val layoutManager = rv.layoutManager as? LinearLayoutManager ?: return
            val totalItems = layoutManager.itemCount
            val visibleItems = layoutManager.childCount - 1
            val firstVisible = layoutManager.findFirstVisibleItemPosition()

            if (totalItems == 0) return

            val scrollableRange = (totalItems - visibleItems).coerceAtLeast(1)
            val proportion = firstVisible / scrollableRange.toFloat()

            scrollYPosition = proportion * (height - scrollBarHeight)
            ViewCompat.postInvalidateOnAnimation(this)
        }
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        val desiredWidth = thumbWidth.toInt()
        setMeasuredDimension(desiredWidth, MeasureSpec.getSize(heightMeasureSpec))
    }
}