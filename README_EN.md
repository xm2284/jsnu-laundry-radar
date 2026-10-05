# 🧺 JSNU Laundry Radar

[中文文档 (Chinese)](README.md) | English Documentation

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/License-MIT-orange?style=flat-square" alt="License" />
</p>

An Android client that turns your dorm's shared laundry room into a **live availability radar**: real-time machine states, auto-stalking for a free washer, and washer-finish notifications.

---

## 💡 Why

Students often walk up several floors only to find every washer busy, or forget to collect laundry after it finishes. This app lets you:

- **See** every washer / shoe-washer / dryer state from your dorm;
- **Stalk** a chosen laundry room — get notified the instant a machine frees up (edge-triggered, 30-min cooldown, 15s high-frequency burst after a hit);
- **Track** your own running washer — per-minute countdown and a high-priority "come collect your laundry" alert when it finishes;
- **Glance** at home-screen widgets with free-machine counts and live countdown.

## ✨ Features

- Real-time device status from the public, read-only campus laundry status API
- Derived states: `Idle` / `Reservable` / `Taken` / `Running-Locked` / `Fault`
- Smart sorting by soonest availability
- Foreground **Watch Service** with edge-triggered notifications and TTL auto-stop
- **Wash tracking** with per-minute countdown and finish alert
- Two **home-screen widgets**
- Multi-point & favourite management, onboarding, theme modes, usage stats
- Optional **Huawei AGC** anonymous auth + Cloud DB user profile (gracefully degrades)
- Optional **Umeng** analytics & push (skipped safely when unconfigured)

## 🏗️ Tech Stack

Kotlin · Jetpack Compose Material 3 · Retrofit/OkHttp · kotlinx.serialization · Coroutines/Flow · DataStore · WorkManager · Foreground Service · Huawei AGC · JUnit/Robolectric/Roborazzi.

## 🚀 Build

```bash
git clone https://github.com/xm2284/jsnu-laundry-radar.git
cd jsnu-laundry-radar
# Optional: copy app/agconnect-services.json.example -> app/agconnect-services.json
# Optional: copy local.properties.example -> local.properties (Umeng keys)
./gradlew assembleDebug
```

> Android Studio Ladybug or newer · JDK 17 · Android SDK 35.

## 🔐 Security

No credentials are hardcoded. Huawei AGC credentials and Umeng keys are externalized and git-ignored; core features keep working when they are absent. Only anonymous identifiers are used.

## ⚠️ Disclaimer

Unofficial third-party tool, not affiliated with the laundry platform or the university. It only calls public read-only status APIs. For learning and personal use only.

## 📄 License

[MIT License](LICENSE).
