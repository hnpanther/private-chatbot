package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.Department;
import com.hnp.privatechatbot.repository.ChatBotRepository;
import com.hnp.privatechatbot.repository.ChatMessageRepository;
import com.hnp.privatechatbot.repository.ChatSessionRepository;
import com.hnp.privatechatbot.repository.DepartmentRepository;
import com.hnp.privatechatbot.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Business logic for the admin panel: department and chatbot lifecycle management
 * plus a dashboard statistics aggregation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AdminService {

    private final DepartmentRepository departmentRepository;
    private final ChatBotRepository chatBotRepository;
    private final UserRepository userRepository;
    private final ChatSessionRepository sessionRepository;
    private final ChatMessageRepository messageRepository;
    private final LlmService llmService;

    // ── Department ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<Department> getAllDepartments() {
        log.debug("Fetching all departments");
        return departmentRepository.findAll();
    }

    @Transactional
    public Department saveDepartment(Department department) {
        boolean isNew = department.getId() == null;
        log.info("{} department: name={}", isNew ? "Creating" : "Updating", department.getName());
        Department saved = departmentRepository.save(department);
        log.info("Department saved: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void deleteDepartment(Long id) {
        log.info("Deleting department: id={}", id);
        departmentRepository.deleteById(id);
        log.info("Department deleted: id={}", id);
    }

    // ── ChatBot ───────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<ChatBot> getAllChatBots() {
        log.debug("Fetching all chatbots");
        return chatBotRepository.findAll();
    }

    @Transactional(readOnly = true)
    public Optional<ChatBot> getChatBotById(Long id) {
        log.debug("Looking up chatbot by id={}", id);
        return chatBotRepository.findById(id);
    }

    @Transactional
    public ChatBot saveChatBot(ChatBot chatBot) {
        boolean isNew = chatBot.getId() == null;
        log.info("{} chatbot: name={}, department={}, provider={}",
                isNew ? "Creating" : "Updating",
                chatBot.getName(),
                chatBot.getDepartment() != null ? chatBot.getDepartment().getName() : "null",
                chatBot.getLlmProvider());

        ChatBot saved = chatBotRepository.save(chatBot);
        // Evict cached LLM client so the new configuration takes effect immediately
        llmService.evictCache(saved.getId());
        log.info("ChatBot saved: id={}, name={}", saved.getId(), saved.getName());
        return saved;
    }

    @Transactional
    public void deleteChatBot(Long id) {
        log.info("Deleting chatbot: id={}", id);
        llmService.evictCache(id);
        chatBotRepository.findById(id).ifPresent(chatBot -> {
            List<com.hnp.privatechatbot.entity.ChatSession> sessions = sessionRepository.findByChatBot(chatBot);
            sessionRepository.deleteAll(sessions);
            log.info("Deleted {} session(s) for chatbot id={}", sessions.size(), id);
        });
        chatBotRepository.deleteById(id);
        log.info("ChatBot deleted: id={}", id);
    }

    @Transactional
    public void toggleChatBotActive(Long id) {
        log.info("Toggling active state of chatbot id={}", id);
        ChatBot bot = chatBotRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("ChatBot not found: " + id));
        bot.setActive(!bot.isActive());
        chatBotRepository.save(bot);
        log.info("ChatBot id={} is now active={}", id, bot.isActive());
    }

    // ── Dashboard ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Map<String, Long> getDashboardStats() {
        log.debug("Collecting dashboard statistics");
        return Map.of(
                "users", userRepository.count(),
                "departments", departmentRepository.count(),
                "chatbots", chatBotRepository.count(),
                "sessions", sessionRepository.count(),
                "messages", messageRepository.count()
        );
    }
}
