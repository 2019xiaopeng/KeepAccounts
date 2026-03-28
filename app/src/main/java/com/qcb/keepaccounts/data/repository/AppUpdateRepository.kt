package com.qcb.keepaccounts.data.repository

import android.content.Context
import com.qcb.keepaccounts.data.remote.github.GitHubReleaseApi
import kotlin.math.max

data class AppUpdateInfo(
    val latestVersionTag: String,
    val releaseUrl: String,
)

class AppUpdateRepository(
    private val api: GitHubReleaseApi,
    private val owner: String,
    private val repo: String,
    context: Context,
) {
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    suspend fun checkForUpdate(
        currentVersion: String,
        minIntervalHours: Long = 12,
    ): AppUpdateInfo? {
        if (owner.isBlank() || repo.isBlank()) return null

        val now = System.currentTimeMillis()
        val minIntervalMillis = (minIntervalHours.coerceAtLeast(1L) * 60L * 60L * 1000L)
        val lastCheckAt = prefs.getLong(KEY_LAST_CHECK_AT, 0L)
        if (now - lastCheckAt < minIntervalMillis) return null
        prefs.edit().putLong(KEY_LAST_CHECK_AT, now).apply()

        val latestRelease = api.latestRelease(owner = owner, repo = repo)
        val latestTag = latestRelease.tagName.trim()
        val latestVersion = normalizeVersion(latestTag)
        val current = normalizeVersion(currentVersion)

        if (!isNewerVersion(latestVersion, current)) return null

        val lastPromptedTag = prefs.getString(KEY_LAST_PROMPTED_TAG, null)
        if (latestTag.equals(lastPromptedTag, ignoreCase = true)) return null

        val fallback = "https://github.com/$owner/$repo/releases/latest"
        return AppUpdateInfo(
            latestVersionTag = latestTag,
            releaseUrl = latestRelease.htmlUrl?.takeIf { it.isNotBlank() } ?: fallback,
        )
    }

    fun markVersionPrompted(versionTag: String) {
        if (versionTag.isBlank()) return
        prefs.edit().putString(KEY_LAST_PROMPTED_TAG, versionTag.trim()).apply()
    }

    private fun normalizeVersion(raw: String): List<Int> {
        return raw
            .trim()
            .removePrefix("v")
            .removePrefix("V")
            .split(Regex("[^0-9]+"))
            .filter { it.isNotBlank() }
            .map { it.toIntOrNull() ?: 0 }
            .ifEmpty { listOf(0) }
    }

    private fun isNewerVersion(latest: List<Int>, current: List<Int>): Boolean {
        val count = max(latest.size, current.size)
        for (index in 0 until count) {
            val latestPart = latest.getOrElse(index) { 0 }
            val currentPart = current.getOrElse(index) { 0 }
            if (latestPart > currentPart) return true
            if (latestPart < currentPart) return false
        }
        return false
    }

    private companion object {
        const val PREFS_NAME = "app_update_preferences"
        const val KEY_LAST_CHECK_AT = "last_check_at"
        const val KEY_LAST_PROMPTED_TAG = "last_prompted_tag"
    }
}
