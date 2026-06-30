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
| `AI_EMBEDDING_PROVIDER` | `OPENAI_COMPATIBLE`                              | Embedding backend: `OPENAI_COMPATIBLE` or `OLLAMA` |
| `AI_EMBEDDING_BASE_URL` | *(falls back to `AI_BASE_URL` or `OLLAMA_BASE_URL`)* | Embedding API base URL. For OpenAI-compatible **must end with `/v1`**. For Ollama falls back to `OLLAMA_BASE_URL`. |
| `AI_EMBEDDING_API_KEY`  | *(falls back to `AI_API_KEY`)*                   | Embedding API key (OpenAI-compatible only). Same `apikey`/`Bearer` prefix rules. |
| `AI_EMBEDDING_MODEL`    | `text-embedding-ada-002`                         | Embedding model name. For Ollama use e.g. `nomic-embed-text`. |
| `PGVECTOR_DIMENSIONS`   | `1024`                                           | **Must match the output dimension of your embedding model.** Common values: 1024, 1536, 3072. Changing this requires recreating the `vector_store` table. |
| `OLLAMA_BASE_URL`       | `http://localhost:11434`                         | Base URL for the global Ollama instance (used when chatbot provider is OLLAMA and no per-bot URL is set) |
| `OLLAMA_MODEL`          | `llama3`                                         | Default Ollama model name |
| `CHAT_HISTORY_LIMIT`    | `20`                                             | Maximum number of prior messages included in each LLM request |
| `RAG_TOP_K`             | `5`                                              | Maximum number of documents retrieved from pgVector per query |
| `LOG_PATH`              | `ProdLogs`                                       | Directory for the active log file and daily archives |
| `SERVER_PORT`           | `8081`                                           | HTTP server port |

### URL format — important

Path suffixes are appended automatically — never include them in the env vars:

| Variable | SDK appends | Correct value example |
|---|---|---|
| `AI_BASE_URL` | `/chat/completions` | `https://api.arvancloud.ir/ai/v1` |
| `AI_EMBEDDING_BASE_URL` (OpenAI-compatible) | `/embeddings` | `https://api.arvancloud.ir/ai/v1` |
| `AI_EMBEDDING_BASE_URL` (Ollama) | `/api/embed` | `http://localhost:11434` |
| `OLLAMA_BASE_URL` | `/api/embed` | `http://localhost:11434` |

**OpenAI-compatible URLs must end with `/v1`.** Ollama URLs must be host + port only — no path.

Common mistakes:
```bash
# Wrong — includes the path
AI_EMBEDDING_BASE_URL=http://localhost:11434/api/embeddings   # → 404

# Wrong — missing /v1
AI_BASE_URL=https://api.arvancloud.ir/ai                      # → 404

# Correct
AI_EMBEDDING_BASE_URL=http://localhost:11434
AI_BASE_URL=https://api.arvancloud.ir/ai/v1
```

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

### Embedding documents with n8n

