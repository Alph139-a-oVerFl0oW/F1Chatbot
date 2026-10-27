package com.f1app.chatbot;

import java.util.UUID;

import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private static final Logger log = LoggerFactory.getLogger(ChatController.class);

    private final F1Assistant assistant;

    public ChatController(F1Assistant assistant) {
        this.assistant = assistant;
    }

    @PostMapping
    public ResponseEntity<ChatReply> chat(@Valid @RequestBody ChatMessageRequest request) {
        String sessionId = (request.sessionId() == null || request.sessionId().isBlank())
                ? UUID.randomUUID().toString()
                : request.sessionId();

        long started = System.currentTimeMillis();
        try {
            String reply = assistant.chat(sessionId, request.message());
            long elapsed = System.currentTimeMillis() - started;
            log.info("chat [{}] answered in {}ms", sessionId, elapsed);
            return ResponseEntity.ok(new ChatReply(reply, sessionId, elapsed));

        } catch (RuntimeException e) {
            // Upstream LLM failures (bad key, rate limit, timeout) must not
            // surface as an opaque 500 to the browser.
            log.error("chat [{}] failed: {}", sessionId, e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new ChatReply(
                    "The assistant is unavailable right now. Check that GROQ_API_KEY is set "
                            + "in infra/.env and that the backend can reach the API.",
                    sessionId,
                    System.currentTimeMillis() - started));
        }
    }
}
