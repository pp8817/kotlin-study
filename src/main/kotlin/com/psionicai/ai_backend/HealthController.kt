package com.psionicai.ai_backend;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/health")
class HealthController {

    data class HealthResponse(val status: String);

    @GetMapping
    fun health(): HealthResponse {
        return HealthResponse("UP");
    }
}