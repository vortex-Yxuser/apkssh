package com.example.sshpayloadvpn

import com.jcraft.jsch.JSch
import com.jcraft.jsch.Session
import java.util.Properties

class SshTunnelManager(
    private val config: TunnelConfig,
    private val onLog: (String) -> Unit = {}
) {
    private var session: Session? = null
    private var socksServer: Socks5Server? = null

    @Throws(Exception::class)
    fun connect(): Session {
        val jsch = JSch()
        val s = jsch.getSession(config.sshUser, config.sshHost, config.sshPort)
        s.setPassword(config.sshPass)
        s.setSocketFactory(PayloadSocketFactory(config, onLog))

        val props = Properties().apply {
            put("StrictHostKeyChecking", "no")
            // خوارزميات إضافية لضمان التوافق مع خوادم SSH الحديثة
            put("kex", "diffie-hellman-group1-sha1,diffie-hellman-group14-sha1,ecdh-sha2-nistp256,ecdh-sha2-nistp384,ecdh-sha2-nistp521,diffie-hellman-group-exchange-sha1,diffie-hellman-group-exchange-sha256")
            put("server_host_key", "ssh-rsa,ssh-dss,ecdsa-sha2-nistp256,ecdsa-sha2-nistp384,ecdsa-sha2-nistp521")
        }
        s.setConfig(props)

        onLog("جاري الاتصال بخادم SSH ${config.sshHost}:${config.sshPort} ...")
        s.connect(30000) // مهلة 30 ثانية
        onLog("تم الاتصال بنجاح بخادم SSH")
        session = s
        return s
    }

    @Throws(Exception::class)
    fun startSocksProxy(): Socks5Server {
        val s = session ?: throw IllegalStateException("يجب استدعاء connect() أولاً")
        val server = Socks5Server(s, config.localSocksPort, onLog)
        server.start()
        socksServer = server
        return server
    }

    fun disconnect() {
        try { socksServer?.stop() } catch (_: Exception) {}
        try { session?.disconnect() } catch (_: Exception) {}
        socksServer = null
        session = null
    }

    fun isConnected(): Boolean = session?.isConnected == true
}
