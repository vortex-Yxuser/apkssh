package com.example.sshpayloadvpn

import android.content.Context

/**
 * إعدادات الاتصال: خادم SSH + الـ Payload + البروكسي.
 */
data class TunnelConfig(
    val sshHost: String,
    val sshPort: Int,
    val sshUser: String,
    val sshPass: String,
    val payload: String,          // مثال: CONNECT [host_port] HTTP/1.1[crlf]Host: example.com[crlf][crlf]
    val proxyEnabled: Boolean,
    val proxyHost: String,
    val proxyPort: Int,
    val localSocksPort: Int = 10808
)

object Prefs {
    private const val NAME = "ssh_payload_vpn_prefs"

    fun save(ctx: Context, c: TunnelConfig) {
        ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE).edit().apply {
            putString("host", c.sshHost)
            putInt("port", c.sshPort)
            putString("user", c.sshUser)
            putString("pass", c.sshPass)
            putString("payload", c.payload)
            putBoolean("proxyEnabled", c.proxyEnabled)
            putString("proxyHost", c.proxyHost)
            putInt("proxyPort", c.proxyPort)
            putInt("localSocksPort", c.localSocksPort)
            apply()
        }
    }

    fun load(ctx: Context): TunnelConfig {
        val p = ctx.getSharedPreferences(NAME, Context.MODE_PRIVATE)
        return TunnelConfig(
            sshHost = p.getString("host", "") ?: "",
            sshPort = p.getInt("port", 22),
            sshUser = p.getString("user", "") ?: "",
            sshPass = p.getString("pass", "") ?: "",
            payload = p.getString("payload", "CONNECT [host_port] HTTP/1.1[crlf]Host: api.snapchat.com[crlf][crlf]") ?: "",
            proxyEnabled = p.getBoolean("proxyEnabled", true),
            proxyHost = p.getString("proxyHost", "34.43.46.91") ?: "",
            proxyPort = p.getInt("proxyPort", 443),
            localSocksPort = p.getInt("localSocksPort", 10808)
        )
    }
}
