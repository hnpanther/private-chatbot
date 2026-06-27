package com.hnp.privatechatbot.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents a chatbot instance tied to a specific department.
 * Each chatbot can use the global LLM configuration or override it with a
 * dedicated endpoint (OpenAI-compatible or Ollama).
 */
@Entity
@Table(name = "chatbots")
@Data
@NoArgsConstructor
public class ChatBot {

    /**
     * Determines which LLM backend this chatbot uses.
     * GLOBAL      – reads credentials from application.properties (default)
     * OPENAI_COMPATIBLE – connects to a custom OpenAI-compatible endpoint
     * OLLAMA      – connects to a local/remote Ollama server
     */
    public enum LlmProvider {
        GLOBAL,
        OPENAI_COMPATIBLE,
        OLLAMA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String name;

    @Column(length = 500)
    private String description;

    /** System prompt injected at the start of every conversation. */
    @Column(name = "system_prompt", columnDefinition = "TEXT")
    private String systemPrompt;

    /** Bootstrap Icons class shown in the UI (e.g. "bi bi-robot"). */
    @Column(name = "icon", length = 50)
    private String icon = "bi-robot";

    @Column(nullable = false)
    private boolean active = true;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "department_id", nullable = false)
    private Department department;

    /**
     * Metadata filter key used when querying pgVector for relevant documents.
     * Matches the "chatbot_id" field stored in vector document metadata.
     * Falls back to the chatbot's numeric ID when blank.
     */
    @Column(name = "rag_filter_key", length = 100)
    private String ragFilterKey;

    // ── Per-chatbot LLM overrides (only used when llmProvider != GLOBAL) ──

    @Enumerated(EnumType.STRING)
    @Column(name = "llm_provider", nullable = false, length = 30)
    private LlmProvider llmProvider = LlmProvider.GLOBAL;

    /** Base URL of the custom LLM endpoint (e.g. http://host/v1). */
    @Column(name = "llm_base_url", length = 500)
    private String llmBaseUrl;

    /**
     * API key for the custom endpoint.
     * Supports plain keys (sent as "Bearer <key>") and prefixed keys
     * such as "apikey <key>" used by some providers (e.g. ArvanCloud).
     */
    @Column(name = "llm_api_key", length = 500)
    private String llmApiKey;

    /** Model name to pass in the "model" field of each chat request. */
    @Column(name = "llm_model", length = 200)
    private String llmModel;
}
