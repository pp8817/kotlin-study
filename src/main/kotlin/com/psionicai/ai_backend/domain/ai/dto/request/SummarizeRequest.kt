package com.psionicai.ai_backend.domain.ai.dto.request

import jakarta.validation.constraints.NotBlank

data class SummarizeRequest(
    @field:NotBlank(message = "text must not be blank")
    val text: String
)