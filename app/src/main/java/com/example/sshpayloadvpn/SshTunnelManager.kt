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
                10000
            )


            onLog("Proxy connected")


            val output =
                socket.getOutputStream()


            val finalPayload =
                payload
                    .replace(
                        "[host]",
                        host
                    )
                    .replace(
                        "[port]",
                        port.toString()
                    )
                    .replace(
                        "[host_port]",
                        "$host:$port"
                    )
                    .replace(
                        "[crlf]",
                        "\r\n"
                    )


            output.write(
                finalPayload.toByteArray(
                    Charsets.UTF_8
                )
            )

            output.flush()


            onLog("Payload sent")


            readProxyResponse(socket)


            onLog("CONNECT OK")


            return socket


        } catch (e: Exception) {


            try {
                socket.close()
            } catch (_: Exception) {
            }


            onLog(
                "Proxy error: ${e.message}"
            )


            throw e
        }
    }



    private fun readProxyResponse(
        socket: Socket
    ) {


        val input =
            socket.getInputStream()


        val buffer =
            ByteArray(1)


        val response =
            StringBuilder()



        while (true) {


            val read =
                input.read(buffer)



            if (read <= 0) {
                break
            }



            response.append(
                buffer[0].toInt()
                    .toChar()
            )



            if (
                response.toString()
                    .endsWith(
                        "\r\n\r\n"
                    )
            ) {
                break
            }



            if (response.length > 8192) {

                throw IOException(
                    "Proxy response too large"
                )

            }

        }



        val result =
            response.toString()



        if (
            !result.startsWith(
                "HTTP/1.1 200"
            )
            &&
            !result.startsWith(
                "HTTP/1.0 200"
            )
        ) {


            throw IOException(
                "Proxy refused:\n$result"
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
