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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@Transactional
class ChatSessionRepositoryTest {

    @MockitoBean LlmService llmService;

    @Autowired ChatSessionRepository sessionRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @PersistenceContext EntityManager em;

    private User alice;
    private User bob;
    private ChatBot bot;
    private ChatSession aliceSession1;
    private ChatSession aliceSession2;

    @BeforeEach
    void setUp() {
        Department dept = new Department();
        dept.setName("Test Dept");
        em.persist(dept);

        bot = new ChatBot();
        bot.setName("Test Bot");
        bot.setActive(true);
        bot.setDepartment(dept);
        em.persist(bot);

        alice = new User();
        alice.setUsername("alice_session_test");
        alice.setPassword(passwordEncoder.encode("pass"));
        alice.setFullName("Alice");
        em.persist(alice);

        bob = new User();
        bob.setUsername("bob_session_test");
        bob.setPassword(passwordEncoder.encode("pass"));
        bob.setFullName("Bob");
        em.persist(bob);

        // Alice has two sessions; the older one was updated first
        aliceSession1 = new ChatSession();
        aliceSession1.setUser(alice);
        aliceSession1.setChatBot(bot);
        aliceSession1.setTitle("Older session");
        aliceSession1.setUpdatedAt(LocalDateTime.now().minusHours(2));
        em.persist(aliceSession1);

        aliceSession2 = new ChatSession();
        aliceSession2.setUser(alice);
        aliceSession2.setChatBot(bot);
        aliceSession2.setTitle("Newer session");
        aliceSession2.setUpdatedAt(LocalDateTime.now());
        em.persist(aliceSession2);

        ChatSession bobSession = new ChatSession();
        bobSession.setUser(bob);
        bobSession.setChatBot(bot);
        bobSession.setTitle("Bob session");
        em.persist(bobSession);

        em.flush();
        em.clear();
    }

    @Test
    void findByUserAndChatBotOrderByUpdatedAtDesc_returnsOnlyUserSessions() {
        List<ChatSession> result = sessionRepository.findByUserAndChatBotOrderByUpdatedAtDesc(alice, bot);

        assertThat(result).hasSize(2);
        assertThat(result).extracting(ChatSession::getTitle)
                .containsExactlyInAnyOrder("Older session", "Newer session");
    }

    @Test
    void findByUserAndChatBotOrderByUpdatedAtDesc_orderedNewestFirst() {
        List<ChatSession> result = sessionRepository.findByUserAndChatBotOrderByUpdatedAtDesc(alice, bot);

        assertThat(result.get(0).getTitle()).isEqualTo("Newer session");
        assertThat(result.get(1).getTitle()).isEqualTo("Older session");
    }

    @Test
    void findByUserAndChatBotOrderByUpdatedAtDesc_doesNotReturnOtherUsersSessions() {
        List<ChatSession> result = sessionRepository.findByUserAndChatBotOrderByUpdatedAtDesc(alice, bot);

        assertThat(result).noneMatch(s -> s.getTitle().equals("Bob session"));
    }

    @Test
    void findByIdAndUser_owner_returnsSession() {
        Optional<ChatSession> result = sessionRepository.findByIdAndUser(aliceSession1.getId(), alice);

        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Older session");
    }

    @Test
    void findByIdAndUser_notOwner_returnsEmpty() {
        Optional<ChatSession> result = sessionRepository.findByIdAndUser(aliceSession1.getId(), bob);

        assertThat(result).isEmpty();
    }

    @Test
    void findByIdAndUser_nonExistentSession_returnsEmpty() {
        Optional<ChatSession> result = sessionRepository.findByIdAndUser(999999L, alice);

        assertThat(result).isEmpty();
    }

    @Test
    void findByUserOrderByUpdatedAtDesc_returnsAllUserSessions() {
        List<ChatSession> result = sessionRepository.findByUserOrderByUpdatedAtDesc(alice);

        assertThat(result).hasSize(2);
    }

    @Test
    void findByChatBot_returnsAllSessionsForThatBot() {
        // All three sessions (alice×2, bob×1) belong to the same bot
        List<ChatSession> result = sessionRepository.findByChatBot(bot);

        assertThat(result).hasSize(3);
    }

    @Test
    void findByChatBot_doesNotReturnSessionsForOtherBot() {
        // A second bot with no sessions should return empty
        com.hnp.privatechatbot.entity.Department dept2 = new com.hnp.privatechatbot.entity.Department();
        dept2.setName("Other Dept");
        em.persist(dept2);

        ChatBot otherBot = new ChatBot();
        otherBot.setName("Other Bot");
        otherBot.setActive(true);
        otherBot.setDepartment(dept2);
        em.persist(otherBot);
        em.flush();
        em.clear();

        List<ChatSession> result = sessionRepository.findByChatBot(otherBot);

        assertThat(result).isEmpty();
    }
}
