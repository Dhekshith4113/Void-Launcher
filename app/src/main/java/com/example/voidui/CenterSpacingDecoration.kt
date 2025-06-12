package com.example.voidui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class CenterSpacingDecoration : RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val adapter = parent.adapter ?: return
        val itemCount = adapter.itemCount
        if (itemCount == 0) return

        val position = parent.getChildAdapterPosition(view)
        if (position == RecyclerView.NO_POSITION) return

        val parentWidth = parent.width
        val itemWidth = view.measuredWidth.takeIf { it > 0 } ?: view.layoutParams.width

        if (itemWidth <= 0 || parentWidth <= 0) return  // Safety check if still not measured

        val totalWidth = itemWidth * itemCount
        val remainingSpace = parentWidth - totalWidth

        if (remainingSpace > 0) {
            val spacing = remainingSpace / (itemCount + 1)
            outRect.left = spacing
            if (position == itemCount - 1) {
                outRect.right = spacing
            }
        } else {
            outRect.left = 8
            outRect.right = 8
        }
    }
}