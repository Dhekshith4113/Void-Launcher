package com.example.voidui

import android.graphics.Rect
import android.view.View
import androidx.recyclerview.widget.RecyclerView

class SpacingItemDecoration(private val spanCount: Int, private val spacing: Int) :
    RecyclerView.ItemDecoration() {
    override fun getItemOffsets(
        outRect: Rect,
        view: View,
        parent: RecyclerView,
        state: RecyclerView.State
    ) {
        val totalItemCount = parent.adapter?.itemCount ?: 0
        val position = parent.getChildAdapterPosition(view)

        if (totalItemCount < spanCount) {
            val totalSpacing = (spanCount - 1) * spacing
            val extraSpace = (parent.width - totalItemCount * view.layoutParams.width - totalSpacing) / 2
            outRect.left = if (position == 0) extraSpace else spacing / 2
            outRect.right = if (position == totalItemCount - 1) extraSpace else spacing / 2
        } else {
            outRect.left = spacing / 2
            outRect.right = spacing / 2
        }
    }
}

