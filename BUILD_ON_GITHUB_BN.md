# NOVA APK তৈরি করার নির্দেশনা (বাংলা)

এই ZIP-এ Android Studio project source এবং GitHub Actions build workflow একসাথে আছে। এই পরিবেশে Android SDK/Gradle runtime নেই, তাই এখানে APK compile করে পরীক্ষা করা সম্ভব হয়নি।

## ফোন থেকেই APK বানানোর পথ
1. ZIP-টি GitHub repository-তে upload করে একই folder structure অক্ষত রাখো।
2. GitHub repository-র **Actions** tab খোলো।
3. **NOVA Android APK Build** workflow নির্বাচন করে **Run workflow** চাপো।
4. Build সফল হলে `NOVA-debug-apk` artifact download করো।
5. ZIP artifact খুলে `app-debug.apk` বের করে Android-এ install করো। Android প্রথমবার browser/file manager থেকে install permission চাইতে পারে।

## Build configuration
- Gradle 8.9 (CI-তে setup-gradle action দিয়ে)
- Android Gradle Plugin 8.7.3
- Kotlin 2.0.21
- Java 17
- compileSdk 35 / minSdk 26

## গুরুত্বপূর্ণ সত্য
- `gradlew`, `gradlew.bat` এবং `gradle/wrapper/gradle-wrapper.jar` এখনো নেই।
- CI workflow তাই Wrapper নয়, runner-এ ইনস্টল করা Gradle 8.9 চালায়।
- GitHub Actions-এও build সফল হয়েছে—এমন দাবি করা হচ্ছে না; workflow চালানোর পরেই ফল জানা যাবে।
- এটি এখনো prototype: পূর্ণ Normal/SHADOW reference dashboard, wake-word, online AI backend এবং Parts 1–3-এর অনুপস্থিত source সম্পূর্ণভাবে পুনরুদ্ধার হয়নি।
- Screen capture কেবল Android-এর নিজস্ব অনুমতি নেওয়ার পর কাজ করার জন্য লেখা হয়েছে।
