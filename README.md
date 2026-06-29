# Private Chatbot

A private, multi-department chatbot web application built with Spring Boot 4.1.0 and Spring AI 2.0.0.
Access is role- and department-based: users can only interact with chatbots assigned to their department.
Admins manage users, departments, and chatbots through a dedicated panel.

## Features

- **Department-scoped access** — each chatbot is assigned to one department; only users in that department can use it
- **Multiple LLM backends** — global OpenAI-compatible endpoint, per-chatbot overrides, or Ollama
- **ArvanCloud support** — custom `apikey <key>` Authorization header handled transparently
- **RAG (Retrieval-Augmented Generation)** — optional vector search via pgVector; tag-based filtering per chatbot
- **Persian/RTL UI** — fully right-to-left Bootstrap 5.3 interface in Farsi
- **Conversation history** — chat sessions are persisted; users can switch and delete sessions
- **Admin panel** — create/edit/toggle users, departments, and chatbots without restarting
- **Offline-ready** — Bootstrap and Bootstrap Icons are bundled as WebJars; no CDN required
- **Daily log rotation** — Logback writes to `ProdLogs/app.log`, archives to `ProdLogs/ArchiveLog/`

## Prerequisites

- Java 25+
- Maven (or use the included `mvnw`)
- Docker (for PostgreSQL + pgVector)
- An OpenAI-compatible LLM API endpoint or a local Ollama instance

## Quick Start

### 1. Start the database

```bash
docker run -d \
  --name chatbot-db \
  -e POSTGRES_DB=chatbot_db \
  -e POSTGRES_USER=postgres \
  -e POSTGRES_PASSWORD=postgres \
  -p 5432:5432 \
  pgvector/pgvector:pg18-trixie
```

### 2. Configure environment variables

```bash
# Required — LLM chat endpoint
export AI_BASE_URL=https://your-llm-endpoint/v1       # must end with /v1
export AI_API_KEY=your-api-key                         # or: apikey your-key (ArvanCloud)
export AI_CHAT_MODEL=your-model-name

# Required — embedding endpoint (for RAG)
export AI_EMBEDDING_BASE_URL=https://your-embed-endpoint/v1   # must end with /v1
export AI_EMBEDDING_API_KEY=apikey your-embedding-key         # or Bearer key
export AI_EMBEDDING_MODEL=text-embedding-3-small
```

### 3. Run the application

```bash
./mvnw spring-boot:run
```

Or on Windows:

```cmd
mvnw.cmd spring-boot:run
```

### 4. Log in

Open `http://localhost:8081/login`

Default credentials (change immediately in production):
- **Username:** `admin`
- **Password:** `admin123`

## Environment Variables

| Variable                | Default                                          | Description |
|-------------------------|--------------------------------------------------|-------------|
| `DB_URL`                | `jdbc:postgresql://localhost:5432/chatbot_db`    | PostgreSQL JDBC URL |
| `DB_USERNAME`           | `postgres`                                       | Database username |
| `DB_PASSWORD`           | `postgres`                                       | Database password |
| `AI_BASE_URL`           | `http://localhost:8001`                          | **Global chat API base URL. Must end with `/v1`.** The SDK appends `/chat/completions` automatically. Example: `https://api.arvancloud.ir/ai/v1` |
| `AI_API_KEY`            | `changeme`                                       | Global chat API key. For ArvanCloud, prefix with `apikey `: `apikey 283de96a-...`. For standard OpenAI-compatible APIs use the bare key or `Bearer <key>`. |
| `AI_CHAT_MODEL`         | `DeepSeek-R1-qwen-7b-awq`                       | Chat model name as returned by the provider |
| `AI_MAX_TOKENS`         | `3000`                                           | Maximum tokens the model can generate per response |
| `AI_TEMPERATURE`        | `0.7`                                            | Sampling temperature (0 = deterministic, 1 = more creative) |
| `AI_EMBEDDING_BASE_URL` | *(falls back to `AI_BASE_URL`)*                  | **Embedding API base URL. Must end with `/v1`.** Many providers (ArvanCloud, etc.) use a different host for embeddings. Example: `https://api.arvancloud.ir/ai/v1` |
| `AI_EMBEDDING_API_KEY`  | *(falls back to `AI_API_KEY`)*                   | Embedding API key. Same `apikey`/`Bearer` prefix rules as `AI_API_KEY`. |
| `AI_EMBEDDING_MODEL`    | `text-embedding-ada-002`                         | Embedding model name. Must match the model available at `AI_EMBEDDING_BASE_URL`. |
| `PGVECTOR_DIMENSIONS`   | `1024`                                           | **Must match the output dimension of your embedding model.** Common values: 1024, 1536, 3072. Changing this requires recreating the `vector_store` table. |
| `OLLAMA_BASE_URL`       | `http://localhost:11434`                         | Base URL for the global Ollama instance (used when chatbot provider is OLLAMA and no per-bot URL is set) |
| `OLLAMA_MODEL`          | `llama3`                                         | Default Ollama model name |
| `CHAT_HISTORY_LIMIT`    | `20`                                             | Maximum number of prior messages included in each LLM request |
| `RAG_TOP_K`             | `5`                                              | Maximum number of documents retrieved from pgVector per query |
| `LOG_PATH`              | `ProdLogs`                                       | Directory for the active log file and daily archives |
| `SERVER_PORT`           | `8081`                                           | HTTP server port |

