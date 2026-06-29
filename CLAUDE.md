# CLAUDE.md — Private Chatbot Project

## Project Summary

A private, multi-department chatbot web application built with Spring Boot 4.1.0 and Spring AI 2.0.0.
Users see only the chatbots assigned to their department. Admins can manage users, departments, and chatbots.
Each chatbot can connect to a global OpenAI-compatible endpoint, its own custom endpoint, or an Ollama instance.

## Tech Stack

| Layer         | Technology                                      |
|---------------|-------------------------------------------------|
| Backend       | Spring Boot 4.1.0, Java 25, Lombok 1.18.38      |
| AI            | Spring AI 2.0.0 (openai-java-core SDK)          |
| DB            | PostgreSQL (Docker: pgvector/pgvector:pg18-trixie) |
| Vector store  | pgVector via Spring AI PgVectorStore            |
| Security      | Spring Security 7                               |
| Frontend      | Thymeleaf 3.1, Bootstrap 5.3.3 RTL (WebJar), Persian/RTL |
| Icons         | Bootstrap Icons 1.11.3 (WebJar)                 |
| LLM backends  | OpenAI-compatible APIs, Ollama                  |
| Tests         | JUnit 5, Mockito, H2 (in-memory for repo tests) |

## Key Architecture Decisions

### Circular Dependency Fix
`PasswordEncoder` is defined in `AppConfig`, NOT `SecurityConfig`.
Without this, `SecurityConfig → UserService → PasswordEncoder → SecurityConfig` causes a circular bean error.

### Spring Security 7 — DaoAuthenticationProvider
Constructor changed: `new DaoAuthenticationProvider(userService)` (not the no-arg constructor).
`PasswordEncoder` is then set via `provider.setPasswordEncoder(passwordEncoder)`.

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
Same pattern is used in `AppConfig.embeddingModel()` with `OpenAiEmbeddingModel.builder()`.

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

### AppConfig — Three Beans
`AppConfig` provides three beans:
1. `PasswordEncoder` — BCrypt, avoids circular dependency
2. `ChatClient` — wraps the auto-configured `OpenAiChatModel`, explicitly typed to avoid Ollama ambiguity
3. `EmbeddingModel` (`@Primary`) — custom-built to support `apikey` Authorization prefix and a separate
   embedding endpoint (e.g. ArvanCloud uses a different host for embeddings); falls back to global settings

### Per-Chatbot LLM Client Cache
`LlmService` holds a `ConcurrentHashMap<Long, ChatClient>`.
Cache is invalidated via `evictCache(id)` whenever an admin saves or deletes a chatbot.
GLOBAL provider chatbots reuse the shared `defaultChatClient` bean from `AppConfig`.
`ChatClient` is stateless — sharing one instance across all concurrent users is safe.

### Authorization Header — non-Bearer APIs
ArvanCloud and similar providers use `Authorization: apikey <key>` instead of `Bearer`.
Store the full prefixed value in the `llmApiKey` field: e.g. `apikey 283de96a-...`.
The OkHttp interceptor in `LlmService.buildOpenAiClient()` detects the prefix and sets the header correctly.
Same logic applies to the embedding client in `AppConfig.embeddingModel()`.

### Bean Ambiguity (OpenAI vs Ollama)
Ollama auto-configurations (both chat AND embedding) are excluded in `application.properties`:
```
spring.autoconfigure.exclude=org.springframework.ai.model.ollama.autoconfigure.OllamaChatAutoConfiguration,\
  org.springframework.ai.model.ollama.autoconfigure.OllamaEmbeddingAutoConfiguration
```
`AppConfig.chatClient()` explicitly takes `OpenAiChatModel` (not `ChatModel`) to avoid ambiguity.

### RAG — Two-Mode Retrieval
`LlmService.retrieveRagContext()` works in two modes:

**Mode 1 — `ragFilterKey` is blank:** no filter, search all docs with exact `topK`.

**Mode 2 — `ragFilterKey` is set (e.g. `"hr,safety"`):** fetch `topK × 5` candidates without a
pgVector filter (pgVector cannot do overlap/substring matching on JSON values), then post-filter in Java:
- Docs with **no `chatbot_id` key** in metadata → always included (shared docs)
- Docs where `chatbot_id` value shares at least one tag with the filter → included

