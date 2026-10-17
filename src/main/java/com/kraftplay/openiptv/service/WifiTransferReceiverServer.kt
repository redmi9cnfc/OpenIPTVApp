package com.kraftplay.openiptv.service

import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom
import java.util.concurrent.atomic.AtomicBoolean

/** One-shot local-network receiver. The random path prevents accidental uploads from other clients. */
class WifiTransferReceiverServer(private val onPayload: (String) -> Boolean) {
    private val running = AtomicBoolean(false)
    private var socket: ServerSocket? = null
    private val token = ByteArray(18).also { SecureRandom().nextBytes(it) }.joinToString("") { "%02x".format(it) }
    val path: String get() = "/receive/$token"
    @Volatile var port: Int = 0
        private set

    @Synchronized fun start(): Int {
        if (running.get()) return port
        val ss = ServerSocket(0, 8, java.net.InetAddress.getByName("0.0.0.0"))
        socket = ss; port = ss.localPort; running.set(true)
        Thread({ serve(ss) }, "OpenIPTV-WiFi-Receiver").apply { isDaemon = true; start() }
        return port
    }
    @Synchronized fun stop() { running.set(false); runCatching { socket?.close() }; socket = null; port = 0 }
    private fun serve(ss: ServerSocket) {
        while (running.get()) try { ss.accept().use { handle(it) } } catch (_: Exception) { if (running.get()) Thread.sleep(100) }
    }
    private fun handle(client: Socket) {
        client.soTimeout = 15000
        val input = client.getInputStream()
        fun line(): String { val b = StringBuilder(); while (b.length < 8192) { val c = input.read(); if (c < 0 || c == 10) break; if (c != 13) b.append(c.toChar()) }; return b.toString() }
        val request = line().split(' ')
        var length = 0
        while (true) { val h = line(); if (h.isEmpty()) break; if (h.substringBefore(':').equals("Content-Length", true)) length = h.substringAfter(':').trim().toIntOrNull() ?: 0 }
        if (request.size < 2 || request[0] != "POST" || request[1].substringBefore('?') != path || length !in 1..5_000_000) { respond(client, 400, "Invalid request"); return }
        val bytes = ByteArray(length); var offset = 0
        while (offset < length) { val n = input.read(bytes, offset, length - offset); if (n < 0) break; offset += n }
        if (offset != length) { respond(client, 400, "Incomplete payload"); return }
        val ok = runCatching { onPayload(String(bytes, Charsets.UTF_8)) }.getOrDefault(false)
        respond(client, if (ok) 200 else 422, if (ok) "OK" else "Import failed")
    }
    private fun respond(client: Socket, status: Int, body: String) {
        val bytes = body.toByteArray(Charsets.UTF_8); val reason = if (status == 200) "OK" else if (status == 422) "Unprocessable Entity" else "Bad Request"
        val header = "HTTP/1.1 $status $reason\r\nContent-Type: text/plain; charset=utf-8\r\nContent-Length: ${bytes.size}\r\nConnection: close\r\n\r\n"
        client.getOutputStream().apply { write(header.toByteArray(Charsets.US_ASCII)); write(bytes); flush() }
    }
}
