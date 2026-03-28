package com.qcb.keepaccounts.data.repository

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
) {
    suspend fun checkForUpdate(currentVersion: String): AppUpdateInfo? {
        if (owner.isBlank() || repo.isBlank()) return null

        val latestRelease = api.latestRelease(owner = owner, repo = repo)
        val latestTag = latestRelease.tagName.trim()
        val latestVersion = normalizeVersion(latestTag)
        val current = normalizeVersion(currentVersion)

        if (!isNewerVersion(latestVersion, current)) return null

        val fallback = "https://github.com/$owner/$repo/releases/latest"
        return AppUpdateInfo(
            latestVersionTag = latestTag,
            releaseUrl = latestRelease.htmlUrl?.takeIf { it.isNotBlank() } ?: fallback,
        )
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
}
