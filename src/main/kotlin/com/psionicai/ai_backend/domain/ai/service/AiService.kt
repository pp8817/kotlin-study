package com.psionicai.ai_backend.domain.ai.service

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.psionicai.ai_backend.domain.ai.dto.SummarizeRequest
import com.psionicai.ai_backend.domain.ai.dto.SummarizeResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.stereotype.Service

@Service
class AiService(
    private val client: OpenAIClient
) {
    private val mapper = jacksonObjectMapper()

    fun summarize(req: SummarizeRequest) : SummarizeResponse {
        val sysPrompt = """
            You are a precise summarizer.
            Return strictly JSON: {"summary":"..."} 
            No explanations, no other fields.
        """.trimIndent()

        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4)
            .temperature(0.0)
            .addSystemMessage(sysPrompt)
            .addUserMessage(req.text)
            .build()

        val completion = client.chat().completions().create(params)
        val content = completion.choices()[0].message().content().orElse("{}")

        val regex = Regex("\"summary\"\\s*:\\s*\"([^\"]+)\"")
        val summaryText = try{
            mapper.readTree(content)
            node.get
        }

            regex.find(content)?.groupValues?.get(1) ?: content

        return SummarizeResponse(summaryText)
    }
}