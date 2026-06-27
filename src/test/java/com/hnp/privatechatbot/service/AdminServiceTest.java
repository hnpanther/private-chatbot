package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock DepartmentRepository departmentRepository;
    @Mock ChatBotRepository chatBotRepository;
    @Mock UserRepository userRepository;
    @Mock ChatSessionRepository sessionRepository;
    @Mock ChatMessageRepository messageRepository;
    @Mock LlmService llmService;

    @InjectMocks AdminService adminService;

    private Department dept;
    private ChatBot bot;

    @BeforeEach
    void setUp() {
        dept = new Department();
        dept.setId(1L);
        dept.setName("HR");

        bot = new ChatBot();
        bot.setId(10L);
        bot.setName("HR Bot");
        bot.setActive(true);
        bot.setDepartment(dept);
    }

    // ── Department ─────────────────────────────────────────────────────────────

    @Test
    void getAllDepartments_returnsList() {
        when(departmentRepository.findAll()).thenReturn(List.of(dept));

        List<Department> result = adminService.getAllDepartments();

        assertThat(result).containsExactly(dept);
    }

    @Test
    void saveDepartment_newDepartment_savedAndReturned() {
        Department input = new Department();
        input.setName("Finance");
        when(departmentRepository.save(input)).thenReturn(dept);

        Department result = adminService.saveDepartment(input);

        assertThat(result).isSameAs(dept);
        verify(departmentRepository).save(input);
    }

    @Test
    void deleteDepartment_callsRepositoryDeleteById() {
        doNothing().when(departmentRepository).deleteById(1L);

        adminService.deleteDepartment(1L);

        verify(departmentRepository).deleteById(1L);
    }

    // ── ChatBot ────────────────────────────────────────────────────────────────

    @Test
    void getAllChatBots_returnsList() {
        when(chatBotRepository.findAll()).thenReturn(List.of(bot));

        assertThat(adminService.getAllChatBots()).containsExactly(bot);
    }

    @Test
    void getChatBotById_found_returnsOptional() {
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));

        Optional<ChatBot> result = adminService.getChatBotById(10L);

        assertThat(result).isPresent().contains(bot);
    }

    @Test
    void getChatBotById_notFound_returnsEmpty() {
        when(chatBotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThat(adminService.getChatBotById(99L)).isEmpty();
    }

    @Test
    void saveChatBot_newBot_savesAndEvictsCache() {
        bot.setId(null);
        ChatBot savedBot = new ChatBot();
        savedBot.setId(20L);
        when(chatBotRepository.save(bot)).thenReturn(savedBot);

        ChatBot result = adminService.saveChatBot(bot);

        assertThat(result.getId()).isEqualTo(20L);
        verify(llmService).evictCache(20L);
    }

    @Test
    void saveChatBot_existingBot_updatesAndEvictsCache() {
        when(chatBotRepository.save(bot)).thenReturn(bot);

        adminService.saveChatBot(bot);

        verify(chatBotRepository).save(bot);
        verify(llmService).evictCache(10L);
    }

    @Test
    void deleteChatBot_evictsCacheAndDeletes() {
        doNothing().when(chatBotRepository).deleteById(10L);

        adminService.deleteChatBot(10L);

        verify(llmService).evictCache(10L);
        verify(chatBotRepository).deleteById(10L);
    }

    @Test
    void toggleChatBotActive_fromActive_setsInactive() {
        bot.setActive(true);
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        when(chatBotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.toggleChatBotActive(10L);

        verify(chatBotRepository).save(argThat(b -> !b.isActive()));
    }

    @Test
    void toggleChatBotActive_fromInactive_setsActive() {
        bot.setActive(false);
        when(chatBotRepository.findById(10L)).thenReturn(Optional.of(bot));
        when(chatBotRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        adminService.toggleChatBotActive(10L);

        verify(chatBotRepository).save(argThat(ChatBot::isActive));
    }

    @Test
    void toggleChatBotActive_notFound_throwsException() {
        when(chatBotRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> adminService.toggleChatBotActive(99L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ChatBot not found");
    }

    // ── Dashboard ──────────────────────────────────────────────────────────────

    @Test
    void getDashboardStats_returnsAllFiveKeys() {
        when(userRepository.count()).thenReturn(5L);
        when(departmentRepository.count()).thenReturn(3L);
        when(chatBotRepository.count()).thenReturn(7L);
        when(sessionRepository.count()).thenReturn(42L);
        when(messageRepository.count()).thenReturn(200L);

        Map<String, Long> stats = adminService.getDashboardStats();

        assertThat(stats).containsKeys("users", "departments", "chatbots", "sessions", "messages");
        assertThat(stats.get("users")).isEqualTo(5L);
        assertThat(stats.get("messages")).isEqualTo(200L);
    }
}
