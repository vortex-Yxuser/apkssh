package com.example.sshpayloadvpn

import java.io.IOException
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket

class PayloadSocketFactory(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val payload: String,
    private val onLog: (String) -> Unit = {}
) {

    fun createSocket(targetHost: String, targetPort: Int): Socket {

        val socket = Socket()

        try {
            onLog("Connecting proxy $proxyHost:$proxyPort")

            socket.connect(
                InetSocketAddress(proxyHost, proxyPort),
                10000
            )

            onLog("Proxy connected")

            val output = socket.getOutputStream()

            val finalPayload = payload
                .replace("[host]", targetHost)
                .replace("[port]", targetPort.toString())
                .replace("[host_port]", "$targetHost:$targetPort")
                .replace("[crlf]", "\r\n")

            sendPayload(output, finalPayload)

            onLog("Payload sent")

            readHttpProxyResponse(socket)

            onLog("Proxy CONNECT established")

            return socket

        } catch (e: Exception) {

            try {
                socket.close()
            } catch (_: Exception) {
            }

            onLog("Socket error: ${e.message}")

            throw e
        }
    }


    private fun sendPayload(
        output: OutputStream,
        data: String
    ) {

        output.write(
            data.toByteArray(Charsets.UTF_8)
        )

        output.flush()
    }


    private fun readHttpProxyResponse(
        socket: Socket
    ) {

        val input = socket.getInputStream()

        val buffer = ByteArray(1)

        val response = StringBuilder()


        while (true) {

            val length = input.read(buffer)

            if (length <= 0) {
                break
            }


            response.append(
                buffer[0].toInt().toChar()
            )


            if (response.toString()
                    .endsWith("\r\n\r\n")) {
                break
            }


            if (response.length > 8192) {
                throw IOException(
                    "Proxy response too large"
                )
            }
        }


        val result = response.toString()


        if (!result.startsWith("HTTP/1.1 200") &&
            !result.startsWith("HTTP/1.0 200")
        ) {

            throw IOException(
                "Proxy rejected CONNECT:\n$result"
            )
        }
    }
}
