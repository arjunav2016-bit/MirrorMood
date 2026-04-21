# MirrorMood Documentation

## Project Overview
MirrorMood is a privacy-focused Android wellness ecosystem designed to track a user's emotional state in real-time. By utilizing the device's front camera, Google ML Kit's face detection, and a TensorFlow Lite neural network trained on FER2013, the app analyzes facial expressions to classify mood into six categories: **Happy**, **Stressed**, **Tired**, **Focused**, **Bored**, and **Neutral**. All data processing is performed entirely on-device.

The ecosystem extends beyond the phone with a **Wear OS companion app** for 1-tap mood logging and **Health Connect integration** for correlating mood with sleep, steps, and heart rate data.

### Key Goals
- Empower users with intuitive insights into their daily and weekly emotional patterns.
- Ensure 100% data privacy with local-only storage and on-device machine learning inference.
- Deliver automated and contextual wellness recommendations based on the user's continuously evaluated emotional state.
- Provide a holistic wellness view by correlating mood with biometric and activity data via Health Connect.
- Gamify the mood-tracking experience with achievements and milestones to encourage consistent usage.

---

## Core Features

### 1. Real-Time Emotion Detection
- Integrates **CameraX 1.4.0** with **Google ML Kit** Face Detection to interpret attributes such as smile probability, head rotation (pitch, yaw, roll), eye openness, and blink rates.
- Employs a **dual classification pipeline**:
  - **MoodClassifier**: Weighted heuristic scoring with contextual boosts (e.g., head tilt → Tired, rapid blinks → Stressed).
  - **TFLiteMoodClassifier**: Neural network inference on face-cropped frames using a TensorFlow Lite 2.16.1 model trained on FER2013.
- **FaceAnalyzer** uses a 5-frame sliding window consensus — mood is only saved when 3+ frames agree, preventing noisy single-frame classifications.

### 2. Face Calibration
- First-launch **CalibrationActivity** guides users through a face-positioning workflow to establish a personalized **FaceBaseline**.
- Baseline values (neutral smile probability, eye openness, head angles) improve classification accuracy by adapting to individual facial characteristics.

### 3. Deep Insights & Analytics
- Segregated **Today** and **This Week** analytical layouts.
- Presents dominant moods, historical peak times, and mood/habit correlation patterns using dynamic, customized **MPAndroidChart** visuals.
- **CorrelationsActivity** cross-references mood data with Health Connect metrics (sleep quality, step count, heart rate) to surface actionable patterns.

### 4. Timeline, History & Journal
- Detailed chronological timeline tracking all registered emotional states via **TimelineActivity**.
- Dedicated calendar-based history grid with **CalendarDayView** custom composites in **HistoryActivity**.
- Rich-text journal entry mechanism via **JournalActivity** with **voice journaling** support using Android's on-device SpeechRecognizer.

### 5. Guided Wellness Sessions
- **WellnessSessionActivity** provides structured wellness exercises:
  - **Guided Breathing**: Timed inhale/hold/exhale cycles with animated visual guides.
  - **Body Scan**: Progressive body awareness meditation.
  - **Gratitude Practice**: Prompted reflection exercises.
- Sessions are contextually recommended based on the user's current detected mood.

### 6. Achievements & Gamification
- **AchievementsActivity** displays a grid of unlockable milestones with progress bars.
- **MilestoneEngine** computes progress criteria from mood entry history (e.g., 7-day streak, 100 total entries, first journal entry).
- Achievements are automatically checked and unlocked after each new mood entry.

### 7. Background Processing & Contextual Nudges
- **WorkManager-based Scheduling:**
  - **MorningWorker**: Triggers morning mood check-in reminders.
  - **EveningWorker**: Generates evening mood summaries.
  - **WeeklySummaryWorker**: Calculates week-over-week trend comparisons (e.g., "30% happier than last week"), evaluates dominant mood streaks, and factors in unique tracking days.
  - **AnomalyWorker**: Detects unusual stress/fatigue patterns and sends proactive alerts.
  - **BackupWorker**: Rolling JSON auto-backup (keeps last 5 backups).
  - **DatabaseCleanupWorker**: Periodic database maintenance and optimization.
- **HabitAnalyzer**: Correlates mood patterns with time-of-day and day-of-week habits.
- Provides an index of 40+ wellness recommendations via **WellnessRepository**, filterable by 5 category chips.

### 8. Smart Monitoring & Foreground Service
- **MoodMonitorService**: Foreground service ensuring camera availability with full notification channel support.
- **Battery-saving mode**: When the screen turns off, switches to brief 5-second captures every 60 seconds.
- **Quiet Hours**: User-configurable time ranges that suspend the camera entirely.
- **24-hour Pause Toggle**: Auto-resumes without user intervention.

### 9. Wear OS Companion
- Dedicated **Wear OS module** with a standalone watch app.
- 6-mood quick-log buttons for manual 1-tap mood logging from the wrist.
- Syncs entries to the phone via **Wearable Data Layer API 18.1.0**.
- **WearDataReceiverService** on the phone receives and persists watch-originated entries into the shared Room database.
- No camera needed — pure manual logging experience.
- **MoodTileService**: Quick Settings tile for instant mood logging access.

