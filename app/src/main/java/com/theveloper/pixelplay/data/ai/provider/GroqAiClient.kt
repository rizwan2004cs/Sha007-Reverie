package com.theveloper.pixelplay.data.ai.provider

import com.openai.client.okhttp.OpenAIOkHttpClient
import com.openai.models.chat.completions.ChatCompletionCreateParams
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Groq support built on top of the official OpenAI Java SDK pointed at Groq's
 * OpenAI-compatible endpoint.
 */
class GroqAiClient(private val apiKey: String) : AiClient {
    override val provider: AiProvider = AiProvider.GROQ

    companion object {
        private const val DEFAULT_GROQ_MODEL = "llama-3.3-70b-versatile"
        private const val BASE_URL = "https://api.groq.com/openai/v1"
    }

    @Serializable
    private data class GroqModelItem(val id: String)

    @Serializable
    private data class GroqModelsResponse(val data: List<GroqModelItem> = emptyList())

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private val sdkClient by lazy {
        OpenAIOkHttpClient.builder()
            .apiKey(apiKey)
            .baseUrl(BASE_URL)
            .build()
    }

    override suspend fun generateContent(model: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            val normalizedModel = normalizeModel(model).ifBlank { DEFAULT_GROQ_MODEL }
            try {
                val params = ChatCompletionCreateParams.builder()
                    .model(normalizedModel)
                    .addUserMessage(prompt)
                    .build()

                val response = sdkClient.chat().completions().create(params)
                response.choices()
                    .firstOrNull()
                    ?.message()
                    ?.content()
                    ?.orElse(null)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw AiClientException.invalidResponse(
                        provider,
                        "Groq response has no text content."
                    )
            } catch (error: AiClientException) {
                throw error
            } catch (error: Exception) {
                throw mapSdkException(error)
            }
        }
    }

    override suspend fun getAvailableModels(): List<String> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            executeRequest(request) { responseBody ->
                val modelsResponse = json.decodeFromString(GroqModelsResponse.serializer(), responseBody)
                val models = modelsResponse.data
                    .map { normalizeModel(it.id) }
                    .filter(::isModelSupported)
                    .distinct()
                if (models.isEmpty()) getDefaultModels() else models.sorted()
            }
        }
    }

    override fun getDefaultModel(): String = DEFAULT_GROQ_MODEL

    override fun normalizeModel(model: String): String = model.trim()

    override fun isModelSupported(model: String): Boolean {
        val normalized = normalizeModel(model)
        return normalized.isNotBlank() &&
            !normalized.contains("whisper", ignoreCase = true) &&
            !normalized.contains("tts", ignoreCase = true)
    }

    private fun getDefaultModels(): List<String> {
        return listOf(
            "llama-3.3-70b-versatile",
            "llama-3.1-8b-instant"
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
                        "Groq returned an empty response body."
                    )
                return onSuccess(body)
            }
        } catch (error: AiClientException) {
            throw error
        } catch (error: IOException) {
            throw AiClientException.network(provider, error.message ?: "Groq request failed.", error)
        } catch (error: Exception) {
            throw AiClientException.unknown(provider, error.message ?: "Groq request failed.", error)
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
            fallbackMessage.ifBlank { "Groq request failed." }
        )
    }

    private fun mapSdkException(error: Exception): AiClientException {
        val detail = AiClientException.sanitizeDetail(
            raw = error.message ?: error.cause?.message,
            fallback = "Groq request failed."
        )
        val lowered = detail.lowercase()

        val statusCode = Regex("""\b(4\d\d|5\d\d)\b""")
            .find(detail)
            ?.groupValues
            ?.firstOrNull()
            ?.toIntOrNull()

        if (statusCode != null) {
            return AiClientException.fromHttpStatus(provider, statusCode, detail)
        }

        return when {
            lowered.contains("api key") || lowered.contains("unauthorized") || lowered.contains("permission denied") ->
                AiClientException(
                    provider = provider,
                    kind = AiErrorKind.AUTHENTICATION,
                    detail = detail,
                    cause = error
                )
            lowered.contains("timed out") ||
                lowered.contains("timeout") ||
                lowered.contains("unknown host") ||
                lowered.contains("unable to resolve host") ||
                lowered.contains("ssl") ||
                lowered.contains("connection") ||
                lowered.contains("network") ->
                AiClientException.network(provider, detail, error)
            else -> AiClientException.unknown(provider, detail, error)
        }
    }
}
