# Private Chatbot

A private, multi-department chatbot web application built with Spring Boot 4.1.0 and Spring AI 2.0.0.
Access is role- and department-based: users can only interact with chatbots assigned to their department.
Admins manage users, departments, and chatbots through a dedicated panel.

## Features

- **Department-scoped access** — each chatbot is assigned to one department; only users in that department can use it
- **Multiple LLM backends** — global OpenAI-compatible endpoint, per-chatbot overrides, or Ollama
- **ArvanCloud support** — custom `apikey <key>` Authorization header handled transparently
- **RAG (Retrieval-Augmented Generation)** — optional vector search via pgVector for context injection
- **Persian/RTL UI** — fully right-to-left Bootstrap 5.3 interface in Farsi
- **Conversation history** — chat sessions are persisted; users can switch between sessions
- **Admin panel** — create/edit/toggle users, departments, and chatbots without restarting
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

### 2. Configure the application

Copy and edit `src/main/resources/application.properties`, or set environment variables:

```bash
export AI_BASE_URL=https://your-llm-endpoint/v1
export AI_API_KEY=your-api-key          # or: apikey your-api-key (for ArvanCloud)
export AI_CHAT_MODEL=your-model-name
export AI_EMBEDDING_MODEL=text-embedding-ada-002
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

Open `http://localhost:8080/login`

Default credentials (change immediately in production):
- **Username:** `admin`
- **Password:** `admin123`

## Environment Variables

| Variable              | Default                                          | Description                          |
|-----------------------|--------------------------------------------------|--------------------------------------|
| `DB_URL`              | `jdbc:postgresql://localhost:5432/chatbot_db`    | PostgreSQL JDBC URL                  |
| `DB_USERNAME`         | `postgres`                                       | Database username                    |
| `DB_PASSWORD`         | `postgres`                                       | Database password                    |
| `AI_BASE_URL`         | `http://localhost:8001`                          | Global LLM API base URL              |
| `AI_API_KEY`          | `changeme`                                       | Global LLM API key                   |
| `AI_CHAT_MODEL`       | `DeepSeek-R1-qwen-7b-awq`                       | Chat model name                      |
| `AI_MAX_TOKENS`       | `3000`                                           | Max completion tokens                |
| `AI_TEMPERATURE`      | `0.7`                                            | Sampling temperature                 |
| `AI_EMBEDDING_MODEL`  | `text-embedding-ada-002`                         | Embedding model name                 |
| `PGVECTOR_DIMENSIONS` | `1024`                                           | Vector embedding dimensions          |
| `CHAT_HISTORY_LIMIT`  | `20`                                             | Max prior messages sent as LLM context |
| `RAG_TOP_K`           | `5`                                              | Max RAG documents retrieved          |
| `LOG_PATH`            | `ProdLogs`                                       | Directory for log files              |
| `SERVER_PORT`         | `8080`                                           | HTTP server port                     |

## Project Structure

```
private-chatbot/
├── src/
│   ├── main/
│   │   ├── java/com/hnp/privatechatbot/
│   │   │   ├── PrivateChatbotApplication.java
│   │   │   ├── config/
│   │   │   │   ├── AppConfig.java           — beans: PasswordEncoder, default ChatClient
│   │   │   │   ├── SecurityConfig.java      — Spring Security, form login, CSRF
│   │   │   │   └── DataInitializer.java     — seeds roles and default admin user
│   │   │   ├── entity/                      — JPA entities
│   │   │   │   ├── User.java
│   │   │   │   ├── Role.java
│   │   │   │   ├── Department.java
│   │   │   │   ├── ChatBot.java             — LlmProvider enum + per-bot override fields
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
│   │   │   │   └── LlmService.java          — per-chatbot ChatClient factory + RAG
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
| Method | Path     | Description           |
|--------|----------|-----------------------|
| GET    | /login   | Login page            |
| POST   | /logout  | Logout (CSRF-protected) |

### Chat UI
| Method | Path                | Description                             |
|--------|---------------------|-----------------------------------------|
| GET    | /chat               | Chat home (no bot selected)             |
| GET    | /chat/{chatbotId}   | Open bot; optional `?sessionId=X`       |

### Chat REST API (consumed by frontend JS)
| Method | Path                        | Description                          |
|--------|-----------------------------|--------------------------------------|
| POST   | /api/chat/send              | Send a message; creates session if needed |
| POST   | /api/chat/session/new       | Create an empty session              |
| DELETE | /api/chat/session/{id}      | Delete a session and its messages    |

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

## LLM Configuration

Each chatbot has a `LlmProvider` field:

- **GLOBAL** — uses the global endpoint from `application.properties` (no per-bot config needed)
- **OPENAI** — uses the per-bot base URL, API key, and model name
- **OLLAMA** — connects to a local Ollama instance using the per-bot base URL and model name

### ArvanCloud API Key Format
ArvanCloud requires `Authorization: apikey <key>` instead of the standard `Bearer <key>`.
In the admin panel, enter the API key as: `apikey 283de96a-b069-565e-bcf6-516abe688d18`

## Logging

- **Active log:** `ProdLogs/app.log`
- **Daily archives:** `ProdLogs/ArchiveLog/app.YYYY-MM-DD.log.gz`
- Retention: 90 days / 2 GB maximum
- App-level logging at DEBUG; root at INFO
- `ProdLogs/` is listed in `.gitignore` and will not be committed

## Security Notes

- Change the default admin password immediately after first login
- API keys are stored in the database in plaintext — use a secrets manager in production
- CSRF protection is enabled for all form submissions
- `/api/**` endpoints disable CSRF (token sent via `X-XSRF-TOKEN` header by the JS client)

## License

Private / internal use. Not for public distribution.
