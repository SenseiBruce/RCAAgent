package com.rca.agent.chat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
    @NotBlank(message = "Message is required") @Size(max = 8000, message = "Message is too long")
        String message,
    @Size(max = 128) String sessionId) {}
