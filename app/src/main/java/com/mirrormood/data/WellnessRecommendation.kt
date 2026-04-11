package com.mirrormood.data

data class WellnessRecommendation(
    val emoji: String,
    val title: String,
    val description: String,
    val category: String // "Breathing" | "Activity" | "Mindset" | "Self-Care"
)
