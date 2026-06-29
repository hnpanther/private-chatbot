package com.hnp.privatechatbot.config;

import com.openai.client.OpenAIClientImpl;
import com.openai.core.ClientOptions;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.embedding.EmbeddingModel;
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
 * EmbeddingModel is built manually so that providers like ArvanCloud that use a
 * different base-url and "apikey" Authorization scheme are handled correctly.
 * Spring AI's auto-configured bean does not apply the interceptor, so we replace it.
 */
@Configuration
public class AppConfig {

    @Value("${spring.ai.openai.base-url}")
    private String globalBaseUrl;

    @Value("${spring.ai.openai.api-key}")
    private String globalApiKey;

    @Value("${spring.ai.openai.embedding.model}")
    private String embeddingModel;

    @Value("${app.embedding.base-url:}")
    private String embeddingBaseUrl;

    @Value("${app.embedding.api-key:}")
    private String embeddingApiKey;

    @Value("${spring.ai.vectorstore.pgvector.dimensions:1024}")
    private int embeddingDimensions;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }

    /**
     * Custom EmbeddingModel that supports "apikey <key>" Authorization headers
     * and a separate base-url for embedding endpoints (e.g. ArvanCloud).
     * Falls back to the global chat API settings when embedding-specific ones are absent.
     */
    @Bean
    @Primary
    public EmbeddingModel embeddingModel() {
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
                        .model(embeddingModel)
                        .dimensions(embeddingDimensions)
                        .build())
                .build();
    }

    private String nonBlank(String value, String fallback) {
        return (value != null && !value.isBlank()) ? value : fallback;
    }
}
