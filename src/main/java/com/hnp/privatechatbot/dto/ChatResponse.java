package com.hnp.privatechatbot.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * JSON response returned by POST /api/chat/send.
 * On success: {@code success=true}, {@code sessionId} (may differ from request when newly created),
 * {@code sessionTitle}, and {@code answer}.
 * On failure: {@code success=false} and {@code errorMessage}.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatResponse {

    private Long sessionId;
    private String sessionTitle;
    private String answer;
    private boolean success;
    private String errorMessage;

    public static ChatResponse success(Long sessionId, String title, String answer) {
        ChatResponse r = new ChatResponse();
        r.setSessionId(sessionId);
        r.setSessionTitle(title);
        r.setAnswer(answer);
        r.setSuccess(true);
        return r;
    }

    public static ChatResponse error(String message) {
        ChatResponse r = new ChatResponse();
        r.setSuccess(false);
        r.setErrorMessage(message);
        return r;
    }
}
