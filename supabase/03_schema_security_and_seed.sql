CREATE OR REPLACE FUNCTION my_alliance_id() RETURNS uuid
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT alliance_id FROM players WHERE id = auth.uid()
$$;

ALTER TABLE realms               ENABLE ROW LEVEL SECURITY;
ALTER TABLE alliances            ENABLE ROW LEVEL SECURITY;
ALTER TABLE players              ENABLE ROW LEVEL SECURITY;
ALTER TABLE alliance_messages    ENABLE ROW LEVEL SECURITY;
ALTER TABLE alliance_invites     ENABLE ROW LEVEL SECURITY;
ALTER TABLE aid_requests         ENABLE ROW LEVEL SECURITY;
ALTER TABLE rallies              ENABLE ROW LEVEL SECURITY;
ALTER TABLE rally_participants   ENABLE ROW LEVEL SECURITY;
ALTER TABLE marches              ENABLE ROW LEVEL SECURITY;
ALTER TABLE strongholds          ENABLE ROW LEVEL SECURITY;
ALTER TABLE stronghold_garrisons ENABLE ROW LEVEL SECURITY;
ALTER TABLE alliance_pacts       ENABLE ROW LEVEL SECURITY;
ALTER TABLE battle_logs          ENABLE ROW LEVEL SECURITY;

CREATE POLICY "read realms"      ON realms               FOR SELECT TO authenticated USING (true);
CREATE POLICY "read alliances"   ON alliances            FOR SELECT TO authenticated USING (true);
CREATE POLICY "read players"     ON players              FOR SELECT TO authenticated USING (true);
CREATE POLICY "read marches"     ON marches              FOR SELECT TO authenticated USING (true);
CREATE POLICY "read strongholds" ON strongholds          FOR SELECT TO authenticated USING (true);
CREATE POLICY "read garrisons"   ON stronghold_garrisons FOR SELECT TO authenticated USING (true);
CREATE POLICY "read pacts"       ON alliance_pacts       FOR SELECT TO authenticated USING (true);

CREATE POLICY "read own alliance chat" ON alliance_messages FOR SELECT TO authenticated
  USING (alliance_id = my_alliance_id());
CREATE POLICY "send chat in own alliance" ON alliance_messages FOR INSERT TO authenticated
  WITH CHECK (
    alliance_id = my_alliance_id()
    AND sender_id = auth.uid()
    AND message_type = 'CHAT'
    AND sender_role <> 'SYSTEM'
  );

CREATE POLICY "read own alliance aid" ON aid_requests FOR SELECT TO authenticated
  USING (alliance_id = my_alliance_id());
CREATE POLICY "read own alliance rallies" ON rallies FOR SELECT TO authenticated
  USING (alliance_id = my_alliance_id());
CREATE POLICY "read own alliance rally members" ON rally_participants FOR SELECT TO authenticated
  USING (rally_id IN (SELECT id FROM rallies WHERE alliance_id = my_alliance_id()));

CREATE POLICY "read relevant invites" ON alliance_invites FOR SELECT TO authenticated
  USING (target_id = auth.uid() OR alliance_id = my_alliance_id());

CREATE POLICY "read relevant battles" ON battle_logs FOR SELECT TO authenticated
  USING (
    attacker_id = auth.uid() OR defender_id = auth.uid()
    OR attacker_alliance_id = my_alliance_id()
    OR defender_alliance_id = my_alliance_id()
  );

CREATE OR REPLACE FUNCTION create_player(p_username text, p_realm_id text DEFAULT NULL)
RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  v_uid   uuid := auth.uid();
  v_realm realms;
  v_row   players;
  v_x int; v_y int;
  v_try int := 0;
BEGIN
  IF v_uid IS NULL THEN
    RAISE EXCEPTION 'Not signed in';
  END IF;

  SELECT * INTO v_row FROM players WHERE id = v_uid;
  IF FOUND THEN
    RETURN v_row;
  END IF;

  IF p_realm_id IS NOT NULL THEN
    SELECT * INTO v_realm FROM realms WHERE id = p_realm_id AND accepting_new_players;
  ELSE
    SELECT * INTO v_realm FROM realms WHERE accepting_new_players
      ORDER BY opened_at DESC LIMIT 1;
  END IF;
  IF NOT FOUND THEN
    RAISE EXCEPTION 'No realm is accepting new players';
  END IF;

  LOOP
    v_try := v_try + 1;
    v_x := 50 + floor(random() * (v_realm.map_width  - 100))::int;
    v_y := 50 + floor(random() * (v_realm.map_height - 100))::int;
    EXIT WHEN NOT EXISTS (
      SELECT 1 FROM players WHERE home_realm_id = v_realm.id AND pos_x = v_x AND pos_y = v_y
    );
    IF v_try > 50 THEN
      RAISE EXCEPTION 'Realm is full';
    END IF;
  END LOOP;

  INSERT INTO players (id, username, home_realm_id, pos_x, pos_y, shield_until)
  VALUES (
    v_uid,
    left(coalesce(nullif(trim(p_username), ''), 'Lord ' || left(v_uid::text, 6)), 28),
    v_realm.id, v_x, v_y,
    now() + interval '24 hours'
  )
  RETURNING * INTO v_row;

  RETURN v_row;
END $$;

REVOKE ALL ON FUNCTION create_player(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION create_player(text, text) TO authenticated;

DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_publication WHERE pubname = 'supabase_realtime') THEN
    ALTER PUBLICATION supabase_realtime ADD TABLE
      alliance_messages, marches, strongholds, stronghold_garrisons,
      rallies, aid_requests, alliance_invites;
  END IF;
END $$;

INSERT INTO realms (id, name, biome_theme, opened_at, protection_ends_at)
VALUES
  ('realm-1', 'Crownlands',   'Dark Medieval',   now() - interval '90 days', now() - interval '76 days'),
  ('realm-2', 'Aether Reach', 'Skylord Citadel', now() - interval '90 days', now() - interval '76 days')
ON CONFLICT (id) DO NOTHING;
