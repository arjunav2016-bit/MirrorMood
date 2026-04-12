# 🪞 MirrorMood

**Real-time facial expression-based mood tracking for Android.**

MirrorMood uses your device's front camera and Google ML Kit's face detection to analyze facial expressions in real time, classify your mood, and provide personalized wellness recommendations — all processed entirely on-device for complete privacy.

> **Note:** For a fully robust feature write-up and component breakdown, please check out [DOCUMENTATION.md](DOCUMENTATION.md).


---

## ✨ Features

### 🎭 Real-Time Mood Detection
- Continuous mood monitoring via the front-facing camera as a foreground service
- Multi-signal classification using **smile probability**, **eye openness**, **head rotation** (pitch, yaw, roll), **wink detection**, and **rapid blink detection**
- Six mood categories: **Happy**, **Stressed**, **Tired**, **Focused**, **Bored**, and **Neutral**
- Temporal smoothing with a 5-frame sliding window and 3-frame consensus threshold
- Confidence scoring for each classification

### 📊 Insights & Analytics
- **Today / This Week** tab-based view for switching time ranges
- **Dominant mood** detection with emoji and percentage
- **Per-mood breakdown** with colored pills showing each mood's share
- **Peak time analysis** — discover when you're happiest or most stressed
- **Stacked bar chart**: Today shows Morning/Afternoon/Evening/Night blocks; This Week shows 7-day breakdown
- **Mood correlations** for deeper pattern analysis
- Dark mode-compatible charts using the Ethereal Archive color system

### 📅 Timeline & History
- Chronological timeline of **all-time** mood entries, grouped by day
- "Today" / "Yesterday" / full-date smart labels
- Calendar-based history view with monthly mood distribution
- Streak tracking (e.g., "3-day Happy streak! 🔥")

### 🧘 Wellness Recommendations
- **40+ curated wellness tips** organized by mood category
- **5 filter categories**: All, Breathing, Activity, Mindset, and Self-Care
- Contextual wellness card on the home screen that adapts to your current mood
- Dedicated recommendations screen with shuffled, mood-specific tips

### 📓 Mood Journal
- Personal journaling with mood tagging
- **Search** entries by note content or mood keyword
- **Mood filter chips** — filter archive by Happy, Focused, Neutral, Stressed, Tired, or Bored
- Full archive view showing all entries (not just today)
- Pair journal entries with detected mood data
- **Long-press to delete** entries with confirmation dialog
- Editable notes with inline save

### 🔔 Smart Notifications
- **Morning reminder** (8:00 AM) to start mood tracking
- **Evening summary** (9:00 PM) with day's mood recap, **yesterday comparison** (↑/↓ delta), and wellness tip
- **Weekly digest** (Sunday 10:00 AM) with dominant mood, **week-over-week trend** comparison, tracking days, and streak info
- **Anomaly alerts** — notified when 75%+ of recent readings show stress/fatigue
- **Quiet hours** support — disable notifications during set time windows
- Powered by WorkManager for reliable scheduling

### 🏠 Home Screen Widget
- At-a-glance mood widget showing your current mood emoji and label
- **Auto-updates** immediately when a new mood is detected
- Tap to open the app directly

### ⚙️ Settings & Customization
- **Theme selection**: Light, Dark, or System Default
- **Quiet hours**: Set start/end times to pause monitoring
- **Pause monitoring**: One-tap 24-hour pause with auto-resume
- **Daily mood goal**: Target a specific mood percentage with a slider
- **App lock**: Biometric authentication (fingerprint/face) via AndroidX Biometric
- **Export data**: Download all mood entries as a CSV file via the share sheet
- **Delete all data**: Erase all entries with confirmation

### 🔒 Privacy First
- **100% on-device processing** — no images or mood data leave your phone
- Dedicated privacy screen explaining data handling
- [Full Privacy Policy](PRIVACY_POLICY.md)
- Optional biometric app lock for sensitive mood data
- Local Room database storage only

---

## 🏗️ Architecture

