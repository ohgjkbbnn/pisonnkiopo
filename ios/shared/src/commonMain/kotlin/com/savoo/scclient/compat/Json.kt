package com.savoo.scclient.compat

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull

class JSONException(message: String) : Exception(message)

private fun fromElement(e: JsonElement): Any? = when (e) {
    is JsonNull -> JSONObject.NULL
    is JsonObject -> JSONObject(e)
    is JsonArray -> JSONArray(e)
    is JsonPrimitive -> when {
        e.isString -> e.content
        e.booleanOrNull != null -> e.booleanOrNull
        e.longOrNull != null -> e.longOrNull
        else -> e.doubleOrNull
    }
}

private fun toElement(v: Any?): JsonElement = when (v) {
    null, JSONObject.NULL -> JsonNull
    is JSONObject -> JsonObject(v.map.mapValues { toElement(it.value) })
    is JSONArray -> JsonArray(v.list.map { toElement(it) })
    is String -> JsonPrimitive(v)
    is Boolean -> JsonPrimitive(v)
    is Number -> JsonPrimitive(v)
    is Map<*, *> -> JsonObject(v.entries.associate { it.key.toString() to toElement(it.value) })
    is Collection<*> -> JsonArray(v.map { toElement(it) })
    else -> JsonPrimitive(v.toString())
}

private fun Any?.asLong(): Long? = when (this) {
    is Number -> toLong()
    is String -> toLongOrNull() ?: toDoubleOrNull()?.toLong()
    else -> null
}

private fun Any?.asDouble(): Double? = when (this) {
    is Number -> toDouble()
    is String -> toDoubleOrNull()
    else -> null
}

private fun Any?.asBoolean(): Boolean? = when (this) {
    is Boolean -> this
    is String -> toBooleanStrictOrNull()
    else -> null
}

private fun Any?.asString(): String? = when (this) {
    null, JSONObject.NULL -> null
    is String -> this
    is JSONObject, is JSONArray -> toString()
    is Double -> if (this == kotlin.math.floor(this) && !isInfinite()) toLong().toString() else toString()
    else -> toString()
}

class JSONObject internal constructor(internal val map: LinkedHashMap<String, Any?>) {
    constructor() : this(LinkedHashMap())
    constructor(json: String) : this(parseObject(json))
    internal constructor(element: JsonObject) : this(LinkedHashMap(element.mapValues { fromElement(it.value) }))
    constructor(source: Map<*, *>) : this(LinkedHashMap(source.entries.associate { it.key.toString() to it.value }))

    fun length(): Int = map.size
    fun has(name: String): Boolean = map.containsKey(name)
    fun isNull(name: String): Boolean = map[name] == null || map[name] == NULL
    fun keys(): Iterator<String> = map.keys.toList().iterator()
    fun names(): JSONArray? = if (map.isEmpty()) null else JSONArray(map.keys.toList())
    fun remove(name: String): Any? = map.remove(name)

    fun opt(name: String): Any? = map[name]?.takeIf { it != NULL }
    fun get(name: String): Any = opt(name) ?: throw JSONException("No value for $name")

    fun put(name: String, value: Any?): JSONObject {
        if (value == null) map.remove(name) else map[name] = value
        return this
    }
    fun put(name: String, value: Int): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Long): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Double): JSONObject = put(name, value as Any?)
    fun put(name: String, value: Boolean): JSONObject = put(name, value as Any?)
    fun putOpt(name: String?, value: Any?): JSONObject = if (name != null && value != null) put(name, value) else this

    fun getString(name: String): String = get(name).asString() ?: throw JSONException("$name is not a string")
    fun getLong(name: String): Long = get(name).asLong() ?: throw JSONException("$name is not a number")
    fun getInt(name: String): Int = getLong(name).toInt()
    fun getDouble(name: String): Double = get(name).asDouble() ?: throw JSONException("$name is not a number")
    fun getBoolean(name: String): Boolean = get(name).asBoolean() ?: throw JSONException("$name is not a boolean")
    fun getJSONObject(name: String): JSONObject = get(name) as? JSONObject ?: throw JSONException("$name is not an object")
    fun getJSONArray(name: String): JSONArray = get(name) as? JSONArray ?: throw JSONException("$name is not an array")

    fun optString(name: String): String = optString(name, "")
    fun optString(name: String, fallback: String?): String = opt(name).asString() ?: fallback.orEmpty()
    fun optLong(name: String): Long = optLong(name, 0L)
    fun optLong(name: String, fallback: Long): Long = opt(name).asLong() ?: fallback
    fun optInt(name: String): Int = optInt(name, 0)
    fun optInt(name: String, fallback: Int): Int = opt(name).asLong()?.toInt() ?: fallback
    fun optDouble(name: String): Double = optDouble(name, Double.NaN)
    fun optDouble(name: String, fallback: Double): Double = opt(name).asDouble() ?: fallback
    fun optBoolean(name: String): Boolean = optBoolean(name, false)
    fun optBoolean(name: String, fallback: Boolean): Boolean = opt(name).asBoolean() ?: fallback
    fun optJSONObject(name: String): JSONObject? = opt(name) as? JSONObject
    fun optJSONArray(name: String): JSONArray? = opt(name) as? JSONArray

    override fun toString(): String = toElement(this).toString()
    fun toString(indent: Int): String = prettyJson.encodeToString(JsonElement.serializer(), toElement(this))

    companion object {
        val NULL: Any = object {
            override fun toString(): String = "null"
            override fun equals(other: Any?): Boolean = other == null || other === this
            override fun hashCode(): Int = 0
        }

        fun quote(value: String?): String = JsonPrimitive(value.orEmpty()).toString()

        private fun parseObject(json: String): LinkedHashMap<String, Any?> {
            val element = runCatching { Json.parseToJsonElement(json) }.getOrElse { throw JSONException(it.message ?: "bad json") }
            if (element !is JsonObject) throw JSONException("Not a JSON object")
            return LinkedHashMap(element.mapValues { fromElement(it.value) })
        }
    }
}

