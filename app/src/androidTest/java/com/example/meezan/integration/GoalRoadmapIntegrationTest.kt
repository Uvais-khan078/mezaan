package com.example.meezan.integration

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meezan.MainActivity
import org.junit.Rule
import org.junit.Test

class GoalRoadmapIntegrationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testValidRoadmapFlow() {
        // Navigate to Goals
        composeTestRule.onNodeWithContentDescription("Goals").performClick()

        // Input valid roadmap
        val validText = "Goal: Fitness\nDay 1\nTask: Pushups\nDuration: 10m"
        composeTestRule.onNodeWithText("Paste roadmap text...").performTextInput(validText)
        
        // Add roadmap
        composeTestRule.onNodeWithText("Add roadmap").performClick()
        
        // Confirm Dialog should appear
        composeTestRule.onNodeWithText("Confirm Plan").assertExists()
        composeTestRule.onNodeWithText("Save").performClick()
        
        // Check if task appears in "Today's Tasks" (since Day 1 is today)
        composeTestRule.onNodeWithText("Pushups").assertExists()
    }

    @Test
    fun testMalformedRoadmapFlow() {
        // Navigate to Goals
        composeTestRule.onNodeWithContentDescription("Goals").performClick()

        // Input invalid roadmap (missing duration)
        val invalidText = "Goal: Fitness\nDay 1\nTask: Pushups"
        composeTestRule.onNodeWithText("Paste roadmap text...").performTextInput(invalidText)
        
        // Add roadmap
        composeTestRule.onNodeWithText("Add roadmap").performClick()
        
        // Error dialog should appear with line number
        composeTestRule.onNodeWithText("Parsing Failed").assertExists()
        composeTestRule.onNodeWithText("Line 2").assertExists() // Missing duration after line 2 content
    }
}
