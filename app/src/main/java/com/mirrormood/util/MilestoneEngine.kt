package com.mirrormood.util

import com.mirrormood.data.Milestone
import com.mirrormood.data.db.MoodEntry
import java.util.Calendar

object MilestoneEngine {

    fun generateMilestones(entries: List<MoodEntry>): List<Milestone> {
        val countMap = entries.groupBy { it.mood }.mapValues { it.value.size }
        val daysTracked = entries.map { entry ->
            Calendar.getInstance().apply {
                timeInMillis = entry.timestamp
            }.get(Calendar.DAY_OF_YEAR)
        }.distinct().size

        val milestones = mutableListOf<Milestone>()

        // Milestone 1: First Step
        val ms1Target = 1
        val ms1Current = entries.size
        milestones.add(
            Milestone(
                id = "first_step",
                title = "The First Step",
                description = "Record your very first mood entry.",
                emoji = "🌱",
                isUnlocked = ms1Current >= ms1Target,
                progress = calculateProgress(ms1Current, ms1Target),
                target = ms1Target,
                currentAmount = ms1Current
            )
        )

        // Milestone 2: The Observer (7 days tracked)
        val ms2Target = 7
        val ms2Current = daysTracked
        milestones.add(
            Milestone(
                id = "the_observer",
                title = "The Observer",
                description = "Track your mood across 7 unique days.",
                emoji = "👁️",
                isUnlocked = ms2Current >= ms2Target,
                progress = calculateProgress(ms2Current, ms2Target),
                target = ms2Target,
                currentAmount = ms2Current
            )
        )

        // Milestone 3: Zen Master (20 Neutral)
        val ms3Target = 20
        val ms3Current = countMap["Neutral"] ?: 0
        milestones.add(
            Milestone(
                id = "zen_master",
                title = "Zen Master",
                description = "Record 20 'Neutral' mood entries.",
                emoji = "🧘",
                isUnlocked = ms3Current >= ms3Target,
                progress = calculateProgress(ms3Current, ms3Target),
                target = ms3Target,
                currentAmount = ms3Current
            )
        )

        // Milestone 4: Radiant (10 Happy)
        val ms4Target = 10
        val ms4Current = countMap["Happy"] ?: 0
        milestones.add(
            Milestone(
                id = "radiant",
                title = "Radiant Glow",
                description = "Record 10 'Happy' mood entries.",
                emoji = "✨",
                isUnlocked = ms4Current >= ms4Target,
                progress = calculateProgress(ms4Current, ms4Target),
                target = ms4Target,
                currentAmount = ms4Current
            )
        )

        // Milestone 5: Eagle Eye (10 Focused)
        val ms5Target = 10
        val ms5Current = countMap["Focused"] ?: 0
        milestones.add(
            Milestone(
                id = "eagle_eye",
                title = "Eagle Eye",
                description = "Find your zone: 10 'Focused' entries.",
                emoji = "🦅",
                isUnlocked = ms5Current >= ms5Target,
                progress = calculateProgress(ms5Current, ms5Target),
                target = ms5Target,
                currentAmount = ms5Current
            )
        )

        // Milestone 6: Dedicated Journaler (5 notes written)
        val ms6Target = 5
        val ms6Current = entries.count { !it.note.isNullOrBlank() }
        milestones.add(
            Milestone(
                id = "dedicated_journaler",
                title = "Scribe",
                description = "Write 5 meaningful notes.",
                emoji = "🖋️",
                isUnlocked = ms6Current >= ms6Target,
                progress = calculateProgress(ms6Current, ms6Target),
                target = ms6Target,
                currentAmount = ms6Current
            )
        )

        return milestones
    }

    private fun calculateProgress(current: Int, target: Int): Int {
        if (target == 0) return 0
        val percentage = (current.toFloat() / target * 100).toInt()
        return percentage.coerceIn(0, 100)
    }
}
