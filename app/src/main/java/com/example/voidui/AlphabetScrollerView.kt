package com.example.voidui

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import android.view.animation.OvershootInterpolator
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.recyclerview.widget.LinearLayoutManager
import kotlin.math.abs
import kotlin.math.exp

class AlphabetScrollerView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    var onLetterSelected: ((Char?) -> Unit)? = null
) : LinearLayout(context, attrs) {

    private var usedAlphabets: List<Char> = emptyList()
    private var indexMap: Map<Char, Int> = emptyMap()
    private var lastIndex = -1
    private var bubbleBackground: Drawable? = null
    private var layoutManager: LinearLayoutManager? = null

    private var lastAnimationUpdateTime = 0L
    private val animationThrottleMs = 16L // ~60 FPS

    private var floatingBubble: TextView? = null
    private var rootOverlay: ViewGroup? = null

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
    }

    fun setup(
        alphabets: List<Char>,
        indexMap: Map<Char, Int>,
        recyclerLayoutManager: LinearLayoutManager,
        bubbleBackground: Drawable? = null
    ) {
        this.usedAlphabets = alphabets
        this.indexMap = indexMap
        this.layoutManager = recyclerLayoutManager
        this.bubbleBackground = bubbleBackground
        refreshLetters()
    }

    private fun refreshLetters() {
        removeAllViews()
        usedAlphabets.forEach { letter ->
            val textView = TextView(context).apply {
                text = letter.toString()
                textSize = 14f
                setTextColor(context.getColor(R.color.textColorPrimary))
                gravity = Gravity.CENTER
                layoutParams = LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                ).apply {
                    setMargins(4.dp, 2.dp, 4.dp, 2.dp)
                    gravity = Gravity.END
                }
                setPadding(4.dp, 2.dp, 4.dp, 2.dp)
            }
            addView(textView)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                val now = System.currentTimeMillis()
                if (now - lastAnimationUpdateTime < animationThrottleMs) return true
                lastAnimationUpdateTime = now

                val itemHeight = height / usedAlphabets.size
                val index = (event.y / itemHeight).toInt().coerceIn(0, usedAlphabets.size - 1)
                val selectedChar = usedAlphabets[index]
                if (index != lastIndex) {
                    indexMap[selectedChar]?.let { layoutManager?.scrollToPositionWithOffset(it, 0) }
                    onLetterSelected?.invoke(selectedChar)
//                    staticScroll(index)                      // STATIC SCROLL BAR WITH AN INDICATOR
                    staticAnimatedScroll(index)                // STATIC ANIMATED SCROLL BAR WITH AN INDICATOR
//                    dynamicBendingScroll(index)              // DYNAMIC BENDING SCROLL BAR WITH AN INDICATOR
//                    dynamicAnimatedBendingScroll(index)      // DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR
//                    dynamicAnimatedBellCurveScroll(index)    // DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR (BELL CURVE METHOD FOR BENDING)
//                    newDynamicAnimatedBellCurveScroll(index) // DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR (BELL CURVE METHOD FOR BENDING AND BETTER PERFORMANCE)
                }
                lastIndex = index

                floatingBubble?.let { bubble ->
                    bubble.text = selectedChar.toString()
                    bubble.visibility = View.VISIBLE

                    val location = IntArray(2)
                    getLocationOnScreen(location)
                    val alphabetScrollerX = location[0]
                    val bubbleX = alphabetScrollerX - bubble.width - 18.dp

                    bubble.x = bubbleX.toFloat()
                    bubble.y = event.rawY - bubble.height * 1.5f
                }
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
//                staticScrollReset()                       // STATIC SCROLL BAR WITH AN INDICATOR
                staticAnimatedScrollReset()                 // STATIC ANIMATED SCROLL BAR WITH AN INDICATOR
//                dynamicBendingScrollReset()               // DYNAMIC BENDING SCROLL BAR WITH AN INDICATOR
//                dynamicAnimatedBendingScrollReset()       // DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR
//                dynamicAnimatedBellCurveScrollReset()     // DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR (BELL CURVE METHOD FOR BENDING)
//                newDynamicAnimatedBellCurveScrollReset()  // DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR (BELL CURVE METHOD FOR BENDING AND BETTER PERFORMANCE)
                lastIndex = -1
                onLetterSelected?.invoke(null)
                floatingBubble?.visibility = View.GONE
            }
        }
        return true
    }

