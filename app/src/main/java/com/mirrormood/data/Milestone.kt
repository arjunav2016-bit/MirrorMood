package com.mirrormood.data

data class Milestone(
    val id: String,
    val title: String,
    val description: String,
    val emoji: String,
    val isUnlocked: Boolean,
    val progress: Int, // 0 to 100
    val target: Int,
    val currentAmount: Int
)
