package com.theveloper.pixelplay.data.ai.provider

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class AiClientContractTest {

    @Test
    fun `resolveModel falls back to provider default when setting is blank`() {
        val client = FakeAiClient(availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro"))

        val resolved = kotlinx.coroutines.runBlocking {
            client.resolveModel("")
        }

        assertEquals("gemini-2.5-flash", resolved)
    }

    @Test
    fun `resolveModel normalizes prefixed model names`() {
        val client = FakeAiClient(availableModels = listOf("gemini-2.5-flash", "gemini-2.5-pro"))

        val resolved = kotlinx.coroutines.runBlocking {
            client.resolveModel("models/gemini-2.5-pro")
        }

        assertEquals("gemini-2.5-pro", resolved)
    }

    @Test
    fun `resolveModel rejects stale selected models before generation`() {
        val client = FakeAiClient(availableModels = listOf("gemini-2.5-flash"))

        val error = assertThrows(AiClientException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.resolveModel("gemini-1.5-pro")
            }
        }

        assertEquals(AiErrorKind.INVALID_MODEL, error.kind)
    }

    @Test
    fun `resolveModel surfaces auth errors from provider model lookup`() {
        val client = FakeAiClient(
            availableModelsError = AiClientException.fromHttpStatus(
                provider = AiProvider.GEMINI,
                statusCode = 401,
                detail = "Invalid API key"
            )
        )

        val error = assertThrows(AiClientException::class.java) {
            kotlinx.coroutines.runBlocking {
                client.resolveModel("gemini-2.5-flash")
            }
        }

        assertEquals(AiErrorKind.AUTHENTICATION, error.kind)
    }

    @Test
    fun `resolveModel skips remote availability lookup when verification is disabled`() {
        val client = FakeAiClient(
            availableModelsError = AiClientException.fromHttpStatus(
                provider = AiProvider.GEMINI,
                statusCode = 429,
                detail = "Quota exceeded on discovery endpoint"
            )
        )

        val resolved = kotlinx.coroutines.runBlocking {
            client.resolveModel(
                requestedModel = "gemini-2.5-flash",
                verifyAvailability = false
            )
        }

        assertEquals("gemini-2.5-flash", resolved)
    }

    @Test
    fun `http status mapping classifies 429 as rate limit`() {
        val error = AiClientException.fromHttpStatus(
            provider = AiProvider.DEEPSEEK,
            statusCode = 429,
            detail = "Too many requests"
        )

        assertEquals(AiErrorKind.RATE_LIMIT, error.kind)
        assertEquals(429, error.statusCode)
        assertTrue(error.detail.contains("Too many requests"))
    }

    private class FakeAiClient(
        private val availableModels: List<String> = emptyList(),
        private val availableModelsError: AiClientException? = null
    ) : AiClient {
        override val provider: AiProvider = AiProvider.GEMINI

        override suspend fun generateContent(model: String, prompt: String): String = "[]"

        override suspend fun getAvailableModels(): List<String> {
            availableModelsError?.let { throw it }
            return availableModels
        }

        override fun getDefaultModel(): String = "gemini-2.5-flash"

        override fun isModelSupported(model: String): Boolean {
            return normalizeModel(model).startsWith("gemini", ignoreCase = true)
        }
    }
}
