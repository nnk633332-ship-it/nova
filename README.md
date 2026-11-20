# NOVA V3 — Dual-Mode Always-On Android Assistant

**Version 3.0.0**  
Complete architecture implementation based on the NOVA V3 Brain Engine specification.

---

## Features Implemented

| Module | Status | Description |
|--------|--------|-------------|
| **NovaBrain.kt** | ✅ | Intent classifier + smart fallback (never parrots) |
| **Device Lock / Unlock** | ✅ | DevicePolicyManager + KeyguardManager |
| **Standby ↔ Active UI** | ✅ | Hide/show telemetry panels |
| **Hacker Mode** | ✅ | Crimson theme + 3 live panels (Transcript / Task / Logcat) |
| **MemoryManager** | ✅ | Chronological episodic memory with relative-time query |
| **WebView Avatar** | ✅ | Canvas-based face with lip-sync, angry, shy states |
| **Voice Pipeline** | ✅ | SpeechRecognizer → Brain → TTS loop |

---

## How to Build the APK

### Requirements
- **Android Studio** Hedgehog (2023.1.1) or newer
- JDK 17
- Android SDK 34

### Steps

1. **Open the project**
   ```
   File → Open → select the NOVA_V3 folder
   ```

2. **Sync Gradle**
   - Android Studio will automatically download dependencies.
   - Wait until “Gradle sync finished”.

3. **Add a real launcher icon (recommended)**
   - Right-click `res` → New → Image Asset
   - Or keep the current vector placeholder.

4. **Build APK**
   ```
   Build → Build Bundle(s) / APK(s) → Build APK(s)
   ```
   Output will be at:
   ```
   app/build/outputs/apk/debug/app-debug.apk
   ```

5. **Install on device**
   ```bash
   adb install -r app/build/outputs/apk/debug/app-debug.apk
   ```

---

## First Run Checklist

1. Grant **Microphone** permission when asked.
2. Say **“Hacker mode”** → crimson telemetry panels appear.
3. Say **“Lock”** → Device Admin activation dialog appears (grant it).
4. After admin is granted, **“Lock”** will instantly lock the screen.
5. Say **“Standby”** → panels disappear, only avatar remains.
6. Say **“Nova”** or **“Wake up”** → full dashboard restores.
7. Say **“What did I say ten days ago”** → memory recall.

---

## Project Structure

```
NOVA_V3/
├── app/
│   ├── build.gradle
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/nova/assistant/
│       │   ├── MainActivity.kt          ← Orchestrator
│       │   ├── NovaBrain.kt             ← Intent Engine
│       │   ├── NovaDeviceAdminReceiver.kt
│       │   ├── UnlockReceiver.kt
│       │   ├── MemoryManager.kt
│       │   └── WebViewInterface.kt
│       ├── assets/index.html            ← Avatar + expressions
│       └── res/
│           ├── layout/activity_main.xml
│           ├── xml/device_admin.xml
│           └── values/...
├── build.gradle
├── settings.gradle
└── README.md
```

---

## Voice Commands (English)

| Phrase | Action |
|--------|--------|
| “Lock” / “Lock the screen” | Hardware screen lock |
| “Unlock” | Request keyguard dismiss |
| “Standby” / “Not needed” | Minimal avatar only |
| “Wake up” / “Nova” / “Full mode” | Restore dashboard |
| “Hacker mode” / “Shadow mode” | Crimson + telemetry panels |
| “Normal mode” | Blue standard theme |
| “Remember…” / “Ten days ago” | Episodic memory recall |
| “Who are you” / “How are you” | Smart conversational reply |

---

## Notes & Limitations

- Continuous listening is demo-style (restarts after each result). For production, move SpeechRecognizer into a **Foreground Service**.
- Memory is stored in SharedPreferences as JSON (good for prototype). Replace with **Room + SQLite** or a vector DB for scale.
- Unlock via voice only works reliably when the activity is already on top of the keyguard (Android security restriction).
- The avatar is pure Canvas/JS — no external assets required.

---

**Built from the official NOVA V3 Architecture & Brain Engine specification.**
