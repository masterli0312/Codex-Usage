<div align="center">

# ⚡ Codex Quota Monitor for Android

**A modern, privacy-first Android client to monitor real-time OpenAI Codex and ChatGPT Plus/Team quotas, rate limits, and subscription renewal schedules across multiple accounts.**

[![Release](https://img.shields.io/badge/Release-v1.2.0-10B981?style=for-the-badge&logo=github)](https://github.com/boudywho/codex-quota-android/releases)
[![Android](https://img.shields.io/badge/Android-8.0%2B%20(API%2026%2B)-3DDC84?style=for-the-badge&logo=android&logoColor=white)](https://developer.android.com)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=for-the-badge&logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![Jetpack Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%26%20M3-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
[![License: MIT](https://img.shields.io/badge/License-MIT-F59E0B?style=for-the-badge)](LICENSE)

<br/>

<p align="center">
  <img src="docs/banner.png" width="100%" alt="Codex Quota Monitor showcase banner demonstrating quota gauges, pacing indicators, and home-screen widgets" />
</p>

</div>

---

## 🚀 What's New in v1.2.0

- **Dual Quota Windows**: View simultaneous **Weekly** and **5-hour** rolling quota windows with independent remaining/used gauges for ChatGPT Plus, Team, and Enterprise accounts.
- **Independent Reset Countdowns**: Real-time rollover timers for each quota window (e.g., `4d 12h`, `2h 15m`).
- **Weekly Ideal-Pace Guidance**: Visual pacing indicators (**Ahead of ideal pace**, **On ideal pace**, **Behind ideal pace**) calculated from weekly remaining capacity and time to reset.
- **Granular 5-Hour Alert Controls**: Choose whether low-quota notifications apply only to weekly limits (default) or extend to 5-hour rolling windows via Settings.
- **Separate Alert Deduplication**: Independent milestone tracking per window prevents duplicate alert notifications across rolling reset cycles.
- **Polished Material 3 Dashboard**: Refined account cards and detail views make weekly pacing and both quota windows easier to scan.

---

## 📑 Table of Contents

- [What's New in v1.2.0](#-whats-new-in-v120)
- [Key Features](#-key-features)
  - [Dual Quota Windows & Reset Timers](#-dual-quota-windows--reset-timers)
  - [Weekly Ideal-Pace Guidance](#-weekly-ideal-pace-guidance)
  - [Smart Background Sync & Milestone Alerts](#-smart-background-sync--milestone-alerts)
  - [Authentication & Account Management](#-authentication--account-management)
  - [Material 3 UI & Home-Screen Widgets](#-material-3-ui--home-screen-widgets)
- [Security & Privacy](#-security--privacy)
- [Download & Installation](#-download--installation)
- [Architecture & Technology Stack](#-architecture--technology-stack)
- [Building from Source](#-building-from-source)
  - [Prerequisites](#prerequisites)
  - [Clone & Build](#clone--build)
  - [Release Signing](#release-signing)
  - [CI and Releases](#ci-and-releases)
- [Legal Disclaimer](#-legal-disclaimer)
- [License](#-license)

---

## ✨ Key Features

### ⏱️ Dual Quota Windows & Reset Timers
- **Simultaneous Rolling Windows**: Tracks both **Weekly** (primary 7-day window) and **5-hour** (secondary rolling window) quota capacities on active subscriber accounts when provided by OpenAI's usage endpoints.
- **Independent Countdown Timers**: Real-time countdowns indicating the exact duration until each window rolls over (e.g., `5d 4h`, `1h 30m`, `45m`).
- **Banked Reset Tracking**: Displays available banked usage-limit resets along with next expiration timestamps when returned in the usage payload.
- **Platform API Rate Limits**: For developer API-key accounts, monitors Requests Per Minute (RPM) and Tokens Per Minute (TPM) against tier limits with dedicated visual meters.

### 📊 Weekly Ideal-Pace Guidance
To assist with budgeting capacity across the 7-day cycle, Codex Quota compares current remaining weekly capacity against an **ideal linear consumption curve**:

| Pacing Status | Condition | Meaning |
| :--- | :--- | :--- |
| **Ahead of ideal pace** | Actual remaining is more than 5 percentage points above ideal | Quota is being consumed slower than the linear guide; surplus capacity remains. |
| **On ideal pace** | Actual remaining is within 5 percentage points of ideal | Usage aligns closely with the linear weekly guide. |
| **Behind ideal pace** | Actual remaining is more than 5 percentage points below ideal | Quota is being consumed faster than the linear guide; it may reach the limit before reset. |

> **How Pacing Works**: Ideal remaining capacity is calculated linearly from the time remaining until reset:
> $$\text{Ideal Remaining \%} = \left(\frac{\text{Time Until Reset}}{\text{7 Days}}\right) \times 100$$
> *Note: This pacing indicator is a reference guide for linear planning based on current percentage and reset time. It does not measure historical token logs or guarantee future availability.*

### 🔔 Smart Background Sync & Milestone Alerts
- **WorkManager Background Sync**: Configurable periodic background sync (15 min, 30 min, 1 hr, 3 hr, 6 hr) with automatic network retry constraints.
- **Multi-Threshold Quota Alerts**: Receive Android notifications when remaining quota drops below selected milestone thresholds (≤5%, ≤10%, ≤25%).
- **Optional 5-Hour Alerts**: Weekly quota is monitored by default; toggle "Include 5-hour quota warnings" in Settings to monitor short-term burst limits too.
- **Per-Window Deduplication**: Milestone alerts fire once as quota enters a lower threshold and reset when capacity recovers above configured limits, tracking weekly and 5-hour windows independently.
- **Signed-Out Detection**: Sends an alert after a background refresh detects that authentication tokens expired or became invalid.

### 🔑 Authentication & Account Management
- **OAuth 2.0 Device Code Authorization**: Sign in using standard OAuth device authorization (`https://auth.openai.com/codex/device`) with PKCE token exchange. No raw secret entry required.
- **Platform API Key Support**: Add direct API keys for standard developer endpoints.
- **Multi-Account Organization**: Custom account nicknames, plan badges (Plus, Team, Enterprise, API), and custom accent color indicators.
- **Subscription Renewal Tracking**: Configurable renewal dates and countdowns to monitor monthly/yearly billing cycles alongside live quotas.

### 📱 Material 3 UI & Home-Screen Widgets
- **Jetpack Compose & Material You**: Adaptive dynamic color palette support, light/dark theme switching, and smooth edge-to-edge layout.
- **Jetpack Glance Widgets**:
  - **Small (2x2)**: Circular gauge, remaining percentage, and reset timer.
  - **Medium (4x2 / 4x1)**: Responsive card with primary quota bar, reset countdown, secondary 5-hour badge, and auto-adapting 1-row compact mode.
  - **Multi-Account (4x3 / 4x4)**: Monitor up to 5 accounts simultaneously with status indicators and individual quota levels.
  - **Custom Widget Themes**: Choose between *Codex Dark* (obsidian) and *Material You* dynamic themes.

---

## 🔒 Security & Privacy

Codex Quota is built with a privacy-first, on-device architecture:

| Aspect | Implementation Details |
| :--- | :--- |
| **Hardware Keystore Encryption** | API keys and OAuth tokens are encrypted on-device with **AES-256-GCM** using cryptographic keys from the Android Keystore system. |
| **Direct Communication** | All HTTPS requests communicate directly with OpenAI authentication and API endpoints (`auth.openai.com`, `api.openai.com`, `chatgpt.com`) with no intermediate proxy servers. |
| **Zero Telemetry & Tracking** | No analytics frameworks, crash trackers, advertising identifiers, or user tracking. |
| **Backup Protection** | Sensitive credential storage files are explicitly excluded from Android cloud backup and device transfer rules (`backup_rules.xml` and `data_extraction_rules.xml`). |

---

## 📥 Download & Installation

Download the latest release APK directly from GitHub Releases:

👉 **[Download Latest APK (GitHub Releases)](https://github.com/boudywho/codex-quota-android/releases/latest)**

### Installation Steps
1. Download `codex-quota-vX.X.X.apk` onto your Android device (requires Android 8.0+ / API 26+).
2. Open the downloaded file to install (allow "Install unknown apps" if prompted).
3. Launch **Codex Quotas** and tap **+** to add your first account.

> **One-Time Signing Migration Note**: If upgrading from an older debug-signed APK, uninstall that build once before installing the production-signed release. Subsequent releases signed with the production certificate can update directly without uninstalling.

---

## 🏗️ Architecture & Technology Stack

Codex Quota follows modern Android architecture and clean separation-of-concerns practices:

```
app/
├── auth/          # OAuth 2.0 PKCE device authorization & JWT parsing
├── data/
│   ├── local/     # Room database, Encrypted SharedPreferences, Preferences DataStore
│   └── remote/    # OkHttp client, Kotlinx Serialization, OpenAI usage endpoints
├── domain/        # Domain entities, use cases, pacing evaluator, repository contracts
├── notifications/ # Quota threshold & auth state notification managers
├── security/      # Android Keystore Manager & AES-256-GCM credential store
├── ui/
│   ├── components/# Circular gauges, linear quota bars, pacing badges, metric cards
│   ├── feature/   # Dashboard, Account Detail, Add Account, Settings, About, Onboarding
│   ├── navigation/# Jetpack Compose type-safe navigation
│   └── theme/     # Material Design 3 color schemes, typography, dynamic theming
├── widget/        # Jetpack Glance widgets (Small, Medium, Multi-Account) & config
└── worker/        # AndroidX WorkManager periodic background synchronization
```

- **Language & Coroutines**: Kotlin 2.0.21, Coroutines & Flow (JVM Target 17)
- **UI Framework**: Jetpack Compose (BOM 2024.11.00) with Material Design 3
- **Local Persistence**: Room SQLite 2.6.1 + Preferences DataStore 1.1.1
- **Security**: Android Keystore + AndroidX Security Crypto (AES-256-GCM)
- **Background Tasks**: AndroidX WorkManager 2.10.0
- **Home Widgets**: Jetpack Glance 1.1.1
- **Networking**: Square OkHttp 4.12.0 + Kotlinx Serialization JSON 1.7.3

---

## 🛠️ Building from Source

### Prerequisites
- **JDK**: Java Development Kit 17 or 21
- **Android Studio**: Ladybug (2024.2.1+) or Gradle 8.11+
- **Android SDK**: Platform API 35 (Compile SDK 35, Min SDK 26, Target SDK 35)

### Clone & Build

```bash
# Clone the repository
git clone https://github.com/boudywho/codex-quota-android.git
cd codex-quota-android

# Run unit tests
./gradlew test

# Assemble Debug APK
./gradlew assembleDebug

# Output APK location:
# app/build/outputs/apk/debug/app-debug.apk
```

*Debug builds and unit tests do not require release signing credentials.*

### Release Signing

Release APKs must be signed with a production certificate. Create a `signing.properties` file in the repository root (this file is git-ignored; do not commit keys or passwords):

```properties
storeFile=path/to/release.keystore
storePassword=your-store-password
keyAlias=your-key-alias
keyPassword=your-key-password
```

`storeFile` can be specified as an absolute path or a path relative to the project root. Each property can also be set via environment variables:

| `signing.properties` Key | Environment Variable |
| :--- | :--- |
| `storeFile` | `ANDROID_KEYSTORE_FILE` |
| `storePassword` | `ANDROID_KEYSTORE_PASSWORD` |
| `keyAlias` | `ANDROID_KEY_ALIAS` |
| `keyPassword` | `ANDROID_KEY_PASSWORD` |

Build a production APK with:
```bash
./gradlew assembleRelease
```
*The build configuration executes a `validateReleaseSigning` verification task that fails with a descriptive error if signing credentials or the keystore file are missing, preventing unauthenticated fallback to debug certificates.*

### CI and Releases

GitHub Actions runs unit tests, Android lint, and a debug APK build on pulls and pushes to `main`. Tagged releases require the following GitHub Actions secrets:

- `ANDROID_KEYSTORE_BASE64`: Base64-encoded production keystore
- `ANDROID_KEYSTORE_PASSWORD`: Keystore password
- `ANDROID_KEY_ALIAS`: Key alias
- `ANDROID_KEY_PASSWORD`: Key password

To publish a release:
1. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
2. Push a matching Git tag (e.g., `v1.2.0` for `versionName = "1.2.0"`).
3. The tag workflow verifies version alignment, decodes the keystore temporarily in the runner environment, compiles the signed release APK, and attaches `codex-quota-vX.X.X.apk` to the GitHub Release.

---

## ⚖️ Legal Disclaimer

Codex Quota is an independent open-source project developed for developers and power users to monitor personal API usage and subscription windows.

- This application is **not** created, affiliated with, authorized, maintained, sponsored, or endorsed by OpenAI, Inc.
- OpenAI, ChatGPT, Codex, and GPT are trademarks or registered trademarks of OpenAI, Inc.

---

## 📄 License

Distributed under the **MIT License**. See [`LICENSE`](LICENSE) for more information.
