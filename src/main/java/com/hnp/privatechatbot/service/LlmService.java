package com.hnp.privatechatbot.service;

import com.hnp.privatechatbot.entity.ChatBot;
import com.hnp.privatechatbot.entity.ChatMessage;
import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.ollama.OllamaChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.ai.document.Document;

/**
 * Handles all LLM communication.
 *
 * Per-chatbot client cache:
 *   Chatbots with LlmProvider.GLOBAL reuse the single {@code defaultChatClient} bean.
 *   Chatbots with a dedicated endpoint get a lazily-built {@link ChatClient} stored in
 *   {@code clientCache} keyed by chatbot ID.  The cache is invalidated whenever an admin
 *   saves or deletes a chatbot so the new configuration takes effect immediately.
 *
 * Authorization header handling:
 *   The OpenAI Java SDK always sends "Authorization: Bearer <key>".  To support providers
 *   that require a different scheme (e.g. ArvanCloud's "apikey <key>"), the stored API key
 *   may include the prefix explicitly.  An OkHttp interceptor overwrites the SDK-generated
 *   header with the correct value before each HTTP request is dispatched.
 */
@Service
@Slf4j
public class LlmService {

    private final ChatClient defaultChatClient;
    private final VectorStore vectorStore;

    @Value("${spring.ai.openai.base-url}")
    private String globalBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String globalApiKey;

    @Value("${spring.ai.openai.chat.model}")
    private String globalModel;

    @Value("${spring.ai.ollama.base-url}")
    private String globalOllamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String globalOllamaModel;

    @Value("${app.rag.top-k:5}")
    private int ragTopK;

    /** Lazily-built, thread-safe cache of dedicated ChatClient instances per chatbot ID. */
    private final ConcurrentHashMap<Long, ChatClient> clientCache = new ConcurrentHashMap<>();

    public LlmService(ChatClient defaultChatClient, VectorStore vectorStore) {
        this.defaultChatClient = defaultChatClient;
        this.vectorStore = vectorStore;
    }

    /**
     * Sends the conversation to the appropriate LLM and returns the generated reply.
     *
     * @param chatBot the chatbot whose LLM configuration should be used
     * @param history preceding messages (chronological, excluding the current userMessage)
     * @param userMessage the new user turn to respond to
     */
    public String chat(ChatBot chatBot, List<ChatMessage> history, String userMessage) {
        log.debug("chat(): chatbotId={}, provider={}, historySize={}",
                chatBot.getId(), chatBot.getLlmProvider(), history.size());

        String ragQuery = buildRagQuery(history, userMessage);
        String ragContext = retrieveRagContext(chatBot, ragQuery);
        List<Message> messages = buildMessages(chatBot, history, userMessage, ragContext);
        ChatClient client = resolveChatClient(chatBot);

        try {
            String response = client.prompt()
                    .messages(messages)
                    .call()
                    .content();
            log.debug("LLM response received: chatbotId={}, responseLength={}", chatBot.getId(), response.length());
            return response;
        } catch (Exception e) {
            log.error("LLM call failed: chatbotId={}, provider={}, error={}",
                    chatBot.getId(), chatBot.getLlmProvider(), e.getMessage(), e);
            throw new RuntimeException("LLM communication error: " + e.getMessage(), e);
        }
    }

