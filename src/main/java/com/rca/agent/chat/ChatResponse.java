package com.rca.agent.chat;

public record ChatResponse(
        String message,
        String sessionId,
        String action
) {
    public static ChatResponse reply(String message, String sessionId) {
        return new ChatResponse(message, sessionId, null);
    }

    public static ChatResponse withAction(String message, String sessionId, String action) {
        return new ChatResponse(message, sessionId, action);
    }
}
