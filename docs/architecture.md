# Architecture

```mermaid
flowchart LR
  UI[React UI] -->|HTTP /api| API[Spring Boot API]
  API -->|JPA| DB[(MySQL)]
  API -->|save bytes| FS[Local file volume]
  API -->|@Async| POOL[Thread pool executor]
  POOL --> EXT[DocumentFieldExtractor]
  EXT --> VAL[ExtractedDataValidator]
  VAL -->|status + result| DB
  POOL --> HIST[DocumentHistoryEvent rows]
  HIST --> DB
  SCHED[Stuck recovery @Scheduled] --> POOL
```

## Request flow

1. **Upload** — `POST /api/documents` stores the file on disk, computes SHA-256, deduplicates on `file_hash`, persists `Document` as `UPLOADED`, appends history, and enqueues async processing.
2. **Processing** — `@Async` worker transitions to `PROCESSING`, extracts fields from the stored file, validates extracted fields, retries I/O failures with backoff, and writes `ExtractedResult` + history events.
3. **Read APIs** — list/detail/history endpoints serve UI polling and dashboards.
