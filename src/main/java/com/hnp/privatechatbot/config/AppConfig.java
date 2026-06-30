package com.hnp.privatechatbot.config;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.ollama.OllamaEmbeddingModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaEmbeddingOptions;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.http.okhttp.SpringAiOpenAiHttpClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * General application beans.
 *
 * PasswordEncoder lives here (not in SecurityConfig) to break a circular dependency:
 *   SecurityConfig → UserService → PasswordEncoder → SecurityConfig
 *
 * ChatClient explicitly wires OpenAiChatModel to avoid ambiguity with an Ollama
 * ChatModel bean.  Ollama auto-configurations are excluded in application.properties;
 * per-chatbot Ollama clients are built programmatically in LlmService instead.
 *
 * EmbeddingModel is built manually to support two providers (OPENAI_COMPATIBLE / OLLAMA)
 * selected by the AI_EMBEDDING_PROVIDER env var.  The OpenAI path also handles the
 * "apikey <key>" Authorization scheme used by ArvanCloud and similar providers.
 */
@Configuration
@Slf4j
public class AppConfig {

    @Value("${spring.ai.openai.base-url}")
    private String globalBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String globalApiKey;

    @Value("${spring.ai.openai.chat.model}")
    private String globalChatModel;

    @Value("${spring.ai.openai.chat.max-completion-tokens:3000}")
    private int globalMaxTokens;

    @Value("${spring.ai.openai.chat.temperature:0.7}")
    private double globalTemperature;

    @Value("${spring.ai.ollama.base-url}")
    private String globalOllamaBaseUrl;

    @Value("${spring.ai.ollama.chat.model}")
    private String globalOllamaModel;

    @Value("${spring.ai.openai.embedding.model}")
    private String embeddingModelName;

    @Value("${app.embedding.provider:OPENAI_COMPATIBLE}")
    private String embeddingProvider;

    @Value("${app.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${app.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}")
    private int embeddingDimensions;

    @PostConstruct
    public void logStartupConfig() {
        log.info("=== Chat configuration ===");
        log.info("  Global provider : OPENAI_COMPATIBLE");
        log.info("  Base URL        : {}", globalBaseUrl);
        log.info("  API key         : {}", maskKey(globalApiKey));
        log.info("  Model           : {}", globalChatModel);
        log.info("  Max tokens      : {}", globalMaxTokens);
        log.info("  Temperature     : {}", globalTemperature);
        log.info("  Ollama base URL : {}", globalOllamaBaseUrl);
        log.info("  Ollama model    : {}", globalOllamaModel);

        log.info("=== Embedding configuration ===");
        log.info("  Provider        : {}", embeddingProvider.toUpperCase());
        if ("OLLAMA".equalsIgnoreCase(embeddingProvider.trim())) {
            String url = nonBlank(embeddingBaseUrl, globalOllamaBaseUrl);
            log.info("  Base URL        : {}", url);
            log.info("  Model           : {}", embeddingModelName);
        } else {
            String url = nonBlank(embeddingBaseUrl, globalBaseUrl);
            String key = nonBlank(embeddingApiKey, globalApiKey);
            log.info("  Base URL        : {}", url);
            log.info("  API key         : {}", maskKey(key));
            log.info("  Model           : {}", embeddingModelName);
        }
        log.info("  Vector dimensions: {}", embeddingDimensions);
    }

    /** Shows only the prefix and first 6 chars of the key — enough to identify it, not enough to leak it. */
    private String maskKey(String key) {
        if (key == null || key.isBlank()) return "(not set)";
        if (key.startsWith("apikey ")) {
            String bare = key.substring(7);
            return "apikey " + (bare.length() > 6 ? bare.substring(0, 6) + "***" : "***");
        }
        if (key.startsWith("Bearer ")) {
            String bare = key.substring(7);
            return "Bearer " + (bare.length() > 6 ? bare.substring(0, 6) + "***" : "***");
        }
        return key.length() > 6 ? key.substring(0, 6) + "***" : "***";
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    /**
     * Builds the global EmbeddingModel based on AI_EMBEDDING_PROVIDER:
     *   OPENAI_COMPATIBLE (default) — supports apikey/Bearer header + separate base URL
     *   OLLAMA                      — connects to the Ollama /api/embed endpoint
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
        if ("OLLAMA".equalsIgnoreCase(embeddingProvider.trim())) {
            return buildOllamaEmbeddingModel();
        }
        return buildOpenAiEmbeddingModel();
    }

    private EmbeddingModel buildOpenAiEmbeddingModel() {
        String baseUrl   = nonBlank(embeddingBaseUrl, globalBaseUrl);
        String storedKey = nonBlank(embeddingApiKey,  globalApiKey);

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

        SpringAiOpenAiHttpClient httpClient = SpringAiOpenAiHttpClient.builder()
                .interceptor(chain -> {
                    okhttp3.Request req = chain.request().newBuilder()
                            .header("Authorization", authHeaderValue)
                            .build();
                    return chain.proceed(req);
                })
                .build();

        ClientOptions clientOptions = ClientOptions.builder()
                .httpClient(httpClient)
                .baseUrl(baseUrl)
                .apiKey(bareKey)
                .build();

        OpenAIClientImpl impl = new OpenAIClientImpl(clientOptions);

        return OpenAiEmbeddingModel.builder()
                .openAiClient(impl)
                .options(OpenAiEmbeddingOptions.builder()
                        .model(embeddingModelName)
                        .dimensions(embeddingDimensions)
                        .build())
                .build();
    }

    // AI_EMBEDDING_BASE_URL falls back to OLLAMA_BASE_URL; model comes from AI_EMBEDDING_MODEL
    private EmbeddingModel buildOllamaEmbeddingModel() {
        String baseUrl = nonBlank(embeddingBaseUrl, globalOllamaBaseUrl);

        OllamaApi ollamaApi = OllamaApi.builder()
                .baseUrl(baseUrl)
                .build();

        return OllamaEmbeddingModel.builder()
                .ollamaApi(ollamaApi)
                .options(OllamaEmbeddingOptions.builder()
                        .model(embeddingModelName)
                        .build())
                .build();
    }

    private String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
