package com.nx.android.data.agent

import android.util.Log
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * The engine package, fetched over the network.
 *
 * The download lands in a .part file and is only put in place when it is whole: a package that was
 * cut short never reaches the installer. The digest is checked by the installer, against the
 * manifest the package carries — the manifest is inside the archive, so it cannot be read before.
 */
object AgentPackageDownloader {

    private const val TAG = "Nx2aAgentPkg"

    /** The download was called off: the player does not want it any more. */
    class Cancelled : IOException("cancelled")

    /**
     * Downloads a package into [dest].
     *
     * @param onProgress how far along it is, 0 to 1
     * @param cancelled whether the download was called off; it stops at the next chunk
     */
    fun download(
        url: String,
        dest: File,
        onProgress: (Float) -> Unit,
        cancelled: () -> Boolean,
    ): Result<File> {
        val tmp = File(dest.path + ".part")
        return runCatching {
            tmp.parentFile?.mkdirs()
            val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 15000
                readTimeout = 60000
                setRequestProperty("User-Agent", "nx2android")
            }
            try {
                if (conn.responseCode != HttpURLConnection.HTTP_OK) throw IOException("HTTP ${conn.responseCode}")
                val total = conn.contentLengthLong.takeIf { it > 0 } ?: 0L
                conn.inputStream.use { input ->
                    tmp.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        var done = 0L
                        while (true) {
                            if (cancelled()) throw Cancelled()
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            done += read
                            if (total > 0) onProgress((done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            } finally {
                conn.disconnect()
            }
            if (dest.exists() && !dest.delete()) throw IOException("the old package could not be replaced")
            if (!tmp.renameTo(dest)) throw IOException("the package could not be put in place")
            onProgress(1f)
            Log.i(TAG, "the package is here: $dest (${dest.length()} bytes)")
            dest
        }.also {
            if (it.isFailure) {
                tmp.delete()
                Log.e(TAG, "the download did not work out: ${it.exceptionOrNull()?.message}")
            }
        }
    }
}