//////////////////////////////// STATIC SCROLL BAR WITH AN INDICATOR ///////////////////////////////
    private fun staticScroll(index: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.translationX = 0f
            child.background = null
        }

        getChildAt(index).translationX = -150f
        getChildAt(index).background = bubbleBackground
    }

    private fun staticScrollReset() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.translationX = 0f
            child.background = null
        }
    }

//////////////////////////// STATIC ANIMATED SCROLL BAR WITH AN INDICATOR //////////////////////////
    private fun staticAnimatedScroll(index: Int) {
        val sigma = 1.5f
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? TextView ?: continue
            val offset = i - index
            val distance = offset.toFloat()
            val curveFactor = exp(-(distance * distance) / (2 * sigma * sigma))

//            val scale = 0.85f + (0.15f * curveFactor)
            val alpha = 0.4f + (0.6f * curveFactor)
            child.alpha = alpha

//            ViewCompat.animate(child).cancel()
//            ViewCompat.animate(child)
//                .scaleX(scale)
//                .scaleY(scale)
//                .alpha(alpha)
//                .setDuration(75)
//                .setInterpolator(LinearInterpolator())
//                .withLayer()
//                .start()

//            if (offset == 0) {
//                child.translationX = -150f
//                child.background = bubbleBackground
//            } else {
//                child.translationX = 0f
//                child.background = null
//            }

            if (offset == 0) {
                if (child.tag == null) child.tag = child.text
                child.text = ""
            } else {
                val originalText = child.tag as? CharSequence
                if (originalText != null && child.text.isNullOrEmpty()) child.text = originalText
            }
        }
    }

    private fun staticAnimatedScrollReset() {
        for (i in 0 until childCount) {
            val child = getChildAt(i) as? TextView ?: continue
            child.alpha = 1f

//            ViewCompat.animate(child).cancel()
//            ViewCompat.animate(child)
//                .scaleX(1f)
//                .scaleY(1f)
//                .alpha(1f)
//                .translationX(0f)
//                .setDuration(75)
//                .setInterpolator(LinearInterpolator())
//                .withLayer()
//                .start()

//            child.background = null

            val originalText = child.tag as? CharSequence
            if (originalText != null && child.text.isNullOrEmpty()) child.text = originalText
        }
    }

/////////////////////////// DYNAMIC BENDING SCROLL BAR WITH AN INDICATOR ///////////////////////////
    private fun dynamicBendingScroll(index: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.translationX = 0f
            child.background = null
        }

        for (offset in -4..4) {
            val childIndex = index + offset
            if (childIndex in 0 until childCount) {
                val child = getChildAt(childIndex)
                val translation = when (offset) {
                    0 -> -150f
                    -1, 1 -> -140f
                    -2, 2 -> -75f
                    -3, 3 -> -20f
                    else -> 0f
                }
                child.translationX = translation

                if (offset == 0) {
                    child.background = ContextCompat.getDrawable(context, R.drawable.bubble_background)
                }
            }
        }
    }

    private fun dynamicBendingScrollReset() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.translationX = 0f
            child.background = null
        }
    }

