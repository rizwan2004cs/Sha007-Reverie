package com.theveloper.pixelplay.data.ai

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object AiResponseParser {

    fun stripCodeFences(rawResponse: String): String {
        return rawResponse
            .replace("```json", "")
            .replace("```", "")
            .trim()
    }

    fun extractFirstJsonObject(rawResponse: String): String? {
        val sanitized = stripCodeFences(rawResponse)
        for (startIndex in sanitized.indices) {
            if (sanitized[startIndex] != '{') continue

            var depth = 0
            var inString = false
            var isEscaped = false

            for (index in startIndex until sanitized.length) {
                val character = sanitized[index]

                if (inString) {
                    if (isEscaped) {
                        isEscaped = false
                        continue
                    }

                    when (character) {
                        '\\' -> isEscaped = true
                        '"' -> inString = false
                    }
                    continue
                }

                when (character) {
                    '"' -> inString = true
                    '{' -> depth++
                    '}' -> {
                        depth--
                        if (depth == 0) {
                            return sanitized.substring(startIndex, index + 1)
                        }
                    }
                }
            }
        }

        return null
    }

    fun extractPlaylistSongIds(json: Json, rawResponse: String): List<String> {
        val sanitized = stripCodeFences(rawResponse)

        runCatching {
            extractSongIdsFromElement(json.parseToJsonElement(sanitized))
        }.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }

        for (startIndex in sanitized.indices) {
            if (sanitized[startIndex] != '[' && sanitized[startIndex] != '{') continue

            var depth = 0
            var inString = false
            var isEscaped = false
            val openingChar = sanitized[startIndex]
            val closingChar = if (openingChar == '[') ']' else '}'

            for (index in startIndex until sanitized.length) {
                val character = sanitized[index]

                if (inString) {
                    if (isEscaped) {
                        isEscaped = false
                        continue
                    }

                    when (character) {
                        '\\' -> isEscaped = true
                        '"' -> inString = false
                    }
                    continue
                }

                when (character) {
                    '"' -> inString = true
                    openingChar -> depth++
                    closingChar -> {
                        depth--
                        if (depth == 0) {
                            val candidate = sanitized.substring(startIndex, index + 1)
                            val decoded = runCatching {
                                extractSongIdsFromElement(json.parseToJsonElement(candidate))
                            }
                            decoded.getOrNull()?.takeIf { it.isNotEmpty() }?.let { return it }
                            break
                        }
                    }
                }
            }
        }

        throw IllegalArgumentException("AI response did not contain a valid playlist.")
    }

    private fun extractSongIdsFromElement(element: JsonElement): List<String> {
        val values = when (element) {
            is JsonArray -> element.flatMap(::extractSongIdsFromElement)
            is JsonObject -> {
                val preferredKeys = listOf(
                    "song_ids",
                    "songIds",
                    "songs",
                    "playlist",
                    "tracks",
                    "ids"
                )
                preferredKeys.firstNotNullOfOrNull { key ->
                    element[key]?.let(::extractSongIdsFromElement)?.takeIf { it.isNotEmpty() }
                } ?: element.values.flatMap(::extractSongIdsFromElement)
            }
            is JsonPrimitive -> listOfNotNull(
                element.contentOrNull
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && !it.startsWith("{") && !it.startsWith("[") }
            )
            else -> emptyList()
        }

        return values.distinct()
    }
}
