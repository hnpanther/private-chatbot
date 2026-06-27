package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.entity.*;
import com.hnp.privatechatbot.repository.ChatBotRepository;
import com.hnp.privatechatbot.repository.ChatMessageRepository;
import com.hnp.privatechatbot.repository.ChatSessionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock ChatSessionRepository sessionRepository;
    @Mock ChatMessageRepository messageRepository;
    @Mock ChatBotRepository chatBotRepository;
    @Mock LlmService llmService;

    @InjectMocks ChatService chatService;

    private Department dept;
    private User adminUser;
    private User regularUser;
    private ChatBot bot;
    private ChatSession session;

    @BeforeEach
    void setUp() {
        dept = new Department();
        dept.setId(1L);
        dept.setName("Engineering");

        Role adminRole = new Role("ROLE_ADMIN", "Admin");
        adminRole.setId(1L);
        Role userRole = new Role("ROLE_USER", "User");
        userRole.setId(2L);

        adminUser = new User();
        adminUser.setId(1L);
        adminUser.setUsername("admin");
        adminUser.setRoles(Set.of(adminRole));
        adminUser.setDepartments(Set.of(dept));

        regularUser = new User();
        regularUser.setId(2L);
        regularUser.setUsername("alice");
        regularUser.setRoles(Set.of(userRole));
        regularUser.setDepartments(Set.of(dept));

        bot = new ChatBot();
        bot.setId(10L);
        bot.setName("Support Bot");
        bot.setActive(true);
        bot.setDepartment(dept);

        session = new ChatSession();
        session.setId(100L);
        session.setUser(regularUser);
        session.setChatBot(bot);
        session.setTitle("Hello there");
    }

    // ── getAccessibleChatBots ──────────────────────────────────────────────────

    @Test
    void getAccessibleChatBots_adminUser_queriesAllBots() {
        when(chatBotRepository.findByActiveTrueOrderByDepartmentNameAsc()).thenReturn(List.of(bot));

        List<ChatBot> result = chatService.getAccessibleChatBots(adminUser);

        assertThat(result).containsExactly(bot);
        verify(chatBotRepository).findByActiveTrueOrderByDepartmentNameAsc();
        verify(chatBotRepository, never()).findByDepartmentsAndActiveTrue(any());
    }

    @Test
    void getAccessibleChatBots_regularUser_queriesOnlyDepartmentBots() {
        when(chatBotRepository.findByDepartmentsAndActiveTrue(regularUser.getDepartments()))
                .thenReturn(List.of(bot));

        List<ChatBot> result = chatService.getAccessibleChatBots(regularUser);

        assertThat(result).containsExactly(bot);
        verify(chatBotRepository, never()).findByActiveTrueOrderByDepartmentNameAsc();
    }

    // ── sendMessage ────────────────────────────────────────────────────────────

    @Test
    void sendMessage_nullSessionId_createsNewSessionWithTruncatedTitle() {
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        ChatSession saved = new ChatSession();
        saved.setId(200L);
        saved.setTitle("Hello world");
        when(sessionRepository.save(any(ChatSession.class))).thenReturn(saved);
        when(messageRepository.save(any(ChatMessage.class))).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findLastNBySession(any(), anyInt())).thenReturn(List.of());
        when(llmService.chat(any(), any(), anyString())).thenReturn("Hi there!");

        ChatService.ChatResult result = chatService.sendMessage(regularUser, 10L, null, "Hello world");

        assertThat(result.sessionId()).isEqualTo(200L);
        assertThat(result.answer()).isEqualTo("Hi there!");
        verify(sessionRepository, atLeastOnce()).save(any(ChatSession.class));
    }

    @Test
    void sendMessage_longMessage_titleTruncatedTo60Chars() {
        String longMessage = "A".repeat(100);
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(201L);
            return s;
        });
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findLastNBySession(any(), anyInt())).thenReturn(List.of());
        when(llmService.chat(any(), any(), anyString())).thenReturn("Answer");

        chatService.sendMessage(regularUser, 10L, null, longMessage);

        verify(sessionRepository).save(argThat(s ->
                s.getTitle() != null && s.getTitle().length() <= 63));
    }

    @Test
    void sendMessage_withExistingSessionId_continuesSession() {
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        when(sessionRepository.findByIdAndUser(100L, regularUser)).thenReturn(Optional.of(session));
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findLastNBySession(any(), anyInt())).thenReturn(List.of());
        when(llmService.chat(any(), any(), anyString())).thenReturn("Sure!");

        ChatService.ChatResult result = chatService.sendMessage(regularUser, 10L, 100L, "Continue");

        assertThat(result.sessionId()).isEqualTo(100L);
        verify(sessionRepository, never()).save(any());
    }

    @Test
    void sendMessage_regularUserWithoutAccess_throwsSecurityException() {
        Department otherDept = new Department();
        otherDept.setId(99L);
        bot.setDepartment(otherDept);

        User outsider = new User();
        outsider.setId(3L);
        outsider.setUsername("outsider");
        outsider.setRoles(Set.of(new Role("ROLE_USER", "User")));
        outsider.setDepartments(Set.of(dept));

        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));

        assertThatThrownBy(() -> chatService.sendMessage(outsider, 10L, null, "Hi"))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void sendMessage_adminUser_bypasesDepartmentCheck() {
        Department otherDept = new Department();
        otherDept.setId(99L);
        bot.setDepartment(otherDept);

        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        ChatSession saved = new ChatSession();
        saved.setId(300L);
        when(sessionRepository.save(any())).thenReturn(saved);
        when(messageRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(messageRepository.findLastNBySession(any(), anyInt())).thenReturn(List.of());
        when(llmService.chat(any(), any(), anyString())).thenReturn("Admin answer");

        ChatService.ChatResult result = chatService.sendMessage(adminUser, 10L, null, "Hi");

        assertThat(result.answer()).isEqualTo("Admin answer");
    }

    @Test
    void sendMessage_chatbotNotFound_throwsException() {
        when(chatBotRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.sendMessage(regularUser, 999L, null, "Hi"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ChatBot not found");
    }

    // ── createSession ──────────────────────────────────────────────────────────

    @Test
    void createSession_validAccess_savesWithNewConversationTitle() {
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        when(sessionRepository.save(any(ChatSession.class))).thenAnswer(inv -> {
            ChatSession s = inv.getArgument(0);
            s.setId(500L);
            return s;
        });

        ChatSession result = chatService.createSession(regularUser, 10L);

        assertThat(result.getId()).isEqualTo(500L);
        assertThat(result.getTitle()).isEqualTo("New conversation");
    }

    @Test
    void createSession_botNotFound_throwsException() {
        when(chatBotRepository.findById(999L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.createSession(regularUser, 999L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createSession_noAccess_throwsSecurityException() {
        Department otherDept = new Department();
        otherDept.setId(99L);
        bot.setDepartment(otherDept);

        User outsider = new User();
        outsider.setUsername("outsider");
        outsider.setRoles(Set.of(new Role("ROLE_USER", "User")));
        outsider.setDepartments(Set.of(dept));

        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));

        assertThatThrownBy(() -> chatService.createSession(outsider, 10L))
                .isInstanceOf(SecurityException.class);
    }

    // ── deleteSession ──────────────────────────────────────────────────────────

    @Test
    void deleteSession_owner_callsRepositoryDelete() {
        when(sessionRepository.findByIdAndUser(100L, regularUser)).thenReturn(Optional.of(session));

        chatService.deleteSession(100L, regularUser);

        verify(sessionRepository).delete(session);
    }

    @Test
    void deleteSession_notOwner_throwsException() {
        when(sessionRepository.findByIdAndUser(100L, adminUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.deleteSession(100L, adminUser))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Session not found");
    }

    // ── getSession ─────────────────────────────────────────────────────────────

    @Test
    void getSession_owner_returnsSession() {
        when(sessionRepository.findByIdAndUser(100L, regularUser)).thenReturn(Optional.of(session));

        ChatSession result = chatService.getSession(100L, regularUser);

        assertThat(result).isSameAs(session);
    }

    @Test
    void getSession_notOwner_throwsException() {
        when(sessionRepository.findByIdAndUser(100L, adminUser)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> chatService.getSession(100L, adminUser))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
