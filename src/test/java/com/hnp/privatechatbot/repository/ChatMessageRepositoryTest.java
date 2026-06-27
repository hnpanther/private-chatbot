package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.*;
import com.hnp.privatechatbot.service.LlmService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ChatMessageRepositoryTest {

    @MockitoBean LlmService llmService;

    @Autowired ChatMessageRepository messageRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @PersistenceContext EntityManager em;

    private ChatSession session;

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setName("Msg Test Dept");
        em.persist(dept);

        ChatBot bot = new ChatBot();
        bot.setName("Msg Test Bot");
        bot.setActive(true);
        bot.setDepartment(dept);
        em.persist(bot);

        User user = new User();
        user.setUsername("msg_test_user");
        user.setPassword(passwordEncoder.encode("pass"));
        user.setFullName("Msg User");
        em.persist(user);

        session = new ChatSession();
        session.setUser(user);
        session.setChatBot(bot);
        session.setTitle("Test session");
        em.persist(session);

        // Persist 5 messages with distinct timestamps (oldest → newest)
        for (int i = 1; i <= 5; i++) {
            ChatMessage msg = new ChatMessage();
            msg.setSession(session);
            msg.setRole(i % 2 == 0 ? ChatMessage.Role.ASSISTANT : ChatMessage.Role.USER);
            msg.setContent("Message " + i);
            msg.setCreatedAt(LocalDateTime.now().plusMinutes(i));
            em.persist(msg);
        }

        em.flush();
        em.clear();
    }

    @Test
    void findBySessionOrderByCreatedAtAsc_returnsAllMessagesChronologically() {
        List<ChatMessage> result = messageRepository.findBySessionOrderByCreatedAtAsc(session);

        assertThat(result).hasSize(5);
        // Verify ascending order: each message's timestamp ≤ the next one's
        for (int i = 0; i < result.size() - 1; i++) {
            assertThat(result.get(i).getCreatedAt())
                    .isBeforeOrEqualTo(result.get(i + 1).getCreatedAt());
        }
    }

    @Test
    void findBySessionOrderByCreatedAtAsc_firstMessageIsOldest() {
        List<ChatMessage> result = messageRepository.findBySessionOrderByCreatedAtAsc(session);

        assertThat(result.get(0).getContent()).isEqualTo("Message 1");
        assertThat(result.get(result.size() - 1).getContent()).isEqualTo("Message 5");
    }

    @Test
    void findLastNBySession_limitsResultCount() {
        List<ChatMessage> result = messageRepository.findLastNBySession(session, 3);

        assertThat(result).hasSize(3);
    }

    @Test
    void findLastNBySession_returnsNewestFirst() {
        List<ChatMessage> result = messageRepository.findLastNBySession(session, 3);

        // Result is newest-first (DESC): messages 5, 4, 3
        assertThat(result.get(0).getContent()).isEqualTo("Message 5");
        assertThat(result.get(1).getContent()).isEqualTo("Message 4");
        assertThat(result.get(2).getContent()).isEqualTo("Message 3");
    }

    @Test
    void findLastNBySession_limitLargerThanTotal_returnsAll() {
        List<ChatMessage> result = messageRepository.findLastNBySession(session, 100);

        assertThat(result).hasSize(5);
    }

    @Test
    void findLastNBySession_limitZero_returnsEmpty() {
        List<ChatMessage> result = messageRepository.findLastNBySession(session, 0);

        assertThat(result).isEmpty();
    }
}
