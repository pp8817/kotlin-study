package com.psionicai.ai_backend.global.config

import com.openai.client.OpenAIClient
import com.openai.client.okhttp.OpenAIOkHttpClient
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenAIConfig(
    @Value("\${app.openai.api-key}") private val apikey: String
){
    @Bean
    fun openAiClient(): OpenAIClient =
        OpenAIOkHttpClient.builder()
            .apiKey(apikey)
            .build()
}