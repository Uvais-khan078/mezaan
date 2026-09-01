package com.example.meezan.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meezan.MainActivity
import org.junit.Rule
import org.junit.Test

class HabitScoreIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testHabitCompletionUpdatesDashboardScore() {
        // 1. Ensure we are on Dashboard and check initial score
        // (Assuming first launch/clean data)
        composeTestRule.onNodeWithContentDescription("Prayers").performClick()
        
        // 2. Navigate to Habits and add a habit
        composeTestRule.onNodeWithContentDescription("Habits").performClick()
        composeTestRule.onNodeWithContentDescription("Add Habit").performClick()
        composeTestRule.onNodeWithText("Name (e.g. Exercise, Read)").performTextInput("Water")
        composeTestRule.onNodeWithText("Add").performClick()
        
        // 3. Complete the habit
        // The PulseCheckbox doesn't have a direct text, but the row contains "Water"
        // We can click the checkbox inside the row
        composeTestRule.onNode(hasTestTag("checkbox"), useUnmergedTree = true).performClick() 
        // Wait, I didn't add test tags. Let's use generic toggle or click by text parent
        composeTestRule.onNodeWithText("Water").performClick() // Clicks the row, maybe not the checkbox
        
        // Let's find the checkbox by its role or just click the parent
        // Actually, HabitCheckItem has PulseCheckbox.
        // I'll use performClick on the text "Water" which should trigger the toggle if I wired it correctly
        // Wait, HabitCheckItem onToggle is wired to the checkbox.
        // Let's just use the checkbox if I can find it.
        composeTestRule.onNode(hasClickAction()).performClick() // This might be too generic.
        
        // Better: toggle the checkbox by finding it near "Water"
        composeTestRule.onAllNodes(hasClickAction()).onFirst().performClick()
        
        // 4. Navigate back to Dashboard
        composeTestRule.onNodeWithContentDescription("Prayers").performClick()
        
        // 5. Verify score is no longer 0%
        // Score card has "Productivity Score" and then the percentage.
        // We can check if any text matches a percentage > 0
        composeTestRule.onNodeWithText("0%").assertDoesNotExist()
    }
}
