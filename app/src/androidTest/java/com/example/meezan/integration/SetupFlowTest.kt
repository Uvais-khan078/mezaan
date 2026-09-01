package com.example.meezan.integration

import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.meezan.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SetupFlowTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testManualLocationSetupFlow() {
        // Wait for splash screen / loading
        composeTestRule.waitUntil(5000) {
            composeTestRule.onAllNodesWithText("Welcome to Meezan").fetchSemanticsNodes().isNotEmpty()
        }

        // Enter manual latitude and longitude
        composeTestRule.onNodeWithText("Latitude").performTextInput("28.6139")
        composeTestRule.onNodeWithText("Longitude").performTextInput("77.2090")
        
        // Tap "Set Manual"
        composeTestRule.onNodeWithText("Set Manual").performClick()

        // Dashboard should load (Header "Today's Prayer Times" appears)
        composeTestRule.waitUntil(10000) {
            composeTestRule.onAllNodesWithText("Today's Prayer Times").fetchSemanticsNodes().isNotEmpty()
        }
    }
}
