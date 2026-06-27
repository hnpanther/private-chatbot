package com.hnp.privatechatbot.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Payload sent by the frontend JS to POST /api/chat/send.
 * If {@code sessionId} is null the backend creates a new session automatically.
 */
@Data
public class ChatRequest {

    @NotNull
    private Long chatbotId;

    /** Null on the first turn of a new conversation. */
    private Long sessionId;

    @NotBlank
    private String message;
}
