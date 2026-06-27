# CLAUDE.md — Private Chatbot Project

## Project Summary

A private, multi-department chatbot web application built with Spring Boot 4.1.0 and Spring AI 2.0.0.
Users see only the chatbots assigned to their department. Admins can manage users, departments, and chatbots.
Each chatbot can connect to a global OpenAI-compatible endpoint, its own custom endpoint, or an Ollama instance.

## Tech Stack

| Layer         | Technology                                      |
|---------------|-------------------------------------------------|
| Backend       | Spring Boot 4.1.0, Java 25, Lombok 1.18.38      |
| AI            | Spring AI 2.0.0 (openai-java-core 4.39.1 SDK)  |
| DB            | PostgreSQL 18 (Docker: pgvector/pgvector:pg18-trixie) |
| Vector store  | pgVector via Spring AI PgVectorStore            |
| Security      | Spring Security 7                               |
| Frontend      | Thymeleaf 3.1, Bootstrap 5.3 RTL, Persian/RTL  |
| LLM backends  | OpenAI-compatible APIs, Ollama                  |

## Key Architecture Decisions

### Circular Dependency Fix
`PasswordEncoder` is defined in `AppConfig`, NOT `SecurityConfig`.
Without this, `SecurityConfig → UserService → PasswordEncoder → SecurityConfig` causes a circular bean error.

### Spring Security 7 — DaoAuthenticationProvider
Constructor changed: `new DaoAuthenticationProvider(userDetailsService)` (not the no-arg constructor).

### Spring AI 2.0 — OpenAI Client
`OpenAiApi` class was removed. Use `OpenAIClientImpl(ClientOptions)` from `com.openai.client`.
`ClientOptions.builder()` requires an explicit `httpClient` — use `SpringAiOpenAiHttpClient.builder().build()`.
Both sync AND async clients must be provided to `OpenAiChatModel.builder()`:
```java
OpenAIClientImpl impl = new OpenAIClientImpl(clientOptions);
OpenAiChatModel.builder()
    .openAiClient(impl)
    .openAiClientAsync(impl.async())   // ← required — prevents OpenAiSetup from running
    ...
```

### Spring AI 2.0 — Ollama
`OllamaOptions` renamed to `OllamaChatOptions`.
Builder method `.defaultOptions()` renamed to `.options()`.

### Spring AI 2.0 — Property Names
Old (deprecated): `spring.ai.openai.chat.options.model`
New (correct):    `spring.ai.openai.chat.model`
Same pattern for `temperature`, `max-completion-tokens`, `embedding.model`.

### Thymeleaf 3.1 — Security Restriction
String variables are blocked in `th:onclick` expressions.
Fix: use `th:data-*` attributes + plain `onclick="fn(this)"` JavaScript.

### Per-Chatbot LLM Client Cache
`LlmService` holds a `ConcurrentHashMap<Long, ChatClient>`.
Cache is invalidated via `evictCache(id)` whenever an admin saves or deletes a chatbot.
GLOBAL provider chatbots reuse the shared `defaultChatClient` bean from `AppConfig`.

### Authorization Header — non-Bearer APIs
ArvanCloud and similar providers use `Authorization: apikey <key>` instead of `Bearer`.
Store the full prefixed value in the `llmApiKey` field: e.g. `apikey 283de96a-...`.
The OkHttp interceptor in `LlmService.buildOpenAiClient()` detects the prefix and sets the header correctly.

### Bean Ambiguity (OpenAI vs Ollama)
Ollama auto-configurations are excluded in `application.properties`:
```
spring.autoconfigure.exclude=org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration,...
```
`AppConfig.chatClient()` explicitly takes `OpenAiChatModel` (not `ChatModel`) to avoid ambiguity.

## File Map