### 10. Health Connect Integration
- **HealthConnectManager** interfaces with Google Health Connect to read:
  - **Sleep sessions** (duration, quality stages)
  - **Step count** (daily totals)
  - **Heart rate** (resting and active measurements)
- **SleepQualityResult** data class structures sleep analysis outcomes.
- Mood-health correlations are visualized in the **CorrelationsActivity** with MPAndroidChart scatter and line charts.

### 11. Security & Customization
- **Biometric App Lock**: Sensitive historical mood data is guarded via AndroidX Biometric 1.1.0 prompts in **LockActivity**.
- **SHA-256 PIN Fallback**: Secure PIN storage via **PinStorage** with cryptographic hashing.
- **Privacy Controls**: Settings support local CSV data exports, bulk erasure, and 24-hour pause features for camera isolation.
- **PrivacyReportActivity**: Generates a transparent snapshot of all data the app has collected via **PrivacySnapshot**.
- **Theme Customization**: Light/Dark/System theme switching via **ThemeHelper** with full edge-to-edge support.

### 12. Home Screen Widget
- **MoodWidgetProvider** delivers a glanceable home screen widget showing the latest mood with emoji and label.
- Auto-refreshes after every new mood entry.

### 13. Onboarding
- **OnboardingActivity** guides first-time users through:
  - Privacy philosophy and data handling explanation.
  - Camera permission request with rationale.
  - Face calibration workflow.
  - Feature highlights and navigation orientation.

---

## Technical Architecture

The codebase adheres to modern Android development standards, enforcing an **MVVM (Model-View-ViewModel)** architectural pattern with **Clean Architecture** principles. Strict boundaries are maintained around the repository layer with state propagation via Flow/StateFlow.

### Technology Stack
| Layer | Technology |
|-------|-----------|
| **Language** | Kotlin |
| **Min SDK** | 26 (Android 8.0) |
| **Target SDK** | 36 |
| **DI** | Dagger Hilt (app + workers) |
| **UI** | XML Layouts + ViewBinding + Material 3 Navigation |
| **Camera** | CameraX 1.4.0 |
| **Face Detection** | Google ML Kit Face Detection 16.1.7 |
| **On-Device ML** | TensorFlow Lite 2.16.1 (FER2013 expression model) |
| **Database** | Room 2.8.0 + KSP (multi-entity: moods + achievements) |
| **Charts** | MPAndroidChart v3.1.0 |
| **Architecture** | MVVM (ViewModel + StateFlow/Flow) |
| **Background** | WorkManager 2.9.0 + Foreground Service + Hilt Worker |
| **Health** | Health Connect Client (sleep, steps, heart rate) |
| **Wearable** | Wearable Data Layer API 18.1.0 |
| **Auth** | AndroidX Biometric 1.1.0 + SHA-256 PIN fallback |
| **Voice** | Android SpeechRecognizer (prefers offline) |
| **Startup** | AndroidX Core SplashScreen API |
| **CI** | GitHub Actions (build + unit tests) |
| **Design** | Ethereal Archive design system (custom theme) |

