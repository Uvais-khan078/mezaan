# Meezan - The Balanced Productivity App

**Meezan** (Arabic for "Balance") is a professional Android application built with **Kotlin** and **Jetpack Compose**. It is meticulously designed to help users achieve a perfect equilibrium between their spiritual obligations and worldly productivity goals.

---

## 📲 Installation Guide

### Option 1: Quick Install (APK)
For the easiest setup, use the pre-built signed APK included in this repository:
1.  Download the **`meezan 1.2.1 release.apk`** from the project root.
2.  Transfer the file to your Android phone.
3.  Open the APK on your phone. If prompted, allow "Install from Unknown Sources" or "Install anyway" (Play Protect).
4.  Launch **Meezan** and start your balanced journey.

### Option 2: Build from Source
If you are a developer and want to build the app yourself:
1.  **Clone the Repository**:
    ```bash
    git clone https://github.com/Uvais-khan078/mezaan.git
    ```
2.  **Open in Android Studio**: Use Android Studio Ladybug (2024.2.1) or newer.
3.  **Sync Gradle**: Allow the project to download all necessary dependencies.
4.  **Run**: Connect your device and click the **Run** button or use:
    ```bash
    ./gradlew installDebug
    ```

---

## 🌟 Section-by-Section Usage

### 1. 🕌 Dashboard (The Spiritual Core)
The Dashboard is your control center for the day.
- **Prayer Schedule**: View precise timings for all 5 prayers, including **Sunrise** and **Sunset**.
- **Next Prayer Countdown**: A real-time timer shows exactly how much time is left until the next prayer.
- **Productivity Score**: A unique circular gauge that calculates your daily balance. Completing prayers, habits, and goal-tasks increases this score.
- **Quick Finance Access**: Click the **Daily Budget Card** at the bottom to instantly jump to your finances (Biometric protected).

### 2. 📈 Goal Planner (Strategic Focus)
Meezan uses an intelligent **Roadmap Parser** to turn your plans into reality.
- **How to use**: Simply paste a structured text plan (e.g., *Goal: Learn Kotlin, Day 1: Basics*) into the input field.
- **Dynamic Scheduling**: The app automatically generates tasks for the upcoming days based on your text.
- **Focus Timer**: Click on any task to start a dedicated, silent focus timer. The app will ring a loud alert only when your duration goal is achieved.

### 3. ✅ Habit Tracker (Consistency Builder)
- **Permanent Prayer Habits**: The 5 main prayers are automatically added as permanent habits. They cannot be deleted, ensuring your spiritual consistency is always tracked.
- **Professional Alarms**: Set reminders for any habit. Unlike standard notifications, Meezan's alarms **ring and vibrate continuously for 60 seconds** until you tap "Dismiss".
- **Streak Tracking**: Keep the fire alive with daily streaks shown on each habit card.

### 4. 💰 Finance Manager (Smart Budgeting)
- **Biometric Privacy**: Your financial ledger is gated behind your phone's **Fingerprint/PIN** for total privacy.
- **Daily Spending Limit**: The app calculates a "Safe to Spend" daily amount based on your total monthly budget and remaining days.
- **Lending & Splits**: Track money you've lent or bills you've split. These records are kept separate so they don't lower your daily spending limit.
- **Savings Rollover**: At the end of every month, any unspent budget is automatically moved to your **Total Savings**.

### 5. 📊 Insights (Deep Analytics)
- **Productivity Trend**: A line chart showing your performance over the week or month, perfectly aligned to the calendar.
- **Daily Spending**: A column chart tracking your expenses.
- **Monthly Consistency Grid**: A professional GitHub-style heatmap showing your habit completion across the entire month, complete with day numbers and month-name headers.

---

## 🛡️ Security & Privacy
- **Full Disk Encryption**: Every bit of your data is encrypted using **SQLCipher (256-bit AES)**. Your database cannot be read even if the phone is rooted.
- **Secure Storage**: Sensitive data like coordinates and contacts are stored in **EncryptedSharedPreferences**.
- **Local-First**: Meezan works entirely offline. Your data never leaves your device unless you manually create a backup.
- **Selective Reset**: Clear your activity data anytime while keeping your prayer settings and location safe.

---

## 🛠️ Technical Stack
- **Language**: Kotlin 2.0+
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM + Repository Pattern
- **Database**: Room + SQLCipher
- **Scheduling**: AlarmManager (`setAlarmClock`) + WorkManager
- **Networking**: Retrofit + Moshi

---

## 📄 License
Licensed under the MIT License. Developed with ❤️ for a balanced life.
