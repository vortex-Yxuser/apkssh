package com.example.sshpayloadvpn

import android.app.*
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.File

class MyVpnService : VpnService() {

    companion object {
        const val ACTION_CONNECT = "com.example.sshpayloadvpn.CONNECT"
        const val ACTION_DISCONNECT = "com.example.sshpayloadvpn.DISCONNECT"
        const val ACTION_LOG = "com.example.sshpayloadvpn.LOG_UPDATE"
        const val CHANNEL_ID = "ssh_payload_vpn_channel"
        const val NOTIF_ID = 1

        @Volatile var isRunning = false
            private set
    }

    private var tunInterface: ParcelFileDescriptor? = null
    private var tunnelManager: SshTunnelManager? = null
    private var workerThread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_DISCONNECT -> {
                stopTunnel()
                return START_NOT_STICKY
            }
            else -> {
                startForeground(NOTIF_ID, buildNotification("جاري الاتصال..."))
                startTunnel()
            }
        }
        return START_STICKY
    }

    private fun sendLogToUI(message: String) {
        val intent = Intent(ACTION_LOG)
        intent.putExtra("log_msg", message)
        sendBroadcast(intent)
    }

    private fun startTunnel() {
        if (isRunning) return
        val config = Prefs.load(applicationContext)

        workerThread = Thread {
            try {
                sendLogToUI("بدء عملية الاتصال...")
                val manager = SshTunnelManager(config) { log -> 
                    updateNotification(log)
                    sendLogToUI(log)
                }
                tunnelManager = manager

                // 1) الاتصال بخادم SSH
                manager.connect()

                // 2) تشغيل خادم SOCKS5 محلي
                manager.startSocksProxy()

                // 3) إنشاء واجهة TUN
                val builder = Builder()
                    .setSession("SSHPayloadVPN")
                    .addAddress("10.10.10.2", 32)
                    .addRoute("0.0.0.0", 0)
                    .addDnsServer("8.8.8.8")
                    .addDnsServer("1.1.1.1")
                    .setMtu(1500)

                tunInterface = builder.establish()
                sendLogToUI("واجهة TUN جاهزة")

                // 4) إعداد ملف tun2socks
                val yamlConfig = File(filesDir, "tun2socks.yaml")
                yamlConfig.writeText(
                    """
                    tunnel:
                      mtu: 1500
                    socks5:
                      address: 127.0.0.1
                      port: ${config.localSocksPort}
                      udp: 'tcp'
                    """.trimIndent()
                )

                val tun2socksStarted = TProxyService.TProxyStartService(
                    yamlConfig.absolutePath,
                    tunInterface!!.fd
                )

                if (!tun2socksStarted) {
                    sendLogToUI("خطأ: فشل تشغيل محرك tun2socks")
                    stopTunnel()
                    return@Thread
                }

                isRunning = true
                sendLogToUI("متصل بالكامل ✅")
                updateNotification("متصل بنجاح")

            } catch (e: Exception) {
                val errorMsg = "فشل الاتصال: ${e.message}"
                sendLogToUI(errorMsg)
                updateNotification(errorMsg)
                stopTunnel()
            }
        }
        workerThread?.start()
    }

    private fun stopTunnel() {
        isRunning = false
        sendLogToUI("تم قطع الاتصال")
        try { TProxyService.TProxyStopService() } catch (_: Exception) {}
        try { tunnelManager?.disconnect() } catch (_: Exception) {}
        try { tunInterface?.close() } catch (_: Exception) {}
        tunInterface = null
        tunnelManager = null
        stopForeground(true)
        stopSelf()
    }

    override fun onDestroy() {
        stopTunnel()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopTunnel()
        super.onRevoke()
    }

    private fun buildNotification(text: String): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "SSH Payload VPN", NotificationManager.IMPORTANCE_LOW
            )
            (getSystemService(NotificationManager::class.java)).createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SSH Payload VPN")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_lock_lock)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm?.notify(NOTIF_ID, buildNotification(text))
    }
}

