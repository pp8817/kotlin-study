package com.psionicai.ai_backend.domain.ai.service

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.psionicai.ai_backend.domain.ai.dto.request.SummarizeRequest
import com.psionicai.ai_backend.domain.ai.dto.response.SummarizeResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.psionicai.ai_backend.domain.ai.dto.request.SentimentRequest
import com.psionicai.ai_backend.domain.ai.dto.response.SentimentResponse
import org.springframework.stereotype.Service

@Service
class AiService(
    private val client: OpenAIClient,

) {
    // Json 파서 준비 : LLM 응답이 Json일 때 파싱
    private val mapper = jacksonObjectMapper()

    // 공통 상수
    private val MODEL = ChatModel.GPT_4O_MINI
    private val TEMPERATURE = 0.0

    /* LLM 기반 텍스트 요약 */
    fun summarize(req: SummarizeRequest) : SummarizeResponse {
        val sys = """
            You are a precise summarizer.
            Return strictly JSON: {"summary":"..."} 
            No explanations, no other fields.
        """.trimIndent()

        val user = "Summarize the following text:\n```${req.text}```"

        val content = runJsonTask(sys, user)
        val summary = extractStringField(content, "summary")
        return SummarizeResponse(summary)
    }

    /* LLM 기반 감성분석 */
    fun sentiment(req: SentimentRequest): SentimentResponse {
        val sys = """
            You are a sentiment classifier.
            Return strictly JSON: {"label":"positive|neutral|negative","score":-10..10}
            No explanations, no other fields.
        """.trimIndent()

        val user = "Classify sentiment for the following text:\n```${req.text}```"

        val content = runJsonTask(sys, user)

        // JSON 우선 파싱 -> 실패 시 기본값
        val node = runCatching { mapper.readTree(content) }.getOrNull()
        if (node != null) {
            val label = node.get("label")?.asText().orEmpty().ifBlank { "neutral" }
            val score = (node.get("score")?.asInt() ?: 0).coerceIn(-10, 10)
            return SentimentResponse(label, score)
        }
        return SentimentResponse("neutral", 0)
    }

    /* 공통 유틸 */

    private fun runJsonTask(sysPrompt: String, userMessage: String): String {
        val params = buildChatParams(sysPrompt, userMessage)
        return runCatching {
            client.chat().completions().create(params)
                .choices().firstOrNull()
                ?.message()?.content()?.orElse(null)
        }.getOrNull() ?: "{}"
    }

    private fun buildChatParams(sysPrompt: String, userMessage: String): ChatCompletionCreateParams {
        val params = ChatCompletionCreateParams.builder()
            .model(MODEL)
            .temperature(TEMPERATURE)
            .addSystemMessage(sysPrompt)
            .addUserMessage(userMessage)
            .build()

        return params
    }

    /**
     * runCatching {...}
     * ** 블록 안의 코드를 실행하고, 결과를 Result<T> 타입으로 감쌈.
     * ** 예외 발생 시 Result.Failure, 정상 종료 시 Result.Success 반환
     * ** try-catch를 더 깔끔하게 쓰는 방식
     *
     * let {...}
     * ** null이 아닌 경우 블록을 실행하고, 블록 결과 반환
     * ** 호출 대상이 null이면 블록이 실행되지 않음
     *
     * takeIf {조건}
     * ** 조건이 true면 자기 자신 반환
     * ** 조건이 false면 null 반환
     */
    private fun extractStringField(jsonOrText: String, field: String): String {
        // json 파싱 시도
        runCatching {
            val node = mapper.readTree(jsonOrText)
            node.get(field)?.asText()?.takeIf { it.isNotBlank() }
        }.getOrNull()?.let { return it }

        // 정규식 풀백
        val regex = Regex("\"${Regex.escape(field)}\"\\s*:\\s*\"([^\"]+)\"")
        regex.find(jsonOrText)?.groupValues?.getOrNull(1)?.let { return it }

        // 실패시 원문 반환
        return jsonOrText
    }

}