```
src/main/java/com/hnp/privatechatbot/
├── PrivateChatbotApplication.java       — entry point
├── config/
│   ├── AppConfig.java                   — PasswordEncoder + default ChatClient bean
│   ├── SecurityConfig.java              — Spring Security filter chain
│   └── DataInitializer.java             — seeds roles and admin account on startup
├── entity/
│   ├── User.java                        — app user with roles + departments
│   ├── Role.java                        — Spring Security role (ROLE_ADMIN, ROLE_USER, ...)
│   ├── Department.java                  — organisational unit
│   ├── ChatBot.java                     — chatbot with LlmProvider enum + override fields
│   ├── ChatSession.java                 — conversation thread (user + chatbot)
│   └── ChatMessage.java                 — single turn (USER / ASSISTANT / SYSTEM)
├── repository/                          — Spring Data JPA interfaces
├── dto/
│   ├── ChatRequest.java                 — POST /api/chat/send payload
│   ├── ChatResponse.java                — JSON response from the API
│   └── UserCreateRequest.java           — admin create/update user form
├── service/
│   ├── UserService.java                 — UserDetailsService + user CRUD
│   ├── AdminService.java                — department + chatbot CRUD + dashboard stats
│   ├── ChatService.java                 — session/message orchestration
│   └── LlmService.java                  — LLM client factory + RAG retrieval
└── controller/
    ├── AuthController.java              — GET /login
    ├── ChatController.java              — GET /chat, GET /chat/{id}
    ├── ChatApiController.java           — POST /api/chat/send + session endpoints
    └── AdminController.java             — admin panel CRUD routes

src/main/resources/
├── application.properties              — all config via env vars with sensible defaults
├── logback-spring.xml                  — file + console logging, daily rotation
├── static/css/style.css
└── templates/                          — Thymeleaf (RTL Persian UI)
    ├── fragments/navbar.html
    ├── auth/login.html
    ├── chat/index.html                 — three-panel chat layout
    └── admin/
        ├── dashboard.html
        ├── users.html
        ├── departments.html
        └── chatbots.html               — includes edit modal with JS population
```

## Environment Variables

| Variable          | Default                                    | Description                       |
|-------------------|--------------------------------------------|-----------------------------------|
| `DB_URL`          | `jdbc:postgresql://localhost:5432/chatbot_db` | PostgreSQL JDBC URL            |
| `DB_USERNAME`     | `postgres`                                 | DB username                       |
| `DB_PASSWORD`     | `postgres`                                 | DB password                       |
| `AI_BASE_URL`     | `http://localhost:8001`                    | Global LLM API base URL           |
| `AI_API_KEY`      | `changeme`                                 | Global LLM API key                |
| `AI_CHAT_MODEL`   | `DeepSeek-R1-qwen-7b-awq`                 | Global chat model name            |
| `AI_MAX_TOKENS`   | `3000`                                     | Max completion tokens             |
| `AI_TEMPERATURE`  | `0.7`                                      | Sampling temperature              |
| `AI_EMBEDDING_MODEL` | `text-embedding-ada-002`               | Embedding model name              |
| `PGVECTOR_DIMENSIONS` | `1024`                                | Vector embedding dimensions       |
| `CHAT_HISTORY_LIMIT` | `20`                                   | Max messages sent as LLM context  |
| `RAG_TOP_K`       | `5`                                        | Max RAG documents retrieved       |
| `LOG_PATH`        | `ProdLogs`                                 | Directory for log files           |
| `SERVER_PORT`     | `8080`                                     | HTTP port                         |

## Default Credentials (first startup)
- Username: `admin`
- Password: `admin123`
- **Change this immediately in production.**

## Docker PostgreSQL+pgVector

```bash
docker run -d \
  --name chatbot-db \
  -e POSTGRES_DB=chatbot_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg18-trixie
```

## Logging

Active log: `ProdLogs/app.log`
Daily archives: `ProdLogs/ArchiveLog/app.YYYY-MM-DD.log.gz`
`ProdLogs/` is in `.gitignore`.
App package logs at DEBUG; Spring/library code logs at INFO.

## Known Issues / Gotchas

1. Lombok `sun.misc.Unsafe` warning on Java 25 — harmless, suppressed at runtime.
2. Spring Security emits a WARN about `UserDetailsService` beans — silenced in logback-spring.xml.
3. pgVector `Request failed` warning on RAG search = pgVector server not running; chat still works.
4. The `app.log` path is relative to the working directory (project root when running via mvnw).
