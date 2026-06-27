package com.hnp.privatechatbot.controller;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.ChatSession;
import com.hnp.privatechatbot.entity.User;
import com.hnp.privatechatbot.service.ChatService;
import com.hnp.privatechatbot.service.UserService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Serves the main chat UI (Thymeleaf server-side rendering).
 *
 * GET /chat               – chat home; no bot selected
 * GET /chat/{chatbotId}   – bot selected, optional ?sessionId=X to open an existing session
 *
 * Message sending is handled by the AJAX endpoint in {@link ChatApiController}.
 */
@Controller
@RequestMapping("/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final UserService userService;

    /** Renders the chat home page with the list of accessible chatbots. */
    @GetMapping
    public String chatHome(@AuthenticationPrincipal UserDetails principal, Model model) {
        log.debug("GET /chat: user={}", principal.getUsername());
        User user = userService.findByUsername(principal.getUsername());
        List<ChatBot> chatBots = chatService.getAccessibleChatBots(user);
        model.addAttribute("chatBots", chatBots);
        model.addAttribute("currentUser", user);
        log.debug("Chat home rendered: user={}, availableBots={}", user.getUsername(), chatBots.size());
        return "chat/index";
    }

    /**
     * Renders the chat page with a specific bot selected.
     * If sessionId is provided, loads the message history for that session.
     */
    @GetMapping("/{chatbotId}")
    public String chatWithBot(@PathVariable Long chatbotId,
                              @RequestParam(required = false) Long sessionId,
                              @AuthenticationPrincipal UserDetails principal,
                              Model model) {
        log.debug("GET /chat/{}: user={}, sessionId={}", chatbotId, principal.getUsername(), sessionId);
        User user = userService.findByUsername(principal.getUsername());
        List<ChatBot> chatBots = chatService.getAccessibleChatBots(user);

        ChatBot selectedBot = chatBots.stream()
                .filter(b -> b.getId().equals(chatbotId))
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Chatbot not found or access denied: chatbotId={}, user={}", chatbotId, user.getUsername());
                    return new IllegalArgumentException("Chatbot not found or access denied: " + chatbotId);
                });

        List<ChatSession> sessions = chatService.getUserSessions(user, selectedBot);

        ChatSession activeSession = null;
        if (sessionId != null) {
            activeSession = chatService.getSession(sessionId, user);
            model.addAttribute("messages", chatService.getSessionMessages(activeSession));
            log.debug("Session loaded: id={}, messageCount={}",
                    sessionId, model.getAttribute("messages") != null
                            ? ((List<?>) model.getAttribute("messages")).size() : 0);
        }

        model.addAttribute("chatBots", chatBots);
        model.addAttribute("selectedBot", selectedBot);
        model.addAttribute("sessions", sessions);
        model.addAttribute("activeSession", activeSession);
        model.addAttribute("currentUser", user);
        return "chat/index";
    }
}
