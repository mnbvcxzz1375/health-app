# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

Health monitoring and analysis platform ("健康监测与分析平台") — a multi-service application for a health competition. The repo contains 5 services plus a fullstack frontend, all sharing a single MySQL database.

## Service Architecture

```
Browser (Vue 3 SPA, port 4173)
  │
  ├── /api/* ──────── Vite proxy ──→ backend-java (port 3302, Spring Boot 3.5, Java 17)
  │                      │             ├── MySQL (health_monitoring, Flyway migrations)
  │                      │             ├── DashScope/Qwen LLM (consult + medication recognition)
  │                      │             └── local_medication_api (port 8011, YOLO+OCR sidecar)
  │                      │
  └── /posture-api/* ── Vite proxy ──→ posture-backend (port 8080, Spring Boot 2.7, Java 8)
                                         ├── MySQL (shared DB)
                                         ├── Redis (job queue: posture:jobs)
                                         └── posture-inference-service (port 8000, MediaPipe)
```

**Authentication**: Stateless token-based in backend-java (`TokenAuthenticationFilter`). Tokens in `auth_sessions` table. Frontend stores token via Pinia auth store.

**Database**: Single MySQL database `health_monitoring` shared by both Java backends. Schema managed by Flyway migrations in `backend-java/src/main/resources/db/migration/`.

**AI integration**: DashScope (Alibaba Cloud) kimi-k2.5 model for health consultations and medication recognition. `local_medication_api` is a local YOLO+OCR alternative.

## Commands

### Full Stack

```powershell
# Start all services (Windows PowerShell)
.\start-local-stack.ps1
```

### Frontend (`健康监测与分析平台/`)

```bash
cd 健康监测与分析平台
npm run dev           # Dev server on port 4173 (Vite proxy: /api→3302, /posture-api→8080)
npm run build         # Production build
npm run typecheck     # TypeScript type checking
npm run test          # Run tests once (Vitest, jsdom)
npm run test:watch    # Watch mode
npm run test:coverage # Coverage report (v8)
```

Run a single test file: `npx vitest run src/modules/auth/LoginForm.spec.ts`

### backend-java (port 3302, requires Java 21)

```bash
cd backend-java
./mvnw.cmd clean spring-boot:run    # Build + run
./mvnw.cmd test                      # Run tests (@WebMvcTest + MockMvc)
```

### posture-backend (port 8080, requires Java 8)

```bash
cd posture-backend
./mvnw.cmd spring-boot:run
./mvnw.cmd test
```

### posture-inference-service (port 8000, Python/FastAPI)

```bash
cd posture-inference-service
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

### local_medication_api (port 8011, Python/FastAPI)

```bash
# From repo root
python -m uvicorn local_medication_api.app:app --host 127.0.0.1 --port 8011
```

### BFF Server (port 3301, Express.js)

```bash
cd 健康监测与分析平台/server
npm install && npm run dev
```

## Frontend Conventions

- **Framework**: Vue 3 + TypeScript + Vite + Tailwind CSS v4 + Pinia
- **Path alias**: `@` → `src/` (configured in tsconfig.json and vite.config.ts)
- **Module structure**: Feature modules in `src/modules/` (home, monitor, upload, medication, rehab, profile, auth, assistant, system), each with their own views/components/composables
- **API layer**: `src/api/http.ts` — Axios instance with auth interceptor for backend-java; `src/api/postureHttp.ts` — separate instance for posture-backend
- **State**: Pinia stores in `src/stores/` (auth, health, toast)
- **Routing**: Lazy-loaded routes with auth guards, module-based route files in `src/router/`
- **Charts**: ECharts 5.5
- **Tests**: Vitest + @testing-library/vue + @vue/test-utils. Test setup at `src/test/setup.ts` mocks ResizeObserver, matchMedia, scrollIntoView. Test files are co-located as `*.spec.ts`.

## backend-java Conventions

- **Package**: `com.ahealth.backend`
- **Module pattern**: Each domain module (auth, monitor, rehab, medication, etc.) has Controller/Service/Repository layers
- **Security**: `TokenAuthenticationFilter` for stateless auth, `SecurityConfig` for route authorization
- **API docs**: Swagger UI at `/swagger-ui.html` (SpringDoc OpenAPI)
- **Profiles**: `application-dev.yml`, `application-test.yml` (uses Testcontainers for MySQL in tests)

## posture-backend Conventions

- **Architecture**: Hexagonal (ports & adapters) — packages: `adapter/`, `api/`, `config/`, `domain/`, `port/`, `service/`
- **Async processing**: Video uploads create jobs in Redis (`posture:jobs`), processed by workers that call posture-inference-service
- **Package**: `com.atitai.posture`

## Python Services Conventions

- Both use FastAPI + Uvicorn
- `local_medication_api` has a vendored/modified ultralytics at `vendor/ultralytics/` (DINO-SO-YOLO project) — do not overwrite this directory
- Python dependencies managed via `requirements.txt` in each service directory

## Environment Variables

Configuration is driven by `.env` files (gitignored). Template files:
- `健康监测与分析平台/.env.example` — frontend env vars
- `健康监测与分析平台/server/.env.example` — BFF server (DB credentials, DashScope API keys)

## Important Notes

- **Java version conflict**: backend-java requires Java 21, posture-backend requires Java 8. The startup script `start-local-stack.ps1` handles this by pointing to specific Windows Java installations.
- **Development is Windows-native**: Maven wrapper uses `mvnw.cmd`, startup uses PowerShell. Adjust shell commands for Linux/macOS.
- **API documentation**: `健康监测与分析平台/API.md` (frontend API contract), `posture-backend/API_DOCUMENTATION.md`

## OpenMed AI Integration

The project integrates OpenMed's open-source medical models via HuggingFace Inference API:

- **PharmaDetect NER** (`OpenMed-NER-PharmaDetect-SuperMedical-125M`): Extracts drug entities from OCR text
- **PII De-identification** (`OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1`): Scrubs personal info before sending to LLMs
- **DDI Knowledge Base**: Drug-drug interaction checks from curated knowledge table

**Architecture:** `ModelRouterService` classifies intent → routes to optimal model → injects user context → returns result. All text passes through `PiiScrubService` before reaching any external model.

**Configuration:** Set `OPENMED_API_TOKEN` (or `HF_API_TOKEN`) env var. Models are configurable via `OPENMED_NER_MODEL` and `OPENMED_PII_MODEL`.
