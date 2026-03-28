package com.qcb.keepaccounts.data.remote.github

import com.squareup.moshi.Json
import retrofit2.http.GET
import retrofit2.http.Path

data class GitHubLatestReleaseDto(
    @Json(name = "tag_name")
    val tagName: String,
    @Json(name = "html_url")
    val htmlUrl: String? = null,
)

interface GitHubReleaseApi {
    @GET("repos/{owner}/{repo}/releases/latest")
    suspend fun latestRelease(
        @Path("owner") owner: String,
        @Path("repo") repo: String,
    ): GitHubLatestReleaseDto
}
