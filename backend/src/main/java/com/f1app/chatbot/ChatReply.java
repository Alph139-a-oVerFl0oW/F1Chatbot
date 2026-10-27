package com.f1app.chatbot;

public record ChatReply(
        String reply,
        String sessionId,
        long elapsedMillis) {
}
