package com.savoo.scclient.compat

import com.savoo.scclient.resources.Res
import com.savoo.scclient.resources.allPluralStringResources
import com.savoo.scclient.resources.allStringArrayResources
import com.savoo.scclient.resources.allStringResources
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringArrayResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.getPluralString
import org.jetbrains.compose.resources.getString
import org.jetbrains.compose.resources.getStringArray

object Strings {
    private val strings = HashMap<String, String>()
    private val arrays = HashMap<String, List<String>>()
    private val plurals = HashMap<String, Map<PluralCategory, String>>()

    var loaded = false
        private set

    suspend fun preload() {
        Res.allStringResources.forEach { (name, res) -> strings[name] = getString(res) }
        Res.allStringArrayResources.forEach { (name, res) -> arrays[name] = getStringArray(res) }
        Res.allPluralStringResources.forEach { (name, res) ->
            plurals[name] = SAMPLES.mapValues { (_, q) -> getPluralString(res, q) }
        }
        loaded = true
    }

    fun get(resource: StringResource, vararg args: Any?): String {
        val raw = strings[resource.key] ?: return resource.key
        return if (args.isEmpty()) raw else raw.formatArgs(*args)
    }

    fun array(resource: StringArrayResource): List<String> = arrays[resource.key].orEmpty()

    fun plural(resource: PluralStringResource, quantity: Int, vararg args: Any?): String {
        val forms = plurals[resource.key] ?: return resource.key
        val raw = forms[pluralCategory(quantity)] ?: forms[PluralCategory.OTHER] ?: forms.values.first()
        return raw.formatArgs(*args)
    }

    private enum class PluralCategory { ONE, FEW, MANY, OTHER }

    private val SAMPLES = mapOf(
        PluralCategory.ONE to 1,
        PluralCategory.FEW to 2,
        PluralCategory.MANY to 5,
        PluralCategory.OTHER to 100,
    )

    private fun pluralCategory(n: Int): PluralCategory {
        if (!Locale.getDefault().language.startsWith("ru")) {
            return if (n == 1) PluralCategory.ONE else PluralCategory.OTHER
        }
        val mod10 = n % 10
        val mod100 = n % 100
        return when {
            mod10 == 1 && mod100 != 11 -> PluralCategory.ONE
            mod10 in 2..4 && mod100 !in 12..14 -> PluralCategory.FEW
            else -> PluralCategory.MANY
        }
    }
}