### URL format — important

The Spring AI OpenAI client SDK appends the path suffix automatically:
- Chat: `<AI_BASE_URL>/chat/completions`
- Embeddings: `<AI_EMBEDDING_BASE_URL>/embeddings`

**Always set base URLs ending with `/v1`**, for example:
```
AI_BASE_URL=https://api.arvancloud.ir/ai/v1
AI_EMBEDDING_BASE_URL=https://api.arvancloud.ir/ai/v1
```

Do **not** include `/chat/completions` or `/embeddings` in the URL.

## RAG (Retrieval-Augmented Generation)

### How it works

1. Documents are embedded and stored in PostgreSQL via pgVector (`vector_store` table).
2. When a user sends a message, the system embeds the last few user messages and searches for similar documents.
3. Matching documents are prepended to the LLM system prompt as context.

### Document metadata and filtering

Each document stored in pgVector has a `metadata` field (PostgreSQL `jsonb` type). To control which chatbot can access which documents, set the `chatbot_id` field in metadata when embedding.

**Both formats are accepted:**

```json
// String (comma-separated)
{"chatbot_id": "hr,safety,finance"}

// JSON array
{"chatbot_id": ["hr", "safety", "finance"]}
```

Documents with **no `chatbot_id` field** (empty metadata `{}`) are treated as **shared/global** — every chatbot can search them.

### Chatbot RAG filter key

In the admin panel, the **RAG Filter Key** field on each chatbot is a comma-separated list of tags. For example: `hr,safety`.

| ragFilterKey value | Behavior |
|--------------------|----------|
| *(empty)*          | Search **all** documents — no filtering |
| `hr`               | Search documents where `chatbot_id` contains `hr`, plus all untagged documents |
| `hr,safety`        | Search documents where `chatbot_id` contains `hr` OR `safety`, plus all untagged documents |

### Storing documents in pgVector

The `vector_store` table has these columns: `id` (uuid), `content` (text), `metadata` (jsonb), `embedding` (vector).

Insert documents directly via SQL after generating embeddings:

```sql
INSERT INTO vector_store (id, content, metadata, embedding)
VALUES (gen_random_uuid(), 'document text here', '{"chatbot_id": "hr"}', '[0.1, 0.2, ...]');
```

Or update existing documents that have empty metadata:

```sql
UPDATE vector_store
SET metadata = '{"chatbot_id": "hr"}'::jsonb
WHERE metadata = '{}'::jsonb;
```

## LLM Configuration

Each chatbot has an `LlmProvider` setting:

| Provider           | Description |
|--------------------|-------------|
| **GLOBAL**         | Uses the global endpoint from `AI_BASE_URL` / `AI_API_KEY` |
| **OPENAI_COMPATIBLE** | Uses a per-chatbot base URL, API key, and model name |
| **OLLAMA**         | Connects to a local Ollama instance using the per-chatbot URL and model |

### API key format

| Provider    | Key format in admin panel              |
|-------------|----------------------------------------|
| ArvanCloud  | `apikey 283de96a-b069-565e-bcf6-...`   |
| OpenAI      | `sk-...` (bare key)                    |
| Local/other | `Bearer sk-...` or bare key            |

## Project Structure

