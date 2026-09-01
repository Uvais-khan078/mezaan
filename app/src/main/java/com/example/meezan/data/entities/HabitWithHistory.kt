package com.example.meezan.data.entities

/**
 * POJO for grouping habit with its history data
 */
data class HabitWithHistory(
    val habit: Habit,
    val streak: Int,
    val history: List<Boolean>
)
