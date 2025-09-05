package com.psionicai.ai_backend.domain.ai.dto.request

import jakarta.validation.constraints.NotBlank
import lombok.Builder

data class KeywordRequest(
    @field:NotBlank(message = "text must not be blank")
    val text: String,
    val topK: Int = 5
)