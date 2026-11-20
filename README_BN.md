# NOVA Android Project

এই Project-এ Part 4–8-এর মূল ZIP reference হিসেবে রাখা হয়েছে এবং একটি reconstructed Android app shell আছে।

## Build configuration
- Android application ID: `com.nova.assistant`
- Compile SDK: 35
- Minimum Android: 8.0 (API 26)
- Target SDK: 35
- Kotlin plugin: 2.0.21
- Android Gradle Plugin: 8.7.3

## Build status
এই প্যাকেজে Android Gradle Plugin 8.7.3, Kotlin 2.0.21, compile SDK 35 এবং Java/Kotlin target 17 নির্দিষ্ট করা আছে। INTERNET permission-ও যোগ করা হয়েছে যাতে ভবিষ্যতে নিরাপদ backend সংযোগ করা যায়।

**গুরুত্বপূর্ণ:** এই প্যাকেজে `gradlew`, `gradlew.bat` এবং `gradle-wrapper.jar` নেই। তাই এখানে APK তৈরি বা compile verification করা হয়নি। ফোন থেকে GitHub Actions দিয়ে Gradle 8.9 ব্যবহার করে APK build করার workflow যোগ করা হয়েছে। বিস্তারিত `BUILD_ON_GITHUB_BN.md`-তে।

## Features currently in source
- Bengali speech input via Android SpeechRecognizer (`bn-IN`)
- Bengali text-to-speech via Android TTS
- Basic local command handling
- Wi-Fi, Bluetooth, Settings shortcuts
- Battery level
- One-shot screenshot flow requiring Android's explicit screen-capture consent

## Important limitations
- INTERNET permission আছে, কিন্তু কোনো online AI backend/API endpoint বা অনুমোদিত credential দেওয়া হয়নি; তাই অনলাইন AI এখনো চালু নয়। API secret কখনো APK-র মধ্যে hard-code করা উচিত নয়.
- No always-on wake-word service is implemented.
- পাওয়া reference image `app/src/main/res/drawable-nodpi/nova_normal_reference.png`-এ রাখা আছে। এটি নীল avatar-এর একটি ছোট reference; সম্পূর্ণ Normal/SHADOW split dashboard-এর locked artwork/layout নয়। তাই সেটি দিয়ে আসল dashboard-এর বদলি design বানানো হয়নি.
- Original Parts 1–3 source files were not available; the current base is a reconstruction, not a verbatim recovery.
- See `docs/MISSING_ITEMS_BN.md` for details.
