package com.savoo.scclient.data.remote

import com.savoo.scclient.data.model.GitHubRelease

class GitHubReleaseApi(private val rest: RestClient) {

    suspend fun listReleases(owner: String, repo: String, perPage: Int = 15): List<GitHubRelease> =
        rest.get("repos/$owner/$repo/releases", "per_page" to perPage)
}
