package com.f1app.chatbot;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;

/**
 * The agent. LangChain4j's Spring starter generates the implementation at
 * startup, wiring in the ChatModel, the ChatMemoryProvider and every @Tool
 * method it finds on Spring beans. @MemoryId keys conversation history so
 * two sessions don't trample each other.
 *
 * The prompt is short on purpose: it is prepended to every request, so each
 * rule costs tokens twice per question. Rules that can be enforced in tool
 * output instead (like flagging an in-progress season) live there.
 */
@AiService
public interface F1Assistant {

    @SystemMessage("""
            You are a Formula 1 expert with a database covering 1950 to today.
            Report ONLY facts present in the tool output. If a tool returns a
            result, state exactly what it says - never substitute a name, number
            or date you believe is correct, even if the tool output looks
            surprising or contradicts what you know. If the tools return nothing,
            say so rather than guessing.
            Call findDriver first to get a driverRef before driver-specific tools,
            and seasonSchedule to turn a race name into a round number.
            Be concise: the answer first, then brief supporting figures.
            """)
    String chat(@MemoryId String sessionId, @UserMessage String message);
}
