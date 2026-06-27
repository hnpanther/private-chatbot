package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.entity.*;
import com.hnp.privatechatbot.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * Core chat orchestration service.
 *
 * Responsibilities:
 * - Resolving which chatbots a user is allowed to access
 * - Creating and retrieving chat sessions
 * - Persisting user/assistant messages and delegating to {@link LlmService} for generation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final ChatBotRepository chatBotRepository;
    private final LlmService llmService;

    @Value("${app.chat.history-limit:20}")
    private int historyLimit;

    /** Result record carrying both the session (possibly newly created) and the LLM reply. */
    public record ChatResult(Long sessionId, String sessionTitle, String answer) {}

    /**
     * Returns all active chatbots the user is permitted to use.
     * Admins see every active bot; regular users see only bots in their departments.
     */
    @Transactional(readOnly = true)
    public List<ChatBot> getAccessibleChatBots(User user) {
        log.debug("Fetching accessible chatbots for user={}", user.getUsername());
        if (user.hasRole("ROLE_ADMIN")) {
            return chatBotRepository.findByActiveTrueOrderByDepartmentNameAsc();
        }
        return chatBotRepository.findByDepartmentsAndActiveTrue(user.getDepartments());
    }

    /** Sessions for a specific user+chatbot combination, newest first. */
    @Transactional(readOnly = true)
    public List<ChatSession> getUserSessions(User user, ChatBot chatBot) {
        log.debug("Fetching sessions for user={}, chatbot={}", user.getUsername(), chatBot.getId());
        return sessionRepository.findByUserAndChatBotOrderByUpdatedAtDesc(user, chatBot);
    }

    @Transactional(readOnly = true)
    public List<ChatSession> getAllUserSessions(User user) {
        return sessionRepository.findByUserOrderByUpdatedAtDesc(user);
    }

    /** Ownership-checked session lookup — throws if the session does not belong to the user. */
    @Transactional(readOnly = true)
    public ChatSession getSession(Long sessionId, User user) {
        log.debug("Loading session id={} for user={}", sessionId, user.getUsername());
        return sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
    }

    @Transactional(readOnly = true)
    public List<ChatMessage> getSessionMessages(ChatSession session) {
        log.debug("Loading messages for session id={}", session.getId());
        return messageRepository.findBySessionOrderByCreatedAtAsc(session);
    }

    /**
     * Creates a blank session with a placeholder title.
     * Used when the JS requests an explicit new-session endpoint.
     */
    @Transactional
    public ChatSession createSession(User user, Long chatbotId) {
        log.info("Creating new session: user={}, chatbotId={}", user.getUsername(), chatbotId);
        ChatBot chatBot = chatBotRepository.findById(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("ChatBot not found: " + chatbotId));
        verifyAccess(user, chatBot);

        ChatSession session = new ChatSession();
        session.setUser(user);
        session.setChatBot(chatBot);
        session.setTitle("New conversation");
        ChatSession saved = sessionRepository.save(session);
        log.info("Session created: id={}", saved.getId());
        return saved;
    }

    /**
     * Main send-message flow:
     * 1. Verify the user can access the chatbot.
     * 2. Create a new session if sessionId is null (first message).
     * 3. Persist the user message.
     * 4. Build conversation history and call the LLM via LlmService.
     * 5. Persist the assistant reply.
     * 6. Update the session title from the first user message.
     *
     * @return a {@link ChatResult} with the (possibly new) session ID and the LLM answer.
     */
    @Transactional
    public ChatResult sendMessage(User user, Long chatbotId, Long sessionId, String userMessage) {
        log.info("sendMessage: user={}, chatbotId={}, sessionId={}, messageLength={}",
                user.getUsername(), chatbotId, sessionId, userMessage.length());

        ChatBot chatBot = chatBotRepository.findById(chatbotId)
                .orElseThrow(() -> new IllegalArgumentException("ChatBot not found: " + chatbotId));
        verifyAccess(user, chatBot);

        ChatSession session;
        if (sessionId != null) {
            session = sessionRepository.findByIdAndUser(sessionId, user)
                    .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
            log.debug("Continuing existing session id={}", session.getId());
        } else {
            session = new ChatSession();
            session.setUser(user);
            session.setChatBot(chatBot);
            session.setTitle(truncate(userMessage, 60));
            session = sessionRepository.save(session);
            log.info("New session created on first message: id={}, title='{}'",
                    session.getId(), session.getTitle());
        }

        // Persist the user turn
        ChatMessage userMsg = new ChatMessage();
        userMsg.setSession(session);
        userMsg.setRole(ChatMessage.Role.USER);
        userMsg.setContent(userMessage);
        messageRepository.save(userMsg);

        // Build the history window for the LLM prompt (last N messages, chronological)
        List<ChatMessage> history = messageRepository.findLastNBySession(session, historyLimit);
        Collections.reverse(history);
        // Drop the message we just saved to avoid passing it twice to the LLM
        if (!history.isEmpty()
                && history.getLast().getRole() == ChatMessage.Role.USER
                && history.getLast().getContent().equals(userMessage)) {
            history.removeLast();
        }
        log.debug("History window size={} for session id={}", history.size(), session.getId());

        // Call the LLM
        long llmStart = System.currentTimeMillis();
        String answer = llmService.chat(chatBot, history, userMessage);
        log.info("LLM responded: chatbotId={}, sessionId={}, user={}, latencyMs={}, answerLength={}",
                chatbotId, session.getId(), user.getUsername(),
                System.currentTimeMillis() - llmStart, answer.length());

        // Persist the assistant turn
        ChatMessage assistantMsg = new ChatMessage();
        assistantMsg.setSession(session);
        assistantMsg.setRole(ChatMessage.Role.ASSISTANT);
        assistantMsg.setContent(answer);
        messageRepository.save(assistantMsg);

        // Update title after the first real user message if still on placeholder
        if ("New conversation".equals(session.getTitle())) {
            session.setTitle(truncate(userMessage, 60));
            sessionRepository.save(session);
            log.debug("Session title updated: id={}, title='{}'", session.getId(), session.getTitle());
        }

        return new ChatResult(session.getId(), session.getTitle(), answer);
    }

    @Transactional
    public void deleteSession(Long sessionId, User user) {
        log.info("Deleting session id={} for user={}", sessionId, user.getUsername());
        ChatSession session = sessionRepository.findByIdAndUser(sessionId, user)
                .orElseThrow(() -> new IllegalArgumentException("Session not found: " + sessionId));
        sessionRepository.delete(session);
        log.info("Session deleted: id={}", sessionId);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /** Throws SecurityException if a non-admin user tries to use a chatbot from another department. */
    private void verifyAccess(User user, ChatBot chatBot) {
        if (user.hasRole("ROLE_ADMIN")) return;
        boolean hasAccess = user.getDepartments().stream()
                .anyMatch(d -> d.getId().equals(chatBot.getDepartment().getId()));
        if (!hasAccess) {
            log.warn("Access denied: user={} tried to access chatbot={} (department={})",
                    user.getUsername(), chatBot.getId(), chatBot.getDepartment().getName());
            throw new SecurityException("Access denied to chatbot: " + chatBot.getId());
        }
    }

    private String truncate(String text, int maxLen) {
        if (text == null) return "";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
