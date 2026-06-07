package com.rca.agent.chat;

import java.time.Instant;
import java.util.List;

public record ChatMessage(String role, String content, Instant timestamp) {
    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, Instant.now());
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, Instant.now());
    }
}