`chatbot_id` in pgVector metadata supports **both formats**:
- String: `{"chatbot_id": "hr,safety,finance"}`
- JSON array: `{"chatbot_id": ["hr", "safety", "finance"]}` — Spring AI deserializes this as a `List`, handled correctly

### RAG Query Enrichment
Before searching pgVector, `buildRagQuery()` prepends the last **3 user messages** from history to the
current message. This helps resolve references like "that document" or "the same tender" without a separate
LLM rewrite call.

### Offline Deployment
All HTML templates use WebJar paths (`/webjars/**`) — no CDN dependencies.
Bootstrap RTL and Bootstrap Icons are bundled via Maven WebJars served automatically by Spring Boot.
Font stack: `'Vazirmatn', Tahoma, 'Segoe UI', Arial, sans-serif` (no Google Fonts).

### Bootstrap RTL — justify-content Direction
In Bootstrap RTL (with `dir="rtl"`), the visual meaning of classes is **reversed**:
- `justify-content-start` = visually **RIGHT** (RTL start = right edge)
- `justify-content-end` = visually **LEFT** (RTL end = left edge)

Chat message layout (as implemented):
- User messages: `justify-content-end` → LEFT side (user avatar on leftmost, then bubble)
- AI messages: `justify-content-start` → RIGHT side (bubble, then AI avatar on rightmost)

### ShamsiDateUtil
`@Component("shamsi")` at `util/ShamsiDateUtil.java`. Converts `LocalDateTime` to Jalali (Shamsi) calendar
with Persian digits. Used in templates as `${@shamsi.format(date)}` and `${@shamsi.formatTime(date)}`.

### Session Delete
Chat sessions can be deleted from the UI (trash icon in session sidebar, visible on hover).
`DELETE /api/chat/session/{id}` — ownership-checked, cascades to messages via JPA.
`AdminService.deleteChatBot()` also explicitly cleans up all sessions before deleting a chatbot.

### Chat History Window
`ChatService.sendMessage()` fetches the last `historyLimit` (default 20) messages (newest first),
reverses them, and drops the just-saved user message to avoid duplication before passing to LLM.
RAG uses only the last **3 user messages** for query building (separate from the full history window).

## File Map