class JSONArray internal constructor(internal val list: MutableList<Any?>) {
    constructor() : this(ArrayList())
    constructor(json: String) : this(parseArray(json))
    internal constructor(element: JsonArray) : this(element.map { fromElement(it) }.toMutableList())
    constructor(source: Collection<*>) : this(source.toMutableList<Any?>())

    fun length(): Int = list.size
    fun isNull(index: Int): Boolean = list.getOrNull(index).let { it == null || it == JSONObject.NULL }
    fun opt(index: Int): Any? = list.getOrNull(index)?.takeIf { it != JSONObject.NULL }
    fun get(index: Int): Any = opt(index) ?: throw JSONException("No value at $index")

    fun put(value: Any?): JSONArray {
        list.add(value)
        return this
    }
    fun put(value: Int): JSONArray = put(value as Any?)
    fun put(value: Long): JSONArray = put(value as Any?)
    fun put(value: Double): JSONArray = put(value as Any?)
    fun put(value: Boolean): JSONArray = put(value as Any?)
    fun put(index: Int, value: Any?): JSONArray {
        while (list.size <= index) list.add(JSONObject.NULL)
        list[index] = value
        return this
    }
    fun remove(index: Int): Any? = list.removeAt(index)

    fun getString(index: Int): String = get(index).asString() ?: throw JSONException("not a string")
    fun getLong(index: Int): Long = get(index).asLong() ?: throw JSONException("not a number")
    fun getInt(index: Int): Int = getLong(index).toInt()
    fun getDouble(index: Int): Double = get(index).asDouble() ?: throw JSONException("not a number")
    fun getBoolean(index: Int): Boolean = get(index).asBoolean() ?: throw JSONException("not a boolean")
    fun getJSONObject(index: Int): JSONObject = get(index) as? JSONObject ?: throw JSONException("not an object")
    fun getJSONArray(index: Int): JSONArray = get(index) as? JSONArray ?: throw JSONException("not an array")

    fun optString(index: Int): String = opt(index).asString().orEmpty()
    fun optString(index: Int, fallback: String): String = opt(index).asString() ?: fallback
    fun optLong(index: Int, fallback: Long = 0L): Long = opt(index).asLong() ?: fallback
    fun optInt(index: Int, fallback: Int = 0): Int = opt(index).asLong()?.toInt() ?: fallback
    fun optDouble(index: Int, fallback: Double = Double.NaN): Double = opt(index).asDouble() ?: fallback
    fun optJSONObject(index: Int): JSONObject? = opt(index) as? JSONObject
    fun optJSONArray(index: Int): JSONArray? = opt(index) as? JSONArray

    override fun toString(): String = toElement(this).toString()
    fun toString(indent: Int): String = prettyJson.encodeToString(JsonElement.serializer(), toElement(this))

    private companion object {
        fun parseArray(json: String): MutableList<Any?> {
            val element = runCatching { Json.parseToJsonElement(json) }.getOrElse { throw JSONException(it.message ?: "bad json") }
            if (element !is JsonArray) throw JSONException("Not a JSON array")
            return element.map { fromElement(it) }.toMutableList()
        }
    }
}

private val prettyJson = Json { prettyPrint = true }
