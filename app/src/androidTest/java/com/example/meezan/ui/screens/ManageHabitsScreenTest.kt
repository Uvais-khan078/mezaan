package com.example.meezan.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meezan.MainActivity
import org.junit.Rule
import org.junit.Test

class ManageHabitsScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testAddHabitFlow() {
        // Navigate to Habits tab
        composeTestRule.onNodeWithContentDescription("Habits").performClick()

        // Check header
        composeTestRule.onNodeWithText("Today's Progress").assertExists()
        
        // Tap Add Habit FAB
        composeTestRule.onNodeWithContentDescription("Add Habit").performClick()
        
        // Input habit name
        composeTestRule.onNodeWithText("Name (e.g. Exercise, Read)").performTextInput("Reading")
        
        // Add
        composeTestRule.onNodeWithText("Add").performClick()
        
        // Dialog should be dismissed
        composeTestRule.onNodeWithText("Add Habit").assertDoesNotExist()
        
        // The new habit should eventually appear in the list (or at least no error shown)
        composeTestRule.onNodeWithText("Reading").assertExists()
    }
}
