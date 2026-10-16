package com.savoo.scclient.data.repository

import com.savoo.scclient.compat.*
import kotlinx.coroutines.IO
import com.savoo.scclient.compat.BuildConfig
import com.savoo.scclient.data.model.GitHubRelease
import com.savoo.scclient.data.model.UpdateChannel
import com.savoo.scclient.data.remote.GitHubReleaseApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

sealed interface UpdateCheckResult {
    data class Available(
        val channel: UpdateChannel,
        val versionLabel: String,
        val changelog: String?,
        val htmlUrl: String,
    ) : UpdateCheckResult

    data object UpToDate : UpdateCheckResult
    data object Error : UpdateCheckResult
}

class UpdateRepository(
    private val api: GitHubReleaseApi,
) {
    companion object {
        private const val OWNER = "savo-o"
        private const val REPO = "resona"
        private val VERSION_CODE_MARKER = Regex("""<!--\s*versionCode:\s*(\d+)\s*-->""")
        private val IOS_ASSET = Regex("""Resona-ios-(\d+(?:\.\d+)*)\.ipa""")
    }

    suspend fun checkForUpdate(channel: UpdateChannel): UpdateCheckResult = withContext(Dispatchers.IO) {
        runCatching {
            val releases = api.listReleases(OWNER, REPO)
            when (channel) {
                UpdateChannel.RELEASE -> checkRelease(releases)
                UpdateChannel.CANARY -> checkCanary(releases)
            }
        }.getOrElse { UpdateCheckResult.Error }
    }

    private fun checkRelease(releases: List<GitHubRelease>): UpdateCheckResult {
        val (candidate, iosVersion) = releases
            .asSequence()
            .filter { !it.draft && !it.prerelease }
            .firstNotNullOfOrNull { release ->
                release.assets.firstNotNullOfOrNull { IOS_ASSET.matchEntire(it.name) }
                    ?.let { release to it.groupValues[1] }
            } ?: return UpdateCheckResult.UpToDate

        if (compareVersions(iosVersion, BuildConfig.VERSION_NAME) <= 0) {
            return UpdateCheckResult.UpToDate
        }

        return UpdateCheckResult.Available(
            channel = UpdateChannel.RELEASE,
            versionLabel = iosVersion,
            changelog = candidate.body.orEmpty().replace(VERSION_CODE_MARKER, "").trim(),
            htmlUrl = candidate.htmlUrl,
        )
    }

    private fun checkCanary(releases: List<GitHubRelease>): UpdateCheckResult {
        val candidate = releases.firstOrNull { !it.draft && it.prerelease }
            ?: return UpdateCheckResult.UpToDate

        val versionCode = VERSION_CODE_MARKER.find(candidate.body.orEmpty())?.groupValues?.get(1)?.toIntOrNull()
            ?: return UpdateCheckResult.UpToDate

        if (versionCode <= BuildConfig.VERSION_CODE) {
            return UpdateCheckResult.UpToDate
        }

        return UpdateCheckResult.Available(
            channel = UpdateChannel.CANARY,
            versionLabel = (candidate.tagName ?: candidate.name.orEmpty()).removePrefix("v"),
            changelog = null,
            htmlUrl = candidate.htmlUrl,
        )
    }

    private fun compareVersions(a: String, b: String): Int {
        val left = a.split('.').map { it.toIntOrNull() ?: 0 }
        val right = b.split('.').map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(left.size, right.size)) {
            val diff = left.getOrElse(i) { 0 } - right.getOrElse(i) { 0 }
            if (diff != 0) return diff
        }
        return 0
    }
}
