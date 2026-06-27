package com.hnp.privatechatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Private Chatbot application.
 *
 * Tech stack: Spring Boot 4.1.0, Spring AI 2.0.0, Spring Security 7,
 * PostgreSQL + pgVector, Thymeleaf, OpenAI-compatible LLM backends, Ollama.
 */
@SpringBootApplication
public class PrivateChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PrivateChatbotApplication.class, args);
    }
}
