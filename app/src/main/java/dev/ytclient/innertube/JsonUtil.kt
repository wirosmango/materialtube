package dev.ytclient.innertube

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** Идёт по вложенному JSON: String — ключ объекта, Int — индекс массива. */
internal fun JsonElement?.at(vararg keys: Any): JsonElement? {
    var current: JsonElement? = this
    for (key in keys) {
        current = when (key) {
            is String -> (current as? JsonObject)?.get(key)
            is Int -> (current as? JsonArray)?.getOrNull(key)
            else -> null
        }
        if (current == null) return null
    }
    return current
}

internal fun JsonElement?.string(): String? = (this as? JsonPrimitive)?.contentOrNull

internal fun JsonElement?.int(): Int? = (this as? JsonPrimitive)?.intOrNull

internal fun JsonElement?.long(): Long? = (this as? JsonPrimitive)?.longOrNull

internal fun JsonElement?.array(): List<JsonElement> = (this as? JsonArray) ?: emptyList()

/** Текст из `{"simpleText": ...}` или `{"runs": [{"text": ...}]}`. */
internal fun JsonElement?.text(): String? {
    this ?: return null
    at("simpleText").string()?.let { return it }
    return at("runs").array()
        .joinToString("") { it.at("text").string().orEmpty() }
        .takeIf { it.isNotBlank() }
}
