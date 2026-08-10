# هذا الملف يستدعي مصدر مكتبة tun2socks (hev-socks5-tunnel) التي يتم
# تحميلها تلقائياً (git clone) بواسطة مهمة Gradle "fetchTun2socks" قبل كل
# بناء (انظر app/build.gradle) — لن تجد مجلد hev-socks5-tunnel هنا في
# المستودع نفسه، فهو يُجلب طازجاً في كل مرة تُبنى فيها المشروع (محلياً أو
# عبر GitHub Actions)، تماماً كما طلبت.
LOCAL_PATH := $(call my-dir)
include $(LOCAL_PATH)/hev-socks5-tunnel/Android.mk
