-- Apply AFTER the backend Flyway migrations, using Supabase SQL Editor as postgres.
-- Browser clients only read participant-safe match notifications. All writes go through Spring.
DO $$ DECLARE t text; BEGIN
 FOREACH t IN ARRAY ARRAY['profiles','subjects','topics','user_subject_ratings','user_topic_mastery','questions','matches','match_players','match_questions','question_attempts','xp_events','reports','matchmaking_queue','recovery_claims','system_locks'] LOOP
  EXECUTE format('ALTER TABLE public.%I ENABLE ROW LEVEL SECURITY',t);
  EXECUTE format('REVOKE ALL ON public.%I FROM anon, authenticated',t);
 END LOOP;
END $$;
CREATE OR REPLACE FUNCTION public.is_match_participant(target uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public
AS $$ SELECT EXISTS(SELECT 1 FROM match_players WHERE match_id=target AND user_id=auth.uid()) $$;
REVOKE ALL ON FUNCTION public.is_match_participant(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.is_match_participant(uuid) TO authenticated;
GRANT SELECT ON public.matches TO authenticated;
DROP POLICY IF EXISTS participant_match_read ON public.matches;
CREATE POLICY participant_match_read ON public.matches FOR SELECT TO authenticated USING(public.is_match_participant(id));
-- matches contains no question answers. Updating version triggers a participant-authorized REST refresh.
DO $$ BEGIN
 IF NOT EXISTS(SELECT 1 FROM pg_publication_tables WHERE pubname='supabase_realtime' AND tablename='matches' AND schemaname='public') THEN
  ALTER PUBLICATION supabase_realtime ADD TABLE public.matches;
 END IF;
END $$;
-- Private presence channels: only room participants may join match:<uuid>.
DROP POLICY IF EXISTS playbit_presence_read ON realtime.messages;
DROP POLICY IF EXISTS playbit_presence_write ON realtime.messages;
CREATE POLICY playbit_presence_read ON realtime.messages FOR SELECT TO authenticated
 USING (extension='presence' AND realtime.topic() LIKE 'match:%' AND public.is_match_participant(substring(realtime.topic() from 7)::uuid));
CREATE POLICY playbit_presence_write ON realtime.messages FOR INSERT TO authenticated
 WITH CHECK (extension='presence' AND realtime.topic() LIKE 'match:%' AND public.is_match_participant(substring(realtime.topic() from 7)::uuid));
