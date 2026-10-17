package com.kraftplay.openiptv.service

import java.net.Inet4Address
import java.net.NetworkInterface
import java.net.ServerSocket
import java.net.Socket
import java.net.SocketException
import java.security.SecureRandom
import java.util.Collections
import java.util.concurrent.atomic.AtomicBoolean

/** Small one-session HTTP server for transferring an OpenIPTV backup on the local Wi-Fi. */
class WifiTransferServer(
    private val exportPayload: () -> String,
    private val onStopped: (() -> Unit)? = null
) {
    private val running = AtomicBoolean(false)
    private var serverSocket: ServerSocket? = null
    private val token: String = ByteArray(18).also { SecureRandom().nextBytes(it) }
        .joinToString("") { "%02x".format(it) }

    @Volatile var shareUrl: String? = null
        private set

    @Synchronized fun start(): String {
        if (running.get()) return shareUrl ?: error("Transfer server is not ready")
        val socket = ServerSocket(0, 8, java.net.InetAddress.getByName("0.0.0.0"))
        socket.soTimeout = 1000
        serverSocket = socket
        val ip = localIpv4Address() ?: run { socket.close(); throw SocketException("Connect to Wi-Fi and try again") }
        shareUrl = "http://$ip:${socket.localPort}/transfer/$token"
        running.set(true)
        Thread({ serve(socket) }, "OpenIPTV-WiFi-Transfer").apply { isDaemon = true; start() }
        return shareUrl!!
    }

    @Synchronized fun stop() {
        running.set(false)
        runCatching { serverSocket?.close() }
        serverSocket = null
        shareUrl = null
        onStopped?.invoke()
    }

    private fun serve(socket: ServerSocket) {
        while (running.get()) {
            try {
                socket.accept().use { client -> handle(client) }
            } catch (_: java.net.SocketTimeoutException) {
                // Periodically check the stop flag.
            } catch (_: Exception) {
                if (running.get()) Thread.sleep(100)
            }
        }
    }

    private fun handle(client: Socket) {
        client.soTimeout = 10_000
        val input = client.getInputStream()
        fun readLineBytes(): String? {
            val bytes = java.io.ByteArrayOutputStream()
            while (bytes.size() < 16_384) {
                val b = input.read()
                if (b < 0) return if (bytes.size() == 0) null else bytes.toString("UTF-8")
                if (b == 10) break
                if (b != 13) bytes.write(b)
            }
            return bytes.toString("UTF-8")
        }
        val requestLine = readLineBytes() ?: return
        val parts = requestLine.split(' ')
        if (parts.size < 2) { respond(client, 400, "Bad request"); return }
        val method = parts[0]
        val path = parts[1].substringBefore('?')
        while (true) {
            val line = readLineBytes() ?: break
            if (line.isEmpty()) break
        }
        if (path != "/transfer/$token") { respond(client, 404, "Not found"); return }
        if (method == "GET") {
            respond(client, 200, exportPayload(), "application/json; charset=utf-8")
        } else {
            respond(client, 405, "Method not allowed")
        }
    }

    private fun respond(client: Socket, status: Int, body: String, contentType: String = "text/plain; charset=utf-8") {
        val bytes = body.toByteArray(Charsets.UTF_8)
        val reason = when (status) { 200 -> "OK"; 400 -> "Bad Request"; 404 -> "Not Found"; 405 -> "Method Not Allowed"; 413 -> "Payload Too Large"; else -> "Error" }
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: $contentType\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\nCache-Control: no-store\r\n\r\n"
        client.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }

    private fun localIpv4Address(): String? = try {
        Collections.list(NetworkInterface.getNetworkInterfaces()).asSequence()
            .filter { it.isUp && !it.isLoopback }
            .flatMap { Collections.list(it.inetAddresses).asSequence() }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { !it.isLoopbackAddress && it.hostAddress.startsWith("192.") || (!it.isLoopbackAddress && it.hostAddress.startsWith("10.")) || (!it.isLoopbackAddress && it.hostAddress.matches(Regex("172\\.(1[6-9]|2[0-9]|3[01])\\..*"))) }
            ?.hostAddress
    } catch (_: Exception) { null }
}
