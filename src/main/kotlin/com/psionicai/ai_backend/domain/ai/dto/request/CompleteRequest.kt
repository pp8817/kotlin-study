package com.psionicai.ai_backend.domain.ai.dto.request

import jakarta.validation.constraints.NotBlank
import lombok.Builder

data class CompleteRequest(
    @field:NotBlank(message = "Prompt is required")
    val prompt: String = "Give me 3 goals as bullet points"
)