# 🎙️ فقاعة الفويس — VoiceBubble

فقاعة عائمة فوق الواتساب بتفرّغ الفويسات لكلام مكتوب **أوفلاين** على الموبايل بـ [whisper.cpp](https://github.com/ggml-org/whisper.cpp).

## المميزات
- فقاعة بتتسحب لأي مكان وبتلزق في الجنب، ودوسة عليها تفتح كارت بآخر الفويسات.
- تفريغ تلقائي لأي فويس جديد (ممكن تقفله وتفرّغ بالضغط).
- واتساب العادي + واتساب بيزنس.
- عربي (متظبط على المصري) / تلقائي / إنجليزي.
- نسخ النص بضغطة، وكاش للنصوص اللي اتفرّغت.
- الفقاعة برتقالي = بتفرّغ، الرقم الأحمر = فويسات جديدة.

## طريقة البناء

### الأسهل: GitHub Actions (من غير ما تنزّل حاجة)
1. ارفع الفولدر ده على ريبو جديد في GitHub (branch اسمه `main`).
2. الـ Action هيشتغل لوحده (حوالي 10 دقايق).
3. هتلاقي `VoiceBubble.apk` في **Releases → latest**، حمّله من الموبايل وسطّبه.

### أو على جهازك بـ Android Studio
```bash
git clone --depth 1 --branch v1.9.5 https://github.com/ggml-org/whisper.cpp third_party/whisper.cpp
./gradlew :app:assembleRelease   # أو افتح المشروع في Android Studio
```
محتاج Android SDK + NDK + CMake (Android Studio بينزّلهم لوحده).

## أول تشغيل
1. صلاحية الملفات (All files access) عشان يقرا فولدر فويسات الواتس.
2. صلاحية الظهور فوق التطبيقات.
3. حمّل موديل: **Base** (~60MB، سريع) أو **Small** (~190MB، أدق في المصري).
4. شغّل الفقاعة.

## ملاحظات تقنية
- الفويسات بتتقري من `Android/media/com.whatsapp/WhatsApp/Media/WhatsApp Voice Notes/`.
- الصوت بيتفك بـ MediaCodec (opus) وبيتحوّل لـ 16kHz mono.
- مبني لـ arm64-v8a مع fp16 (أي موبايل من 2018 وطالع).
- صلاحية All-files-access مش مسموحة على Google Play لنوع التطبيق ده، فهو للاستخدام الشخصي / التوزيع المباشر كـ APK.
