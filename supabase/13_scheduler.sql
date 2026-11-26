-- Crownfall part 13: keep the world running when nobody has the app open.
-- Every 30 seconds the server settles battles, pays landmarks, moves camps and runs the AI lords' turns.

CREATE EXTENSION IF NOT EXISTS pg_cron WITH SCHEMA pg_catalog;
GRANT USAGE ON SCHEMA cron TO postgres;

CREATE OR REPLACE FUNCTION cf_server_tick() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  -- resolve_due_battles expects a signed-in caller; the scheduler acts as "the realm itself".
  PERFORM set_config('request.jwt.claim.sub', '00000000-0000-0000-0000-000000000000', true);
  RETURN resolve_due_battles();
END $$;
REVOKE ALL ON FUNCTION cf_server_tick() FROM PUBLIC, anon, authenticated;

DO $$
BEGIN
  PERFORM cron.unschedule('crownfall-tick');
EXCEPTION WHEN OTHERS THEN NULL;   -- not scheduled yet
END $$;
SELECT cron.schedule('crownfall-tick', '30 seconds', 'SELECT cf_server_tick()');

SELECT jobname, schedule, active FROM cron.job WHERE jobname = 'crownfall-tick';
