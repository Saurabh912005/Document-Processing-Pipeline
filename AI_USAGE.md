# AI Usage

## Tools

- **Cursor (Claude-based agent)** — primary tool for scaffolding the repository, implementing backend/frontend code, Docker setup, tests, and documentation from the take-home specification.

## What AI was used for

- Generating the Spring Boot domain model, repositories, async orchestration, retry/validation logic, REST controllers, and integration tests.
- Generating the React (Vite) UI pages (upload, list, detail, dashboard), API client, CSS layout, and a small Vitest component test.
- Drafting `README.md`, `docs/architecture.md`, and this file.
- Debugging test failures (multipart parameter binding, H2 vs Testcontainers in Dockerized Maven runs, test isolation with `@Transactional`).

## Accepted vs changed

- **Accepted:** Monolith + `@Async` thread pool (no Kafka/RabbitMQ), human-readable `DOC-xxxxx` IDs via DB sequence, duplicate uploads return **200** with `duplicate: true`, validation failures map to **`FAILED` + `VALIDATION_FAILED`** with field errors on `ExtractedResult`.
- **Changed:** Default integration tests run on **H2 (MySQL mode)** for CI/agent environments where Testcontainers cannot reach Docker; optional `MySQLTestcontainersIntegrationTest` is `@Disabled` and documented for local Docker runs.
- **Rejected / not implemented:** PDF iframe preview (timeboxed; detail page shows extracted fields and timeline only).

## Review notes

- AI-generated retry loops needed a separate `DocumentProcessingStateService` so `@Transactional` boundaries work (self-invocation on `@Async` beans).
- Upload tests initially failed because `documentType` must be a form field (`@RequestParam`), not a multipart part — caught by running tests, not by inspection alone.

## Learnings

- Structured log keys (`documentId`, `attempt`, `status`, `reason`) make grep-based lifecycle reconstruction practical.
- Hash-based dedup still needs a **unique DB constraint** plus handling `DataIntegrityViolationException` for concurrent identical uploads.