```
com.mirrormood/
├── MainActivity.kt                    # Dashboard: mood card, streak, wellness tip
├── MirrorMoodApp.kt                   # Application class, theme & pref migration
│
├── data/
│   ├── WellnessRecommendation.kt      # Data model (emoji, title, description, category)
│   ├── db/
│   │   ├── MoodEntry.kt               # Room entity (mood, scores, timestamp, note)
│   │   ├── MoodDao.kt                 # DAO: CRUD, range queries, deleteById
│   │   └── MoodDatabase.kt            # Room database singleton (v1)
│   └── repository/
│       ├── MoodRepository.kt          # Data layer: save, delete, query, export
│       └── WellnessRepository.kt      # 40+ curated wellness tips by mood
│
├── detection/
│   ├── FaceAnalyzer.kt                # CameraX ImageAnalysis → ML Kit → consensus
│   ├── MoodClassifier.kt              # Multi-signal scoring with boosts
│   └── MoodResult.kt                  # Result wrapper (mood + confidence)
│
├── insights/
│   └── InsightsActivity.kt            # Today/Week tabs, charts, breakdown pills
│
├── notification/
│   ├── MoodNotificationManager.kt     # Channel creation & notification builders
│   ├── NotificationScheduler.kt       # WorkManager: morning, evening, weekly
│   ├── MorningWorker.kt               # 8 AM daily reminder
│   ├── EveningWorker.kt               # 9 PM daily summary with stats
│   └── WeeklySummaryWorker.kt         # Sunday digest with streaks
│
├── service/
│   └── MoodMonitorService.kt          # Foreground service: camera, quiet hours,
│                                      #   pause support, smart screen-off cycling
├── ui/
│   ├── main/MainViewModel.kt          # Mood state, streak, monitoring toggle
│   ├── timeline/
│   │   ├── TimelineActivity.kt        # All-time chronological list
│   │   └── TimelineViewModel.kt       # StateFlow of all mood entries
│   ├── history/
│   │   ├── HistoryActivity.kt         # Calendar grid with mood dots
│   │   ├── HistoryViewModel.kt        # Monthly data loading
│   │   └── CalendarDayView.kt         # Custom day cell renderer
│   ├── correlations/
│   │   ├── CorrelationsActivity.kt    # Time-of-day & day-of-week patterns
│   │   └── CorrelationsViewModel.kt   # 30-day correlation data
│   ├── journal/
│   │   ├── JournalActivity.kt         # Entry composer + list with delete
│   │   └── JournalViewModel.kt        # Save, update notes, delete entries
│   ├── recommendations/
│   │   └── RecommendationsActivity.kt # 5-category filter chips + tip cards
│   ├── settings/
│   │   └── SettingsActivity.kt        # Theme, goals, quiet hours, pause,
│   │                                  #   export CSV, delete all, app lock
│   ├── privacy/
│   │   └── PrivacyActivity.kt         # Privacy information screen
│   └── lock/
│       └── LockActivity.kt            # Biometric lock (launcher activity)
│
├── util/
│   ├── MoodUtils.kt                   # Emoji, color, time formatting, transitions
│   ├── BottomNavHelper.kt             # Consistent bottom nav across activities
│   ├── SettingsDialogHelper.kt        # Settings navigation wrapper
│   └── ThemeHelper.kt                 # Light/Dark/System + edge-to-edge
│
└── widget/
    └── MoodWidgetProvider.kt          # Home widget + auto-refresh helper
```

---

## 🛠️ Tech Stack

| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 36 |
| **DI** | Dagger Hilt |
| **UI** | XML Layouts + ViewBinding + Material 3 Nav |
| **Camera** | CameraX 1.4.0 |
| **Face Detection** | Google ML Kit Face Detection 16.1.7 |
| **Database** | Room 2.8.0 + KSP |
| **Charts** | MPAndroidChart v3.1.0 |
| **Architecture** | MVVM (ViewModel + StateFlow/Flow) |
| **Background** | WorkManager 2.9.0 + Foreground Service + Hilt Worker |
| **Auth** | AndroidX Biometric 1.1.0 |
| **Startup** | AndroidX Core SplashScreen API |
| **Design** | Ethereal Archive design system (custom theme) |

---

## 🎨 Design System — Ethereal Archive

MirrorMood uses a custom "Ethereal Archive" design system instead of Material You's wallpaper-based dynamic colors:

- **Surface hierarchy**: 6-level tonal system (`mm_surface` → `mm_surface_container_highest`)
- **No-Line philosophy**: Cards use tonal elevation instead of borders
- **Custom typography**: Manrope font family
- **Mood palette**: Each mood has distinct light and dark variants
- **Full dark mode** support via `values-night` resource qualifiers

---

## 🚀 Getting Started

### Prerequisites
- Android Studio Ladybug (2024.2.1) or later
- JDK 11+
- Android device or emulator with a front-facing camera (API 26+)

### Setup

1. **Clone the repository**
   ```bash
   git clone https://github.com/arjunav2016-bit/MirrorMood.git
   cd MirrorMood
   ```

2. **Open in Android Studio**
   - File → Open → select the `MirrorMood` directory

3. **Sync Gradle**
   - Android Studio will auto-sync; if not, click **Sync Now**

4. **Run the app**
   - Select a physical device or emulator with a camera
   - Click ▶️ **Run**

### Permissions
The app requests the following permissions at runtime:
- **Camera** — required for facial expression analysis
- **Notifications** (Android 13+) — for mood reminders and summaries

---

## 📸 How It Works

1. **Tap "Start Monitoring"** on the home screen
2. The app launches a **foreground service** with CameraX accessing the front camera
3. **ML Kit Face Detection** processes each frame to extract:
   - Smile probability
   - Left/right eye openness
   - Head Euler angles (pitch, yaw, roll)
   - Blink and wink events
4. **MoodClassifier** scores each mood using weighted signals + contextual boosts
5. **FaceAnalyzer** uses a 5-frame sliding window — mood is only saved when 3+ frames agree
6. Results are saved to the local **Room database** with timestamps
7. The **home screen widget** auto-refreshes after every new entry
8. The UI updates in real-time with mood emoji, label, color, and wellness tips

### Smart Monitoring
- When the screen turns off, the service switches to **battery-saving mode**: brief 5-second captures every 60 seconds
- **Quiet hours** and **pause toggle** suspend the camera entirely
- 24-hour pause auto-resumes without user intervention

---

## 🧪 Testing

Run unit tests:
```bash
./gradlew testDebugUnitTest
```

Current test coverage:
- **MoodClassifierTest** — 21 tests covering all moods, head rotation boosts, wink/blink signals, confidence bounds, and combined edge cases
- **MoodUtilsTest** — 11 tests for emoji mapping, hour formatting, and streak subtitles

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1. Fork the repository
2. Create your feature branch (`git checkout -b feature/AmazingFeature`)
3. Commit your changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

---

## 📄 License

This project is licensed under the MIT License — see the [LICENSE](LICENSE) file for details.

---

<p align="center">
  Built with ❤️ using Kotlin, CameraX, and ML Kit
</p>
