package com.psionicai.ai_backend.domain.ai.service

import com.openai.client.OpenAIClient
import com.openai.models.ChatModel
import com.openai.models.chat.completions.ChatCompletionCreateParams
import com.psionicai.ai_backend.domain.ai.dto.request.SummarizeRequest
import com.psionicai.ai_backend.domain.ai.dto.response.SummarizeResponse
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.psionicai.ai_backend.domain.ai.dto.request.CompleteRequest
import com.psionicai.ai_backend.domain.ai.dto.request.KeywordRequest
import com.psionicai.ai_backend.domain.ai.dto.request.SentimentRequest
import com.psionicai.ai_backend.domain.ai.dto.response.CompleteResponse
import com.psionicai.ai_backend.domain.ai.dto.response.KeywordResponse
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

    /* 상위 키워드 추출: topK 기반 */
    fun keyword(req: KeywordRequest): KeywordResponse {
        val topK = req.topK
        val sys = """
            You are a keyword extraction engine.
            Extract the most salient, deduplicated keywords from the user's text.
            - Language: follow the language of the input.
            - Return EXACTLY $topK keywords (if not enough, return as many as possible).
            - No explanations. Output ONLY valid JSON.
            - JSON schema: {"keywords": ["k1","k2",...]}
        """.trimIndent()

        val user = "Extract the top $topK keywords from the following text:\n```${req.text}```"

        val content = runJsonTask(sys, user)

        // Json 파싱 시도
        val parsedKeywords: List<String>? = runCatching {
            val nano = mapper.readTree(content)
            val arr = nano.get("keywords")
            if (arr != null && arr.isArray) {
                arr.mapNotNull { it.asText().trim() }
                    .filter { it.isNotBlank() }
            } else null
        }.getOrNull()

        // LLM이 JSON 형식을 어길 경우 간단한 로컬 백업 로직
        val fallbackKeywords = if (parsedKeywords.isNullOrEmpty()) {
            req.text
                .lowercase()
                .split(Regex("[\\s,.;:!?'\"()\\[\\]{}<>/\\\\]+"))
                .filter { it.isNotBlank() }
                .groupingBy { it }.eachCount()
                .entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key })
                .map { it.key }
        } else parsedKeywords

        // 중복 제거 및 개수 제한
        val deduped = LinkedHashSet<String>().apply { fallbackKeywords.forEach { add(it) } }.toList()
        val capped = deduped.take(topK)

        return KeywordResponse(
            keywords = capped
        )
    }

    /* 프롬프트 응답(LLM) + 토큰 사용량 기록 */
    fun complete(req: CompleteRequest): CompleteResponse {
        // 시스템/유저 메시지 구성
        val sys = """
            You are a helpful assistant.
            Answer the user's prompt directly and concisely.
            Do not include any extra metadata.
        """.trimIndent()

        val user = req.prompt

        // 모델 추출
        val params = buildChatParams(sys, user)
        val completion = runCatching { client.chat().completions().create(params) }
            .getOrElse { throw RuntimeException("OpenAI call failed!", it) }

        // 응답 텍스트(content) 안전 추출
        val output = (completion.choices().firstOrNull()
            ?.message()?.content()?.orElse(null)
            ?.takeIf { it.isNotBlank() }
            ?: "")

        // 토큰 사용량 추출
        val usage = completion.usage().orElse(null)
        val promptTokens = usage?.promptTokens() ?: 0
        val completionTokens = usage?.completionTokens() ?: 0
        val totalTokens = usage?.totalTokens() ?: (promptTokens + completionTokens)

        // 5) 응답 DTO 구성
        return CompleteResponse(
            output,
            promptTokens,
            completionTokens,
            totalTokens
        )
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