package com.hnp.privatechatbot.repository;

import com.hnp.privatechatbot.entity.ChatMessage;
import com.hnp.privatechatbot.entity.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/** Data access for {@link ChatMessage} entities. */
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** All messages in a session in chronological order — used when rendering the chat view. */
    List<ChatMessage> findBySessionOrderByCreatedAtAsc(ChatSession session);

    /**
     * The N most-recent messages in a session (newest first).
     * After retrieval, callers reverse the list to get chronological order for the LLM prompt.
     */
    @Query("SELECT m FROM ChatMessage m WHERE m.session = :session ORDER BY m.createdAt DESC LIMIT :limit")
    List<ChatMessage> findLastNBySession(@Param("session") ChatSession session, @Param("limit") int limit);
}