/////////////////////// DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR //////////////////////
    private fun dynamicAnimatedBendingScroll(index: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.animate()
                .translationX(0f)
                .setDuration(75)
                .start()
            child.background = null
        }

        for (offset in -4..4) {
            val childIndex = index + offset
            if (childIndex in 0 until childCount) {
                val child = getChildAt(childIndex)
                val translation = when (offset) {
                    0 -> -150f
                    -1, 1 -> -140f
                    -2, 2 -> -75f
                    -3, 3 -> -20f
                    else -> 0f
                }
                child.animate()
                    .translationX(translation)
                    .setDuration(75)
                    .start()

                if (offset == 0) {
                    child.background = ContextCompat.getDrawable(context, R.drawable.bubble_background)
                }
            }
        }
    }

    private fun dynamicAnimatedBendingScrollReset() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.animate()
                .translationX(0f)
                .setDuration(75)
                .start()
            child.background = null
        }
    }

/////////////////////// DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR //////////////////////
////////////////////////////////// (BELL CURVE METHOD FOR BENDING) /////////////////////////////////
    private fun dynamicAnimatedBellCurveScroll(index: Int) {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.animate()
                .translationX(0f)
                .setDuration(75)
                .start()
            child.background = null
        }

        val amplitude = 150f
        val sigma = 1.5f

        for (i in 0 until childCount) {
            val offset = i - index
            if (abs(offset) <= 4) {
                val child = getChildAt(i)

                // Bell curve translation based on distance from touch
                val distance = offset.toFloat()
                val translation = -amplitude * exp(-((distance * distance) / (2 * sigma * sigma)))

                child.animate()
                    .translationX(translation)
                    .setDuration(75)
                    .start()

                if (offset == 0) {
                    child.background = ContextCompat.getDrawable(context, R.drawable.bubble_background)
                }
            }
        }
    }

    private fun dynamicAnimatedBellCurveScrollReset() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            child.animate()
                .translationX(0f)
                .setDuration(75)
                .start()
            child.background = null
        }
    }

/////////////////////// DYNAMIC ANIMATED BENDING SCROLL BAR WITH AN INDICATOR //////////////////////
/////////////////////// (BELL CURVE METHOD FOR BENDING AND BETTER PERFORMANCE) /////////////////////
    private fun newDynamicAnimatedBellCurveScroll(index: Int) {
        val amplitude = 150f
        val sigma = 1.5f

        for (i in 0 until childCount) {
            val child = getChildAt(i)
            val offset = i - index

            val distance = offset.toFloat()
            val curveFactor = exp(-(distance * distance) / (2 * sigma * sigma))

            val translationX = -amplitude * curveFactor
            val scale = 0.85f + (0.15f * curveFactor)
            val alpha = 0.4f + (0.6f * curveFactor)

            ViewCompat.animate(child).cancel()
            ViewCompat.animate(child)
                .translationX(translationX)
                .scaleX(scale)
                .scaleY(scale)
                .alpha(alpha)
                .setDuration(75)
                .setInterpolator(LinearInterpolator())
                .withLayer()
                .start()

            child.background = if (offset == 0) {
                bubbleBackground
            } else {
                null
            }
        }
    }

    private fun newDynamicAnimatedBellCurveScrollReset() {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            ViewCompat.animate(child).cancel()
            ViewCompat.animate(child)
                .translationX(0f)
                .scaleX(1f)
                .scaleY(1f)
                .alpha(1f)
                .setDuration(75)
                .setInterpolator(LinearInterpolator())
                .withLayer()
                .start()
            child.background = null
        }
    }

    fun enableFloatingBubble(bubbleParent: ViewGroup) {
        rootOverlay = bubbleParent
        floatingBubble = TextView(context).apply {
            layoutParams = LayoutParams(32.dp, 32.dp).apply {
                gravity = Gravity.TOP or Gravity.START
            }
            background = AppCompatResources.getDrawable(context, R.drawable.bubble_background)
            gravity = Gravity.CENTER
            textSize = 14f
            setTextColor(Color.WHITE)
            visibility = View.GONE
            elevation = 20f
        }
        rootOverlay?.addView(floatingBubble)
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
}