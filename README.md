# PLAYBIT

**Play Smart. Compete Hard. Level Up.**

A competitive study companion built for MNIT Jaipur’s AI Study Companion problem statement. React + TypeScript / Vite on the frontend; Java 21 / Spring Boot on the backend; Supabase Auth, PostgreSQL, and participant-authorized Realtime. Original branding and CSS/SVG illustrations. No static leaderboard entries, invented player metrics, or browser-authoritative scores.

## Start here

The source is implemented. The Supabase URL and user-supplied public anon key are configured in the gitignored `frontend/.env.local`; the public Auth settings endpoint confirmed email sign-in is enabled. **The backend still needs the database password and connection details** from the Supabase **Connect** panel. No AI provider key is configured; AI assistance will use the verified deterministic fallback. Never put database credentials in frontend variables.

1. Copy `.env.example` to `.env` in the repository root. Supply `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` from your Supabase **Connect** panel. Use the direct connection or **session** pooler with SSL; transaction pooling is unsuitable for migrations. The backend role needs migration privileges and access through RLS (the project database owner works for a hackathon).
2. Supply `VITE_SUPABASE_ANON_KEY` (the existing public anon/publishable key) and retain the supplied project URL. The backend **never** uses an anon key as its signing secret.
3. The supplied project's public JWKS endpoint was checked and currently publishes an ES256 key, so the default asymmetric verification configuration matches it. Check Supabase **Authentication → Signing Keys** again when deploying. Only if the project switches to legacy HS256, set the backend-only `SUPABASE_JWT_SECRET` to its actual JWT secret. Both modes validate issuer, expiry, audience `authenticated`, role, and signature.
4. In Supabase Auth, enable email/password sign-up, configure email confirmation as desired, and add `http://localhost:5173/play` (and the production equivalent) to redirect URLs. Set the site URL to your frontend origin.
5. Start the backend. Flyway baselines at version 0 if Supabase has installed helper functions in the otherwise empty `public` schema, then installs schema, seed data, and automatic browser lockdown. **Then run `database/supabase-security.sql` in Supabase SQL Editor** to authorize participant match notifications and private presence. Leave the questions, attempts, XP, and reports tables inaccessible to browser queries.
6. Start the frontend and register a real user. Complete the profile and subject placement. Play a bot round and inspect the stored report.

## Deploy the web app on Vercel

Vercel serves the Vite frontend. The Java Spring Boot API is a separate service and must be deployed to a host that can run the backend container; pointing a browser at `localhost` will only point back to that visitor’s own computer.

1. In Vercel, connect this GitHub repository and set the project **Root Directory** to `frontend`.
   The included `frontend/vercel.json` rewrites client-side routes such as `/auth` and `/play` to the Vite app, so direct links and page refreshes work.
2. In **Settings → Environment Variables**, add these variables to **Production** and **Preview**:
   - `VITE_SUPABASE_URL` — the Supabase project URL.
   - `VITE_SUPABASE_ANON_KEY` — the Supabase anon/publishable key. This is a browser-visible public key; never use a service-role key here.
   The included Vercel rewrite proxies `/api/*` to the Render service, so leave `VITE_API_BASE_URL` unset in Vercel. For a direct browser-to-backend connection instead, set it to the deployed backend’s HTTPS URL ending in `/api` and configure backend CORS.
3. Deploy the backend separately using `backend/Dockerfile` or the included `render.yaml` Blueprint. Configure its `DATABASE_JDBC_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and `SUPABASE_URL`. The Render Blueprint sets the JDBC URL and Supabase pooler username for this project; enter only the database password in Render. Keep database credentials only on the backend host.
4. In Supabase **Authentication → URL Configuration**, set the production Site URL to the Vercel origin and add `https://your-project.vercel.app/play` to the redirect URL allowlist. Add the corresponding Preview URL patterns if preview deployments need email confirmation redirects.
5. Redeploy the frontend after adding or changing the variables. Vite embeds `VITE_` values during the build, so changing them does not update an existing deployment.

If Supabase configuration is missing from a production build, the sign-up page explains which Vercel variables to add. If the backend URL is missing, API requests report the missing `VITE_API_BASE_URL` instead of silently trying the visitor’s `localhost`.

### Docker (Java 21 runtime)

```sh
docker compose --env-file .env up --build
```

Open `http://localhost:5173`. The frontend proxies `/api` to the Java container. This uses your configured Supabase database; it does not create a fake local production database. Restart/rebuild the frontend after changing its public configuration.

### Native development

Prerequisites: Node 22+, Java **21** (newer JDKs can compile with the configured Java 21 release target), Maven 3.9+, and a configured Supabase project.

