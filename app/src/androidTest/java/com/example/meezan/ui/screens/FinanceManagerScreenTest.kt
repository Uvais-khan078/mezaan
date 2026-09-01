package com.example.meezan.ui.screens

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.example.meezan.MainActivity
import com.example.meezan.MeezanTab
import org.junit.Rule
import org.junit.Test

class FinanceManagerScreenTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun testFinanceNavigationAndAddBudget() {
        // Navigate to Finance tab
        composeTestRule.onNodeWithContentDescription("Finance").performClick()

        // Check if Ledger page is shown
        composeTestRule.onNodeWithText("Ledger").assertIsSelected()
        
        // Tap Add Budget button (green FAB)
        composeTestRule.onNodeWithContentDescription("Add Budget").performClick()
        
        // Verify Add Budget dialog
        composeTestRule.onNodeWithText("Add Budget").assertExists()
        composeTestRule.onNodeWithText("Amount").assertExists()
        
        // Input details
        composeTestRule.onNodeWithText("Amount").performTextInput("5000")
        composeTestRule.onNodeWithText("Source").performTextInput("Salary")
        
        // Submit
        composeTestRule.onNodeWithText("Submit").performClick()
        
        // Dialog should be dismissed
        composeTestRule.onNodeWithText("Add Budget").assertDoesNotExist()
    }
}
