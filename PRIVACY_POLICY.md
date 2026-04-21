# Privacy Policy — MirrorMood

**Last updated:** April 21, 2026

## Overview

MirrorMood is a mood-tracking wellness ecosystem that uses your device's front camera and on-device machine learning to analyze facial expressions and help you understand your emotional patterns. The ecosystem includes an Android phone app and an optional Wear OS companion app. **Your privacy is our absolute priority.**

## What Data We Collect

### Mood Entries
- **Mood label** (e.g., Happy, Stressed, Tired, Focused, Bored, Neutral)
- **Detection scores** (smile probability, eye-open probability, confidence scores)
- **Timestamps** of when each entry was recorded
- **Source indicator** (camera-detected or manually logged via Wear OS)
- **Optional journal notes** that you write or dictate manually

### Achievement Data
- **Milestone progress** (e.g., streak counts, total entries logged)
- **Unlock timestamps** for earned achievements

### Health Data (Optional — Health Connect)
If you grant Health Connect permissions, MirrorMood reads (but never writes or modifies):
- **Sleep sessions** — duration and quality stages
- **Step count** — daily totals
- **Heart rate** — resting and active measurements

This data is used solely to correlate with your mood patterns within the Correlations screen. It is read on-demand and not stored separately — only the correlation analysis results are displayed.

### Face Calibration Data
- **Baseline metrics** (neutral smile probability, eye openness, head angle defaults)
- Stored locally to personalize mood classification accuracy
- No images are stored — only numerical calibration values

### What We Do NOT Collect
- ❌ No photos or videos are ever saved
- ❌ No facial images leave your device
- ❌ No personal identification data
- ❌ No location data
- ❌ No contacts or browsing history
- ❌ No analytics or usage tracking
- ❌ No advertising identifiers
- ❌ No cloud sync or server transmission

## Camera Usage

MirrorMood uses your front-facing camera **exclusively for real-time facial expression analysis**. Here's exactly what happens:

1. The camera feed is processed frame-by-frame using **Google ML Kit Face Detection**, which runs entirely on your device.
2. Each frame produces numerical scores (smile probability, eye openness, head rotation angles, blink/wink events).
3. A **TensorFlow Lite neural network** (FER2013-trained) also analyzes face crops on-device for expression recognition.
4. A **5-frame consensus algorithm** ensures mood is only classified when 3+ consecutive frames agree, preventing noisy single-frame errors.
5. **The camera frame is immediately discarded** — no image data is stored, transmitted, or cached.

### Smart Battery Management
- When the screen turns off, the camera switches to battery-saving mode: brief 5-second captures every 60 seconds.
- **Quiet Hours** and **Pause Toggle** suspend the camera entirely.
- 24-hour pause auto-resumes without user intervention.

## Microphone Usage (Optional)

MirrorMood offers **voice journaling** using Android's built-in SpeechRecognizer:
- Speech-to-text processing runs **on-device** (prefers offline mode when available).
- Audio is **not recorded or stored** — only the transcribed text is saved as a journal entry.
- Microphone permission can be denied without affecting any other app functionality.

## Data Storage

All mood data is stored in a **local SQLite database** on your device using Android's Room persistence library. This data:

- Never leaves your device unless you explicitly export it
- Is not synced to any cloud service
- Is not accessible to other apps
- Can be deleted at any time from Settings → Delete All Data
- Is automatically maintained by a periodic database cleanup worker

### Automated Backups
- A local **rolling JSON backup** system keeps the last 5 backups on your device.
- Backups are stored locally and are never transmitted externally.

## Data Export

You can export your data in two formats:
- **CSV** — for spreadsheet analysis
- **JSON Backup** — for restoring data on another device

Exports are initiated **only by you** and shared through Android's standard share sheet. MirrorMood has no server to receive this data.

## Wear OS Companion

If you use the optional Wear OS companion app:
- Mood entries logged on the watch are synced to your phone via the **Wearable Data Layer API**.
- This is a **direct device-to-device** connection — no cloud servers are involved.
- Watch-originated entries are stored in the same local Room database on your phone.
- The watch app does not use any camera or microphone.

## Health Connect Integration

If you grant Health Connect permissions:
- MirrorMood **reads** sleep, step, and heart rate data from Health Connect.
- MirrorMood **never writes** data to Health Connect.
- Health data is used to compute mood-health correlations displayed in the Correlations screen.
- Raw health data is not stored separately in MirrorMood's database.
- You can revoke Health Connect permissions at any time through Android Settings.

## Third-Party Services

MirrorMood uses the following on-device libraries:

| Library | Purpose | Data Sent Externally |
|---------|---------|---------------------|
| Google ML Kit Face Detection | Facial expression analysis | None — runs on-device |
| TensorFlow Lite | Neural network mood inference | None — runs on-device |
| AndroidX Room | Local database storage | None |
| AndroidX WorkManager | Background task scheduling | None |
| Wearable Data Layer API | Watch-phone sync | None — device-to-device only |
| Health Connect Client | Read health metrics | None — local API only |
| Android SpeechRecognizer | Voice-to-text journaling | None — prefers offline |

**No data is transmitted to Google, MirrorMood developers, or any third party.**

## Notifications

MirrorMood sends local notifications using Android's notification system:
- **Morning reminders** — encouraging mood check-ins
- **Evening summaries** — daily mood recap
- **Weekly digests** — week-over-week trend comparisons
- **Anomaly alerts** — proactive notifications when unusual stress or fatigue patterns are detected

All notifications are generated entirely on-device from your local mood data.

## Security

MirrorMood offers multiple layers of security:
- **Biometric Lock**: App access can be guarded by fingerprint or face authentication via AndroidX Biometric.
- **PIN Fallback**: A SHA-256 hashed PIN provides an alternative authentication method. The PIN itself is never stored — only its cryptographic hash.
- **LockActivity**: Serves as the launcher activity, ensuring the app is locked by default when enabled.

## Children's Privacy

MirrorMood is not directed at children under 13. We do not knowingly collect data from children.

## Your Rights

You have full control over your data:

- **Access**: View all your mood entries in the Timeline, History, and Journal screens
- **Correlate**: See how your mood relates to sleep, steps, and heart rate in Correlations
- **Export**: Download your complete history as CSV or JSON at any time
- **Delete**: Permanently erase individual entries or all data from Settings
- **Revoke Camera**: Deny camera permission at any time through Android Settings
- **Revoke Microphone**: Deny microphone permission without affecting other features
- **Revoke Health Connect**: Remove Health Connect permissions through Android Settings
- **Privacy Report**: View a transparent snapshot of all collected data via Privacy Report

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be reflected in the "Last updated" date above.

## Contact

If you have questions about this privacy policy, please open an issue on our GitHub repository:

**GitHub:** [github.com/arjunav2016-bit/MirrorMood](https://github.com/arjunav2016-bit/MirrorMood)