```powershell
# Terminal 1, repository root. Loads the backend values from .env without printing them.
.\scripts\start-backend.ps1

# Terminal 2
cd frontend
npm ci
# Create frontend/.env.local with ONLY VITE_SUPABASE_URL,
# VITE_SUPABASE_ANON_KEY, and VITE_API_BASE_URL=http://localhost:8080/api
npm run dev
```

On macOS/Linux export the backend environment variables, then run `mvn -f backend/pom.xml spring-boot:run`. The app is at `http://localhost:5173`; health is at `http://localhost:8080/api/health`. An unconfigured frontend still displays the landing page and precise setup instructions; gameplay always requires verified authentication and the backend.

## Implemented experience

- Email signup/login/logout and persisted Supabase sessions; protected routes; profile editing; eight original avatar variations; three subjects and nine topics.
- Per-subject, one-time five-question diagnostic. Self-reported confidence affects placement difficulty; correct answers determine the provisional rating. Other subjects retain their independent assessment state.
- Timed bot, solo, recovery, real friend-duel, and skill-based ranked matchmaking flows. Rooms have six-character invitation codes, two-player ready checks, shared questions, cancellation while waiting, and REST recovery after refresh.
- Byte is explicitly a simulated opponent, with deterministic per-match delay and probability. Its score is computed on the server. No expensive LLM calls during matches.
- Persistent question IDs, SHA-256 normalized fingerprints, parameter variation, topic balance, and avoidance of each participant's last 150 attempted questions. If a finite template domain is exhausted, the oldest-used verified bank question is a fallback; no question repeats within a match. This minimizes recent repetition without claiming zero repetition.
- Match history, profile totals, XP/levels, topic mastery, paginated XP/subject leaderboards, complete stored reports, formulas, worked solutions, and fresh three-question recovery challenges.
- Optional AI selects revision priorities and explanation emphasis from actual reports. All displayed math comes from verified generators. Provider failures return explicit verified fallbacks. Administrator-only AI question generation proposes template specifications, validates them, and returns **unpublished** candidates for review; unrestricted AI prose is not admitted to ranked games.

## Rules and architecture

Each normal round has 5 questions at 15 seconds each, after a 3-second countdown. Recovery uses 3 questions. All questions are generated and assigned before the game starts. Correct answers earn `100 + floor(20 × (1 − serverElapsedMs / 15000))`; wrong and missed answers earn zero. Future questions and correct answers are never in the active question DTO. Answer acceptance reveals neither correctness nor points. Player scores, correctness, and answers are released only after each question's deadline. Reports unlock after the entire match completes.

The database stores exact per-question start/deadline timestamps. A one-second backend scheduler finalizes rounds, timeouts, bot activity, and abandoned matches. REST polling is a reconnect and Realtime fallback; the client never sends authoritative scores. Supabase listens only to RLS-protected `matches` updates, then retrieves state from the authenticated backend. Private presence is informational only.

Database transactions, unique answer keys, unique XP source keys, a conditional completed transition, and a database lock coordinate all gameplay writers. **The MVP deliberately serializes mutations using one `system_locks` row**, including across multiple backend processes. This prevents double pairing, answer races, and duplicate awards at hackathon scale. Replace it with ordered participant/match locks before scaling to substantial traffic. The scheduler runs in the same transaction boundary.

XP: game completion +30, correct answer +10, win +50, recovery completion +20 extra. Placement awards no XP. Bot XP is limited to ten rewarded games per UTC day. Values are configurable in `application.yml` or corresponding `PLAYBIT_XP_*` environment variables. A unique ledger prevents replay. Level L begins at `25 × (L−1) × (L+2)` XP: 0, 100, 250, 450, 700…

Ranked human games use subject-specific Elo, K=32. Bot, solo, recovery, and private duels leave ranked Elo unchanged. Placement is `clamp(confidenceBase + 80 × (correct−3), 600, 1400)`, with bases 800/1000/1200. Ratings are game estimates, not validated academic ability measures. A subject remains provisional until five ranked matches; only then is it eligible for that subject leaderboard. XP rankings include onboarded players, with stable ID tie breaks and 20-player pages. The current player's rank is returned independently of the page.

Mastery is an exponential moving score, 80% previous and 20% latest evidence; a fast correct response targets 100, a slower correct response 85, and an incorrect/missed response 0. Adaptive mode uses subject rating, topic mastery, and the last ten attempts' correctness and response time: low mastery/accuracy selects easy; sustained high mastery and fast accuracy selects hard; rating sets the remaining baseline. Recovery targets the lowest-accuracy topic from its source report. Each source match creates at most one recovery session; repeat requests resume it.

## API map

All routes except health and subject catalog require `Authorization: Bearer <Supabase access token>`.

