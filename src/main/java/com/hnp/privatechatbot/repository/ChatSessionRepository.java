package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.ChatSession;
import com.hnp.privatechatbot.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/** Data access for {@link ChatSession} entities. */
public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    /** Sessions for a user within a specific chatbot, newest first (sidebar list). */
    List<ChatSession> findByUserAndChatBotOrderByUpdatedAtDesc(User user, ChatBot chatBot);

    /** All sessions for a user across all chatbots. */
    List<ChatSession> findByUserOrderByUpdatedAtDesc(User user);

    /** Ownership-checked lookup — prevents one user reading another's session. */
    Optional<ChatSession> findByIdAndUser(Long id, User user);

    List<ChatSession> findByChatBot(ChatBot chatBot);
}