You can automate the ingestion pipeline using [n8n](https://n8n.io). The workflow below downloads a PDF, splits it into overlapping chunks, embeds each chunk via the embedding API, and inserts the result into `vector_store`.

**Workflow steps:**

```
Manual Trigger
  → Download a File   (HTTP GET — fetch the PDF from a URL)
  → Extract from File (PDF → plain text)
  → Create chunks     (JS: 800-char chunks, 100-char overlap)
  → Loop Over Items
      → Embedding a chunk  (POST to embedding API)
      → Prepare data       (format content + metadata + embedding vector)
      → Insert data        (INSERT INTO vector_store)
      → (back to loop)
```

**Key settings to adjust before running:**

| Node | What to set |
|------|-------------|
| Download a File | URL of the PDF to ingest |
| Embedding a chunk → Authorization header | Your embedding API key (`apikey ...` or `Bearer ...`) |
| Embedding a chunk → `model` | Your embedding model name |
| Embedding a chunk → `dimensions` | Must match `PGVECTOR_DIMENSIONS` (default `1024`) |
| Prepare data → `chatbot_id` | Tags that control which chatbots can search this document |
| Insert data | PostgreSQL credentials pointing to `chatbot_db` |

**Metadata in the Prepare data node:**

```javascript
return {
  content: content,
  metadata: {
    chatbot_id: ["hr", "contract"],  // JSON array — supported natively
    source: "my-file.pdf"
  },
  embedding: `[${embedding.join(',')}]`
};
```

<details>
<summary>Import this workflow into n8n (click to expand JSON)</summary>

```json
{
  "name": "Embed PDF into pgVector",
  "nodes": [
    {
      "parameters": {},
      "type": "n8n-nodes-base.manualTrigger",
      "typeVersion": 1,
      "position": [160, -16],
      "id": "30d78f2c-417e-45af-9c60-8d8004df4d35",
      "name": "When clicking Execute workflow"
    },
    {
      "parameters": {
        "operation": "pdf",
        "options": {}
      },
      "type": "n8n-nodes-base.extractFromFile",
      "typeVersion": 1.1,
      "position": [528, -192],
      "id": "bb3cd1de-3086-4644-bf2d-b43c95f657a8",
      "name": "Extract from File"
    },
    {
      "parameters": {
        "options": {}
      },
      "type": "n8n-nodes-base.splitInBatches",
      "typeVersion": 3,
      "position": [448, 48],
      "id": "f1af8f5b-6db9-460b-afad-82effc5d5e79",
      "name": "Loop Over Items"
    },
    {
      "parameters": {
        "url": "file url...",
        "options": {}
      },
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.4,
      "position": [352, -192],
      "id": "0b423142-8f2d-4ae4-ab45-6c03204a6a75",
      "name": "Download a File"
    },
    {
      "parameters": {
        "jsCode": "const text = $input.first().json.text;\n\nconst chunkSize = 800;\nconst overlap = 100;\n\nconst items = [];\n\nfor (let i = 0; i < text.length; i += (chunkSize - overlap)) {\n  const chunk = text.substring(i, i + chunkSize);\n\n  if (chunk.trim()) {\n    items.push({\n      json: {\n        chunk,\n        chunkIndex: items.length\n      }\n    });\n  }\n}\n\nreturn items;"
      },
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [704, -192],
      "id": "4b2ad51f-fde4-419c-a781-12b3122a4ef8",
      "name": "Create chunks"
    },
    {
      "parameters": {
        "method": "POST",
        "url": "embed model...",
        "sendHeaders": true,
        "headerParameters": {
          "parameters": [
            {
              "name": "Authorization",
              "value": "apikey YOUR_KEY_HERE"
            }
          ]
        },
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={\n  \"model\": \"your-embedding-model\",\n  \"input\": {{ JSON.stringify($json.chunk) }},\n  \"dimensions\": 1024\n}",
        "options": {}
      },
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.4,
      "position": [672, 64],
      "id": "5bc75988-1e41-455d-8c0e-ea0085379f5b",
      "name": "Embedding a chunk"
    },
    {
      "parameters": {
        "mode": "runOnceForEachItem",
        "jsCode": "const content = $('Loop Over Items').item.json.chunk;\nconst embedding = $json.data[0].embedding;\n\nreturn {\n  content: content,\n  metadata: {\n    chatbot_id: [\"hr\", \"contract\"],\n    source: \"my-file.pdf\"\n  },\n  embedding: `[${embedding.join(',')}]`\n};"
      },
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [880, 64],
      "id": "1b5c150c-78db-4737-afbc-23d425d80e63",
      "name": "Prepare data for database"
    },
    {
      "parameters": {
        "schema": { "__rl": true, "mode": "list", "value": "public" },
        "table": { "__rl": true, "value": "vector_store", "mode": "list", "cachedResultName": "vector_store" },
        "columns": {
          "mappingMode": "defineBelow",
          "value": {
            "content": "={{ $json.content }}",
            "metadata": "={{ $json.metadata }}",
            "embedding": "={{ $json.embedding }}"
          },
          "matchingColumns": ["id"],
          "schema": [
            { "id": "id", "displayName": "id", "required": false, "defaultMatch": true, "display": true, "type": "string", "canBeUsedToMatch": true },
            { "id": "content", "displayName": "content", "required": false, "defaultMatch": false, "display": true, "type": "string", "canBeUsedToMatch": true },
            { "id": "metadata", "displayName": "metadata", "required": false, "defaultMatch": false, "display": true, "type": "object", "canBeUsedToMatch": true },
            { "id": "embedding", "displayName": "embedding", "required": false, "defaultMatch": false, "display": true, "type": "string", "canBeUsedToMatch": true }
          ]
        },
        "options": {}
      },
      "type": "n8n-nodes-base.postgres",
      "typeVersion": 2.6,
      "position": [1056, 64],
      "id": "a9a5a2bb-bfd0-4603-81a0-902376364308",
      "name": "Insert data"
    }
  ],
  "connections": {
    "When clicking Execute workflow": { "main": [[{ "node": "Download a File", "type": "main", "index": 0 }]] },
    "Download a File": { "main": [[{ "node": "Extract from File", "type": "main", "index": 0 }]] },
    "Extract from File": { "main": [[{ "node": "Create chunks", "type": "main", "index": 0 }]] },
    "Create chunks": { "main": [[{ "node": "Loop Over Items", "type": "main", "index": 0 }]] },
    "Loop Over Items": { "main": [[], [{ "node": "Embedding a chunk", "type": "main", "index": 0 }]] },
    "Embedding a chunk": { "main": [[{ "node": "Prepare data for database", "type": "main", "index": 0 }]] },
    "Prepare data for database": { "main": [[{ "node": "Insert data", "type": "main", "index": 0 }]] },
    "Insert data": { "main": [[{ "node": "Loop Over Items", "type": "main", "index": 0 }]] }
  },
  "settings": { "executionOrder": "v1" }
}
```

To import: open n8n → **Workflows** → **Import from file / clipboard** → paste the JSON above.

</details>

If you are using **Ollama for embeddings**, the workflow below uses the local Ollama API instead of a cloud endpoint — no API key required.
It also includes **Farsi text normalization** (Arabic letter unification, diacritic removal, ZWNJ cleanup) and **sentence-aware chunking** for better retrieval quality on Persian documents.

Key differences from the OpenAI-compatible workflow:

| | OpenAI-compatible | Ollama |
|---|---|---|
| Embedding URL | `https://your-api/v1/embeddings` | `http://localhost:11434/api/embeddings` |
| Request field | `"input"` | `"prompt"` |
| Response field | `$json.data[0].embedding` | `$json.embedding` |
| Authorization | required | not needed |
| Chunking | character-based | sentence-aware + Farsi normalization |

<details>
<summary>Import Ollama workflow into n8n (click to expand JSON)</summary>

```json
{
  "nodes": [
    {
      "parameters": {},
      "type": "n8n-nodes-base.manualTrigger",
      "typeVersion": 1,
      "position": [160, -16],
      "id": "4c1be6c1-2afe-4ab1-b61e-613164224488",
      "name": "When clicking 'Execute workflow'"
    },
    {
      "parameters": {
        "operation": "pdf",
        "options": {}
      },
      "type": "n8n-nodes-base.extractFromFile",
      "typeVersion": 1.1,
      "position": [528, -192],
      "id": "ac8fddff-26bd-4b2e-aa15-ca262b1adcfd",
      "name": "Extract from File"
    },
    {
      "parameters": {
        "options": {}
      },
      "type": "n8n-nodes-base.splitInBatches",
      "typeVersion": 3,
      "position": [448, 48],
      "id": "662ec191-f0bd-4845-a450-e432bce09f0f",
      "name": "Loop Over Items"
    },
    {
      "parameters": {
        "url": "https://drive.google.com/uc?export=download&id=...",
        "options": {}
      },
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.4,
      "position": [352, -192],
      "id": "68fa1261-b333-4fea-abc4-6dd45587eb97",
      "name": "Download a File"
    },
    {
      "parameters": {
        "jsCode": "const text = $input.first().json.text;\n\nconst CHUNK_SIZE = 500;   // characters — lower this for more precise matching\nconst OVERLAP = 80;\n\n// ---------- Farsi normalization ----------\nfunction normalizeFarsi(input) {\n  if (!input) return \"\";\n  return input\n    // Arabic letters → Farsi\n    .replace(/ك/g, \"ک\")\n    .replace(/[يىئ]/g, \"ی\")            // Arabic yeh, alef maksura, yeh with hamza\n    .replace(/ة/g, \"ه\")               // teh marbuta\n    .replace(/[أإآ]/g, \"ا\")           // alef variants with hamza\n    .replace(/ؤ/g, \"و\")\n    .replace(/\\u0640/g, \"\")           // tatweel (kashida ـ)\n    // remove diacritics / harakat\n    .replace(/[\\u064B-\\u0652\\u0670\\u0653-\\u0655]/g, \"\")\n    // Arabic digits → Farsi (Latin digits left untouched for technical names)\n    .replace(/[\\u0660-\\u0669]/g, d => String.fromCharCode(d.charCodeAt(0) - 0x0660 + 0x06F0))\n    // normalize ZWNJ: trim spaces around it and collapse repeats\n    .replace(/[ \\t]*\\u200C[ \\t]*/g, \"\\u200C\")\n    .replace(/\\u200C{2,}/g, \"\\u200C\")\n    // collapse whitespace (except newlines)\n    .replace(/[^\\S\\n]+/g, \" \")\n    .trim();\n}\n// -----------------------------------------\n\nconst clean = normalizeFarsi(text);\n\n// Sentence boundaries: period, Farsi/Latin question & exclamation, ellipsis, semicolon, newline\nconst sentences = clean\n  .split(/(?<=[.!?؟…؛\\n])\\s*/)\n  .map(s => s.trim())\n  .filter(Boolean);\n\nconst items = [];\nlet chunk = \"\";\n\nconst push = (c) => {\n  const t = c.trim();\n  if (t) items.push(t);\n};\n\nfor (const s of sentences) {\n  // Force-split a sentence longer than the chunk size\n  if (s.length > CHUNK_SIZE) {\n    push(chunk);\n    chunk = \"\";\n    for (let i = 0; i < s.length; i += CHUNK_SIZE - OVERLAP) {\n      push(s.slice(i, i + CHUNK_SIZE));\n    }\n    continue;\n  }\n\n  if ((chunk + \" \" + s).length > CHUNK_SIZE) {\n    push(chunk);\n    chunk = chunk.slice(-OVERLAP) + \" \" + s;  // carry overlap\n  } else {\n    chunk = chunk ? chunk + \" \" + s : s;\n  }\n}\npush(chunk);\n\n// Output with metadata\nreturn items.map((chunk, index) => ({\n  json: { chunk, index, length: chunk.length }\n}));"
      },
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [704, -192],
      "id": "0f7104f7-2eeb-4c74-af25-828c4b64efd5",
      "name": "Create chunks"
    },
    {
      "parameters": {
        "method": "POST",
        "url": "http://localhost:11434/api/embeddings",
        "sendHeaders": true,
        "headerParameters": {
          "parameters": [{}]
        },
        "sendBody": true,
        "specifyBody": "json",
        "jsonBody": "={\n  \"model\": \"bge-m3\",\n  \"prompt\": {{ JSON.stringify($json.chunk) }}\n}",
        "options": {}
      },
      "type": "n8n-nodes-base.httpRequest",
      "typeVersion": 4.4,
      "position": [704, 48],
      "id": "48222cdb-2376-49cf-98d6-0b463f9fe73c",
      "name": "Embedding a chunk",
      "executeOnce": true
    },
    {
      "parameters": {
        "mode": "runOnceForEachItem",
        "jsCode": "\n\nconst content = $('Loop Over Items').item.json.chunk\n\nconst embedding = $json.embedding;\n\nreturn {\n  content: content,\n   metadata: {\n    chatbot_id: [\"contract\", \"hr\"],\n    source: \"my-file.pdf\"\n  },\n  embedding: `[${embedding.join(',')}]`\n};"
      },
      "type": "n8n-nodes-base.code",
      "typeVersion": 2,
      "position": [912, 48],
      "id": "595c96b5-d6d1-44b1-a31c-e1db39aa6916",
      "name": "Prepare data for database"
    },
    {
      "parameters": {
        "schema": { "__rl": true, "mode": "list", "value": "public" },
        "table": { "__rl": true, "value": "vector_store", "mode": "list", "cachedResultName": "vector_store" },
        "columns": {
          "mappingMode": "defineBelow",
          "value": {
            "content": "={{ $json.content }}",
            "metadata": "={{ $json.metadata }}",
            "embedding": "={{ $json.embedding }}"
          },
          "matchingColumns": ["id"],
          "schema": [
            { "id": "id", "displayName": "id", "required": false, "defaultMatch": true, "display": true, "type": "string", "canBeUsedToMatch": true, "removed": false },
            { "id": "content", "displayName": "content", "required": false, "defaultMatch": false, "display": true, "type": "string", "canBeUsedToMatch": true },
            { "id": "metadata", "displayName": "metadata", "required": false, "defaultMatch": false, "display": true, "type": "object", "canBeUsedToMatch": true },
            { "id": "embedding", "displayName": "embedding", "required": false, "defaultMatch": false, "display": true, "type": "string", "canBeUsedToMatch": true }
          ],
          "attemptToConvertTypes": false,
          "convertFieldsToString": false
        },
        "options": {}
      },
      "type": "n8n-nodes-base.postgres",
      "typeVersion": 2.6,
      "position": [1120, 48],
      "id": "39210496-dcfb-4579-aaa9-30ddc998207f",
      "name": "Insert data"
    }
  ],
  "connections": {
    "When clicking 'Execute workflow'": { "main": [[{ "node": "Download a File", "type": "main", "index": 0 }]] },
    "Download a File": { "main": [[{ "node": "Extract from File", "type": "main", "index": 0 }]] },
    "Extract from File": { "main": [[{ "node": "Create chunks", "type": "main", "index": 0 }]] },
    "Create chunks": { "main": [[{ "node": "Loop Over Items", "type": "main", "index": 0 }]] },
    "Loop Over Items": { "main": [[], [{ "node": "Embedding a chunk", "type": "main", "index": 0 }]] },
    "Embedding a chunk": { "main": [[{ "node": "Prepare data for database", "type": "main", "index": 0 }]] },
    "Prepare data for database": { "main": [[{ "node": "Insert data", "type": "main", "index": 0 }]] },
    "Insert data": { "main": [[{ "node": "Loop Over Items", "type": "main", "index": 0 }]] }
  },
  "settings": { "executionOrder": "v1" }
}
```

**Key settings to adjust before running:**

| Node | What to set |
|------|-------------|
| Download a File | URL of the PDF to ingest |
| Embedding a chunk → `url` | Ollama base URL if not running locally (default: `http://localhost:11434/api/embeddings`) |
| Embedding a chunk → `model` | Your pulled Ollama embedding model (e.g. `bge-m3`, `nomic-embed-text`, `mxbai-embed-large`) |
| Create chunks → `CHUNK_SIZE` | Characters per chunk (default: 500) |
| Prepare data → `chatbot_id` | Tags that control which chatbots can search this document |
| Insert data | PostgreSQL credentials pointing to `chatbot_db` |

> Make sure `PGVECTOR_DIMENSIONS` matches your model's output dimension (e.g. `bge-m3` → 1024, `nomic-embed-text` → 768).

To import: open n8n → **Workflows** → **Import from file / clipboard** → paste the JSON above.

</details>

## LLM Configuration

There are two independent LLM concerns in this application:

- **Chat model** — answers user messages; configured per-chatbot in the admin panel
- **Embedding model** — converts text to vectors for RAG; configured globally via env vars

---

### Chat model — per-chatbot providers

Each chatbot in the admin panel has an **LLM Provider** dropdown with three options:

| Provider | What it does |
|---|---|
| **GLOBAL** | Uses the shared endpoint defined in `AI_BASE_URL` / `AI_API_KEY` / `AI_CHAT_MODEL` |
| **OPENAI_COMPATIBLE** | Uses a custom endpoint specified in the chatbot's own Base URL / API Key / Model fields |
| **OLLAMA** | Connects to a local or remote Ollama server using the chatbot's URL and model fields |

For **GLOBAL** chatbots, configure the shared endpoint with these env vars:

```bash
AI_BASE_URL=https://api.arvancloud.ir/ai/v1   # must end with /v1
AI_API_KEY=apikey 283de96a-...               # your key (see API key format below)
AI_CHAT_MODEL=DeepSeek-R1-distill-qwen-32b   # exact model name from the provider
AI_MAX_TOKENS=3000                            # max tokens per response (default: 3000)
AI_TEMPERATURE=0.7                            # 0 = deterministic, 1 = creative (default: 0.7)
```

For **OLLAMA** chatbots, configure the default Ollama server:

```bash
OLLAMA_BASE_URL=http://localhost:11434   # Ollama server address (default: localhost:11434)
OLLAMA_MODEL=llama3                      # default model if not set per-chatbot
```

Each chatbot with provider **OPENAI_COMPATIBLE** or **OLLAMA** can override all fields (URL, key, model)
directly in the admin panel — no env vars needed for those specific chatbots.

---

### API key format

The app supports multiple Authorization header formats. Store the **full value** in the API Key field:

| Provider | Format | Example |
|---|---|---|
| ArvanCloud | `apikey <key>` | `apikey 283de96a-b069-565e-bcf6-...` |
| Standard OpenAI / OpenAI-compatible | bare key | `sk-proj-...` |
| Explicit Bearer | `Bearer <key>` | `Bearer sk-proj-...` |

A bare key without a prefix is automatically sent as `Bearer <key>`.

---

### Embedding model

The embedding model is used globally for RAG document search. It is selected by `AI_EMBEDDING_PROVIDER`:

#### Option A — OpenAI-compatible (default)

Use any OpenAI-compatible `/v1/embeddings` endpoint:

```bash
AI_EMBEDDING_PROVIDER=OPENAI_COMPATIBLE   # default, can be omitted

# If your embedding endpoint is the same host as chat:
AI_EMBEDDING_MODEL=text-embedding-ada-002

# If your embedding endpoint is a different host (e.g. ArvanCloud separate URL):
AI_EMBEDDING_BASE_URL=https://api.arvancloud.ir/ai/v1
AI_EMBEDDING_API_KEY=apikey 283de96a-...
AI_EMBEDDING_MODEL=Embedding-3-Small

PGVECTOR_DIMENSIONS=1024   # must match the model's output dimension
```

If `AI_EMBEDDING_BASE_URL` is not set, it falls back to `AI_BASE_URL`.
If `AI_EMBEDDING_API_KEY` is not set, it falls back to `AI_API_KEY`.

#### Option B — Ollama

Use a locally running Ollama model for embeddings:

```bash
AI_EMBEDDING_PROVIDER=OLLAMA
AI_EMBEDDING_MODEL=nomic-embed-text      # or any Ollama embedding model you have pulled
AI_EMBEDDING_BASE_URL=http://localhost:11434   # optional — falls back to OLLAMA_BASE_URL

PGVECTOR_DIMENSIONS=768   # must match the model's output dimension
```

> **`AI_EMBEDDING_BASE_URL` must be only the host and port — do NOT include any path.**
> Spring AI 2.0 uses the `/api/embed` endpoint and appends it automatically.
> Setting this to `http://localhost:11434/api/embeddings` (full path) causes a 404 error.
>
> Correct: `AI_EMBEDDING_BASE_URL=http://localhost:11434`
> Wrong:   `AI_EMBEDDING_BASE_URL=http://localhost:11434/api/embeddings`

> **API endpoint note:** Spring AI 2.0 calls `/api/embed` (Ollama ≥ 0.1.31), not the older `/api/embeddings`.
> If you test with curl using the old endpoint, that's fine — both work in recent Ollama versions.
> The Spring AI client always uses the newer endpoint regardless of your curl test command.

Pull the model in Ollama before starting the app:

```bash
ollama pull nomic-embed-text
```

Common Ollama embedding models and their dimensions:

| Model | Dimension | Notes |
|---|---|---|
| `nomic-embed-text` | 768 | Good general-purpose, small |
| `mxbai-embed-large` | 1024 | Higher quality |
| `all-minilm` | 384 | Very fast, lightweight |
| `bge-m3` | 1024 | Multilingual, good for Persian |

> **Important:** `PGVECTOR_DIMENSIONS` must match the model's actual output dimension.
> Changing this after the `vector_store` table has been created requires dropping and recreating the table.

---

### Common configuration scenarios

**Scenario 1 — ArvanCloud for both chat and embedding:**
```bash
AI_BASE_URL=https://api.arvancloud.ir/ai/v1
AI_API_KEY=apikey 283de96a-...
AI_CHAT_MODEL=DeepSeek-R1-distill-qwen-32b

AI_EMBEDDING_PROVIDER=OPENAI_COMPATIBLE
AI_EMBEDDING_BASE_URL=https://api.arvancloud.ir/ai/v1
AI_EMBEDDING_API_KEY=apikey 283de96a-...
AI_EMBEDDING_MODEL=Embedding-3-Small
PGVECTOR_DIMENSIONS=1024
```

**Scenario 2 — Ollama for everything (fully local, offline):**
```bash
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=llama3

AI_EMBEDDING_PROVIDER=OLLAMA
AI_EMBEDDING_MODEL=nomic-embed-text
PGVECTOR_DIMENSIONS=768
```

**Scenario 3 — Cloud chat + local Ollama embeddings:**
```bash
AI_BASE_URL=https://api.openai.com/v1
AI_API_KEY=sk-proj-...
AI_CHAT_MODEL=gpt-4o-mini

AI_EMBEDDING_PROVIDER=OLLAMA
AI_EMBEDDING_MODEL=nomic-embed-text
PGVECTOR_DIMENSIONS=768
```

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
