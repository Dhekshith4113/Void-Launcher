package com.example.voidui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.RecyclerView

class DrawerItemAnimator : DefaultItemAnimator() {

    companion object {
        private const val ANIMATION_DURATION = 200L
        private const val FADE_DURATION = 150L
    }

    init {
        addDuration = FADE_DURATION
        removeDuration = FADE_DURATION
        moveDuration = ANIMATION_DURATION
        changeDuration = ANIMATION_DURATION
    }

    override fun animateAdd(holder: RecyclerView.ViewHolder): Boolean {
        // Check if this is a drop indicator
        if (isDropIndicator(holder)) {
            return animateDropIndicatorAdd(holder)
        }
        return super.animateAdd(holder)
    }

    override fun animateRemove(holder: RecyclerView.ViewHolder): Boolean {
        // Check if this is a drop indicator
        if (isDropIndicator(holder)) {
            return animateDropIndicatorRemove(holder)
        }
        return super.animateRemove(holder)
    }

    override fun animateMove(
        holder: RecyclerView.ViewHolder,
        fromX: Int, fromY: Int,
        toX: Int, toY: Int
    ): Boolean {
        val view = holder.itemView
        val deltaX = toX - fromX
        val deltaY = toY - fromY

        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder)
            return false
        }

        // Start from the offset position
        view.translationX = -deltaX.toFloat()
        view.translationY = -deltaY.toFloat()

        // Animate to the final position
        val animator = ObjectAnimator.ofFloat(view, "translationX", 0f).apply {
            duration = moveDuration
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.translationX = 0f
                    view.translationY = 0f
                    dispatchMoveFinished(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.translationX = 0f
                    view.translationY = 0f
                }
            })
        }

        animator.start()
        return true
    }

    private fun animateDropIndicatorAdd(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView

        // Start invisible and scale down
        view.alpha = 0f
        view.scaleX = 0.2f
        view.scaleY = 0.2f

        val fadeIn = ObjectAnimator.ofFloat(view, "alpha", 0f, 1f)
        val scaleXIn = ObjectAnimator.ofFloat(view, "scaleX", 0.2f, 1f)
        val scaleYIn = ObjectAnimator.ofFloat(view, "scaleY", 0.2f, 1f)

        val animatorSet = AnimatorSet().apply {
            playTogether(fadeIn, scaleXIn, scaleYIn)
            duration = FADE_DURATION
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                    dispatchAddFinished(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
            })
        }

        animatorSet.start()
        return true
    }

    private fun animateDropIndicatorRemove(holder: RecyclerView.ViewHolder): Boolean {
        val view = holder.itemView

        val fadeOut = ObjectAnimator.ofFloat(view, "alpha", 1f, 0f)
        val scaleXOut = ObjectAnimator.ofFloat(view, "scaleX", 1f, 0.2f)
        val scaleYOut = ObjectAnimator.ofFloat(view, "scaleY", 1f, 0.2f)

        val animatorSet = AnimatorSet().apply {
            playTogether(fadeOut, scaleXOut, scaleYOut)
            duration = FADE_DURATION
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                    dispatchRemoveFinished(holder)
                }

                override fun onAnimationCancel(animation: Animator) {
                    view.alpha = 1f
                    view.scaleX = 1f
                    view.scaleY = 1f
                }
            })
        }

        animatorSet.start()
        return true
    }

    private fun isDropIndicator(holder: RecyclerView.ViewHolder): Boolean {
        // Check if this holder represents a drop indicator
        // You can identify this by checking the adapter or view type
        return holder.itemViewType == 1 // Assuming drop indicator has viewType 1
    }

    override fun canReuseUpdatedViewHolder(viewHolder: RecyclerView.ViewHolder): Boolean {
        return true
    }
}