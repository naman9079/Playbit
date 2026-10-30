# Verification and local browser tests

This project distinguishes local verification from live Supabase integration. No live Supabase credential was available during implementation.

### Results from this implementation run

- Backend: **14 tests passed** (9 API integration scenarios, 5 engine scenarios; zero failures or errors) on Java 21. API scenarios used the real Spring security filter chain and H2/PostgreSQL compatibility mode.
- Frontend: `npm run build` succeeded; generated split bundles for charts, motion, Supabase, and the main app. npm reported zero known dependency vulnerabilities at install time.
- Browser: **3 Playwright tests passed** against Java REST plus a separate local PostgreSQL 18.2 database. The bot and placement plus recovery flow took about 3.7 minutes; the separate-browser friend duel took about 1.5 minutes. Reload preserved match answers, and both players received the same questions.
- Database: all three Flyway migrations applied on the isolated PostgreSQL database. The Supabase RLS SQL applied against local Supabase interface fixtures; the follow-up query verified participant reads, denial to a stranger, no question/attempt/XP/report grants, and no browser write grants.
- Visual QA: desktop and 390-pixel mobile landing-page screenshots were reviewed. The mobile hero text was adjusted to avoid overlap with the illustration.
- Real user signup/login, remote database writes, Realtime, AI-provider, and deployment checks remain unrun. The database password and AI key are unavailable; the SQL fixture cannot establish behavior of Supabase's hosted Realtime authorization service.
- The supplied Supabase project's public JWKS endpoint is reachable without credentials and currently advertises ES256. This confirms the backend's default signature algorithm and JWKS URL shape; it does not verify an actual user's access token, database access, RLS installation, or Realtime delivery.
- The user-supplied public anon key is configured in the gitignored `frontend/.env.local`. A request to `/auth/v1/settings` with that key succeeded and reported email sign-in enabled. This verifies public Auth connectivity/configuration only; no user was created and no database password or secret was used. The frontend production build succeeds with this configuration.
- On the supplied live project, the session-pooler connection was authenticated successfully. Read-only inspection found no tables in `public`, but found Supabase's `rls_auto_enable()` helper function, which causes Flyway to regard the schema as non-empty. Flyway therefore baselines at version 0; this preserves the function and still applies V1–V3. The first migration attempt failed before any schema changes, so rerun Flyway after this configuration change.

## Backend tests

`mvn -f backend/pom.xml test` runs the generated arithmetic, validation, scoring, level, placement, Elo, authentication, authorization, profile, timed answer, duplicate submission, report, recovery, XP cap, matchmaking, room, reconnect, and leaderboard scenarios.

The integration database is H2 in PostgreSQL mode. Most scenario identities use Spring Security test JWT principals; the authentication test verifies signed HS256 tokens through the actual production decoder and rejects missing, forged, expired, and wrong-audience tokens. Clock control makes deadline tests deterministic. Production has no test bypass endpoint or test authentication profile.

## Browser suite against real local PostgreSQL

Use a **separate** local PostgreSQL cluster. The setup below was used on this workstation; adapt binary paths on another machine. These fixtures must never run against a real Supabase project. The test cluster uses loopback-only trust authentication and contains synthetic test users. Local JWT signing uses an explicitly test-only key; no live user session is involved.

1. Initialize a fresh cluster in `.tools/pg-test`, start it on `127.0.0.1:55432`, create database `playbit` owned by `playbit_test`.
2. Run the Java backend with:

```powershell
$env:DATABASE_URL='jdbc:postgresql://127.0.0.1:55432/playbit'
$env:DATABASE_USERNAME='playbit_test'
$env:DATABASE_PASSWORD=''
$env:SUPABASE_URL='http://127.0.0.1:54321'
$env:SUPABASE_JWT_SECRET='local-e2e-only-never-use-in-production-0123456789'
$env:CORS_ORIGINS='http://127.0.0.1:5173'
$env:PORT='18080'
mvn -f backend/pom.xml spring-boot:run
```

3. Launch Vite in a second terminal with **local-only test** configuration:

```powershell
$env:VITE_SUPABASE_URL='http://127.0.0.1:54321'
$env:VITE_SUPABASE_ANON_KEY='local-test-public-placeholder'
$env:VITE_API_BASE_URL='http://127.0.0.1:18080/api'
cd frontend
npm run dev
```

No Supabase service runs at 54321. Tests seed Supabase SDK session storage with valid local JWTs and use the **real Java REST API and real PostgreSQL writes**. Realtime is unavailable in this fixture, exercising REST recovery. Signup/email and actual Supabase Realtime require a separate live integration check.

4. In a third terminal:

```powershell
cd frontend
$env:E2E_JWT_SECRET='local-e2e-only-never-use-in-production-0123456789'
$env:E2E_PSQL='C:\Program Files\PostgreSQL\18\bin\psql.exe'
npx playwright install chromium
npm run test:e2e
```

The public test checks desktop/mobile layout and the authentication guard. The full learning-loop test completes placement, a bot match, a persistent report, and recovery using actual 15-second rounds. The friend test uses **two isolated browser contexts**, checks identical questions, refreshes after a submitted answer, and completes both reports. Only the friend test's initial placement state is inserted through SQL as a fixture. It never supplies scores or completed results. These full-round tests need about five minutes.

Without `E2E_JWT_SECRET`, authenticated browser scenarios are explicitly skipped. Without `E2E_PSQL`, the friend fixture is skipped. Do not report skipped tests as passes.

## SQL policy checks

After Flyway initializes the local database, run `scripts/test-supabase-roles.sql`, then `database/supabase-security.sql` with `psql -v ON_ERROR_STOP=1`. The first script supplies minimal local `auth.uid()` and `realtime.topic()` interfaces and database roles. It does not emulate Supabase's Realtime transport. After a test match completes, `scripts/check-rls.ps1` checks participant-only match reads, nonparticipant isolation, absent answer-table grants, and absent authoritative browser writes.

## Workstation-specific notes

- Port 8080 was already occupied by an unrelated process. Verification uses 18080 without modifying that process.
- Installed Java 23 and isolated Java 21 initially encountered Windows AF_UNIX `Invalid argument: connect` while creating NIO selectors. An isolated selector probe confirmed the TCP fallback works. On an affected Windows host only, set `PLAYBIT_WINDOWS_TCP_FALLBACK=1` in `.env` when using `scripts/start-backend.ps1`, or set `JAVA_TOOL_OPTIONS=-Djdk.net.unixdomain.tmpdir=../.tools/no-unix-sockets` before Maven. Keep that path nonexistent. This changes the selector's local wake-up transport, not HTTP authentication. No workaround is needed in the Java 21 Linux Docker image.
- PostgreSQL 18.2 is installed here. The bundled Flyway version warns that its tested support range ends at PostgreSQL 17; migration and transaction checks on the local 18.2 instance are recorded separately. Use your Supabase project's supported version in deployment.

## Live integration checklist (requires credentials)

- Register and confirm two real Supabase users; login, logout, reload, and refresh a session.
- Confirm whether the project uses ES256, RS256, or legacy HS256 and verify Spring accepts that real token.
- Complete placement, bot play, and recovery; inspect the resulting Supabase rows.
- Apply the Realtime policy script. Confirm both room participants receive match changes and private presence, while a third authenticated account is denied.
- Complete a friend duel in two browsers, reconnect mid-match, and check final stored reports.
- Configure an AI key if desired and verify the labeled AI-assisted path; remove it and verify the deterministic fallback.
- Build/deploy Java and frontend separately or through Compose, verify HTTPS, CORS, auth redirects, and SPA deep links.
