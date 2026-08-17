package com.originisle.android.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Checks GitHub's Releases API for a version newer than what's installed, and — if found — opens
 * the release page in the browser. Never downloads or installs anything itself: the whole point is
 * to leave the existing manual checksum/signature-verify-then-install flow (see README) intact,
 * not to quietly route around it.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private const val LATEST_RELEASE_URL =
        "https://api.github.com/repos/fvhde/origin-isle/releases/latest"

    sealed class Result {
        data class UpToDate(val current: String) : Result()
        data class UpdateAvailable(val latest: String, val url: String) : Result()
        data class Error(val message: String) : Result()
    }

    suspend fun check(context: Context): Result = withContext(Dispatchers.IO) {
        val current = runCatching {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        }.getOrNull() ?: return@withContext Result.Error("Unknown installed version")

        try {
            val conn = (URL(LATEST_RELEASE_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                connectTimeout = 8000
                readTimeout = 8000
            }
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            val tag = json.getString("tag_name").removePrefix("v")
            val url = json.getString("html_url")
            if (isNewer(tag, current)) Result.UpdateAvailable(tag, url) else Result.UpToDate(current)
        } catch (e: Exception) {
            Log.w(TAG, "update check failed: ${e.message}")
            Result.Error("Couldn't check for updates")
        }
    }

    /** "1.2.10" > "1.2.9" — compares numeric dot-separated parts, not lexicographically. */
    private fun isNewer(latest: String, current: String): Boolean {
        val l = latest.split(".").map { it.toIntOrNull() ?: 0 }
        val c = current.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(l.size, c.size)) {
            val lv = l.getOrElse(i) { 0 }
            val cv = c.getOrElse(i) { 0 }
            if (lv != cv) return lv > cv
        }
        return false
    }

    fun openRelease(context: Context, url: String) {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }
}