```
src/main/java/com/hnp/privatechatbot/
├── PrivateChatbotApplication.java
├── config/
│   ├── AppConfig.java          — PasswordEncoder + ChatClient + EmbeddingModel (custom, apikey interceptor)
│   ├── SecurityConfig.java     — Spring Security filter chain (CSRF disabled for /api/**)
│   └── DataInitializer.java    — seeds ROLE_ADMIN, ROLE_USER, ROLE_DEPT_ADMIN + default admin user
├── entity/
│   ├── User.java               — username, password (BCrypt), fullName, email, enabled, createdAt, roles, departments
│   ├── Role.java               — name (e.g. "ROLE_ADMIN"), description
│   ├── Department.java         — name, nameEn, description, active; one-to-many chatBots
│   ├── ChatBot.java            — name, description, systemPrompt, icon (Bootstrap Icons class e.g. "bi-robot"),
│   │                             active, department, ragFilterKey, llmProvider (GLOBAL/OPENAI_COMPATIBLE/OLLAMA),
│   │                             llmBaseUrl, llmApiKey, llmModel
│   ├── ChatSession.java        — title (first 60 chars of first message), user, chatBot,
│   │                             createdAt, updatedAt (@PreUpdate auto-set), messages (cascade ALL)
│   └── ChatMessage.java        — role (USER/ASSISTANT/SYSTEM), content, createdAt,
│                                 inputTokens, outputTokens (null — reserved for future analytics)
├── repository/
│   ├── ChatBotRepository.java  — findByActiveTrueOrderByDepartmentNameAsc, findByDepartmentsAndActiveTrue (JPQL)
│   ├── ChatSessionRepository.java — findByUserAndChatBotOrderByUpdatedAtDesc, findByIdAndUser, findByChatBot
│   ├── ChatMessageRepository.java — findBySessionOrderByCreatedAtAsc, findLastNBySession (JPQL LIMIT)
│   ├── UserRepository.java
│   ├── RoleRepository.java
│   └── DepartmentRepository.java
├── dto/
│   ├── ChatRequest.java        — chatbotId, sessionId (nullable = new session), message
│   ├── ChatResponse.java       — sessionId, sessionTitle, answer, success, errorMessage
│   └── UserCreateRequest.java  — username, password, fullName, email, roleIds, departmentIds
├── service/
│   ├── UserService.java        — UserDetailsService + CRUD: createUser, updateUser, toggleUserEnabled, findAll
│   ├── AdminService.java       — department/chatbot CRUD + toggleChatBotActive + getDashboardStats (5 counters)
│   ├── ChatService.java        — getAccessibleChatBots (admin=all, user=dept-filtered), session CRUD,
│   │                             sendMessage (verify access → persist user msg → build history →
│   │                             call LLM → persist assistant msg → update title)
│   └── LlmService.java         — per-chatbot ChatClient factory + ConcurrentHashMap cache +
│                                 RAG two-mode retrieval + buildRagQuery (last 3 user msgs)
└── util/
    └── ShamsiDateUtil.java     — @Component("shamsi"), Gregorian→Jalali + Persian digits

src/main/resources/
├── application.properties
├── logback-spring.xml          — file + console logging, daily rotation, 90 days / 2 GB retention
├── static/css/style.css        — RTL chat layout, session delete button hover effect
└── templates/
    ├── fragments/navbar.html
    ├── auth/login.html
    ├── chat/index.html         — three-panel: chatbot list | session sidebar (+ delete btn) | messages + input
    └── admin/
        ├── dashboard.html
        ├── users.html
        ├── departments.html
        └── chatbots.html

src/test/java/com/hnp/privatechatbot/
├── PrivateChatbotApplicationTests.java
├── service/
│   ├── LlmServiceTest.java     — 14 unit tests: cache eviction, RAG modes 1&2, array/string metadata format,
│   │                             query enrichment, vectorStore exception degradation
│   └── AdminServiceTest.java   — department/chatbot CRUD, delete cascade to sessions, toggle, dashboard stats
└── repository/
    └── ChatSessionRepositoryTest.java  — @SpringBootTest + H2, ownership queries, findByChatBot
```

## Environment Variables

| Variable               | Default                                          | Description |
|------------------------|--------------------------------------------------|-------------|
| `DB_URL`               | `jdbc:postgresql://localhost:5432/chatbot_db`    | PostgreSQL JDBC URL |
| `DB_USERNAME`          | `postgres`                                       | DB username |
| `DB_PASSWORD`          | `postgres`                                       | DB password |
| `AI_BASE_URL`          | `http://localhost:8001`                          | **Global chat API base URL. Must end with `/v1`.** SDK appends `/chat/completions` automatically. |
| `AI_API_KEY`           | `changeme`                                       | Global chat API key. Prefix `apikey ` for ArvanCloud: `apikey 283de96a-...` |
| `AI_CHAT_MODEL`        | *(none — must set)*                              | Chat model name as returned by the provider |
| `AI_MAX_TOKENS`        | `3000`                                           | Max completion tokens per response |
| `AI_TEMPERATURE`       | `0.7`                                            | Sampling temperature (0=deterministic, 1=creative) |
| `AI_EMBEDDING_BASE_URL`| *(falls back to `AI_BASE_URL`)*                  | **Embedding API base URL. Must end with `/v1`.** Set separately when embedding uses a different host. |
| `AI_EMBEDDING_API_KEY` | *(falls back to `AI_API_KEY`)*                   | Embedding API key. Same `apikey`/`Bearer` prefix rules. |
| `AI_EMBEDDING_MODEL`   | `text-embedding-ada-002`                         | Embedding model name |
| `PGVECTOR_DIMENSIONS`  | `1024`                                           | **Must match the output dimension of your embedding model.** Changing requires recreating `vector_store` table. |
| `OLLAMA_BASE_URL`      | `http://localhost:11434`                         | Global Ollama base URL (per-chatbot URL overrides this) |
| `OLLAMA_MODEL`         | `llama3`                                         | Default Ollama model |
| `CHAT_HISTORY_LIMIT`   | `20`                                             | Max prior messages sent as LLM context per request |
| `RAG_TOP_K`            | `5`                                              | Max docs returned from pgVector (Mode 2 fetches `topK × 5` before Java post-filtering) |
| `LOG_PATH`             | `ProdLogs`                                       | Directory for app.log and daily archives |
| `SERVER_PORT`          | `8081`                                           | HTTP server port |

