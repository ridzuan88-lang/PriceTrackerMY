package com.chewanninja.pricetrackermy

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.core.content.FileProvider
import androidx.core.content.pm.PackageInfoCompat
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Checks the latest GitHub Release of this repo; the release tag is "v<build number>" and carries the APK. */
object Updater {
    const val REPO = "ridzuan88-lang/PriceTrackerMY"

    data class Info(val code: Long, val name: String, val notes: String, val apkUrl: String, val size: Long)

    fun current(ctx: Context): Pair<Long, String> {
        val p = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(p) to (p.versionName ?: "")
    }

    fun fetchLatest(): Info? {
        val c = URL("https://api.github.com/repos/$REPO/releases/latest").openConnection() as HttpURLConnection
        c.connectTimeout = 15000; c.readTimeout = 20000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "PriceTrackerMY")
        if (c.responseCode == 404) return null
        if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
        val j = JSONObject(c.inputStream.bufferedReader().readText())
        val code = j.optString("tag_name").filter { it.isDigit() }.toLongOrNull() ?: return null
        val assets = j.optJSONArray("assets") ?: return null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            if (a.optString("name").endsWith(".apk"))
                return Info(code, j.optString("name", "build $code"), j.optString("body"), a.getString("browser_download_url"), a.optLong("size"))
        }
        return null
    }

    fun download(ctx: Context, info: Info, onProgress: (Float) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val out = File(dir, "PriceTrackerMY-${info.code}.apk")
        dir.listFiles()?.filter { it != out }?.forEach { it.delete() }
        val c = URL(info.apkUrl).openConnection() as HttpURLConnection
        c.connectTimeout = 20000; c.readTimeout = 60000
        c.setRequestProperty("User-Agent", "PriceTrackerMY")
        if (c.responseCode !in 200..299) throw IOException("Download failed: HTTP ${c.responseCode}")
        val total = if (c.contentLengthLong > 0) c.contentLengthLong else info.size
        c.inputStream.use { i ->
            out.outputStream().use { o ->
                val buf = ByteArray(64 * 1024); var done = 0L
                while (true) {
                    val n = i.read(buf); if (n < 0) break
                    o.write(buf, 0, n); done += n
                    if (total > 0) onProgress((done.toFloat() / total).coerceAtMost(1f))
                }
            }
        }
        return out
    }

    fun canInstall(ctx: Context): Boolean = ctx.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(ctx: Context) {
        ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${ctx.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    fun install(ctx: Context, f: File) {
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.files", f)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
