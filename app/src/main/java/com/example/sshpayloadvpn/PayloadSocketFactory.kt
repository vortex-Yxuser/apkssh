package com.example.sshpayloadvpn

import com.jcraft.jsch.SocketFactory
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

/**
 * هذا الكلاس هو "قلب" أسلوب SSH + Payload المعروف في تطبيقات مثل HTTP Injector:
 *
 * 1) يفتح اتصال TCP خام (عبر بروكسي HTTP/SOCKS إن وُجد، أو مباشرة إلى خادم SSH).
 * 2) يرسل نص الـ Payload الذي كتبه المستخدم (عادة طلب HTTP وهمي/CONNECT مع هيدرات مموّهة)
 *    والغرض منه خداع أنظمة فحص الحزم العميق (DPI) لدى مزود الشبكة بحيث "يظن" أن الاتصال
 *    هو تصفح HTTP عادي، بينما تبدأ بعده مباشرة مصافحة بروتوكول SSH الحقيقية على نفس المقبس (Socket).
 * 3) يُعيد نفس الـ Socket إلى مكتبة JSch لتكمل عليه الاتصال الفعلي.
 *
 * الرموز المدعومة داخل نص الـ Payload:
 *   [host]        -> عنوان خادم SSH
 *   [port]        -> منفذ خادم SSH
 *   [host_port]   -> host:port معاً
 *   [crlf]        -> \r\n
 *   [cr]          -> \r
 *   [lf]          -> \n
 */
class PayloadSocketFactory(
    private val config: TunnelConfig,
    private val onLog: (String) -> Unit = {}
) : SocketFactory {

    override fun createSocket(host: String?, port: Int): Socket {
        val targetHost: String
        val targetPort: Int

        if (config.proxyEnabled && config.proxyHost.isNotBlank()) {
            targetHost = config.proxyHost
            targetPort = config.proxyPort
        } else {
            targetHost = host ?: config.sshHost
            targetPort = port
        }

        val socket = Socket()
        socket.tcpNoDelay = true
        socket.connect(InetSocketAddress(targetHost, targetPort), 15000)
        onLog("تم فتح اتصال TCP إلى $targetHost:$targetPort")

        if (config.payload.isNotBlank()) {
            val finalPayload = applyPlaceholders(config.payload, host ?: config.sshHost, port)
            socket.getOutputStream().write(finalPayload.toByteArray(Charsets.ISO_8859_1))
            socket.getOutputStream().flush()
            onLog("تم إرسال الـ Payload (${finalPayload.length} حرف)")

            // قراءة رد HTTP CONNECT فقط. لا نحذف بيانات SSH بعد ذلك.            if (config.proxyEnabled) {                readHttpProxyResponse(socket)            }            } finally {
                socket.soTimeout = 0
            }
        }

        return socket
    }

    override fun getInputStream(socket: Socket): InputStream = socket.getInputStream()
    override fun getOutputStream(socket: Socket): OutputStream = socket.getOutputStream()

    private fun readHttpProxyResponse(socket: Socket) {        val input = socket.getInputStream()        val sb = StringBuilder()        val one = ByteArray(1)        while (sb.length < 8192) {            val n = input.read(one)            if (n <= 0) break            sb.append(one[0].toInt().toChar())            if (sb.endsWith("\\r\\n\\r\\n")) break        }        val response = sb.toString()        if (!response.startsWith("HTTP/1.1 200") && !response.startsWith("HTTP/1.0 200")) {            throw Exception("Proxy CONNECT failed: $response")        }        onLog("Proxy CONNECT OK")    }    private fun applyPlaceholders(raw: String, host: String, port: Int): String {
        return raw
            .replace("[host_port]", "$host:$port")
            .replace("[host]", host)
            .replace("[port]", port.toString())
            .replace("[crlf]", "\r\n")
            .replace("[cr]", "\r")
            .replace("[lf]", "\n")
    }
}
