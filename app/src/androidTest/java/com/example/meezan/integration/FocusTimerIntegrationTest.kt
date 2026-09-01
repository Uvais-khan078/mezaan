package com.example.meezan.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meezan.MainActivity
import org.junit.Rule
import org.junit.Test

class FocusTimerIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testFocusTimerFlowAndTaskCompletion() {
        // 1. Add a task via Goals Roadmap
        composeTestRule.onNodeWithContentDescription("Goals").performClick()
        val roadmapText = "Goal: Work\nDay 1\nTask: Deep Work\nDuration: 1m"
        composeTestRule.onNodeWithText("Paste roadmap text...").performTextInput(roadmapText)
        composeTestRule.onNodeWithText("Add roadmap").performClick()
        composeTestRule.onNodeWithText("Save").performClick()

        // 2. Start the task
        composeTestRule.onNodeWithText("Deep Work").performClick()
        
        // 3. Verify on Timer Screen
        composeTestRule.onNodeWithText("Focus Timer").assertExists()
        composeTestRule.onNodeWithText("Start").performClick()
        
        // Wait a few seconds to simulate focus
        composeTestRule.mainClock.advanceTimeBy(2000)
        
        // 4. Finish the task
        composeTestRule.onNodeWithText("Finish").performClick()
        
        // 5. Verify back on Goals screen and status is COMPLETED
        // (StatusPill shows the status name)
        composeTestRule.onNodeWithText("COMPLETED").assertExists()
    }
}
