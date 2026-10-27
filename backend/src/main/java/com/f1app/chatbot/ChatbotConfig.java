package com.f1app.chatbot;

import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ChatbotConfig {

    /**
     * One sliding-window memory per session id.
     *
     * 20 messages is a compromise: enough for follow-ups like "and the year
     * before?" to resolve, small enough that the prompt stays cheap. Note this
     * is memory, not history — it lives in the JVM heap and dies with the
     * process. Persisting conversations means a ChatMemoryStore backed by
     * Postgres, which pairs naturally with Phase 5's user accounts.
     */
    @Bean
    public ChatMemoryProvider chatMemoryProvider() {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .maxMessages(20)
                .build();
    }
}
