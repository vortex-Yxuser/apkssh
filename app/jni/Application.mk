APP_ABI := arm64-v8a armeabi-v7a x86_64
APP_PLATFORM := android-24
APP_STL := c++_shared

# hev-socks5-tunnel يسمح بتخصيص اسم الحزمة/الكلاس اللذين سيُصدَّر تحتهما JNI
# حتى يتوافقا مع TProxyService.java الموجود في مشروعنا
APP_CFLAGS := -DPKGNAME=com/example/sshpayloadvpn -DCLSNAME=TProxyService
