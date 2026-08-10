package com.example.sshpayloadvpn

import com.jcraft.jsch.ChannelDirectTCPIP
import com.jcraft.jsch.Session
import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * تطبيق مبسّط لبروتوكول SOCKS5 (RFC 1928) بدون مصادقة، محلي فقط (127.0.0.1).
 * كل اتصال SOCKS5 وارد يُفتح له قناة "direct-tcpip" داخل جلسة SSH،
 * أي أن كل حركة المرور تخرج فعلياً من خادم SSH (تماماً كما تفعل: ssh -D <port>).
 */
class Socks5Server(
    private val session: Session,
    private val localPort: Int,
    private val onLog: (String) -> Unit = {}
) {
    private var serverSocket: ServerSocket? = null
    private val running = AtomicBoolean(false)
    private val pool = Executors.newCachedThreadPool()

    fun start() {
        running.set(true)
        serverSocket = ServerSocket(localPort, 128, InetAddress.getByName("127.0.0.1"))
        onLog("تشغيل SOCKS5 محلي على 127.0.0.1:$localPort")
        pool.execute {
            while (running.get()) {
                try {
                    val client = serverSocket!!.accept()
                    pool.execute { handleClient(client) }
                } catch (e: Exception) {
                    if (running.get()) onLog("خطأ في قبول اتصال SOCKS: ${e.message}")
                }
            }
        }
    }

    fun stop() {
        running.set(false)
        try { serverSocket?.close() } catch (_: Exception) {}
        pool.shutdownNow()
    }

    private fun handleClient(client: Socket) {
        try {
            val input = client.getInputStream()
            val output = client.getOutputStream()

            // ---- مرحلة 1: المصافحة (تحديد طرق المصادقة) ----
            val ver = input.read()
            if (ver != 0x05) { client.close(); return }
            val nMethods = input.read()
            val methods = ByteArray(nMethods)
            input.read(methods)
            // نرد: نسخة 5، الطريقة 0x00 = بدون مصادقة
            output.write(byteArrayOf(0x05, 0x00))
            output.flush()

            // ---- مرحلة 2: طلب الاتصال (CONNECT) ----
            val reqVer = input.read()
            val cmd = input.read()
            input.read() // reserved byte
            val atyp = input.read()

            val targetHost: String = when (atyp) {
                0x01 -> { // IPv4
                    val addr = ByteArray(4)
                    input.read(addr)
                    InetAddress.getByAddress(addr).hostAddress
                }
                0x03 -> { // Domain name
                    val len = input.read()
                    val domain = ByteArray(len)
                    input.read(domain)
                    String(domain, Charsets.US_ASCII)
                }
                0x04 -> { // IPv6
                    val addr = ByteArray(16)
                    input.read(addr)
                    InetAddress.getByAddress(addr).hostAddress
                }
                else -> { client.close(); return }
            }
            val portBytes = ByteArray(2)
            input.read(portBytes)
            val targetPort = ((portBytes[0].toInt() and 0xFF) shl 8) or (portBytes[1].toInt() and 0xFF)

            if (cmd != 0x01) { // ندعم CONNECT فقط
                output.write(byteArrayOf(0x05, 0x07, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                client.close()
                return
            }

            // ---- مرحلة 3: فتح قناة direct-tcpip داخل جلسة SSH ----
            val channel = session.openChannel("direct-tcpip") as ChannelDirectTCPIP
            channel.setHost(targetHost)
            channel.setPort(targetPort)

            val chIn: InputStream
            val chOut: OutputStream
            try {
                chOut = channel.outputStream
                chIn = channel.inputStream
                channel.connect(10000)
            } catch (e: Exception) {
                onLog("فشل فتح قناة إلى $targetHost:$targetPort -> ${e.message}")
                output.write(byteArrayOf(0x05, 0x05, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
                client.close()
                return
            }

            // نرد بنجاح العملية
            output.write(byteArrayOf(0x05, 0x00, 0x00, 0x01, 0, 0, 0, 0, 0, 0))
            output.flush()

            // ---- مرحلة 4: تمرير البيانات في الاتجاهين ----
            val t1 = Thread { pipe(input, chOut) }
            val t2 = Thread { pipe(chIn, output) }
            t1.start(); t2.start()
            t1.join(); t2.join()

            channel.disconnect()
        } catch (e: Exception) {
            onLog("خطأ في معالجة عميل SOCKS: ${e.message}")
        } finally {
            try { client.close() } catch (_: Exception) {}
        }
    }

    private fun pipe(from: InputStream, to: OutputStream) {
        val buffer = ByteArray(8192)
        try {
            while (true) {
                val n = from.read(buffer)
                if (n == -1) break
                to.write(buffer, 0, n)
                to.flush()
            }
        } catch (_: Exception) {
        } finally {
            try { to.close() } catch (_: Exception) {}
        }
    }
}
