package com.hnp.privatechatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hnp.privatechatbot.config.DataInitializer;
import com.hnp.privatechatbot.dto.ChatRequest;
import com.hnp.privatechatbot.entity.ChatSession;
import com.hnp.privatechatbot.entity.User;
import com.hnp.privatechatbot.service.ChatService;
import com.hnp.privatechatbot.service.LlmService;
import com.hnp.privatechatbot.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
class ChatApiControllerTest {

    @Autowired WebApplicationContext wac;

    @MockitoBean ChatService chatService;
    @MockitoBean UserService userService;
    @MockitoBean LlmService llmService;
    @MockitoBean DataInitializer dataInitializer;

    private final ObjectMapper objectMapper = new ObjectMapper();

    MockMvc mockMvc;

    private User mockUser;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();

        mockUser = new User();
        mockUser.setId(1L);
        mockUser.setUsername("alice");
    }

    // ── POST /api/chat/send ────────────────────────────────────────────────────

    @Test
    void sendMessage_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"chatbotId\":1,\"message\":\"Hello\"}"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    @WithMockUser(username = "alice")
    void sendMessage_authenticated_returns200WithAnswer() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(mockUser);
        when(chatService.sendMessage(any(), eq(1L), isNull(), eq("Hello")))
                .thenReturn(new ChatService.ChatResult(10L, "Hello", "Hi there!"));

        ChatRequest req = new ChatRequest();
        req.setChatbotId(1L);
        req.setMessage("Hello");

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.answer").value("Hi there!"))
                .andExpect(jsonPath("$.sessionId").value(10));
    }

    @Test
    @WithMockUser(username = "alice")
    void sendMessage_serviceThrowsSecurityException_returns403() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(mockUser);
        when(chatService.sendMessage(any(), anyLong(), any(), anyString()))
                .thenThrow(new SecurityException("Access denied"));

        ChatRequest req = new ChatRequest();
        req.setChatbotId(99L);
        req.setMessage("Hello");

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "alice")
    void sendMessage_serviceThrowsRuntimeException_returns500() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(mockUser);
        when(chatService.sendMessage(any(), anyLong(), any(), anyString()))
                .thenThrow(new RuntimeException("LLM error"));

        ChatRequest req = new ChatRequest();
        req.setChatbotId(1L);
        req.setMessage("Hello");

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    @WithMockUser(username = "alice")
    void sendMessage_nullChatbotId_returns400() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setMessage("Hello"); // chatbotId is null → @NotNull fails

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "alice")
    void sendMessage_blankMessage_returns400() throws Exception {
        ChatRequest req = new ChatRequest();
        req.setChatbotId(1L);
        req.setMessage("   "); // @NotBlank fails

        mockMvc.perform(post("/api/chat/send")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    // ── POST /api/chat/session/new ─────────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void newSession_authenticated_returnsSessionId() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(mockUser);
        ChatSession created = new ChatSession();
        created.setId(50L);
        created.setTitle("New conversation");
        when(chatService.createSession(any(), eq(1L))).thenReturn(created);

        mockMvc.perform(post("/api/chat/session/new").param("chatbotId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(50))
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void newSession_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(post("/api/chat/session/new").param("chatbotId", "1"))
                .andExpect(status().is3xxRedirection());
    }

    // ── DELETE /api/chat/session/{id} ──────────────────────────────────────────

    @Test
    @WithMockUser(username = "alice")
    void deleteSession_authenticated_returns204() throws Exception {
        when(userService.findByUsername("alice")).thenReturn(mockUser);

        mockMvc.perform(delete("/api/chat/session/100"))
                .andExpect(status().isNoContent());
    }

    @Test
    void deleteSession_unauthenticated_redirectsToLogin() throws Exception {
        mockMvc.perform(delete("/api/chat/session/100"))
                .andExpect(status().is3xxRedirection());
    }
}
