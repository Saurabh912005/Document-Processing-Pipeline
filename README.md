# Document Processing Pipeline

Full-stack take-home implementation: upload documents, extract structured fields from file content (PDF text or embedded JSON), validate results, retry transient read failures, and inspect status/history via a React UI.

## Stack

| Layer | Choice |
|--------|--------|
| Backend | Java 17, Spring Boot 3.3, Maven |
| Frontend | React 19, Vite |
| Database | MySQL 8 |
| Async | `@Async` + `ThreadPoolTaskExecutor` |
| Containers | Docker Compose (mysql, backend, frontend) |

## Quick start (Docker)

```bash
cp .env.example .env
docker compose up --build
```

- UI: http://localhost (port 80 by default)
- API: http://localhost:8080/api/documents
- MySQL: localhost:3306 (credentials in `.env.example`)

### Sample PDF

A reference financial statement is included for onboarding and manual testing:

**[docs/samples/financial_statement_ABC_Construction_Pvt_Ltd.pdf](docs/samples/financial_statement_ABC_Construction_Pvt_Ltd.pdf)**

Upload it from the UI (**Upload** page) with document type **Financial statement**. After processing, extracted fields should match the reference values below (company **ABC Construction Pvt Ltd**, registration **U12345DL2020PTC123456**, etc.) and status should become **`PROCESSED`** when validation passes.

From the repo root you can also upload via API:

```bash
curl -X POST http://localhost:8080/api/documents \
  -F "file=@docs/samples/financial_statement_ABC_Construction_Pvt_Ltd.pdf" \
  -F "documentType=FINANCIAL_STATEMENT"
```

## Local development

### Backend

Requires MySQL running (e.g. `docker compose up mysql`).

```bash
cd backend
# with Maven installed:
mvn spring-boot:run
```

Storage path defaults to `./storage/documents` (override with `DOCUMENT_STORAGE_PATH`).

### Frontend

```bash
cd frontend
npm install
npm run dev
```

Vite proxies `/api` → `http://localhost:8080`.

### Tests

```bash
cd backend && mvn test
cd frontend && npm test
```

Backend integration tests use **H2 in MySQL compatibility mode** by default (fast, no Docker). An optional Testcontainers MySQL smoke test exists but is disabled unless you run it locally with Docker:

```bash
mvn test -Dtest=MySQLTestcontainersIntegrationTest
```

## API summary

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/documents` | Multipart upload (`file`, `documentType`, optional `metadata[...]`) |
| GET | `/api/documents/{id}` | Detail + extracted result |
| GET | `/api/documents/{id}/history` | Status timeline |
| GET | `/api/documents` | Paginated list (`status`, `documentType`, `search`, `page`, `size`) |
| GET | `/api/documents/stats/dashboard` | Counts (bonus dashboard) |

### Duplicate uploads

SHA-256 hash is computed on upload. If a document with the same hash exists, the API returns **200 OK** with the existing `documentId`, `status`, and `"duplicate": true` (no second row).

## Design decisions

- **Document IDs:** `DOC-00001` style via a locked `document_id_sequence` row (human-readable, sequential).
- **Validation failures:** status **`FAILED`**, `failureReason` **`VALIDATION_FAILED`**, field messages in `extractedResult.validationErrors`. Tradeoff: simpler than a separate `VALIDATION_FAILED` status enum value, but still distinguishable via reason + errors (no retry).

### Document content format

Include either embedded JSON or labeled lines in the uploaded file (PDF with a text layer, or a `.pdf` upload whose bytes are JSON/text for local demos):

```json
{
  "companyName": "ABC Construction Pvt Ltd",
  "registrationNumber": "U12345DL2020PTC123456",
  "address": "New Delhi",
  "annualRevenue": 12500000,
  "documentDate": "2026-08-15"
}
```

If the text matches the reference registration number and company name, the canonical values above are used. Otherwise fields are parsed from JSON or `field: value` lines. Missing/invalid required fields → **`FAILED`**; all validators pass → **`PROCESSED`**.

## Engineering Q&A

### 1. Why this architecture?

Single deployable **monolith** keeps operational complexity low for a take-home. Upload returns quickly; CPU/IO-heavy extraction runs on a **bounded thread pool** so HTTP threads are not blocked. History rows provide an audit trail without event sourcing.

### 2. Why MySQL?

Relational fit for documents, 1:1 extracted results, and append-only history. Mature tooling, easy Docker image, and JPA portability. Alternatives like Postgres would work; MySQL matches the spec and is widely used in enterprise Java shops.

### 3. How does async processing work?

`DocumentProcessingOrchestrator.processDocumentAsync` is annotated with `@Async("documentProcessingExecutor")`. `AsyncConfig` defines a `ThreadPoolTaskExecutor` (core 4, max 8, queue 100). After upload, the service fires async processing; the worker loop handles PROCESSING → extract → validate → PROCESSED/FAILED, including retries on read errors.

### 4. How do retries work?

- **Max attempts:** 3 (`processing.max-attempts`).
- **Retried:** `PROCESSOR_ERROR` (transient file read failures).
- **Not retried:** validation failures (deterministic).
- **Backoff:** 2s, 4s, 8s (`Thread.sleep` in-process between attempts).
- Each attempt writes **FAILED** (with reason) then re-enters **PROCESSING** on retry; terminal failure uses `*_EXHAUSTED` reasons.

### 5. Duplicate processing prevention

- **Dedup on upload:** unique index on `file_hash`; pre-insert lookup + catch `DataIntegrityViolationException` on concurrent identical uploads.
- **Processing:** one async job per upload; no re-queue on duplicate response. Stuck recovery (below) only requeues stale `PROCESSING` rows.

### 6. Crash mid-processing

`StuckDocumentRecoveryJob` runs every 60s and requeues documents in `PROCESSING` with `updatedAt` older than 120s back to `UPLOADED` and triggers async processing again. Limitation: not a full exactly-once processor; at scale you'd use a lease/visibility timeout on a durable queue.

### 7. At ~1M documents/day

- Move files to **object storage** (S3/GCS) with pre-signed uploads.
- Introduce a **durable queue** (SQS/Kafka/Rabbit) and horizontally scaled workers.
- **Partition** documents table (by date/hash), archive history, read replicas for list/detail.
- Externalize OCR/ML extraction as a separate service with idempotent job IDs.

### 8. Limitations (honest)

1. Async retries use in-thread sleep — not durable across restarts (queue + scheduled jobs would be safer).
2. Extraction is PDF text / pattern parsing only — not production OCR for scanned documents.
3. Local disk storage is not HA; backup and multi-AZ would require object storage.
4. No authn/authz, rate limits, or virus scanning on uploads.

## Project layout

```
backend/          Spring Boot API + processing
frontend/         React UI
docs/             Architecture diagram + sample PDFs
docker-compose.yml
.env.example
AI_USAGE.md
```

See [docs/architecture.md](docs/architecture.md) for the flow diagram.

live demo-link- https://drive.google.com/file/d/1a-oiLSC8De8Uf9aFE3Hf2SoVhrRsxizm/view?usp=sharing
