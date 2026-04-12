# Privacy Policy — MirrorMood

**Last updated:** April 12, 2026

## Overview

MirrorMood is a mood-tracking application that uses your device's front camera and on-device machine learning to analyze facial expressions and help you understand your emotional patterns. **Your privacy is our absolute priority.**

## What Data We Collect

### Mood Entries
- **Mood label** (e.g., Happy, Stressed, Tired)
- **Detection scores** (smile probability, eye-open probability)
- **Timestamps** of when each entry was recorded
- **Optional journal notes** that you write manually

### What We Do NOT Collect
- ❌ No photos or videos are ever saved
- ❌ No facial images leave your device
- ❌ No personal identification data
- ❌ No location data
- ❌ No contacts or browsing history
- ❌ No analytics or usage tracking
- ❌ No advertising identifiers

## Camera Usage

MirrorMood uses your front-facing camera **exclusively for real-time facial expression analysis**. Here's exactly what happens:

1. The camera feed is processed frame-by-frame using **Google ML Kit Face Detection**, which runs entirely on your device.
2. Each frame produces numerical scores (smile probability, eye openness, head rotation).
3. These scores are used to classify your current mood.
4. **The camera frame is immediately discarded** — no image data is stored, transmitted, or cached.

## Data Storage

All mood data is stored in a **local SQLite database** on your device using Android's Room persistence library. This data:

- Never leaves your device unless you explicitly export it
- Is not synced to any cloud service
- Is not accessible to other apps
- Can be deleted at any time from Settings → Delete All Data

## Data Export

You can export your data in two formats:
- **CSV** — for spreadsheet analysis
- **JSON Backup** — for restoring data on another device

Exports are initiated **only by you** and shared through Android's standard share sheet. MirrorMood has no server to receive this data.

## Third-Party Services

MirrorMood uses the following on-device libraries:

| Library | Purpose | Data Sent Externally |
|---------|---------|---------------------|
| Google ML Kit Face Detection | Facial expression analysis | None — runs on-device |
| AndroidX Room | Local database storage | None |
| AndroidX WorkManager | Background task scheduling | None |

**No data is transmitted to Google, MirrorMood developers, or any third party.**

## Notifications

MirrorMood sends local notifications (morning reminders, evening summaries, weekly reports) using Android's notification system. These are generated entirely on-device from your local mood data.

## Children's Privacy

MirrorMood is not directed at children under 13. We do not knowingly collect data from children.

## Your Rights

You have full control over your data:

- **Access**: View all your mood entries in the Timeline and Journal screens
- **Export**: Download your complete history as CSV or JSON at any time
- **Delete**: Permanently erase individual entries or all data from Settings
- **Revoke Camera**: Deny camera permission at any time through Android Settings

## Changes to This Policy

We may update this privacy policy from time to time. Changes will be reflected in the "Last updated" date above.

## Contact

If you have questions about this privacy policy, please open an issue on our GitHub repository:

**GitHub:** [github.com/arjunav2016-bit/MirrorMood](https://github.com/arjunav2016-bit/MirrorMood)
