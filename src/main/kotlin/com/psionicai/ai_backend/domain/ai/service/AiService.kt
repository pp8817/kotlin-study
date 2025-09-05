package com.psionicai.ai_backend.domain.ai.service

import com.fasterxml.jackson.databind.JsonNode
import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.psionicai.ai_backend.domain.ai.dto.SummarizeRequest
import com.psionicai.ai_backend.domain.ai.dto.SummarizeResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.openai.models.chat.completions.ChatCompletion
import org.springframework.stereotype.Service

@Service
class AiService(
    private val client: OpenAIClient
) {
    // Json 파서 준비 : LLM 응답이 Json일 때 파싱
    private val mapper = jacksonObjectMapper()

    // LLM 프롬프트 정의, 모델이 반드시 {"summary": "..."} 형태로 응답
    fun summarize(req: SummarizeRequest) : SummarizeResponse {
        val sysPrompt = """
            You are a precise summarizer.
            Return strictly JSON: {"summary":"..."} 
            No explanations, no other fields.
        """.trimIndent()

        // OpenAI API 요청 파라미터 구성
        val params = ChatCompletionCreateParams.builder()
            .model(ChatModel.GPT_4)
            .temperature(0.0)
            .addSystemMessage(sysPrompt)
            .addUserMessage(req.text)
            .build()

        // API 호출 : OpenAI API에 요청 보내기
        val completion: ChatCompletion = client.chat().completions().create(params)

        // 모델 응답 꺼내기, 기본 값: "{}"
        val content = completion.choices()[0].message().content().orElse("{}")

        // 응답 파싱
        val summaryText = try{
            // Jackson의 Tree Model을 사용해서, LLM이 반환한 문자열(JSON)을 안전하게 파싱
            // Json을 객체로 파싱하지 않고, 동적으로 탐색 가능한 트리 구조로 변환
            val node: JsonNode = mapper.readTree(content)
            node.get("summary")?.asText() ?: content
        } catch (_: Exception){
            // 정규식을 사용해 LLM 응답 문자열에서 "summary": "..." 부분만 뽑아내는 폴백 처리
            Regex("\"summary\"\\s*:\\s*\"([^\"]+)\"").find(content)?.groupValues?.get(1) ?: content
        }

        return SummarizeResponse(summaryText)
    }
}