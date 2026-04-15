package com.theveloper.pixelplay.data.ai.provider

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Gemini AI provider implementation
 */
class GeminiAiClient(private val apiKey: String) : AiClient {
    override val provider: AiProvider = AiProvider.GEMINI

    companion object {
        private const val DEFAULT_GEMINI_MODEL = "gemini-2.5-flash"
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta"
    }

    @Serializable
    private data class GeminiModelsResponse(val models: List<GeminiModelItem> = emptyList())

    @Serializable
    private data class GeminiModelItem(
        val name: String,
        val supportedGenerationMethods: List<String> = emptyList()
    )

    @Serializable
    private data class GeminiPart(val text: String)

    @Serializable
    private data class GeminiContent(val parts: List<GeminiPart>)

    @Serializable
    private data class GeminiGenerateContentRequest(val contents: List<GeminiContent>)

    @Serializable
    private data class GeminiCandidateContent(val parts: List<GeminiPart> = emptyList())

    @Serializable
    private data class GeminiCandidate(val content: GeminiCandidateContent? = null)

    @Serializable
    private data class GeminiGenerateContentResponse(
        val candidates: List<GeminiCandidate> = emptyList()
    )

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun generateContent(model: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            val normalizedModel = normalizeModel(model).ifBlank { DEFAULT_GEMINI_MODEL }
            val requestBody = GeminiGenerateContentRequest(
                contents = listOf(
                    GeminiContent(parts = listOf(GeminiPart(text = prompt)))
                )
            )

            val request = Request.Builder()
                .url("$BASE_URL/models/$normalizedModel:generateContent?key=$apiKey")
                .addHeader("Content-Type", "application/json")
                .post(
                    json.encodeToString(requestBody)
                        .toRequestBody("application/json".toMediaType())
                )
                .build()

            executeRequest(request) { responseBody ->
                val parsed = json.decodeFromString(
                    GeminiGenerateContentResponse.serializer(),
                    responseBody
                )
                parsed.candidates
                    .firstOrNull()
                    ?.content
                    ?.parts
                    ?.joinToString(separator = "\n") { it.text.trim() }
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw AiClientException.invalidResponse(
                        provider,
                        "Gemini response has no text content."
                    )
            }
        }
    }

    override suspend fun getAvailableModels(): List<String> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/models?key=$apiKey")
                .get()
                .build()

            executeRequest(request, ::parseModelsFromResponse)
        }
    }

    override fun getDefaultModel(): String = DEFAULT_GEMINI_MODEL

    override fun normalizeModel(model: String): String = model.trim().removePrefix("models/")

    override fun isModelSupported(model: String): Boolean {
        val normalized = normalizeModel(model)
        return normalized.startsWith("gemini", ignoreCase = true) &&
            !normalized.contains("embedding", ignoreCase = true)
    }

    private fun parseModelsFromResponse(jsonResponse: String): List<String> {
        val models = try {
            json.decodeFromString(GeminiModelsResponse.serializer(), jsonResponse)
                .models
                .asSequence()
                .filter { model ->
                    model.supportedGenerationMethods.any { method ->
                        method.equals("generateContent", ignoreCase = true)
                    }
                }
                .map { normalizeModel(it.name) }
                .filter(::isModelSupported)
                .distinct()
                .sorted()
                .toList()
        } catch (error: Exception) {
            throw AiClientException.invalidResponse(
                provider,
                "Unable to parse Gemini model list.",
                error
            )
        }

        return if (models.isNotEmpty()) models else getDefaultModels()
    }

    private fun getDefaultModels(): List<String> {
        return listOf(
            "gemini-2.5-flash",
            "gemini-2.5-pro",
            "gemini-1.5-flash",
            "gemini-1.5-pro"
        )
    }

    private inline fun <T> executeRequest(
        request: Request,
        onSuccess: (String) -> T
    ): T {
        try {
            httpClient.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    throw AiClientException.fromHttpStatus(
                        provider = provider,
                        statusCode = response.code,
                        detail = extractErrorDetail(responseBody, response.message)
                    )
                }

                val body = responseBody.takeIf { it.isNotBlank() }
                    ?: throw AiClientException.invalidResponse(
                        provider,
                        "Gemini returned an empty response body."
                    )
                return onSuccess(body)
            }
        } catch (error: AiClientException) {
            throw error
        } catch (error: IOException) {
            throw AiClientException.network(provider, error.message ?: "Gemini request failed.", error)
        } catch (error: Exception) {
            throw AiClientException.unknown(provider, error.message ?: "Gemini request failed.", error)
        }
    }

    private fun extractErrorDetail(responseBody: String, fallbackMessage: String): String {
        val parsedMessage = runCatching {
            val payload = json.parseToJsonElement(responseBody)
            payload.jsonObject["error"]
                ?.jsonObject
                ?.get("message")
                ?.let { it as? JsonPrimitive }
                ?.contentOrNull
        }.getOrNull()

        return AiClientException.sanitizeDetail(
            parsedMessage ?: responseBody.takeIf { it.isNotBlank() } ?: fallbackMessage,
            fallbackMessage.ifBlank { "Gemini request failed." }
        )
    }
}
