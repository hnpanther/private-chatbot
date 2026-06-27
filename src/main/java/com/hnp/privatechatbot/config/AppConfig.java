package com.hnp.privatechatbot.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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
 */
@Configuration
public class AppConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * Default ChatClient backed by the globally configured OpenAI-compatible endpoint.
     * Chatbots with LlmProvider.GLOBAL use this bean; others get a dedicated client
     * built by {@link com.hnp.privatechatbot.service.LlmService}.
     */
    @Bean
    public ChatClient chatClient(OpenAiChatModel openAiChatModel) {
        return ChatClient.builder(openAiChatModel).build();
    }
}
