package com.psionicai.ai_backend.domain.ai.dto.response

data class SentimentResponse(
    val label: String,
    val score: Int
) {
}