package com.rca.agent.chat;

import java.util.List;

public record ChatResponse(
        String message,
        String sessionId,
        String action,
        List<String> quickReplies
) {
    public static ChatResponse reply(String message, String sessionId) {
        return new ChatResponse(message, sessionId, null, List.of());
    }

    public static ChatResponse reply(String message, String sessionId, List<String> quickReplies) {
        return new ChatResponse(message, sessionId, null, quickReplies);
    }

    public static ChatResponse withAction(String message, String sessionId, String action) {
        return new ChatResponse(message, sessionId, action, List.of());
    }

    public static ChatResponse withAction(String message, String sessionId, String action, List<String> quickReplies) {
        return new ChatResponse(message, sessionId, action, quickReplies);
    }
}
