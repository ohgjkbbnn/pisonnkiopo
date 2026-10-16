package com.savoo.scclient.data.repository

import com.savoo.scclient.compat.*
import com.savoo.scclient.compat.Context
import com.savoo.scclient.compat.Uri
import com.savoo.scclient.compat.SystemClock
import com.savoo.scclient.data.model.User
import com.savoo.scclient.data.remote.BridgeResponse
import com.savoo.scclient.data.remote.SoundCloudApi
import com.savoo.scclient.data.remote.WebViewApiBridge
import com.savoo.scclient.debug.DebugLog
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.savoo.scclient.compat.JSONArray
import com.savoo.scclient.compat.JSONObject

data class ProfileFields(
    val username: String,
    val firstName: String,
    val lastName: String,
    val city: String,
    val description: String,
)

fun User.toProfileFields(): ProfileFields = ProfileFields(
    username = username,
    firstName = firstName.orEmpty(),
    lastName = lastName.orEmpty(),
    city = city.orEmpty(),
    description = description.orEmpty(),
)

class ProfileUpdateException(val code: Int, val serverMessage: String?) :
    Exception(serverMessage ?: "HTTP $code")
class ProfileRepository(
    private val context: Context,
    private val api: SoundCloudApi,
    private val webBridge: WebViewApiBridge,
) {
    suspend fun updateProfile(original: ProfileFields, edited: ProfileFields) {
        val changes = JSONObject()
        if (edited.username != original.username) changes.put("username", edited.username)
        if (edited.firstName != original.firstName) changes.put("first_name", edited.firstName)
        if (edited.lastName != original.lastName) changes.put("last_name", edited.lastName)
        if (edited.city != original.city) changes.put("city", edited.city)
        if (edited.description != original.description) changes.put("description", edited.description)
        if (changes.length() == 0) return
        val response = webBridge.updateMe(changes.toString())
        DebugLog.log(TAG, "updateMe(${changes.keys().asSequence().toList()}) -> ${response.code}")
        response.throwIfFailed()
    }

    suspend fun uploadAvatar(uri: Uri) {
        val encodeStart = SystemClock.elapsedRealtime()
        val base64 = withContext(Dispatchers.Default) { encodeAvatar(uri) }
        val requestStart = SystemClock.elapsedRealtime()
        val response = webBridge.uploadAvatar(base64)
        val done = SystemClock.elapsedRealtime()
        DebugLog.log(
            TAG,
            "uploadAvatar(${base64.length / 1024} KB base64) -> ${response.code}, encode ${requestStart - encodeStart} ms, request ${done - requestStart} ms",
        )
        response.throwIfFailed()
    }

    suspend fun deleteAvatar() {
        val response = webBridge.deleteAvatar()
        DebugLog.log(TAG, "deleteAvatar -> ${response.code}")
        if (response.code != 404) response.throwIfFailed()
    }

    suspend fun reloadMe(): User {
        val start = SystemClock.elapsedRealtime()
        return api.getMe().also { DebugLog.log(TAG, "reloadMe ${SystemClock.elapsedRealtime() - start} ms") }
    }

    private fun BridgeResponse.throwIfFailed() {
        if (!isSuccess) throw ProfileUpdateException(code, serverMessage(body))
    }

    private fun serverMessage(body: String?): String? {
        if (body.isNullOrBlank()) return null
        val json = runCatching { JSONObject(body) }.getOrNull() ?: return body.take(200)
        val errors = json.opt("errors")
        val messages = when (errors) {
            is JSONArray -> (0 until errors.length()).mapNotNull { index ->
                when (val item = errors.opt(index)) {
                    is JSONObject -> item.optString("error_message").ifBlank { item.optString("message") }.ifBlank { null }
                    is String -> item
                    else -> null
                }
            }
            is JSONObject -> errors.keys().asSequence().map { key -> "$key: ${errors.opt(key)}" }.toList()
            else -> listOfNotNull(json.optString("error").ifBlank { null }, json.optString("message").ifBlank { null })
        }
        return messages.joinToString("; ").ifBlank { null }
    }

    private fun encodeAvatar(uri: Uri): String {
        val source = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Cannot read image")
        val bytes = squareJpeg(source, AVATAR_SIZE) ?: error("Cannot decode image")
        return Base64.encodeToString(bytes, Base64.NO_WRAP)
    }

    private companion object {
        const val TAG = "ProfileRepository"
        const val AVATAR_SIZE = 500
    }
}
