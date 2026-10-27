# Paddock AI — Frontend (Phase 1 scaffold)

Angular 20 standalone app, scaffolded for the full roadmap. This slice only
wires the "hello" round trip (dashboard page + a health check call to the
backend) plus the module shape the later phases will fill in.

## Structure

```
src/app/
  core/
    services/health.service.ts     # GET /api/health
    services/auth.service.ts       # stub — real JWT storage lands Phase 5
    interceptors/auth.interceptor.ts  # wired, no-op until AuthService has a token
  shared/components/
    nav-bar/                       # top nav — Dashboard / Chatbot / Sign in
    footer/
  dashboard/                       # home page: connection status + phase cards
  chat/                            # placeholder — LangChain4j chat UI, Phase 4
  auth/login/                      # placeholder — JWT login form, Phase 5
```

`environments/environment.ts` points at `http://localhost:8080` for local dev
against the Spring Boot backend; `environment.prod.ts` assumes the backend is
reverse-proxied at `/api`.

## Theme

F1 brand red `#e10600` on near-black `#15151e`, Titillium Web (Google Fonts,
linked in `index.html`) as the free stand-in for F1's licensed typeface.
Tokens live in `src/styles.scss` as CSS variables (`--f1-red`, `--f1-black`,
`--f1-white`, `--f1-white-dim`, `--font-display`, `--font-body`).

## Run it

```bash
npm install
npm start          # ng serve, http://localhost:4200
```

The dashboard will show "Backend unreachable" until the Spring Boot backend
(Phase 1, backend half) is running on port 8080 with CORS open for
`http://localhost:4200` and a `GET /api/health` endpoint returning:

```json
{ "status": "ok", "service": "f1-chatbot-backend", "timestamp": "..." }
```

## Build

```bash
npm run build       # production build → dist/frontend
```

Note: `angular.json` has `optimization.fonts: false` in the production
config. Angular normally inlines Google Fonts at build time, which requires
build-time internet access to fonts.googleapis.com; disabling it keeps CI/
restricted-network builds working. The `<link>` tag in `index.html` still
loads the font fine in the browser at runtime — only the build-time inlining
optimization is skipped.

## Next slices

- Backend: Spring Boot skeleton + `/api/health` + CORS (Phase 1, other half)
- docker-compose: Postgres (+pgvector) wiring both services together
- Phase 2+: data foundation, F1 REST API, chatbot, auth, realtime, charts
