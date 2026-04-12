package com.mirrormood.ui.history

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.content.ContextCompat
import com.mirrormood.R
import com.mirrormood.util.MoodUtils

class CalendarDayView(context: Context) : LinearLayout(context) {

    private val tvDayNumber: TextView
    private val tvMoodEmoji: TextView

    var dayOfMonth: Int = 0
        private set
    var isCurrentMonth: Boolean = true
        private set
    var isToday: Boolean = false
        private set

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER
        val cellSize = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 48f, resources.displayMetrics
        ).toInt()
        layoutParams = LayoutParams(0, cellSize, 1f).apply {
            val margin = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, 2f, resources.displayMetrics
            ).toInt()
            setMargins(margin, margin, margin, margin)
        }
        setPadding(0, 4, 0, 4)

        // mm_on_surface now has a night override in values-night/colors.xml
        tvDayNumber = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            textSize = 12f
            setTextColor(ContextCompat.getColor(context, R.color.mm_on_surface))
            gravity = Gravity.CENTER
        }

        tvMoodEmoji = TextView(context).apply {
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT)
            textSize = 14f
            gravity = Gravity.CENTER
        }

        addView(tvDayNumber)
        addView(tvMoodEmoji)
    }

    fun bind(
        day: Int,
        currentMonth: Boolean,
        today: Boolean,
        dominantMood: String?,
        entryCount: Int,
        maxEntryCount: Int,
        selected: Boolean
    ) {
        dayOfMonth = day
        isCurrentMonth = currentMonth
        isToday = today

        if (day == 0) {
            tvDayNumber.text = ""
            tvMoodEmoji.text = ""
            background = null
            isClickable = false
            return
        }

        tvDayNumber.text = day.toString()
        tvMoodEmoji.text = if (dominantMood != null) MoodUtils.getEmoji(dominantMood) else ""

        if (currentMonth) {
            // Both mm_on_surface and mm_calendar_outside_month now have night overrides
            tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.mm_on_surface))
        } else {
            tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.mm_calendar_outside_month))
            tvMoodEmoji.text = ""
        }

        background = buildHeatMapBackground(currentMonth, today, selected, dominantMood, entryCount, maxEntryCount)

        isClickable = currentMonth && day > 0
        isFocusable = isClickable
    }

    private fun buildHeatMapBackground(
        currentMonth: Boolean,
        today: Boolean,
        selected: Boolean,
        dominantMood: String?,
        entryCount: Int,
        maxEntryCount: Int
    ): GradientDrawable? {
        if (!currentMonth) return null

        val radius = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, 8f, resources.displayMetrics
        )
        val shape = GradientDrawable().apply {
            cornerRadius = radius
        }

        if (dominantMood != null && entryCount > 0) {
            val moodColor = ContextCompat.getColor(context, MoodUtils.getColorRes(dominantMood))
            val intensity = if (maxEntryCount > 0) {
                entryCount.toFloat() / maxEntryCount.toFloat()
            } else {
                0f
            }
            val alpha = (58 + (112 * intensity)).toInt().coerceIn(58, 170)
            shape.setColor(ColorUtils.setAlphaComponent(moodColor, alpha))
        } else {
            shape.setColor(Color.TRANSPARENT)
        }

        when {
            selected -> {
                val strokeColor = ContextCompat.getColor(context, R.color.mm_primary)
                shape.setStroke(dp(2f), strokeColor)
            }
            today -> {
                val strokeColor = ContextCompat.getColor(context, R.color.mm_ghost_outline)
                shape.setStroke(dp(1f), strokeColor)
            }
        }
        return shape
    }

    private fun dp(value: Float): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics
        ).toInt()
    }
}
