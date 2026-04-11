package com.mirrormood.util

import android.content.Intent
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.mirrormood.MainActivity
import com.mirrormood.R
import com.mirrormood.ui.insights.InsightsActivity
import com.mirrormood.ui.recommendations.RecommendationsActivity
import com.mirrormood.ui.timeline.TimelineActivity
import com.mirrormood.util.MoodUtils.slideTransition

enum class BottomNavTab {
    NONE,
    HOME,
    INSIGHTS,
    TIMELINE,
    ADVICE
}

object BottomNavHelper {

    fun setup(activity: AppCompatActivity, activeTab: BottomNavTab) {
        // When <include android:id="@+id/includeBottomNav"> is used, it overrides
        // the root view's ID. Try both the original and the include ID.
        val nav = activity.findViewById<BottomNavigationView>(R.id.bottomNav)
            ?: activity.findViewById<BottomNavigationView>(R.id.includeBottomNav)
            ?: return

        val menuId = when (activeTab) {
            BottomNavTab.HOME -> R.id.navHome
            BottomNavTab.INSIGHTS -> R.id.navInsights
            BottomNavTab.TIMELINE -> R.id.navTimeline
            BottomNavTab.ADVICE -> R.id.navTips
            BottomNavTab.NONE -> R.id.navHome
        }

        // Set active item without triggering the listener
        nav.selectedItemId = menuId

        // Apply system bar insets as bottom padding so the nav bar draws behind the gesture bar
        ViewCompat.setOnApplyWindowInsetsListener(nav) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            nav.setPadding(
                nav.paddingLeft,
                nav.paddingTop,
                nav.paddingRight,
                systemBars.bottom
            )
            insets
        }
        ViewCompat.requestApplyInsets(nav)

        nav.setOnItemSelectedListener { item ->
            val dest = when (item.itemId) {
                R.id.navHome -> MainActivity::class.java
                R.id.navInsights -> InsightsActivity::class.java
                R.id.navTimeline -> TimelineActivity::class.java
                R.id.navTips -> RecommendationsActivity::class.java
                else -> return@setOnItemSelectedListener false
            }

            if (activity::class.java != dest) {
                val intent = Intent(activity, dest).apply {
                    addFlags(
                        Intent.FLAG_ACTIVITY_CLEAR_TOP or
                        Intent.FLAG_ACTIVITY_SINGLE_TOP
                    )
                }
                activity.startActivity(intent)
                activity.slideTransition(forward = item.itemId != R.id.navHome)
            }
            true
        }
    }
}
