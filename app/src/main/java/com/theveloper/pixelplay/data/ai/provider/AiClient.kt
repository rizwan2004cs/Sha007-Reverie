package com.theveloper.pixelplay.data.ai.provider

/**
 * Abstract interface for AI providers
 * Defines common operations for text generation and metadata completion
 */
interface AiClient {
    val provider: AiProvider

    /**
     * Generate text content based on a prompt
     * @param model The model identifier to use
     * @param prompt The input prompt
     * @return Generated text response
     * @throws Exception if generation fails
     */
    suspend fun generateContent(model: String, prompt: String): String
    
    /**
     * Get list of available models for this provider
     * @return List of available model names
     */
    suspend fun getAvailableModels(): List<String>

    /**
     * Get the default model for this provider
     * @return Default model identifier
     */
    fun getDefaultModel(): String

    fun normalizeModel(model: String): String = model.trim().removePrefix("models/")

    fun isModelSupported(model: String): Boolean = normalizeModel(model).isNotBlank()

    suspend fun resolveModel(
        requestedModel: String,
        verifyAvailability: Boolean = true
    ): String {
        val normalizedRequested = normalizeModel(requestedModel)
        val candidate = normalizedRequested.ifBlank { normalizeModel(getDefaultModel()) }

        if (!isModelSupported(candidate)) {
            throw AiClientException.invalidModel(provider, candidate)
        }

        // Generation should not depend on a separate model-list lookup, because some providers
        // throttle or restrict discovery endpoints independently from text generation.
        if (!verifyAvailability) {
            return candidate
        }

        val availableModels: List<String> = try {
            getAvailableModels()
                .map(::normalizeModel)
                .filter(String::isNotBlank)
                .distinct()
        } catch (error: AiClientException) {
            when (error.kind) {
                AiErrorKind.API_KEY_MISSING,
                AiErrorKind.INVALID_MODEL,
                AiErrorKind.AUTHENTICATION,
                AiErrorKind.RATE_LIMIT,
                AiErrorKind.NOT_FOUND,
                AiErrorKind.BAD_REQUEST -> throw error
                AiErrorKind.NETWORK,
                AiErrorKind.INVALID_RESPONSE,
                AiErrorKind.UNKNOWN -> emptyList<String>()
            }
        } catch (_: Exception) {
            emptyList<String>()
        }

        if (availableModels.isNotEmpty() && candidate !in availableModels) {
            throw AiClientException.invalidModel(provider, candidate)
        }

        return candidate
    }
}
