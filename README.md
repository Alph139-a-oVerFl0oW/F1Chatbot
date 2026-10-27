# f1-chatbot

Full-stack F1 assistant: Angular frontend, Spring Boot backend, Postgres + pgvector,
LangChain4j chatbot, and live timing from OpenF1.

## Architecture

```
Angular (nginx) ──REST/WS──► Spring Boot ──► Postgres + pgvector
                                  │
                                  ├──► LLM (GPT / Gemini)
                                  └──► Jolpica-F1 (history) + OpenF1 (live timing)
```

## Layout

```
frontend/   Angular 20 SPA (chat, dashboards, auth)
backend/    Spring Boot 3.5 / Java 21
  common/       health, CORS, security config
  auth/         Phase 5 - JWT, users, favorites
  f1data/       Phase 3 - races/drivers/standings REST API
  chatbot/      Phase 4 - LangChain4j agent + tools
  ingestion/    Phase 2 - Jolpica-F1 backfill + scheduled updates
  realtime/     Phase 6 - OpenF1 poller + WebSocket
infra/      docker-compose, Postgres bootstrap
data/       archived JSONL corpus, eval sets (not in git)
```

## Running it

Requires Docker Desktop (or Docker Engine + compose plugin).

```bash
cp .env.example infra/.env      # compose reads .env from its own directory
cd infra
docker compose up --build
```

| Service  | URL                            |
|----------|--------------------------------|
| Frontend | http://localhost:8081          |
| Backend  | http://localhost:8080/api/health |
| Postgres | localhost:5432 (f1app/f1app)   |

Expected health response:

```json
{"status":"UP","service":"f1-backend","timestamp":"...","database":"UP"}
```

### Day-to-day development

Run only the database in Docker and the two apps natively — much faster feedback loop:

```bash
cd infra && docker compose up -d postgres
cd ../backend && ./mvnw spring-boot:run      # or: mvn spring-boot:run
cd ../frontend && npm start                  # http://localhost:4200
```

The Angular dev server talks to `http://localhost:8080` directly (see
`src/environments/environment.ts`); CORS for `:4200` is already allowed.

### Useful commands

```bash
docker compose logs -f backend      # tail backend logs
docker compose up --build backend   # rebuild one service
docker compose down                 # stop
docker compose down -v              # stop + wipe the Postgres volume
```

## Database

Flyway owns the schema — `backend/src/main/resources/db/migration`. Hibernate is set
to `ddl-auto: validate` and will never create or alter tables. Add a new
`V2__*.sql` to change the schema; never edit a migration that has already run.

## Roadmap

| Phase | What | Status |
|-------|------|--------|
| 1 | Backend skeleton + docker-compose | done |
| 2 | Postgres schema + Jolpica-F1 backfill | next |
| 3 | F1 data REST API | |
| 4 | Chatbot v2 (LangChain4j + pgvector) | |
| 5 | Auth + favorites (JWT) | |
| 6 | Real-time (OpenF1 poller + WebSocket) | |
| 7 | Charts + theme polish | |
| 8 | Deploy (ECS/Fargate + RDS) | |

### Evaluation (Phase 4b)

9/9 on a gold set generated from Postgres, covering race winners, both
championships, career stats, pole positions, season lengths, circuit
locations, and negatives where the correct answer is "that didn't exist".

The harness found three real defects that manual testing had missed:

- **Hallucination despite grounding** — asked who won the 1982 German GP, the
  agent reported Pironi (plausible: Ferrari driver, championship leader, career
  ended that weekend). The database correctly held Tambay. Fixed with a stricter
  system prompt and an authoritative-result marker in the tool output.
- **`findDriver` failed on full names** — the query matched against given name
  or family name separately, so "Alberto Ascari" matched neither. Intermittent,
  because it depended on what the model chose to send.
- **Prompt overhead** — nine verbose tool schemas cost ~1,700 tokens on every
  call, twice per question, against a 12,000 TPM free-tier ceiling. Trimming
  descriptions and capping result rows cut that 60% with no measured accuracy loss.