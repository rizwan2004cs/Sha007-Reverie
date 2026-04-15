package com.theveloper.pixelplay.data.ai.provider

enum class AiErrorKind {
    API_KEY_MISSING,
    AUTHENTICATION,
    INVALID_MODEL,
    RATE_LIMIT,
    NOT_FOUND,
    BAD_REQUEST,
    NETWORK,
    INVALID_RESPONSE,
    UNKNOWN
}

class AiClientException(
    val provider: AiProvider,
    val kind: AiErrorKind,
    val statusCode: Int? = null,
    val detail: String,
    cause: Throwable? = null
) : Exception(detail, cause) {
    companion object {
        fun missingApiKey(provider: AiProvider): AiClientException {
            return AiClientException(
                provider = provider,
                kind = AiErrorKind.API_KEY_MISSING,
                detail = "API key not configured for ${provider.displayName}."
            )
        }

        fun invalidModel(provider: AiProvider, model: String): AiClientException {
            return AiClientException(
                provider = provider,
                kind = AiErrorKind.INVALID_MODEL,
                detail = "Selected model '$model' is unavailable for ${provider.displayName}."
            )
        }

        fun invalidResponse(
            provider: AiProvider,
            detail: String,
            cause: Throwable? = null
        ): AiClientException {
            return AiClientException(
                provider = provider,
                kind = AiErrorKind.INVALID_RESPONSE,
                detail = sanitizeDetail(detail, "The AI response could not be parsed."),
                cause = cause
            )
        }

        fun network(
            provider: AiProvider,
            detail: String,
            cause: Throwable? = null
        ): AiClientException {
            return AiClientException(
                provider = provider,
                kind = AiErrorKind.NETWORK,
                detail = sanitizeDetail(detail, "Network request failed."),
                cause = cause
            )
        }

        fun unknown(
            provider: AiProvider,
            detail: String,
            cause: Throwable? = null
        ): AiClientException {
            return AiClientException(
                provider = provider,
                kind = AiErrorKind.UNKNOWN,
                detail = sanitizeDetail(detail, "Unknown AI error."),
                cause = cause
            )
        }

        fun fromHttpStatus(
            provider: AiProvider,
            statusCode: Int,
            detail: String
        ): AiClientException {
            val sanitizedDetail = sanitizeDetail(detail, "HTTP $statusCode")
            val kind = when (statusCode) {
                400 -> AiErrorKind.BAD_REQUEST
                401, 403 -> AiErrorKind.AUTHENTICATION
                404 -> AiErrorKind.NOT_FOUND
                429 -> AiErrorKind.RATE_LIMIT
                else -> AiErrorKind.UNKNOWN
            }
            return AiClientException(
                provider = provider,
                kind = kind,
                statusCode = statusCode,
                detail = sanitizedDetail
            )
        }

        fun sanitizeDetail(raw: String?, fallback: String): String {
            val normalized = raw
                ?.replace(Regex("\\s+"), " ")
                ?.replace(Regex("key=[^&\\s]+", RegexOption.IGNORE_CASE), "key=<redacted>")
                ?.trim()
                ?.takeIf { it.isNotEmpty() }
                ?: fallback
            return normalized.take(220)
        }
    }
}
