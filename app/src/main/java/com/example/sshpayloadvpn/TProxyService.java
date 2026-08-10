package com.example.sshpayloadvpn;

/**
 * جسر JNI لمكتبة tun2socks الحقيقية: hev-socks5-tunnel
 * (https://github.com/heiher/hev-socks5-tunnel)
 *
 * توقيع الدوال هنا مطابق تماماً لما توثقه المكتبة نفسها؛ الحزمة/الكلاس
 * (com.example.sshpayloadvpn.TProxyService) محدَّدان في app/jni/Application.mk
 * عبر APP_CFLAGS=-DPKGNAME=...-DCLSNAME=TProxyService حتى يتطابقا مع رموز
 * JNI المصدَّرة من المكتبة الأصلية.
 */
public class TProxyService {

    /** يبدأ نفق tun2socks: يقرأ إعدادات socks5 من ملف yaml ويستخدم fd الخاص بواجهة VPN */
    public static native boolean TProxyStartService(String configPath, int fd);

    /** يوقف النفق */
    public static native boolean TProxyStopService();

    /** هل النفق يعمل حالياً */
    public static native boolean TProxyIsRunning();

    /** إحصائيات النقل (بايتات مرسلة/مستقبلة) */
    public static native long[] TProxyGetStats();

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }
}
