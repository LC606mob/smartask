# AGENTS.md

Guidance for Codex when working in this repository.

## Project Snapshot

SmartAsk is a Java 17 / Spring Boot 3.4.2 + Vue 3 knowledge-base RAG system.
The main business flow is:

`UploadController -> UploadService -> Kafka FileProcessingConsumer -> ParseService -> VectorizationService -> EmbeddingClient -> ElasticsearchService -> HybridSearchService -> ChatHandler -> ChatWebSocketHandler`

Core capabilities:

- Upload, merge, store, and parse documents.
- Generate DashScope `text-embedding-v4` vectors with 2048 dimensions.
- Store searchable chunks in Elasticsearch index `knowledge_base`.
- Apply user, public, and organization-tag permissions during retrieval.
- Stream chat responses back to the frontend over WebSocket.

## Repository Layout

- `src/main/java/com/lcmob/smartask/controller/`: REST controllers.
- `src/main/java/com/lcmob/smartask/service/`: business logic for auth, upload, parsing, vectorization, search, chat, documents, and org tags.
- `src/main/java/com/lcmob/smartask/consumer/`: Kafka file-processing consumer.
- `src/main/java/com/lcmob/smartask/client/`: external AI clients, including `DeepSeekClient` and `EmbeddingClient`.
- `src/main/java/com/lcmob/smartask/config/`: Spring Security, Kafka, Elasticsearch, MinIO, WebSocket, and client configuration.
- `src/main/resources/`: Spring profiles, logging, static test pages, and ES mappings.
- `docs/`: Docker Compose files, database DDL, nginx config, and project notes.
- `frontend/`: Vue 3 + TypeScript + Vite + Pinia + Naive UI frontend.

## Current Local Configuration

- Backend port: `8081` from `src/main/resources/application.yml`.
- Active Spring profile: `dev`.
- Dev database: MySQL on `localhost:13306`, database `smartask`.
- Redis: `localhost:6379`, password `123456`, database `1`.
- Kafka: `localhost:19092`, topic `file-processing-topic1`, DLT `file-processing-dlt`.
- MinIO: `http://localhost:19000`, bucket `uploads`.
- Elasticsearch: `localhost:9201`, username `elastic`, password `123456`.
- Frontend dev server: `127.0.0.1:5173` from `frontend/vite.config.ts`.
- Frontend test API base URL: `http://localhost:8081/api/v1`.
- WebSocket base URL in frontend test env: `ws://localhost:8081`.

There is no `src/main/resources/application-docker.yml` in this checkout. Do not document or rely on that file unless it is added later.

## Development Commands

Run from the repository root unless noted.

### Backend

```powershell
mvn "-Dmaven.test.skip=true" spring-boot:run "-Dspring-boot.run.profiles=dev"
mvn "-Dmaven.test.skip=true" process-resources
mvn test
mvn "-Dtest=ClassName" test
```

Use `process-resources` after editing Spring config so `target/classes/application-dev.yml` matches `src/main/resources/application-dev.yml`.

### Frontend

```powershell
Set-Location frontend
pnpm install
pnpm dev
pnpm typecheck
pnpm build:test
```

The normal Vite dev port is `5173`. If port behavior looks wrong, inspect `frontend/vite.config.ts` and the Windows excluded port ranges before assuming a process conflict.

### Docker Middleware

The dev profile currently matches `docs/docker-compose.yaml` better than `docs/docker-compose.local-middleware.yml`.

```powershell
docker compose -f docs/docker-compose.yaml config
docker compose -f docs/docker-compose.yaml up -d
docker compose -f docs/docker-compose.yaml ps
```

Expected containers include `smartask-mysql`, `smartask-redis`, `smartask-kafka`, `smartask-minio`, and `smartask-es`.

## Verification Habits

- For startup work, verify the full local chain instead of stopping at container health: backend reachable, frontend reachable, login/API path works, and Elasticsearch/index access works when relevant.
- For config/auth failures, compare source config, compiled resources under `target/classes`, and active environment variables before changing code.
- For Docker config edits, run `docker compose -f docs/docker-compose.yaml config`.
- For backend edits, prefer the smallest relevant Maven command first; full `mvn test` may expose unrelated or existing test failures.
- For frontend edits, run targeted checks first, then `pnpm typecheck` or `pnpm build:test` when the change affects shared behavior.

## Known Project Details

- `src/main/resources/es-mappings/knowledge_base.json` defines `vector` as a 2048-dimensional `dense_vector` with cosine similarity.
- `HybridSearchService` is the main retrieval path for chat context.
- `ChatWebSocketHandler` uses JWT in the WebSocket path `/chat/{jwtToken}`.
- Private organization tags use the `PRIVATE_` prefix. Preserve that behavior when editing org-tag validation or profile code.
- AI generation config is under `deepseek.api.*`; embedding config is under `embedding.api.*`. Embedding success does not prove chat client config is correct because they use different client paths.

## Working Rules

- Preserve user changes. Check `git status --short` before edits and do not revert unrelated modifications.
- Keep changes narrow and aligned with the existing Spring/Vue patterns.
- Do not commit secrets or local runtime artifacts. Be careful with `.env`, `application*.yml`, logs, `target/`, `node_modules/`, and generated frontend build output.
- Prefer evidence from current files and runtime checks over README assumptions. This repository has had stale docs and generated resources before.
- In this Windows PowerShell environment, if broad `rg` searches fail or time out, switch to narrower `Get-ChildItem` / `Select-String` searches.
