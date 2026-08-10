package com.example.sshpayloadvpn

import com.jcraft.jsch.SocketFactory
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Socket


class PayloadSocketFactory(
    private val proxyHost: String,
    private val proxyPort: Int,
    private val payload: String,
    private val onLog: (String) -> Unit = {}
) : SocketFactory {


    override fun createSocket(
        host: String,
        port: Int
    ): Socket {

        val socket = Socket()

        try {

            onLog("Connecting proxy $proxyHost:$proxyPort")


            socket.connect(
                InetSocketAddress(
                    proxyHost,
                    proxyPort
                ),
                15000
            )


            onLog("Proxy connected")


            val request = payload
                .replace("[host]", host)
                .replace("[port]", port.toString())
                .replace("[host_port]", "$host:$port")
                .replace("[crlf]", "\r\n")


            val output = socket.getOutputStream()

            output.write(
                request.toByteArray(Charsets.UTF_8)
            )

            output.flush()


            onLog("Payload sent")


            checkProxyResponse(socket)


            onLog("Tunnel established")


            return socket


        } catch (e: Exception) {

            try {
                socket.close()
            } catch (_: Exception) {
            }

            onLog(
                "Connection failed: ${e.message}"
            )

            throw IOException(e)

        }
    }



    private fun checkProxyResponse(
        socket: Socket
    ) {

        val input = socket.getInputStream()

        val buffer = ByteArray(1)

        val response = StringBuilder()


        while (true) {

            val count = input.read(buffer)

            if (count <= 0) {
                break
            }


            response.append(
                buffer[0].toInt().toChar()
            )


            if (
                response.endsWith(
                    "\r\n\r\n"
                )
            ) {
                break
            }


            if (response.length > 8192) {
                throw IOException(
                    "Invalid proxy response"
                )
            }
        }


        val result = response.toString()


        if (
            !result.contains(
                "200"
            )
        ) {

            throw IOException(
                "Proxy rejected:\n$result"
            )
        }
    }



    override fun getInputStream(
        socket: Socket
    ): InputStream {

        return socket.getInputStream()

    }



    override fun getOutputStream(
        socket: Socket
    ): OutputStream {

        return socket.getOutputStream()

    }

}
