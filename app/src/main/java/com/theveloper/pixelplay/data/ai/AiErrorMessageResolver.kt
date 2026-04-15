package com.theveloper.pixelplay.data.ai

import android.content.Context
import com.theveloper.pixelplay.R
import com.theveloper.pixelplay.data.ai.provider.AiClientException
import com.theveloper.pixelplay.data.ai.provider.AiErrorKind

object AiErrorMessageResolver {

    fun toUserMessage(context: Context, error: Throwable): String {
        val aiError = error.asAiClientException()
        if (aiError != null) {
            val providerName = aiError.provider.displayName
            return when (aiError.kind) {
                AiErrorKind.API_KEY_MISSING -> context.getString(R.string.ai_error_api_key)
                AiErrorKind.AUTHENTICATION -> context.getString(R.string.ai_error_auth, providerName)
                AiErrorKind.INVALID_MODEL -> context.getString(R.string.ai_error_invalid_model, providerName)
                AiErrorKind.RATE_LIMIT -> context.getString(R.string.ai_error_rate_limit, providerName)
                AiErrorKind.NOT_FOUND -> context.getString(R.string.ai_error_not_found, providerName)
                AiErrorKind.BAD_REQUEST -> context.getString(
                    R.string.ai_error_bad_request,
                    providerName,
                    aiError.detail
                )
                AiErrorKind.NETWORK -> context.getString(R.string.ai_error_network, providerName)
                AiErrorKind.INVALID_RESPONSE -> context.getString(R.string.ai_error_invalid_response, providerName)
                AiErrorKind.UNKNOWN -> context.getString(R.string.ai_error_generic, aiError.detail)
            }
        }

        val detail = extractDetail(error)
        return if (detail.contains("api key", ignoreCase = true)) {
            context.getString(R.string.ai_error_api_key)
        } else {
            context.getString(R.string.ai_error_generic, detail)
        }
    }

    fun extractDetail(error: Throwable): String {
        return error.asAiClientException()?.detail
            ?: listOf(error.message.orEmpty(), error.cause?.message.orEmpty())
                .map { raw ->
                    raw.replace(Regex("^AI\\s*Error:\\s*", RegexOption.IGNORE_CASE), "").trim()
                }
                .firstOrNull { it.isNotBlank() }
            ?: "Unknown error"
    }

    private fun Throwable.asAiClientException(): AiClientException? {
        return this as? AiClientException ?: cause as? AiClientException
    }
}
