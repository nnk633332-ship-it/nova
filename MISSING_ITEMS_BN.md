# NOVA project audit — কী আছে, কী নেই

## আছে
- Android app source: MainActivity.kt ও ScreenCaptureService.kt
- AndroidManifest.xml, Gradle Kotlin build files
- Bengali SpeechRecognizer/TTS ভিত্তিক prototype
- Local command handling, Wi-Fi/Bluetooth/Settings shortcut, battery display
- Android consent flow-এর পর one-shot screenshot capture-এর source
- Reference image asset এবং Parts 4–8-এর original ZIP archive
- GitHub Actions workflow: Gradle 8.9 দিয়ে `assembleDebug` চালিয়ে APK artifact প্রকাশের চেষ্টা করবে

## নেই / এখনো যাচাই হয়নি
- `gradlew`, `gradlew.bat`, `gradle/wrapper/gradle-wrapper.jar`
- Parts 1–3-এর original source
- সম্পূর্ণ original Normal/SHADOW split dashboard implementation; reference PNG একা সম্পূর্ণ UI নয়
- Online AI backend endpoint ও নিরাপদ server-side credential
- Always-on wake word implementation
- এই প্যাকেজের জন্য সফল Android build log, device installation test বা APK output

## Build status
এটি একটি repaired/reconstructed Android prototype source package; verified installable APK নয়। GitHub Actions workflow যোগ করা হয়েছে, কিন্তু workflow চালিয়ে সফল artifact না পাওয়া পর্যন্ত build success বলা যাবে না।