### Package Structure 
```
com.mirrormood/
├── MainActivity.kt              # Main dashboard with bottom nav
├── MirrorMoodApp.kt             # Hilt Application class
│
├── data/
│   ├── AchievementDefinitions.kt  # Achievement catalog
│   ├── Milestone.kt               # Milestone data model
│   ├── WellnessRecommendation.kt  # Recommendation data model
│   ├── db/
│   │   ├── MoodDatabase.kt       # Room database (moods + achievements)
│   │   ├── MoodEntry.kt          # Mood entry entity
│   │   ├── MoodDao.kt            # Mood CRUD operations
│   │   ├── AchievementEntity.kt  # Achievement entity
│   │   └── AchievementDao.kt     # Achievement CRUD operations
│   └── repository/
│       ├── MoodRepository.kt     # Mood data access facade
│       ├── AchievementRepository.kt # Achievement data access
│       ├── WellnessRepository.kt # 40+ categorized wellness tips
│       └── PromptEngine.kt       # Contextual prompt generation
│
├── detection/
│   ├── FaceAnalyzer.kt           # 5-frame consensus pipeline
│   ├── FaceBaseline.kt           # Personalized calibration data
│   ├── CalibrationFaceAnalyzer.kt # Calibration-specific analyzer
│   ├── MoodClassifier.kt         # Heuristic mood scoring
│   ├── TFLiteMoodClassifier.kt   # Neural network inference
│   └── MoodResult.kt             # Classification result model
│
├── di/
│   ├── DatabaseModule.kt         # Hilt Room/DAO providers
│   └── RepositoryModule.kt       # Hilt repository providers
│
├── health/
│   ├── HealthConnectManager.kt   # Health Connect API wrapper
│   └── SleepQualityResult.kt     # Sleep analysis data class
│
├── insights/
│   └── InsightsActivity.kt       # Chart-driven analytics screen
│
├── notification/
│   ├── MoodNotificationManager.kt # Notification channel management
│   ├── NotificationScheduler.kt   # WorkManager scheduling
│   ├── MorningWorker.kt          # Morning reminder worker
│   ├── EveningWorker.kt          # Evening summary worker
│   ├── WeeklySummaryWorker.kt    # Weekly digest with trends
│   └── HabitAnalyzer.kt          # Time/day pattern analysis
│
├── security/
│   └── PinStorage.kt             # SHA-256 hashed PIN storage
│
├── service/
│   ├── MoodMonitorService.kt     # Foreground camera service
│   └── MoodTileService.kt        # Quick Settings tile
│
├── ui/
│   ├── main/
│   │   └── MainViewModel.kt      # Dashboard state management
│   ├── timeline/
│   │   ├── TimelineActivity.kt   # Chronological mood entries
│   │   └── TimelineViewModel.kt
│   ├── history/
│   │   ├── HistoryActivity.kt    # Calendar grid view
│   │   ├── HistoryViewModel.kt
│   │   └── CalendarDayView.kt    # Custom calendar cell
│   ├── journal/
│   │   ├── JournalActivity.kt    # Rich text + voice journal
│   │   └── JournalViewModel.kt
│   ├── recommendations/
│   │   └── RecommendationsActivity.kt # 5-category filter + tip cards
│   ├── wellness/
│   │   └── WellnessSessionActivity.kt # Breathing, body scan, gratitude
│   ├── achievements/
│   │   ├── AchievementsActivity.kt   # Achievement grid
│   │   └── AchievementsViewModel.kt
│   ├── correlations/
│   │   ├── CorrelationsActivity.kt   # Mood-health data charts
│   │   └── CorrelationsViewModel.kt
│   ├── calibration/
│   │   └── CalibrationActivity.kt    # Face calibration workflow
│   ├── onboarding/
│   │   └── OnboardingActivity.kt     # First-launch guide
│   ├── settings/
│   │   └── SettingsActivity.kt       # Theme, goals, quiet hours, export
│   ├── privacy/
│   │   ├── PrivacyActivity.kt        # Privacy information
│   │   ├── PrivacyReportActivity.kt  # Data transparency report
│   │   └── PrivacySnapshot.kt        # Data collection snapshot
│   └── lock/
│       └── LockActivity.kt           # Biometric + PIN lock
│
├── util/
│   ├── MoodUtils.kt              # Emoji, color, time formatting
│   ├── MilestoneEngine.kt        # Milestone progress computation
│   ├── MilestoneAdapter.kt       # Achievement RecyclerView adapter
│   ├── VoiceJournalHelper.kt     # On-device speech recognition
│   ├── BottomNavHelper.kt        # M3 bottom nav routing
│   ├── SettingsDialogHelper.kt   # Settings navigation wrapper
│   └── ThemeHelper.kt            # Light/Dark/System + edge-to-edge
│
├── wear/
│   └── WearDataReceiverService.kt # Receives mood logs from Wear OS
│
├── widget/
│   └── MoodWidgetProvider.kt     # Home widget + auto-refresh
│
└── worker/
    ├── AnomalyWorker.kt          # Stress/fatigue anomaly alerts
    ├── BackupWorker.kt           # Rolling JSON auto-backup
    └── DatabaseCleanupWorker.kt  # Periodic database maintenance

wear/                              # Wear OS companion module
└── src/main/java/com/mirrormood/wear/
    └── MainActivity.kt           # 6-mood quick-log + Data Layer sync
```

---

## Design System — Ethereal Archive

MirrorMood uses a custom "Ethereal Archive" design system instead of Material You's wallpaper-based dynamic colors:

- **Surface hierarchy**: 6-level tonal system (`mm_surface` → `mm_surface_container_highest`)
- **No-Line philosophy**: Cards use tonal elevation instead of borders
- **Custom typography**: Manrope (headers) + Inter (body) font pairing
- **Mood palette**: Each mood has distinct light and dark color variants
- **Glassmorphism**: Frosted glass effects for floating elements (70% opacity + 24px backdrop blur)
- **Soulful Gradient**: Primary CTAs use a 135° gradient from primary to primary_container
- **Full dark mode** support via `values-night` resource qualifiers
- **Slide transitions**: Consistent activity slide animations across all screens
- **Edge-to-edge**: Full-width layouts with system bar integration

---

## Multi-Module Structure

```
MirrorMood/
├── app/          # Main Android application
├── wear/         # Wear OS companion app
├── .github/      # CI workflows (android-ci.yml)
├── gradle/       # Version catalog & wrapper
└── stitch/       # Design assets & Ethereal Archive specification
```

---

## CI/CD

The project uses **GitHub Actions** for continuous integration:

- **Trigger**: Pushes and pull requests to `main` branch
- **Environment**: Ubuntu latest with JDK 21 (Temurin)
- **Pipeline**:
  1. Checkout repository
  2. Setup JDK and Gradle
  3. Run unit tests (`testDebugUnitTest`)
  4. Build debug APK (`assembleDebug`)
- **Concurrency**: Auto-cancels in-progress runs for the same branch
