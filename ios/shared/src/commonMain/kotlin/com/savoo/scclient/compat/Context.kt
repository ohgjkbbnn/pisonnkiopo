package com.savoo.scclient.compat

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import okio.Path.Companion.toPath
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import kotlin.properties.ReadOnlyProperty

open class Context {
    val applicationContext: Context get() = this
    val filesDir: File get() = File(Platform.filesDirPath).also { it.mkdirs() }
    val cacheDir: File get() = File(Platform.cacheDirPath).also { it.mkdirs() }
    val noBackupFilesDir: File get() = filesDir
    val packageName: String get() = Platform.bundleId

    fun getSharedPreferences(name: String, mode: Int): SharedPreferences = SharedPreferencesImpl.get(name)

    fun deleteSharedPreferences(name: String): Boolean {
        SharedPreferencesImpl.get(name).edit().clear().apply()
        return true
    }

    fun getString(resource: StringResource, vararg args: Any?): String = Strings.get(resource, *args)

    fun getString(resource: PluralStringResource, quantity: Int, vararg args: Any?): String =
        Strings.plural(resource, quantity, *args)

    fun getStringArray(resource: StringArrayResource): List<String> = Strings.array(resource)

    companion object {
        const val MODE_PRIVATE = 0
    }
}

object AppContext : Context()

private val dataStores = HashMap<String, DataStore<Preferences>>()

fun preferencesDataStore(name: String): ReadOnlyProperty<Context, DataStore<Preferences>> =
    ReadOnlyProperty { context, _ ->
        dataStores.getOrPut(name) {
            PreferenceDataStoreFactory.createWithPath(
                produceFile = { "${context.filesDir.absolutePath}/datastore/$name.preferences_pb".toPath() },
            )
        }
    }
