package com.psionicai.ai_backend.domain.ai.api

import com.psionicai.ai_backend.domain.ai.dto.SummarizeRequest
import com.psionicai.ai_backend.domain.ai.dto.SummarizeResponse
import com.psionicai.ai_backend.domain.ai.service.AiService
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/ai")
class AiController(
    private val aiService: AiService
) {
    /* 요약 */
    @PostMapping("/summarize")
    fun summarize(@Validated @RequestBody req: SummarizeRequest): ResponseEntity<SummarizeResponse>? {
        val response: SummarizeResponse = aiService.summarize(req)
        return ResponseEntity.ok(response)
    }
}