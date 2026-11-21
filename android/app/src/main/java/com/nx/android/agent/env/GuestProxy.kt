package com.nx.android.agent.env

import java.io.InputStream
import java.io.OutputStream
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * The CONNECT proxy the guest agent's traffic rides through (ADR-009).
 *
 * The guest (the glibc environment pi lives in) cannot resolve domain names: glibc reads
 * /etc/resolv.conf, Android has none, and an unprivileged app may not write /etc. A direct
 * connection by IP is no way out either — TLS certificate checks need the host name. So the
 * guest programs point HTTPS_PROXY at this thread: they forward "CONNECT host:443" here, we
 * resolve the name with the app's own network stack and pipe the bytes. TLS stays end-to-end —
 * this thread only ever moves ciphertext and holds no keys (not the ADR-003 proxy pool: no
 * model traffic is decrypted or re-signed here, and the app never sees the player's prompts).
 */
@Singleton
class GuestProxy @Inject constructor() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var server: ServerSocket? = null

    /** The port to point HTTPS_PROXY at; the first call starts the listener. */
    @Synchronized
    fun ensure(): Int {
        server?.let { return it.localPort }
        var cause: Exception? = null
        for (port in intArrayOf(7879, 7880, 7881, 0)) {
            try {
                val s = ServerSocket()
                s.reuseAddress = true
                s.bind(InetSocketAddress(InetAddress.getByName("127.0.0.1"), port))
                server = s
                scope.launch { acceptLoop(s) }
                return s.localPort
            } catch (e: Exception) {
                cause = e
            }
        }
        throw IllegalStateException("the guest proxy found no free port", cause)
    }

    private fun acceptLoop(s: ServerSocket) {
        while (!s.isClosed) {
            val client = try {
                s.accept()
            } catch (_: Exception) {
                return
            }
            scope.launch { handle(client) }
        }
    }

    /** One CONNECT tunnel: read the target, resolve it here, then pipe ciphertext both ways. */
    private suspend fun handle(client: Socket) {
        var upstream: Socket? = null
        try {
            client.soTimeout = 15_000
            val head = readHead(client.getInputStream()) ?: return
            val target = CONNECT_LINE.find(head)?.groupValues?.get(1) ?: return
            val host = target.substringBeforeLast(':')
            val port = target.substringAfterLast(':').toIntOrNull() ?: 443
            // the relay serves the model providers only: on a shared loopback an open relay
            // would serve the whole phone
            if (ALLOWED_HOSTS.none { host.endsWith(it) }) {
                writeLine(client.getOutputStream(), "HTTP/1.1 403 Forbidden\r\n\r\n")
                return
            }
            val up = Socket()
            upstream = up
            up.connect(InetSocketAddress(host, port), 15_000)
            writeLine(client.getOutputStream(), "HTTP/1.1 200 Connection Established\r\n\r\n")
            client.soTimeout = 0
            up.soTimeout = 0
            // a finished direction half-closes its socket: no truncation for a still-streaming side
            val toUp = scope.launch { copy(client.getInputStream(), up.getOutputStream()) }
            val toClient = scope.launch { copy(up.getInputStream(), client.getOutputStream()) }
            toUp.invokeOnCompletion { runCatching { up.shutdownOutput() } }
            toClient.invokeOnCompletion { runCatching { client.shutdownOutput() } }
            toUp.join()
            toClient.join()
        } catch (_: Exception) {
            // a tunneled connection dies with its peer; the loop goes on
        } finally {
            runCatching { upstream?.close() }
            runCatching { client.close() }
        }
    }

    /** The request head's bytes, up to the blank line; null when the peer hung up. */
    private fun readHead(input: InputStream): String? {
        val buf = StringBuilder()
        while (buf.length < 16_384) {
            val c = input.read()
            if (c < 0) return null
            buf.append(c.toChar())
            if (buf.endsWith("\r\n\r\n")) return buf.toString()
        }
        return null
    }

    private fun writeLine(out: OutputStream, line: String) {
        out.write(line.toByteArray(Charsets.ISO_8859_1))
        out.flush()
    }

    private suspend fun copy(from: InputStream, to: OutputStream) = kotlinx.coroutines.withContext(Dispatchers.IO) {
        try {
            from.copyTo(to, 8_192)
            to.flush()
        } catch (_: Exception) {
            // a half-closed tunnel is how every tunnel ends
        }
    }

    private companion object {
        val CONNECT_LINE = Regex("CONNECT (\\S+) HTTP/1\\.[01]")

        /** The hosts the relay is allowed to open: the BYOK providers' endpoints. */
        val ALLOWED_HOSTS = listOf(
            ".minimax.chat", ".minimaxi.com",
            ".bigmodel.cn", ".zhipu.ai",
            ".deepseek.com",
            ".moonshot.cn", ".moonshot.ai",
            ".volces.com",
            ".openai.com", ".anthropic.com",
        )
    }
}
