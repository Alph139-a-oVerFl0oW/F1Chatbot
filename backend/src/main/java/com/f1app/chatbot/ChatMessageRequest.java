package com.f1app.chatbot;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Named ChatMessageRequest rather than ChatRequest to avoid colliding with
 * LangChain4j's own ChatRequest type.
 */
public record ChatMessageRequest(
        @NotBlank @Size(max = 2000) String message,
        String sessionId) {
}
