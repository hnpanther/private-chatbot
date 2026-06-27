package com.hnp.privatechatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Single turn in a {@link ChatSession}: either a user message, an AI reply, or a system prompt.
 * Token counts are stored for future analytics but are not yet populated by the LLM call.
 */
@Entity
@Table(name = "chat_messages")
@Data
@NoArgsConstructor
public class ChatMessage {

    /** Identifies who produced the message in the conversation. */
    public enum Role {
        USER,
        ASSISTANT,
        SYSTEM
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    /** Number of prompt tokens consumed (reserved for future billing/analytics). */
    @Column(name = "input_tokens")
    private Integer inputTokens;

    /** Number of completion tokens produced (reserved for future billing/analytics). */
    @Column(name = "output_tokens")
    private Integer outputTokens;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    private ChatSession session;
}
