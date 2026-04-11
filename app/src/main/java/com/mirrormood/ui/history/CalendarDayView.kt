package com.mirrormood.ui.history

import android.content.Context
import android.util.TypedValue
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.mirrormood.R

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
        tvMoodEmoji.text = if (dominantMood != null) com.mirrormood.util.MoodUtils.getEmoji(dominantMood) else ""

        if (currentMonth) {
            // Both mm_on_surface and mm_calendar_outside_month now have night overrides
            tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.mm_on_surface))
        } else {
            tvDayNumber.setTextColor(ContextCompat.getColor(context, R.color.mm_calendar_outside_month))
            tvMoodEmoji.text = ""
        }

        // Use ContextCompat.getDrawable (non-deprecated) for calendar cell backgrounds
        background = when {
            selected && currentMonth -> ContextCompat.getDrawable(context, R.drawable.bg_calendar_day_selected)
            today && currentMonth    -> ContextCompat.getDrawable(context, R.drawable.bg_calendar_today)
            else                    -> null
        }

        isClickable = currentMonth && day > 0
        isFocusable = isClickable
    }
}
