package com.example.vidsafe

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap

object Downloader {
    private class H(val ua: String?, val ref: String?, val cookie: String?)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val main = Handler(Looper.getMainLooper())

    val active = ConcurrentHashMap<String, Int>()
    @Volatile var onUpdate: (() -> Unit)? = null

    fun dir(ctx: Context) = File(ctx.filesDir, "videos").apply { mkdirs() }

    private fun toast(ctx: Context, msg: String) =
        main.post { Toast.makeText(ctx, msg, Toast.LENGTH_LONG).show() }

    private fun changed() { main.post { onUpdate?.invoke() } }

    fun start(ctx: Context, url: String, userAgent: String? = null, referer: String? = null, cookie: String? = null) {
        val app = ctx.applicationContext
        val hls = url.substringBefore('?').lowercase().endsWith(".m3u8")
        val name = makeName(url, hls)
        val part = File(dir(app), "$name.part")
        scope.launch {
            try {
                active[name] = 0; changed()
                toast(app, "Download shuru: $name")
                val h = H(userAgent, referer, cookie)
                if (hls) hls(url, part, name, h) else direct(url, part, name, h)
                part.renameTo(File(dir(app), name))
                toast(app, "Ho gaya: $name")
            } catch (e: Exception) {
                part.delete()
                toast(app, "Fail: ${e.message}")
            } finally {
                active.remove(name); changed()
            }
        }
    }

    private fun open(url: String, h: H): HttpURLConnection {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 30000
        c.setRequestProperty("User-Agent", h.ua ?: "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 Chrome/120 Mobile Safari/537.36")
        h.ref?.let { c.setRequestProperty("Referer", it) }
        h.cookie?.let { c.setRequestProperty("Cookie", it) }
        if (c.responseCode !in 200..299) throw Exception("Server error ${c.responseCode}")
        return c
    }

    private fun direct(url: String, part: File, name: String, h: H) {
        val c = open(url, h)
        val total = c.contentLengthLong
        c.inputStream.use { input ->
            FileOutputStream(part).use { out ->
                val buf = ByteArray(64 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n <= 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val p = (done * 100 / total).toInt()
                        if (p != last) { last = p; active[name] = p; changed() }
                    }
                }
            }
        }
    }

    private fun text(url: String, h: H) = open(url, h).inputStream.bufferedReader().use { it.readText() }

    private fun hls(url0: String, part: File, name: String, h: H) {
        var url = url0
        var pl = text(url, h)
        if (pl.contains("#EXT-X-STREAM-INF")) {
            val lines = pl.lines()
            var best = -1L
            var uri: String? = null
            for (i in lines.indices) {
                if (lines[i].startsWith("#EXT-X-STREAM-INF")) {
                    val bw = Regex("BANDWIDTH=(\\d+)").find(lines[i])?.groupValues?.get(1)?.toLong() ?: 0L
                    val next = lines.drop(i + 1).firstOrNull { it.isNotBlank() && !it.startsWith("#") }
                    if (next != null && bw > best) { best = bw; uri = next.trim() }
                }
            }
            url = URL(URL(url), uri ?: throw Exception("Playlist khali hai")).toString()
            pl = text(url, h)
        }
        if (Regex("#EXT-X-KEY:(?!METHOD=NONE)").containsMatchIn(pl) || pl.contains("#EXT-X-MAP"))
            throw Exception("Encrypted/protected stream supported nahi hai")
        val segs = pl.lines().map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { URL(URL(url), it).toString() }
        if (segs.isEmpty()) throw Exception("Koi segment nahi mila")
        FileOutputStream(part).use { out ->
            segs.forEachIndexed { i, s ->
                open(s, h).inputStream.use { it.copyTo(out) }
                active[name] = (i + 1) * 100 / segs.size
                changed()
            }
        }
    }

    private fun makeName(url: String, hls: Boolean): String {
        val path = url.substringBefore('?')
        var base = path.substringAfterLast('/').substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_-]"), "_").take(40)
        if (base.isBlank() || hls) base = "video"
        val ext = if (hls) "ts" else path.substringAfterLast('.', "mp4").lowercase()
            .takeIf { it in listOf("mp4", "webm", "mkv", "mov", "3gp") } ?: "mp4"
        return "${base}_${System.currentTimeMillis()}.$ext"
    }
}