## Roles (seeded on startup, idempotent)

| Role              | Description |
|-------------------|-------------|
| `ROLE_ADMIN`      | Full access, bypasses department checks, can use admin panel |
| `ROLE_USER`       | Regular user, sees only chatbots in their departments |
| `ROLE_DEPT_ADMIN` | Seeded but not yet enforced — reserved for future use |

## RAG Metadata Convention

`vector_store` table (pgVector): columns `id` (uuid), `content` (text), `metadata` (jsonb), `embedding` (vector).

| `chatbot_id` in metadata | Accessible by |
|--------------------------|---------------|
| key absent / `{}`        | Every chatbot (shared document) |
| `"hr"`                   | Chatbots whose `ragFilterKey` contains `hr` |
| `"hr,safety,finance"`    | Chatbots with `ragFilterKey` overlapping any of those tags |
| `["hr", "safety"]`       | Same — JSON array also supported |

## API Endpoints

### Chat UI
| Method | Path                | Description                       |
|--------|---------------------|-----------------------------------|
| GET    | /chat               | Chat home (no bot selected)       |
| GET    | /chat/{chatbotId}   | Open bot; optional `?sessionId=X` |

### Chat REST API (CSRF disabled, JS sends token via `X-XSRF-TOKEN` header)
| Method | Path                        | Description |
|--------|-----------------------------|-------------|
| POST   | /api/chat/send              | Send message; creates session when sessionId=null |
| POST   | /api/chat/session/new       | Create empty session (new-chat button) |
| DELETE | /api/chat/session/{id}      | Delete session + messages (ownership-checked) |

### Admin Panel (ROLE_ADMIN required)
| Method | Path                            | Description |
|--------|---------------------------------|-------------|
| GET    | /admin                          | Dashboard (5 stats: users/depts/bots/sessions/messages) |
| GET    | /admin/users                    | User list |
| POST   | /admin/users/create             | Create user |
| POST   | /admin/users/{id}/update        | Update name/email/password/roles/departments |
| POST   | /admin/users/{id}/toggle        | Enable/disable user |
| GET    | /admin/departments              | Department list |
| POST   | /admin/departments/save         | Create or update department |
| POST   | /admin/departments/{id}/delete  | Delete department |
| GET    | /admin/chatbots                 | Chatbot list |
| POST   | /admin/chatbots/save            | Create or update chatbot (blank API key field retains stored key) |
| POST   | /admin/chatbots/{id}/toggle     | Enable/disable chatbot |
| POST   | /admin/chatbots/{id}/delete     | Delete chatbot + its sessions |

## Default Credentials (first startup)
- Username: `admin`
- Password: `admin123`
- **Change immediately in production.**

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
Retention: 90 days / 2 GB.
App package (`com.hnp`) at DEBUG (includes full stacktraces on errors); root at INFO.
`ProdLogs/` is in `.gitignore`.

## Known Issues / Gotchas

1. Lombok `sun.misc.Unsafe` warning on Java 25 — harmless, suppressed at runtime.
2. Spring Security emits a WARN about `UserDetailsService` beans — silenced in logback-spring.xml.
3. pgVector `Request failed` warning on RAG search = pgVector server not running; chat still works without RAG.
4. `app.log` path is relative to the working directory (project root when running via `mvnw`).
5. **API key not cleared on edit**: Leaving the API key field blank on chatbot edit retains the stored key. To clear it, type a new value explicitly.
6. `ROLE_DEPT_ADMIN` is seeded but not yet used in any access check.
7. `inputTokens` / `outputTokens` on `ChatMessage` are always `null` — LLM responses don't populate them yet.
8. `ChatSession.updatedAt` is set by `@PreUpdate` JPA hook, not manually — only fires on entity updates, not on `save()` of a brand-new session.
