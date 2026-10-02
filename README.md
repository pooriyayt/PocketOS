# PocketOS

<div align="center">

**Your private daily command center.**

*Your data stays on your device.*

[![Platform](https://img.shields.io/badge/Platform-Android%208.0%2B-brightgreen.svg)](https://developer.android.com)
[![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](LICENSE)
[![Privacy](https://img.shields.io/badge/Privacy-100%25%20Local--First-emerald.svg)](#privacy--security)
[![Release](https://img.shields.io/github/v/release/pooriyayt/PocketOS?include_prereleases&color=emerald)](https://github.com/pooriyayt/PocketOS/releases)

</div>

---

PocketOS is a modern, privacy-first, local-first Android application designed for managing your reminders, daily tasks, and recurring subscriptions in one calm, unified dashboard.

There are **no accounts**, **no cloud sync servers**, and **no third-party tracking**. All data is stored directly on your phone in an **AES-256 encrypted database** managed by the **Android Keystore**.

---

## Key Features

### 🌿 Luxury Emerald UI & Liquid Motion
- **Premium Emerald Theme**: Custom-crafted dark (`#090E0B`) and light (`#F5F9F7`) color schemes with natural neutral tones.
- **Liquid Morphing Navigation**: Dynamic drop/morphing indicator transitions with bouncy icons and selective haptic feedback.
- **Micro-Interactions**: Press-scale feedback, smooth sheet transitions, and standard 48dp+ touch targets across the app.

### 🛡️ 100% Local-First & Privacy-First
- **Zero Cloud Dependence**: Operates completely offline without accounts, login screens, or cloud synchronization.
- **On-Device SQLCipher Encryption**: The local SQLite database is encrypted with AES-256, and encryption keys are generated and protected by the hardware-backed **Android Keystore**.
- **Encrypted Backups**: Export and import your data using password-protected backups encrypted with **AES-256-GCM** and **PBKDF2** key derivation (100,000 rounds).
- **Data Export & Wipe**: Full transparency with plain JSON exports and one-tap complete device data erasure.

### 🎯 Smart Onboarding & Guided Tour
- **3-Step Tailored Setup**: Language (English / فارسی), Theme (Dark / Light / System), and Primary Focus (Reminders, Tasks, Subscriptions, Expenses, Organization, or All).
- **Personalized Tour**: Highlights relevant actions on Home based on what matters most to you.

### 💳 Smart Service & Subscription Tracking
- **Automated Service Recognition**: Recognizes popular services (Netflix, Spotify, GitHub, YouTube, etc.) as you type.
- **Icon Customizer**: Choose from the bundled brand catalog, built-in vector symbol library, custom monogram generator, or website domain matcher.
- **Renewal Alerts & Trial End Tracking**: Timely local notifications before billing cycles repeat or free trials expire.

### 📱 Modern AppWidgets & Housekeeping
- **Glance AppWidgets**: Small, medium, and large responsive home-screen widgets matching the Emerald aesthetic.
- **Daily Housekeeping Worker**: Local midnight maintenance rolling forward renewal dates and pruning historical logs.

### 🔄 In-App GitHub Updates
- Automatically checks official [GitHub Releases](https://github.com/pooriyayt/PocketOS/releases) for new versions.
- Downloads APKs directly with progress indication, verifies **SHA-256 checksums**, and prompts installation via standard Android system package installer.

---

## Security & Play Protect Standards

PocketOS adheres to strict Android security guidelines:
- **Zero Hardcoded Secrets**: No hidden API keys or telemetry tokens.
- **Minimal Permissions**: Only requests essential permissions (`POST_NOTIFICATIONS`, `SCHEDULE_EXACT_ALARM`, `USE_BIOMETRIC`, and `REQUEST_INSTALL_PACKAGES` for updates).
- **Standard Release Configuration**: Built with ProGuard/R8 minification, resource shrinking, non-debuggable flags, and secure network security configs.

---

## Building from Source

### Prerequisites
- **JDK 17** or **JDK 21**
- **Android SDK** (API 26 minSdk, API 36 targetSdk, API 37 compileSdk)
- **Gradle 9.x** (wrapper provided)

### Build Commands

```bash
# Clone the repository
git clone https://github.com/pooriyayt/PocketOS.git
cd PocketOS

# Build debug APK
./gradlew assembleDebug

# Build release APK
./gradlew assembleRelease
```

The resulting APK will be located in:
- Debug: `app/build/outputs/apk/debug/app-debug.apk`
- Release: `app/build/outputs/apk/release/app-release-unsigned.apk`

---

## Repository Structure

```
PocketOS/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── assets/service_catalog.json   # Bundled offline services
│   │   │   ├── java/app/pocketos/
│   │   │   │   ├── core/                    # Clocks, security, pin vault
│   │   │   │   ├── data/                    # Room DB, SQLCipher, backup
│   │   │   │   ├── domain/                  # Insights, smart defaults, catalog
│   │   │   │   ├── notifications/           # Local alarms & broadcast receivers
│   │   │   │   ├── ui/                      # Compose screens, theme, design system
│   │   │   │   ├── updater/                 # GitHub in-app release updater
│   │   │   │   └── widget/                  # Jetpack Glance AppWidgets
│   │   │   └── res/                         # Drawables, layouts, strings
│   ├── build.gradle.kts
│   └── proguard-rules.pro
├── gradle/
├── build.gradle.kts
├── settings.gradle.kts
├── README.md
├── SECURITY.md
└── CONTRIBUTING.md
```

---

## License

```
Copyright 2026 PocketOS Contributors

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0
```
