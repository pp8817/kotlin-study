package com.psionicai.ai_backend.domain.ai.dto.response

data class CompleteResponse(
    val output: String,
    val tokens_in: Long,
    val tokens_out: Long,
    val total_tokens: Long
) {
}