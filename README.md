# SSH Payload VPN — يبنى تلقائياً على GitHub

تطبيق Android (Kotlin) يبني نفق **SSH** مع **Payload** لتمويه الاتصال، **بروكسي وسيط** اختياري، وتوجيه **كامل حركة الجهاز** عبر tun2socks حقيقي.

## ✅ يبنى تلقائياً عند الرفع على GitHub
عند رفع هذا المستودع إلى GitHub، سير عمل `.github/workflows/build.yml` يعمل تلقائياً عند كل `push` إلى `main`/`master` (أو يدوياً من تبويب **Actions → Run workflow**)، وينتج ملف **APK** جاهز يمكنك تحميله من تبويب **Actions** (قسم Artifacts) دون أي إعداد يدوي من طرفك.

## ✅ tun2socks حقيقي — يُجلب ويُبنى تلقائياً
لا حاجة لإضافة أي شيء يدوياً. أضفت مكتبة **[hev-socks5-tunnel](https://github.com/heiher/hev-socks5-tunnel)** (tun2socks حقيقي مفتوح المصدر، خفيف وسريع، +1900 نجمة على GitHub) وربطتها بالكامل:

- مهمة Gradle `fetchTun2socks` تُنزّل مصدرها تلقائياً (`git clone`) قبل كل بناء.
- مهمة `buildTun2socks` تُصرّفها عبر `ndk-build` تلقائياً (محلياً أو على GitHub Actions).
- `TProxyService.java` هو الجسر الحقيقي (JNI) لهذه المكتبة، بنفس التوقيع الموثّق رسمياً من المطوّر.
- `MyVpnService.kt` يستدعيها ليمرر **كل** حركة مرور الجهاز (وليس تطبيقاً واحداً فقط) عبر نفق SSH.

لا يوجد أي جزء "ناقص" أو "TODO" متبقٍ في هذه السلسلة — الأنبوب كامل من واجهة الإدخال إلى الجهاز بأكمله.

## بنية المشروع
```
SSHPayloadVPN/
├── .github/workflows/build.yml     # بناء APK تلقائي عند كل رفع
├── app/jni/Android.mk              # يستدعي مصدر tun2socks المُجلب تلقائياً
├── app/jni/Application.mk          # إعدادات NDK + تخصيص اسم الحزمة لـ JNI
├── app/src/main/java/.../
│   ├── MainActivity.kt             # واجهة الإدخال: SSH + Payload + بروكسي
│   ├── MyVpnService.kt             # VpnService + استدعاء tun2socks
│   ├── SshTunnelManager.kt         # فتح جلسة SSH وتشغيل SOCKS5
│   ├── PayloadSocketFactory.kt     # إرسال Payload قبل مصافحة SSH + بروكسي
│   ├── Socks5Server.kt             # خادم SOCKS5 محلي فوق قناة SSH
│   └── TProxyService.java          # جسر JNI الحقيقي لـ tun2socks
```

## خطوات الاستخدام
1. أنشئ مستودع GitHub جديد وارفع محتوى هذا المجلد إليه (`git init && git add . && git commit -m init && git push`).
2. افتح تبويب **Actions** في المستودع — سيبدأ البناء تلقائياً؛ انتظر اكتماله (~5-10 دقائق أول مرة بسبب تصريف tun2socks).
3. حمّل ملف APK من قسم **Artifacts** أسفل تشغيلة العمل (workflow run) الناجحة، وثبّته على جهازك.
4. أو للبناء محلياً: افتح المشروع في Android Studio (يحتاج NDK مثبتاً من SDK Manager) واضغط Run — كل شيء (تحميل وبناء tun2socks) يحدث تلقائياً ضمن دورة Gradle.

## حقول SSH في التطبيق (موجودة بالفعل)
شاشة التطبيق تحتوي أصلاً على: **Host/IP**، **Port**، **اسم المستخدم**، و**كلمة المرور** الخاصة بحساب SSH — لا حاجة لإضافتها، فقط أدخل بيانات حسابك فيها.

**بخصوص طلب تزويدك بحساب SSH جاهز:** لا أملك أي خادم لأعطيك بياناته — لست مزوّد استضافة ولا أشغّل خوادم SSH. للحصول على حساب لتجربة التطبيق، لديك خياران مشروعان:
- استئجار خادم (VPS) خاص بك بسعر رخيص جداً (Contabo, Hetzner, DigitalOcean...) وتفعيل SSH عليه بنفسك — هذا الأضمن والأنسب فنياً وقانونياً.
- بعض المزودات (Oracle Cloud, Google Cloud) تقدم طبقة مجانية (Free Tier) يمكن تفعيل SSH عليها.

في الحالتين، أدخل الـ Host/Port/User/Password الخاصة بخادمك في الحقول الموجودة بالتطبيق.

## مثال Payload شائع
```
GET / HTTP/1.1[crlf]Host: [host][crlf]Connection: Upgrade[crlf][crlf]
```

## ملاحظة استخدام
هذه التقنية شائعة لتجاوز القيود على الشبكات والحفاظ على الخصوصية. تأكد أن استخدامك متوافق مع شروط مزود الخدمة والقوانين المعمول بها في بلدك، خصوصاً إن كان الهدف الوصول لبيانات مجانية غير مخصصة لك تعاقدياً من مشغل الاتصالات.