| Area | Routes |
|---|---|
| Profile | `GET/PUT /api/me`, `GET /api/profile/{userId}` |
| Catalog | `GET /api/subjects`, `GET /api/subjects/{id}/topics` |
| Placement | `POST /api/diagnostics/start`, `POST /api/diagnostics/{id}/answers`, `GET /api/ratings/me` |
| Games | `POST /api/matches/bot`, `POST /api/matches/solo`, `GET /api/matches/{id}`, `POST /api/matches/{id}/answers`, `GET /api/matches/{id}/results` |
| Rooms | `POST /api/rooms/create`, `/api/rooms/join`, `/api/rooms/{id}/ready`, `/api/rooms/{id}/leave` |
| Matchmaking | `POST /api/matchmaking/join`, `/api/matchmaking/cancel` |
| Progress | `GET /api/progress`, `/api/mastery`, `/api/matches/history?page=0` |
| Rankings | `GET /api/leaderboards/overall?page=0`, `/api/leaderboards/subject/{subjectId}?page=0` |
| Learning | `GET /api/reports/{matchId}`, `POST /api/recovery/start`, `/api/ai/hint`, `/api/ai/reports/{matchId}`, `/api/ai/questions/generate` |

Start body: `{"subjectId":"math","topicId":null,"difficulty":0}` (0 adaptive, 1/2/3 fixed). Answer body: `{"questionId":"uuid","answer":"one of the four option strings"}`. Join: `{"code":"ABC234"}`. Recovery: `{"matchId":"uuid"}`. Hint: `{"matchId":"uuid","questionId":"uuid"}`. AI hints are available **only after match completion**. Question generation is restricted to UUIDs in `AI_ADMIN_IDS`.

Matchmaking join is also its heartbeat: poll every two seconds while searching. Expired entries are removed after 20 seconds. Tolerance begins at ±150, widens by 50 per ten seconds up to 600, and must satisfy both players. Matched queue records return the existing match on retries and are cleared at completion. A late cancellation returns the already-created match instead of silently losing a rival.

## Tests

```sh
mvn -f backend/pom.xml test
cd frontend
npm ci
npm run build
npx playwright install chromium
npm run test:e2e
```

The API suite uses an isolated H2 database in PostgreSQL mode and a controllable clock. Signed JWT tests run through the real security chain; most scenario fixtures use Spring Security's test JWT support. Engine tests verify generated arithmetic/options across all topics, timing, levels, and Elo. Browser tests have a public landing/mobile/auth-guard check and optional real API scenarios against a separate PostgreSQL database. See `docs/TESTING.md` for exact setup and executed results.

## Deployment

Deploy the Java container on a host with Java 21 or container support (not a static-only site host). Provide backend secrets in that host's secret manager. Run a single backend instance for this MVP unless intentionally sharing the database lock. Deploy the frontend Docker image or Vite `dist/` to static hosting with SPA fallback. If hosting frontend separately, set `VITE_API_BASE_URL` to the HTTPS backend `/api` URL and configure `CORS_ORIGINS` to the exact frontend origin. Use HTTPS, preserve Supabase email redirects, apply the Realtime authorization script, and run the live demo checklist before the hackathon.

Never copy `.env`, `.tools`, database data, or test JWTs into the production image. Docker ignores local environment files. Java 21 release output and Java 21 Docker runtime are configured. Backend entities use JPA for subject lookup and parameterized JDBC for transactional gameplay; this is one modular application, not microservices. Flyway's classpath migrations are canonical; `database/migrations/001_schema.sql` and `database/seed.sql` are reviewable copies, not scripts to run before Flyway.

## Practical limits

- Supabase account creation/email, the actual project's JWT signing configuration, hosted database writes, Realtime presence/events, and a live AI provider need the missing credentials and a live verification run. No deployment is claimed.
- Questions and verified explanations currently use English. The profile stores Hindi as a preference, but a translated bank is not implemented.
- AI uses a conservative verified-template workflow. It does not provide unrestricted generated tutoring prose or silently publish free-form questions. With no AI key, deterministic explanations and recovery remain fully functional.
- Public profile statistics are limited to gameplay fields. Authentication secrets stay server-side. This MVP has no admin UI, password reset UI, moderation, or sophisticated anti-collusion system.
- The bot cap protects basic XP farming; solo/recovery are educational practice and have no daily XP cap. A repeated recovery request cannot award duplicate XP, but successive completed games can produce further recovery sessions.
- Match history/progress charts show the most recent 20 rounds; the history API supports pagination. XP history returns the last 30 events. A timeout counts as an incorrect attempt with 15 seconds response time. Overall profile win percentage uses all non-diagnostic games as its denominator.

## Reference documentation

[Spring Security JWT resource server](https://docs.spring.io/spring-security/reference/servlet/oauth2/resource-server/jwt.html) · [Supabase database-change subscriptions](https://supabase.com/docs/guides/realtime/postgres-changes) · [Matiks, conceptual inspiration only](https://matiks.com/)
