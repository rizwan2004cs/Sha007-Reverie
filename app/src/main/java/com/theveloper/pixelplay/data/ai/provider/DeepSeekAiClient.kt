package com.theveloper.pixelplay.data.ai.provider

import java.io.IOException
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
import java.util.concurrent.TimeUnit

/**
 * DeepSeek AI provider implementation
 * Uses OpenAI-compatible API
 */
class DeepSeekAiClient(private val apiKey: String) : AiClient {
    override val provider: AiProvider = AiProvider.DEEPSEEK

    companion object {
        private const val DEFAULT_DEEPSEEK_MODEL = "deepseek-chat"
        private const val BASE_URL = "https://api.deepseek.com"
    }
    
    @Serializable
    data class ChatMessage(val role: String, val content: String)
    
    @Serializable
    data class ChatRequest(
        val model: String,
        val messages: List<ChatMessage>,
        val temperature: Double = 0.7
    )
    
    @Serializable
    data class ChatChoice(val message: ChatMessage)
    
    @Serializable
    data class ChatResponse(val choices: List<ChatChoice>)
    
    @Serializable
    data class ModelItem(val id: String)
    
    @Serializable
    data class ModelsResponse(val data: List<ModelItem>)
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
    
    private val json = Json { 
        ignoreUnknownKeys = true
        isLenient = true
    }

    override suspend fun generateContent(model: String, prompt: String): String {
        return withContext(Dispatchers.IO) {
            val requestBody = ChatRequest(
                model = normalizeModel(model).ifBlank { DEFAULT_DEEPSEEK_MODEL },
                messages = listOf(ChatMessage(role = "user", content = prompt))
            )
            
            val jsonBody = json.encodeToString(ChatRequest.serializer(), requestBody)
            val body = jsonBody.toRequestBody("application/json".toMediaType())
            
            val request = Request.Builder()
                .url("$BASE_URL/v1/chat/completions")
                .addHeader("Authorization", "Bearer $apiKey")
                .addHeader("Content-Type", "application/json")
                .post(body)
                .build()

            executeRequest(request) { responseBody ->
                val chatResponse = json.decodeFromString<ChatResponse>(responseBody)
                chatResponse.choices.firstOrNull()?.message?.content?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: throw AiClientException.invalidResponse(
                        provider,
                        "DeepSeek response has no content."
                    )
            }
        }
    }

    override suspend fun getAvailableModels(): List<String> {
        return withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url("$BASE_URL/v1/models")
                .addHeader("Authorization", "Bearer $apiKey")
                .get()
                .build()

            executeRequest(request) { responseBody ->
                val modelsResponse = json.decodeFromString<ModelsResponse>(responseBody)
                val models = modelsResponse.data
                    .map { normalizeModel(it.id) }
                    .filter(::isModelSupported)
                    .distinct()
                if (models.isEmpty()) getDefaultModels() else models
            }
        }
    }

    override fun getDefaultModel(): String = DEFAULT_DEEPSEEK_MODEL

    override fun normalizeModel(model: String): String = model.trim()

    override fun isModelSupported(model: String): Boolean {
        return normalizeModel(model).startsWith("deepseek", ignoreCase = true)
    }

    private fun getDefaultModels(): List<String> {
        return listOf(
            "deepseek-chat",
            "deepseek-reasoner"
        )
    }

    private inline fun <T> executeRequest(
        request: Request,
        onSuccess: (String) -> T
    ): T {
        try {
            client.newCall(request).execute().use { response ->
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
                        "DeepSeek returned an empty response body."
                    )
                return onSuccess(body)
            }
        } catch (error: AiClientException) {
            throw error
        } catch (error: IOException) {
            throw AiClientException.network(
                provider,
                error.message ?: "DeepSeek request failed.",
                error
            )
        } catch (error: Exception) {
            throw AiClientException.unknown(
                provider,
                error.message ?: "DeepSeek request failed.",
                error
            )
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
            fallbackMessage.ifBlank { "DeepSeek request failed." }
        )
    }
}
