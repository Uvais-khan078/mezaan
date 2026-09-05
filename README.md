# Meezan - The Balanced Productivity App

**Meezan** (Arabic for "Balance") is a professional Android application built with **Kotlin** and **Jetpack Compose**. It is designed to help users achieve a perfect equilibrium between their spiritual obligations and worldly productivity goals.

## 🌟 Key Features

### 🕌 Spiritual Core
- **Localized Prayer Timings**: Precise timings for Fajr, Dhuhr, Asr, Maghrib, and Isha, including **Sunrise** and **Sunset**.
- **Professional Alarm System**:
    - **Intelligent Picker**: A professional 3-wheel scrolling UI for setting time offsets (Sign `+/-`, Hours, Minutes).
    - **Precision Offsets**: Set alarms before or after any prayer (e.g., `- 01:15` for 75 mins before Fajr).
    - **Continuous Ringing**: Alarms ring and vibrate continuously for 60 seconds or until manually dismissed, ensuring you never miss a prayer.
- **Prayer Habits**: The 5 main prayers are integrated as permanent, non-deletable habits to ensure they contribute to your overall consistency.

### 📈 Worldly Productivity
- **Goal Planner**: Support for dynamic roadmap parsing. Paste structured text to generate multi-day focus tasks automatically.
- **Focus Timer**: A high-priority foreground service timer for tasks. It runs silently in the background and triggers a loud alert exactly when your goal is reached.
- **Habit Tracker**: Build streaks and track consistency for both spiritual and custom personal habits.

### 💰 Financial Management
- **Local Ledger**: A comprehensive system to track income, withdrawals, and savings deposits.
- **Smart Budgeting**: Automatically calculates your daily spending limit based on remaining days in the month.
- **Lending & Debt**: Track money lent to others or split bills without affecting your daily spending capacity.
- **Privacy Gated**: Sensitive financial details are protected behind system **Biometric (Fingerprint/PIN)** authentication.

### 📊 Insights & Analytics
- **Dynamic Charting**: Visualize your performance over 7 or 30 days with integrated line and column charts, now fully aligned with the calendar month.
- **Spending Distribution**: Analyze your daily spending habits with detailed breakdown charts.
- **Monthly Habit Grid**: A professional monthly calendar view with day numbers and "Today" highlighting to track consistency across every month.

### 🛡️ Security & Privacy
- **Database Encryption**: All user data is secured with **SQLCipher** using 256-bit AES encryption.
- **Secure Preferences**: Sensitive data like location and contact names are stored in `EncryptedSharedPreferences`.
- **Biometric Lock**: Critical sections like Finance and Data Reset are protected by system **Biometric (Fingerprint/PIN)** authentication.
- **Data Portability**: Full support for JSON-based **Backup and Restore**.
- **Selective Reset**: A "Danger Zone" feature to clear all activity data while protecting your settings and prayer configurations. Includes an optional toggle to wipe or keep Finance records.

---

## 🎨 Professional UI
- **Seamless Modern Design**: Header and Footer blend perfectly with the main content for an edge-to-edge, unified experience.
- **High-Visibility Light Mode**: A custom-designed light theme with a "Deep Indigo & Soft Teal" palette for professional looks and high readability.
- **Fluid Animations**: Smooth transitions and haptic feedback for a premium feel.

---

## 🛠️ Technical Stack

- **UI**: Jetpack Compose (Material 3) with modern gradients and adaptive layouts.
- **Architecture**: MVVM (Model-View-ViewModel) + Repository Pattern.
- **Database**: Room Persistence Library with **SQLCipher** for full-disk encryption.
- **Networking**: Retrofit + OkHttp + Moshi for API communication.
- **Scheduling**: AlarmManager (using `setAlarmClock` for maximum reliability) + WorkManager.
- **Charts**: Vico Charts for high-performance data visualization.
- **Security**: Android Biometric Library + Jetpack Security (Crypto).

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer.
- Android device running API 26 (Oreo) or higher.

### Build Instructions
```bash
# Clone the repository
git clone https://github.com/yourusername/meezan.git

# Build the project
./gradlew assembleDebug
```

### Setup
Upon first launch, the app will request **Location Permissions** to fetch accurate prayer timings. You can also set your coordinates manually in the Settings screen.

---

## 📄 License
This project is licensed under the MIT License - see the [LICENSE](LICENSE) file for details.
