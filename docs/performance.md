# Performance and hosting

The app can keep React/Vite and Spring Boot. The deployed configuration uses Render
Free in Singapore and a Supabase pooler endpoint in Seoul. Sequential database
queries therefore add network latency. Moving the API near the existing database
can help; changing a hostname does not move the database.

## Changes

- V6 adds a persisted settlement cursor. Each deadline is processed once instead
  of recalculating all previous rounds on every ticker cycle. A missed tick catches
  up all newly ended rounds. Scores and correct answers remain hidden until the
  deadline.
- Profile reads for existing users no longer acquire the global game lock.
- Match responses fetch the submitted answer with the question and timestamp the
  response after the database work.
- Realtime events and question boundaries reuse an in-flight refresh rather than
  repeatedly discarding it. Polling remains a fallback.
- Arena navigation reuses the start/join response. Chapter lists stay fresh for
  five minutes, JSON compression is enabled, and CORS preflight is cached.

Deploy the backend and frontend commits. Flyway runs V6 on backend startup. Old
frontend versions remain compatible with the extra database field.

## Limits and next steps

Render Free sleeps after 15 minutes without inbound traffic; startup can take
about a minute. These code changes cannot remove that hosting behavior. Avoid
artificial keep-alive traffic as a performance solution.

The ticker still serializes settlement with other game writes for scoring safety.
If concurrency grows, measure lock wait time and move to per-match transactions
with a consistent locking order for shared player ratings/XP. Measure actual
authenticated match requests before promising a particular response time.

## Free alternatives

- Oracle Cloud Always Free Ampere VM: can run the existing Docker backend and
  background ticker. Requires VM administration, TLS, and an ARM image build.
  Free capacity may be unavailable and idle instances may be reclaimed. Check
  the current allowance and choose a region near the database where available.
  https://docs.oracle.com/en-us/iaas/Content/FreeTier/freetier_topic-Always_Free_Resources.htm
- Google Cloud Run: has a monthly free allowance, with charges above it and for
  other billable resources. Request-based CPU and scale-to-zero do not guarantee
  the background ticker keeps running. Adapt settlement to requests or use a
  suitable worker/instance billing configuration before migrating; keeping an
  instance running is not guaranteed free.
  https://cloud.google.com/run/pricing
  https://docs.cloud.google.com/run/docs/configuring/billing-settings

Keep the Vercel frontend and Supabase database unless measurements identify an
additional problem there. The backend can be moved independently by updating
VITE_API_BASE_URL and redeploying the frontend, with CORS_ORIGINS allowing the site.

Render's free-service limits: https://render.com/docs/free

## Verification

Run `npm run build` in frontend and Maven tests in backend. The legacy H2 test
profile cannot execute the existing V5 PostgreSQL `ON CONFLICT(id)` migration.
Use a dedicated disposable local PostgreSQL database, overriding
`spring.datasource.url`, `spring.datasource.username`, and
`spring.datasource.password` on the Maven command line. Never point these tests
at production. The integration suite includes a regression for boundary question
availability, no repeated settlement writes, and catching up missed deadlines.
