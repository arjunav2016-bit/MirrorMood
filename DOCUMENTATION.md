# MirrorMood Documentation

## Project Overview
MirrorMood is a privacy-focused Android application designed to track a user's emotional state in real-time. By utilizing the device's front camera and Google ML Kit's face detection technology, the app analyzes facial expressions to classify mood into six categories: **Happy**, **Stressed**, **Tired**, **Focused**, **Bored**, and **Neutral**. All data processing is explicitly performed on-device.

### Key Goals
- Empower users with intuitive insights into their daily and weekly emotional patterns.
- Ensure 100% data privacy with local-only storage and on-device machine learning inference.
- Deliver automated and contextual wellness recommendations based on the user's continuously evaluated emotional state.

---

## Core Features

### 1. Real-Time Emotion Detection
- Integrates **CameraX** with **Google ML Kit** Face Detection to interpret attributes such as smile probability, head rotation (pitch, yaw, roll), eye openness, and blink rates.
- Employs a temporal smoothing algorithm with a sliding window threshold to confidently classify mood.

### 2. Deep Insights & Analytics
- Segregated **Today** and **This Week** analytical layouts.
- Presents dominant moods, historical peak times, and mood/habit correlation patterns using dynamic, customized MPAndroidChart visuals.

### 3. Timeline, History & Journal
- Detailed chronological timeline tracking all registered emotional states.
- Dedicated calendar-based history grid.
- Rich-text journal entry mechanism to contextualize detected periods of stress, happiness, or fatigue.

### 4. Background Processing & Contextual Nudges
- **WorkManager-based Scheduling:** Triggers morning reminders, evening summaries, and robust weekly statistical digests.
- Provides an index of over 40 wellness recommendations tailored immediately to the sensed mood.

### 5. Security & Customization
- **Biometric App Lock:** Sensitive historical mood data is guarded natively via Android biometric prompts.
- **Privacy Controls:** Settings support local CSV data exports, bulk erasure, and 24-hour pause features for camera isolation.

---

## Technical Architecture

The codebase adheres strongly to modern Android development standards, specifically enforcing an **MVVM (Model-View-ViewModel)** architectural pattern. It is fully integrated with **Clean Architecture** concepts, primarily maintaining strict boundaries around the repository layer and state propagation (via Flow/StateFlow).

### Selected Technology Stack
- **Languages:** Kotlin
- **SDK Targets:** Min API 26 (Android 8.0) | Target API 36
- **Dependency Injection:** Dagger Hilt globally coordinates ViewModels, application scopes, and Worker classes.
- **User Interface:** XML ViewBinding integrated deeply with a custom semantic color/token system known as *Ethereal Archive*. Activity navigation adheres to Material Design 3 guidelines.
- **Camera/Perception:** CameraX v1.3.0 combined seamlessly with ML Kit Face Detection v16.1.7.
- **Local Persistence:** Room Database v2.8.0 combined with Kotlin Symbol Processing (KSP).
- **Background Tasks:** AndroidX WorkManager handles resilient deferred jobs.
- **Other Notables:** AndroidX Core SplashScreen API and Biometric Prompt API.

### Package Structure 
- `data/` -> Core SQLite schema implementations (Room entities, DAOs) alongside repository facades.
- `detection/` -> Image Analysis operations, algorithmic inference on facial characteristics, and confidence score calculation wrappers.
- `insights/ & correlations/` -> Chart controllers, custom rendering logic, and specific analytical ViewModels.
- `notification/` -> Subroutines defining application notification channels and `CoroutineWorker` classes handling daily/weekly cron routines.
- `service/` -> The Foreground Service component ensuring camera availability, handling "Quiet Hours," and regulating battery usage when the screen is disabled.
- `ui/` -> Primary application modules encapsulating core workflows: `main`, `timeline`, `history`, `journal`, `recommendations`, `settings`. 
- `util/` -> UI assistance classes containing emoji rendering utilities, relative time generation, BottomNav normalization, and dark/light mode toggles.
- `widget/` -> Lightweight implementations supporting native Android launcher AppWidgets.
