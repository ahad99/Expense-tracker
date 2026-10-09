# 💰 Expense Tracker for Android

> 🚀 **Vibe Coding Project** — An AI-assisted Android app built as part of my AI-powered development portfolio.

An offline-first personal expense tracker built with **Kotlin** and **Jetpack Compose**, featuring expense management, budget tracking, interactive charts, and Google Sheets sync.

## 📱 Screenshots

<img width="720" height="1600" alt="image" src="https://github.com/user-attachments/assets/6328acb8-bbe4-4ec1-a20a-123d2255c7fd" /> <img width="720" height="1600" alt="image" src="https://github.com/user-attachments/assets/1575b671-c92e-4e66-8fd8-669201a18914" />



## ✨ Key Features

* 📊 **Dashboard & Analytics** — Spending summaries, category charts, and daily expense trends.
* 💰 **Budget Management** — Monthly budgets with spending progress and alerts.
* 📝 **Expense Tracking** — Add, edit, delete, search, and categorize expenses.
* ☁️ **Google Sheets Sync** — Back up expenses to your spreadsheet.
* 🔒 **Offline-First** — Store data locally using Room Database and use the app without internet.

## 🛠️ Tech Stack

* **Language:** Kotlin
* **UI:** Jetpack Compose & Material Design 3
* **Database:** Room (SQLite)
* **Architecture:** MVVM
* **State Management:** ViewModel, StateFlow, and Kotlin Flow
* **Cloud Sync:** Google Sheets API / Google Apps Script
* **Build System:** Gradle

## 🚀 Getting Started

1. Clone the repository:

   ```bash
   git clone https://github.com/your-username/expense-tracker-android.git
   ```

2. Open the project in Android Studio.

3. Allow Gradle to sync dependencies.

4. Run the app on an Android device or emulator.

**Requirements:** Android Studio, JDK 17 or newer, and Android SDK 26+.

## ☁️ Google Sheets Setup

1. Create a Google Spreadsheet.
2. Open **Extensions → Apps Script**.
3. Add the sync script provided by the project.
4. Deploy it as a Web App.
5. Copy the deployment URL into the app's Google Sheets Sync settings.

## 📄 License

This project is available under the [MIT License](LICENSE).
