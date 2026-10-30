-- TEST FIXTURE ONLY: isolated local PostgreSQL, never run on Supabase.
-- Provides only the SQL interfaces required to exercise the RLS policy script.
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='anon') THEN CREATE ROLE anon; END IF;
 IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname='authenticated') THEN CREATE ROLE authenticated; END IF;
END $$;
CREATE SCHEMA IF NOT EXISTS auth;
CREATE SCHEMA IF NOT EXISTS realtime;
CREATE OR REPLACE FUNCTION auth.uid() RETURNS uuid LANGUAGE sql STABLE AS
 $$ SELECT nullif(current_setting('request.jwt.claim.sub',true),'')::uuid $$;
CREATE OR REPLACE FUNCTION realtime.topic() RETURNS text LANGUAGE sql STABLE AS
 $$ SELECT current_setting('realtime.topic',true) $$;
CREATE TABLE IF NOT EXISTS realtime.messages(id bigint GENERATED ALWAYS AS IDENTITY, extension text);
ALTER TABLE realtime.messages ENABLE ROW LEVEL SECURITY;
GRANT USAGE ON SCHEMA public,auth,realtime TO authenticated,anon;
GRANT SELECT,INSERT ON realtime.messages TO authenticated;
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_publication WHERE pubname='supabase_realtime') THEN CREATE PUBLICATION supabase_realtime; END IF;
END $$;