```
private-chatbot/
├── src/
│   ├── main/
│   │   ├── java/com/hnp/privatechatbot/
│   │   │   ├── PrivateChatbotApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java           — beans: PasswordEncoder, default ChatClient, EmbeddingModel
│   │   │   │   ├── SecurityConfig.java      — Spring Security, form login, CSRF
│   │   │   │   └── DataInitializer.java     — seeds roles and default admin user
│   │   │   ├── entity/                      — JPA entities
│   │   │   │   ├── User.java
│   │   │   │   ├── Role.java
│   │   │   │   ├── Department.java
│   │   │   │   ├── ChatBot.java             — LlmProvider enum + per-bot override fields + ragFilterKey
│   │   │   │   ├── ChatSession.java
│   │   │   │   └── ChatMessage.java
│   │   │   ├── repository/                  — Spring Data JPA interfaces
│   │   │   ├── dto/
│   │   │   │   ├── ChatRequest.java
│   │   │   │   ├── ChatResponse.java
│   │   │   │   └── UserCreateRequest.java
│   │   │   ├── service/
│   │   │   │   ├── UserService.java         — UserDetailsService + user CRUD
│   │   │   │   ├── AdminService.java        — department + chatbot CRUD + stats
│   │   │   │   ├── ChatService.java         — session/message orchestration
│   │   │   │   └── LlmService.java          — per-chatbot ChatClient factory + RAG retrieval
│   │   │   └── controller/
│   │   │       ├── AuthController.java      — GET /login
│   │   │       ├── ChatController.java      — chat UI pages
│   │   │       ├── ChatApiController.java   — REST API for JS frontend
│   │   │       └── AdminController.java     — admin panel CRUD
│   │   └── resources/
│   │       ├── application.properties
│   │       ├── logback-spring.xml
│   │       ├── static/css/style.css
│   │       └── templates/
│   │           ├── fragments/navbar.html
│   │           ├── auth/login.html
│   │           ├── chat/index.html
│   │           └── admin/
│   │               ├── dashboard.html
│   │               ├── users.html
│   │               ├── departments.html
│   │               └── chatbots.html
│   └── test/
│       └── java/com/hnp/privatechatbot/
│           └── PrivateChatbotApplicationTests.java
├── ProdLogs/               — runtime logs (git-ignored)
│   └── ArchiveLog/         — daily compressed log archives
├── pom.xml
├── CLAUDE.md
└── README.md
```

## API Endpoints

### Authentication
| Method | Path     | Description             |
|--------|----------|-------------------------|
| GET    | /login   | Login page              |
| POST   | /logout  | Logout (CSRF-protected) |

### Chat UI
| Method | Path                | Description                       |
|--------|---------------------|-----------------------------------|
| GET    | /chat               | Chat home (no bot selected)       |
| GET    | /chat/{chatbotId}   | Open bot; optional `?sessionId=X` |

### Chat REST API (consumed by frontend JS)
| Method | Path                        | Description                              |
|--------|-----------------------------|------------------------------------------|
| POST   | /api/chat/send              | Send a message; creates session if needed |
| POST   | /api/chat/session/new       | Create an empty session                  |
| DELETE | /api/chat/session/{id}      | Delete a session and its messages        |

### Admin Panel
| Method | Path                            | Description                  |
|--------|---------------------------------|------------------------------|
| GET    | /admin                          | Dashboard with stats         |
| GET    | /admin/users                    | User management              |
| POST   | /admin/users/create             | Create a user                |
| POST   | /admin/users/{id}/toggle        | Enable/disable a user        |
| GET    | /admin/departments              | Department management        |
| POST   | /admin/departments/save         | Create or update a dept      |
| POST   | /admin/departments/{id}/delete  | Delete a department          |
| GET    | /admin/chatbots                 | Chatbot management           |
| POST   | /admin/chatbots/save            | Create or update a chatbot   |
| POST   | /admin/chatbots/{id}/toggle     | Enable/disable a chatbot     |
| POST   | /admin/chatbots/{id}/delete     | Delete a chatbot             |

## Logging

- **Active log:** `ProdLogs/app.log`
- **Daily archives:** `ProdLogs/ArchiveLog/app.YYYY-MM-DD.log.gz`
- Retention: 90 days / 2 GB maximum
- App-level logging at DEBUG (includes stacktraces on errors); root at INFO
- `ProdLogs/` is listed in `.gitignore` and will not be committed

## Offline Deployment

Bootstrap CSS/JS and Bootstrap Icons are bundled as Maven WebJars — no internet connection is needed at runtime. WebJar resources are served at `/webjars/**` by Spring Boot automatically.

The Vazirmatn font is loaded from the system font stack (`Tahoma`, `Segoe UI`, `Arial` as fallbacks). If you want to use Vazirmatn in an offline environment:

1. Download the Vazirmatn woff2 files from the Vazirmatn GitHub releases.
2. Place them in `src/main/resources/static/fonts/`.
3. Add a `@font-face` rule to `static/css/style.css` pointing to `/fonts/Vazirmatn.woff2`.

## Security Notes

- Change the default admin password immediately after first login
- API keys are stored in the database in plaintext — use a secrets manager in production
- CSRF protection is enabled for all form submissions
- `/api/**` endpoints disable CSRF (the JS client sends the token via `X-XSRF-TOKEN` header)

## License

Private / internal use. Not for public distribution.
