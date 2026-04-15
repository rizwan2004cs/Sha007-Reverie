package com.theveloper.pixelplay.data.ai

import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class AiResponseParserTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `extractPlaylistSongIds reads fenced json arrays`() {
        val response = """
            ```json
            ["song-1", "song-2", "song-2"]
            ```
        """.trimIndent()

        val ids = AiResponseParser.extractPlaylistSongIds(json, response)

        assertEquals(listOf("song-1", "song-2"), ids)
    }

    @Test
    fun `extractPlaylistSongIds unwraps song ids from json objects`() {
        val response = """
            Here is your playlist:
            {
              "songs": ["alpha", "beta", "beta"]
            }
        """.trimIndent()

        val ids = AiResponseParser.extractPlaylistSongIds(json, response)

        assertEquals(listOf("alpha", "beta"), ids)
    }

    @Test
    fun `extractFirstJsonObject finds embedded metadata payload`() {
        val response = """
            The best match is:
            {
              "album": "Discovery",
              "genre": "Electronic"
            }
            Hope that helps.
        """.trimIndent()

        val extracted = AiResponseParser.extractFirstJsonObject(response)

        assertNotNull(extracted)
        assertEquals(
            """
            {
              "album": "Discovery",
              "genre": "Electronic"
            }
            """.trimIndent(),
            extracted
        )
    }
}
