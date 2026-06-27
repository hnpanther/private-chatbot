package com.hnp.privatechatbot.controller;

import com.hnp.privatechatbot.dto.ChatRequest;
import com.hnp.privatechatbot.dto.ChatResponse;
import com.hnp.privatechatbot.entity.User;
import com.hnp.privatechatbot.service.ChatService;
import com.hnp.privatechatbot.service.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * REST API consumed by the frontend JavaScript.
 *
 * CSRF is disabled for /api/** (configured in SecurityConfig) because the JS client
 * sends the token in the X-XSRF-TOKEN header which satisfies the same protection goal.
 *
 * All endpoints require authentication (enforced by SecurityConfig's authorizeHttpRequests).
 */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatApiController {

    private final ChatService chatService;
    private final UserService userService;

    /**
     * Sends a user message and returns the LLM reply.
     * Creates a new session automatically when {@code sessionId} is null.
     */
    @PostMapping("/send")
    public ResponseEntity<ChatResponse> sendMessage(@Valid @RequestBody ChatRequest request,
                                                    @AuthenticationPrincipal UserDetails principal) {
        log.info("POST /api/chat/send: user={}, chatbotId={}, sessionId={}, messageLength={}",
                principal.getUsername(), request.getChatbotId(),
                request.getSessionId(), request.getMessage().length());
        try {
            User user = userService.findByUsername(principal.getUsername());
            ChatService.ChatResult result = chatService.sendMessage(
                    user, request.getChatbotId(), request.getSessionId(), request.getMessage());

            log.info("Chat response: user={}, sessionId={}, answerLength={}",
                    principal.getUsername(), result.sessionId(), result.answer().length());
            return ResponseEntity.ok(
                    ChatResponse.success(result.sessionId(), result.sessionTitle(), result.answer()));
        } catch (SecurityException e) {
            log.warn("Access denied in chat send: user={}, error={}", principal.getUsername(), e.getMessage());
            return ResponseEntity.status(403).body(ChatResponse.error(e.getMessage()));
        } catch (Exception e) {
            log.error("Error processing chat message: user={}, chatbotId={}, error={}",
                    principal.getUsername(), request.getChatbotId(), e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ChatResponse.error(e.getMessage()));
        }
    }

    /**
     * Creates an empty session without sending a message.
     * Used when the user explicitly clicks "New Conversation" before typing.
     */
    @PostMapping("/session/new")
    public ResponseEntity<ChatResponse> newSession(@RequestParam Long chatbotId,
                                                   @AuthenticationPrincipal UserDetails principal) {
        log.info("POST /api/chat/session/new: user={}, chatbotId={}", principal.getUsername(), chatbotId);
        try {
            User user = userService.findByUsername(principal.getUsername());
            var session = chatService.createSession(user, chatbotId);
            log.info("Session created via API: id={}, user={}", session.getId(), principal.getUsername());
            return ResponseEntity.ok(ChatResponse.success(session.getId(), session.getTitle(), null));
        } catch (Exception e) {
            log.error("Error creating session: user={}, chatbotId={}, error={}",
                    principal.getUsername(), chatbotId, e.getMessage(), e);
            return ResponseEntity.internalServerError().body(ChatResponse.error(e.getMessage()));
        }
    }

    /** Deletes a chat session and all its messages. */
    @DeleteMapping("/session/{sessionId}")
    public ResponseEntity<Void> deleteSession(@PathVariable Long sessionId,
                                              @AuthenticationPrincipal UserDetails principal) {
        log.info("DELETE /api/chat/session/{}: user={}", sessionId, principal.getUsername());
        User user = userService.findByUsername(principal.getUsername());
        chatService.deleteSession(sessionId, user);
        log.info("Session deleted via API: id={}, user={}", sessionId, principal.getUsername());
        return ResponseEntity.noContent().build();
    }
}