    /**
     * Removes the cached ChatClient for the given chatbot so it is rebuilt on the next request.
     * Called by AdminService whenever a chatbot's LLM settings change.
     */
    public void evictCache(Long chatBotId) {
        clientCache.remove(chatBotId);
        log.debug("LLM client cache evicted for chatbot id={}", chatBotId);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private ChatClient resolveChatClient(ChatBot chatBot) {
        if (chatBot.getLlmProvider() == null
                || chatBot.getLlmProvider() == ChatBot.LlmProvider.GLOBAL) {
            log.debug("Using global ChatClient for chatbot id={}", chatBot.getId());
            return defaultChatClient;
        }
        return clientCache.computeIfAbsent(chatBot.getId(), id -> buildChatClient(chatBot));
    }

    private ChatClient buildChatClient(ChatBot chatBot) {
        log.info("Building dedicated LLM client: chatbotId={}, provider={}",
                chatBot.getId(), chatBot.getLlmProvider());
        return switch (chatBot.getLlmProvider()) {
            case OPENAI_COMPATIBLE -> buildOpenAiClient(chatBot);
            case OLLAMA             -> buildOllamaClient(chatBot);
            default                 -> defaultChatClient;
        };
    }

    /**
     * Constructs a per-chatbot OpenAI-compatible client.
     *
     * Both the sync and async OpenAI client instances must be provided to the Spring AI
     * builder; if either is missing, OpenAiChatModel.Builder.build() calls OpenAiSetup
     * which requires application-level credentials and will fail.
     *
     * The OkHttp interceptor rewrites the Authorization header so that providers using
     * non-Bearer schemes (e.g. "apikey <key>" for ArvanCloud) are supported.
     */
    private ChatClient buildOpenAiClient(ChatBot chatBot) {
        String baseUrl   = nonBlank(chatBot.getLlmBaseUrl(), globalBaseUrl);
        String storedKey = nonBlank(chatBot.getLlmApiKey(),  globalApiKey);
        String model     = nonBlank(chatBot.getLlmModel(),   globalModel);

        log.debug("Building OpenAI-compatible client: chatbotId={}, baseUrl={}, model={}",
                chatBot.getId(), baseUrl, model);

        // Determine the Authorization header value from the stored key.
        // Supported formats: "apikey <key>", "Bearer <key>", or a bare key (Bearer is used).
        final String authHeaderValue;
        final String bareKey;
        if (storedKey.startsWith("apikey ")) {
            authHeaderValue = storedKey;
            bareKey = storedKey.substring("apikey ".length()).trim();
        } else if (storedKey.startsWith("Bearer ")) {
            authHeaderValue = storedKey;
            bareKey = storedKey.substring("Bearer ".length()).trim();
        } else {
            authHeaderValue = "Bearer " + storedKey;
            bareKey = storedKey;
        }

        // SpringAiOpenAiHttpClient implements com.openai.core.http.HttpClient,
        // which is mandatory since openai-java-core 4.x (ClientOptions.build() requires it).
        SpringAiOpenAiHttpClient springHttpClient = SpringAiOpenAiHttpClient.builder()
                .interceptor(chain -> {
                    okhttp3.Request req = chain.request().newBuilder()
                            .header("Authorization", authHeaderValue)
                            .build();
                    return chain.proceed(req);
                })
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(springHttpClient)
                .baseUrl(baseUrl)
                .apiKey(bareKey)
                .build();

        // Provide both sync and async clients so the Spring AI builder does not
        // call OpenAiSetup (which expects credentials from application.properties).
        OpenAIClientImpl openAiClientImpl = new OpenAIClientImpl(clientOptions);

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiClient(openAiClientImpl)
                .openAiClientAsync(openAiClientImpl.async())
                .options(OpenAiChatOptions.builder()
                        .model(model)
                        .maxCompletionTokens(3000)
                        .temperature(0.7)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    /** Constructs a per-chatbot Ollama client pointing at the configured server URL. */
    private ChatClient buildOllamaClient(ChatBot chatBot) {
        String baseUrl = nonBlank(chatBot.getLlmBaseUrl(), globalOllamaBaseUrl);
        String model   = nonBlank(chatBot.getLlmModel(),   globalOllamaModel);

        log.debug("Building Ollama client: chatbotId={}, baseUrl={}, model={}",
                chatBot.getId(), baseUrl, model);

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        OllamaChatModel chatModel = OllamaChatModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaChatOptions.builder()
                        .model(model)
                        .temperature(0.7)
                        .build())
                .build();

        return ChatClient.builder(chatModel).build();
    }

    /**
     * Queries pgVector for documents related to the user message and returns them as
     * a single concatenated context string.  Failures are swallowed so a pgVector outage
     * degrades gracefully to a non-RAG response.
     */
    /**
     * Builds the RAG search query by prepending the last few user turns from history.
     * This lets the vector search understand references like "that tender" or "the same document"
     * even when the current message alone doesn't contain enough context.
     */
    private String buildRagQuery(List<ChatMessage> history, String userMessage) {
        String recentHistory = history.stream()
                .filter(m -> m.getRole() == ChatMessage.Role.USER)
                .skip(Math.max(0, history.stream()
                        .filter(m -> m.getRole() == ChatMessage.Role.USER)
                        .count() - 3))
                .map(ChatMessage::getContent)
                .collect(Collectors.joining(" "));

        return recentHistory.isBlank() ? userMessage : recentHistory + " " + userMessage;
    }

    /**
     * Two-mode RAG retrieval.
     *
     * Document metadata convention:
     *   - No chatbot_id key (empty metadata {}) → shared document, visible to every chatbot.
     *   - chatbot_id = "hr,safety,finance"      → tagged document, visible only to chatbots
     *     whose ragFilterKey overlaps with at least one of these tags.
     *
     * Mode 1 — ragFilterKey is blank:
     *   No filter; all documents (tagged and untagged) are searched.
     *
     * Mode 2 — ragFilterKey is set (e.g. "hr,safety"):
     *   Fetch a broad candidate set without a pgVector filter (pgVector cannot do
     *   substring/overlap matching on comma-separated strings), then post-filter in Java:
     *     - Keep untagged docs (no chatbot_id key) — always shared.
     *     - Keep tagged docs whose chatbot_id value shares at least one tag with the filter.
     */
    private String retrieveRagContext(ChatBot chatBot, String query) {
        try {
            String rawKey = chatBot.getRagFilterKey() != null ? chatBot.getRagFilterKey().trim() : "";

            List<Document> results;

            if (rawKey.isBlank()) {
                // Mode 1 — no filter, search all documents
                results = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(ragTopK)
                                .build()
                );
            } else {
                // Mode 2 — chatbot has tag filters; fetch broadly then post-filter
                Set<String> filterTags = Arrays.stream(rawKey.split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .collect(Collectors.toSet());

                // Fetch more candidates than needed because post-filtering will discard some
                List<Document> candidates = vectorStore.similaritySearch(
                        SearchRequest.builder()
                                .query(query)
                                .topK(ragTopK * 5)
                                .build()
                );

                results = candidates.stream()
                        .filter(d -> {
                            Object val = d.getMetadata().get("chatbot_id");
                            if (val == null) {
                                return true; // no chatbot_id key = shared doc
                            }
                            // Support both formats:
                            //   array:  ["contract", "hr"]  → List from Jackson
                            //   string: "contract,hr"       → comma-separated
                            Set<String> docTags;
                            if (val instanceof List<?> list) {
                                docTags = list.stream()
                                        .map(Object::toString).map(String::trim)
                                        .filter(s -> !s.isBlank())
                                        .collect(Collectors.toSet());
                            } else {
                                String str = val.toString().trim();
                                if (str.isBlank()) return true; // empty string = shared
                                docTags = Arrays.stream(str.split(","))
                                        .map(String::trim)
                                        .filter(s -> !s.isBlank())
                                        .collect(Collectors.toSet());
                            }
                            return docTags.stream().anyMatch(filterTags::contains);
                        })
                        .limit(ragTopK)
                        .collect(Collectors.toList());
            }

            if (results == null || results.isEmpty()) {
                log.debug("RAG: no documents found for chatbot id={}, key='{}'", chatBot.getId(), rawKey);
                return "";
            }
            log.debug("RAG: {} document(s) retrieved for chatbot id={}", results.size(), chatBot.getId());
            return results.stream()
                    .map(Document::getText)
                    .collect(Collectors.joining("\n\n---\n\n"));
        } catch (Exception e) {
            log.warn("RAG search failed for chatbot id={}", chatBot.getId(), e);
            return "";
        }
    }

    /**
     * Assembles the full message list sent to the LLM:
     * [SystemMessage] → [history turns] → [current UserMessage]
     */
    private List<Message> buildMessages(ChatBot chatBot, List<ChatMessage> history,
                                        String userMessage, String ragContext) {
        List<Message> messages = new ArrayList<>();

        StringBuilder systemPrompt = new StringBuilder();
        if (chatBot.getSystemPrompt() != null && !chatBot.getSystemPrompt().isBlank()) {
            systemPrompt.append(chatBot.getSystemPrompt());
        } else {
            systemPrompt.append("You are a helpful assistant. Please respond in the same language the user writes in.");
        }
        if (!ragContext.isBlank()) {
            systemPrompt.append("\n\nRelevant knowledge base context:\n").append(ragContext);
        }

        messages.add(new SystemMessage(systemPrompt.toString()));

        for (ChatMessage msg : history) {
            if (msg.getRole() == ChatMessage.Role.USER) {
                messages.add(new UserMessage(msg.getContent()));
            } else if (msg.getRole() == ChatMessage.Role.ASSISTANT) {
                messages.add(new AssistantMessage(msg.getContent()));
            }
        }
        messages.add(new UserMessage(userMessage));
        return messages;
    }

    private String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
