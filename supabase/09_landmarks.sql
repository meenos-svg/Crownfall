-- ===== Crownfall part 9: landmarks (control points) =====

CREATE TABLE IF NOT EXISTS landmarks (
  id                 text PRIMARY KEY,
  realm_id           text NOT NULL REFERENCES realms(id),
  name               text NOT NULL,
  kind               text NOT NULL CHECK (kind IN ('MINE','QUARRY','LUMBER','FARM','SHRINE')),
  resource           text CHECK (resource IN ('FOOD','TIMBER','STONE','GOLD')),
  map_x              int  NOT NULL,
  map_y              int  NOT NULL,
  yield_per_hour     bigint NOT NULL DEFAULT 0,
  guardian_power     bigint NOT NULL DEFAULT 2000,
  holder_alliance_id uuid REFERENCES alliances(id) ON DELETE SET NULL,
  holder_since       timestamptz,
  last_paid_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_landmarks_realm ON landmarks (realm_id);

CREATE TABLE IF NOT EXISTS landmark_garrisons (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  landmark_id  text NOT NULL REFERENCES landmarks(id) ON DELETE CASCADE,
  player_id    uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  alliance_id  uuid REFERENCES alliances(id) ON DELETE SET NULL,
  troops_json  jsonb  NOT NULL DEFAULT '{}'::jsonb,
  troop_power  bigint NOT NULL DEFAULT 0 CHECK (troop_power >= 0),
  joined_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (landmark_id, player_id)
);

ALTER TABLE landmarks ENABLE ROW LEVEL SECURITY;
ALTER TABLE landmark_garrisons ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "read landmarks" ON landmarks;
DROP POLICY IF EXISTS "read landmark garrisons" ON landmark_garrisons;
CREATE POLICY "read landmarks" ON landmarks FOR SELECT TO authenticated USING (true);
CREATE POLICY "read landmark garrisons" ON landmark_garrisons FOR SELECT TO authenticated USING (true);
GRANT SELECT ON landmarks, landmark_garrisons TO authenticated;

ALTER TABLE marches DROP CONSTRAINT IF EXISTS marches_target_type_check;
ALTER TABLE marches ADD CONSTRAINT marches_target_type_check
  CHECK (target_type IN ('KINGDOM','BASTION','RESOURCE','STRONGHOLD','TILE','CAMP','LANDMARK'));

-- Six landmarks in every realm, in the same spots (map squares 0-99).
INSERT INTO landmarks (id, realm_id, name, kind, resource, map_x, map_y, yield_per_hour, guardian_power)
SELECT r.id || ':' || l.slug, r.id, l.name, l.kind, l.res, l.x, l.y, l.yield, l.guard
  FROM realms r
 CROSS JOIN (VALUES
   ('gold',   'Golden Vein Mine',      'MINE',   'GOLD',   14, 30,  600, 2600),
   ('quarry', 'Stonewall Quarry',      'QUARRY', 'STONE',  86, 22,  900, 2200),
   ('lumber', 'Blackwood Lumberyard',  'LUMBER', 'TIMBER', 12, 86,  900, 2000),
   ('farm',   'Riverbend Granary',     'FARM',   'FOOD',   62, 86, 1100, 1800),
   ('cold',   'Cold Mine',             'MINE',   'GOLD',   88, 64,  450, 1600),
   ('shrine', 'Ancient Shrine',        'SHRINE', NULL,     50, 12,  120, 3400)
 ) AS l(slug, name, kind, res, x, y, yield, guard)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, kind = EXCLUDED.kind, resource = EXCLUDED.resource,
  map_x = EXCLUDED.map_x, map_y = EXCLUDED.map_y, yield_per_hour = EXCLUDED.yield_per_hour,
  guardian_power = EXCLUDED.guardian_power;

-- ===== 9b: landmark helpers =====

-- Held: garrison (with the holder's defense tech) plus 400 for the palisade. Unclaimed: its guardians.
CREATE OR REPLACE FUNCTION cf_landmark_defense(p_landmark text) RETURNS bigint
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT CASE WHEN l.holder_alliance_id IS NULL THEN l.guardian_power
    ELSE round(coalesce((SELECT sum(troop_power) FROM landmark_garrisons WHERE landmark_id = l.id), 0)
               * (1 + cf_tech_level(l.holder_alliance_id, 'DEFENSE') * 0.06))::bigint + 400 END
    FROM landmarks l WHERE l.id = p_landmark
$$;

CREATE OR REPLACE FUNCTION cf_landmark_target(p_src players, p_type text, p_id text,
  OUT x int, OUT y int, OUT tname text)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE l landmarks; v_friend boolean;
BEGIN
  SELECT * INTO l FROM landmarks WHERE id = p_id;
  IF NOT FOUND THEN RAISE EXCEPTION 'Unknown landmark.'; END IF;
  IF l.realm_id <> p_src.home_realm_id THEN
    RAISE EXCEPTION '% is in another realm. Set up a camp there first.', l.name;
  END IF;
  IF p_src.alliance_id IS NULL THEN RAISE EXCEPTION 'Join an alliance to hold landmarks.'; END IF;
  v_friend := l.holder_alliance_id IS NOT NULL
    AND (l.holder_alliance_id = p_src.alliance_id OR cf_at_peace(p_src.alliance_id, l.holder_alliance_id));
  IF p_type = 'REINFORCE' AND NOT v_friend THEN
    RAISE EXCEPTION 'Only the holding alliance and its pact allies can reinforce %.', l.name;
  END IF;
  IF p_type = 'RAID' AND v_friend THEN
    RAISE EXCEPTION '% is held by your alliance or a pact ally. Reinforce it instead.', l.name;
  END IF;
  x := l.map_x * 14; y := l.map_y * 14; tname := l.name;
END $$;

-- Pays the garrison for the time since the last payout (at most 12 hours), split by troop power.
CREATE OR REPLACE FUNCTION cf_pay_landmark(p_landmark text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE l landmarks; v_hours numeric; v_amount bigint; v_total bigint; g landmark_garrisons; v_share bigint;
BEGIN
  SELECT * INTO l FROM landmarks WHERE id = p_landmark FOR UPDATE SKIP LOCKED;
  IF NOT FOUND OR l.holder_alliance_id IS NULL THEN RETURN; END IF;
  v_hours := LEAST(extract(epoch FROM now() - l.last_paid_at) / 3600.0, 12);
  UPDATE landmarks SET last_paid_at = now() WHERE id = l.id;
  v_amount := floor(l.yield_per_hour * v_hours);
  IF v_amount <= 0 THEN RETURN; END IF;

  IF l.kind = 'SHRINE' THEN
    UPDATE alliances SET tech_xp = tech_xp + v_amount WHERE id = l.holder_alliance_id;
    RETURN;
  END IF;

  SELECT coalesce(sum(troop_power), 0) INTO v_total FROM landmark_garrisons WHERE landmark_id = l.id;
  IF v_total <= 0 THEN RETURN; END IF;     -- no garrison, no income
  FOR g IN SELECT * FROM landmark_garrisons WHERE landmark_id = l.id LOOP
    v_share := floor(v_amount * g.troop_power::numeric / v_total);
    UPDATE players SET
      food   = food   + CASE WHEN l.resource = 'FOOD'   THEN v_share ELSE 0 END,
      timber = timber + CASE WHEN l.resource = 'TIMBER' THEN v_share ELSE 0 END,
      stone  = stone  + CASE WHEN l.resource = 'STONE'  THEN v_share ELSE 0 END,
      gold   = gold   + CASE WHEN l.resource = 'GOLD'   THEN v_share ELSE 0 END
     WHERE id = g.player_id;
  END LOOP;
END $$;

CREATE OR REPLACE FUNCTION withdraw_landmark_garrison(p_landmark text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); g landmark_garrisons;
BEGIN
  PERFORM cf_pay_landmark(p_landmark);     -- collect your share before leaving
  SELECT * INTO g FROM landmark_garrisons WHERE landmark_id = p_landmark AND player_id = me.id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'You have no troops at that landmark.'; END IF;
  PERFORM cf_return_troops(me.id, g.troops_json);
  DELETE FROM landmark_garrisons WHERE id = g.id;
END $$;

-- ===== 9c: battles and reinforcements at landmarks =====

CREATE OR REPLACE FUNCTION cf_resolve_landmark_march(p_march uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  m marches; att players; l landmarks; g landmark_garrisons; c record;
  v_atk numeric; v_def numeric; v_left jsonb; v_gleft jsonb; v_def_losses bigint := 0;
  v_old uuid; v_friend boolean; v_tag text;
BEGIN
  SELECT * INTO m FROM marches WHERE id = p_march AND status = 'MARCHING' FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN; END IF;
  UPDATE marches SET status = 'COMPLETED', returns_at = now() WHERE id = m.id;
  SELECT * INTO att FROM players WHERE id = m.player_id FOR UPDATE;
  SELECT * INTO l FROM landmarks WHERE id = m.target_id FOR UPDATE;
  IF NOT FOUND OR att.alliance_id IS NULL THEN PERFORM cf_march_home(m, m.troops_json); RETURN; END IF;

  v_friend := l.holder_alliance_id IS NOT NULL
    AND (l.holder_alliance_id = att.alliance_id OR cf_at_peace(att.alliance_id, l.holder_alliance_id));

  IF m.march_type = 'REINFORCE' THEN
    IF NOT v_friend THEN PERFORM cf_march_home(m, m.troops_json); RETURN; END IF;
    PERFORM cf_pay_landmark(l.id);
    INSERT INTO landmark_garrisons (landmark_id, player_id, alliance_id, troops_json, troop_power)
    VALUES (l.id, att.id, att.alliance_id, m.troops_json, m.troop_power)
    ON CONFLICT (landmark_id, player_id) DO UPDATE
      SET troops_json = cf_add_troops(landmark_garrisons.troops_json, EXCLUDED.troops_json),
          troop_power = landmark_garrisons.troop_power + EXCLUDED.troop_power;
    RETURN;
  END IF;

  -- An attack. If the landmark became friendly on the way, the army turns back.
  IF v_friend THEN PERFORM cf_march_home(m, m.troops_json); RETURN; END IF;

  v_old := l.holder_alliance_id;
  v_atk := (m.troop_power + cf_tq(m.troops_json, 'siege') * 12) * (1 + cf_tech_level(att.alliance_id, 'ATTACK') * 0.06);
  v_def := cf_landmark_defense(l.id);
  c := cf_combat(v_atk, v_def);
  v_left := cf_scale_troops(m.troops_json, 1 - c.att_loss);

  IF v_old IS NOT NULL THEN
    PERFORM cf_pay_landmark(l.id);
    FOR g IN SELECT * FROM landmark_garrisons WHERE landmark_id = l.id FOR UPDATE LOOP
      v_gleft := cf_scale_troops(g.troops_json, 1 - c.def_loss);
      v_def_losses := v_def_losses + cf_unit_count(g.troops_json) - cf_unit_count(v_gleft);
      IF c.victory OR cf_unit_count(v_gleft) = 0 THEN
        PERFORM cf_return_troops(g.player_id, v_gleft);     -- routed: survivors go home
        DELETE FROM landmark_garrisons WHERE id = g.id;
      ELSE
        UPDATE landmark_garrisons SET troops_json = v_gleft, troop_power = cf_troop_power(v_gleft) WHERE id = g.id;
      END IF;
    END LOOP;
  END IF;

  IF c.victory THEN
    UPDATE landmarks SET holder_alliance_id = att.alliance_id, holder_since = now(), last_paid_at = now()
     WHERE id = l.id;
    DELETE FROM landmark_garrisons WHERE landmark_id = l.id;
    IF cf_unit_count(v_left) > 0 THEN      -- the victors stay on as the new garrison
      INSERT INTO landmark_garrisons (landmark_id, player_id, alliance_id, troops_json, troop_power)
      VALUES (l.id, att.id, att.alliance_id, v_left, cf_troop_power(v_left));
    END IF;
    SELECT tag INTO v_tag FROM alliances WHERE id = att.alliance_id;
    PERFORM cf_herald_with_allies(att.alliance_id, format('%s captured %s for [%s]! Garrison it to collect its riches.',
      att.username, l.name, v_tag));
    IF v_old IS NOT NULL THEN
      PERFORM cf_herald_with_allies(v_old, format('%s fell to %s of [%s]!', l.name, att.username, v_tag));
    END IF;
  ELSE
    PERFORM cf_march_home(m, v_left);
    IF v_old IS NOT NULL THEN
      PERFORM cf_system_message(v_old, left(format('%s held %s against %s.',
        (SELECT '[' || tag || ']' FROM alliances WHERE id = v_old), l.name, att.username), 300));
    END IF;
  END IF;

  UPDATE players SET honor = honor + CASE WHEN c.victory THEN 100 ELSE 10 END WHERE id = att.id;
  INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
    defender_name, defender_alliance_id, defender_kind, victory,
    attacker_power, defender_power, attacker_losses, defender_losses, march_id)
  VALUES (m.realm_id, 'RAID', att.id, att.username, att.alliance_id,
    l.name, v_old, 'LANDMARK', c.victory,
    round(v_atk), round(v_def), cf_unit_count(m.troops_json) - cf_unit_count(v_left), v_def_losses, m.id);
END $$;

-- ===== 9d: march dispatch and the battle resolver, now aware of landmarks =====

CREATE OR REPLACE FUNCTION dispatch_march(p_type text, p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint,
  p_from_camp boolean DEFAULT false) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_id uuid; sh strongholds;
  v_camp camps; src players;
BEGIN
  -- From a camp the army starts in that realm, at the camp, with the camp's troops.
  src := me;
  IF p_from_camp THEN
    SELECT * INTO v_camp FROM camps WHERE player_id = me.id AND status = 'CAMPED' FOR UPDATE;
    IF NOT FOUND THEN RAISE EXCEPTION 'You have no camp set up in another realm.'; END IF;
    src.home_realm_id := v_camp.realm_id; src.pos_x := v_camp.pos_x; src.pos_y := v_camp.pos_y;
  END IF;

  IF p_type = 'ATTACK' THEN p_type := 'RAID'; END IF;
  IF p_type = 'GATHER' AND p_target_type <> 'RESOURCE' THEN
    RAISE EXCEPTION 'Gathering only works on resource sites.';
  END IF;
  IF p_type = 'RAID' AND p_target_type NOT IN ('KINGDOM','BASTION','STRONGHOLD','CAMP','LANDMARK') THEN
    RAISE EXCEPTION 'Raids target kingdoms, bastions, strongholds, camps or landmarks.';
  END IF;
  IF p_type = 'REINFORCE' AND p_target_type NOT IN ('STRONGHOLD','LANDMARK') THEN
    RAISE EXCEPTION 'Only strongholds and landmarks can be reinforced.';
  END IF;
  IF p_type NOT IN ('RAID','GATHER','REINFORCE') THEN RAISE EXCEPTION 'Unknown march type %', p_type; END IF;
  IF (SELECT count(*) FROM marches WHERE player_id = me.id AND status = 'MARCHING') >= 3 THEN
    RAISE EXCEPTION 'All 3 march slots are busy. Wait for an army to return.';
  END IF;

  IF p_target_type = 'LANDMARK' THEN
    t := cf_landmark_target(src, p_type, p_target_id);
  ELSIF p_type = 'REINFORCE' THEN
    SELECT * INTO sh FROM strongholds WHERE id::text = p_target_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'That stronghold has already fallen.'; END IF;
    IF sh.home_realm_id <> src.home_realm_id THEN
      RAISE EXCEPTION 'That stronghold is in another realm. Set up a camp there first.';
    END IF;
    IF NOT cf_can_reinforce(me, sh) THEN
      RAISE EXCEPTION 'Only their alliance and pact allies can reinforce, unless they call for aid.';
    END IF;
    SELECT sh.pos_x AS x, sh.pos_y AS y INTO t;
  ELSE
    t := cf_war_target(src, p_target_type, p_target_id);
  END IF;
  IF p_from_camp THEN
    v_troops := cf_take_camp_troops(v_camp, p_infantry, p_cavalry, p_archers, p_siege);
  ELSE
    v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  END IF;
  IF p_target_type IN ('KINGDOM','STRONGHOLD','CAMP') AND p_type = 'RAID' THEN
    UPDATE players SET shield_until = NULL WHERE id = me.id;  -- attacking drops your own shield
  END IF;
  v_secs := cf_travel_seconds(src.pos_x, src.pos_y, t.x, t.y, me.alliance_id);

  INSERT INTO marches (player_id, realm_id, march_type, target_type, target_id,
                       start_x, start_y, target_x, target_y, departed_at, arrives_at,
                       troops_json, troop_power, status, camp_id)
  VALUES (me.id, src.home_realm_id, p_type, p_target_type, p_target_id,
          src.pos_x, src.pos_y, t.x, t.y, now(), now() + make_interval(secs => v_secs),
          v_troops, cf_troop_power(v_troops), 'MARCHING', v_camp.id)
  RETURNING id INTO v_id;
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION resolve_due_battles() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; v_id uuid; v_lid text; n int := 0; lx int; ly int;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;

  -- expeditions arriving at camp, and camps arriving back home
  UPDATE camps SET status = 'CAMPED' WHERE status = 'TRAVELING' AND arrives_at <= now();
  FOR v_id IN SELECT id FROM camps WHERE status = 'RETURNING' AND arrives_at <= now() LIMIT 50 LOOP
    PERFORM cf_return_troops(player_id, troops_json) FROM camps WHERE id = v_id;
    DELETE FROM camps WHERE id = v_id;
  END LOOP;

  -- 30 quiet minutes ends a siege: walls are repaired and the call for aid lowered
  UPDATE strongholds SET is_under_siege = false, siege_started_at = NULL, durability = max_durability,
         call_for_aid_active = false, aid_message = NULL
   WHERE is_under_siege AND last_attacked_at < now() - interval '30 minutes';

  FOR r IN SELECT * FROM rallies WHERE status = 'GATHERING' AND launch_at <= now()
           ORDER BY launch_at LIMIT 20 FOR UPDATE SKIP LOCKED LOOP
    SELECT pos_x, pos_y INTO lx, ly FROM players WHERE id = r.leader_id;
    UPDATE rallies SET status = 'MARCHING' WHERE id = r.id;
    INSERT INTO marches (player_id, realm_id, march_type, target_type, target_id, rally_id,
                         start_x, start_y, target_x, target_y, departed_at, arrives_at,
                         troop_power, status)
    VALUES (r.leader_id, r.realm_id, 'RALLY', r.target_type, r.target_id, r.id,
            coalesce(lx, r.target_x), coalesce(ly, r.target_y), r.target_x, r.target_y,
            r.launch_at, GREATEST(coalesce(r.arrives_at, r.launch_at), r.launch_at), r.total_power, 'MARCHING');
  END LOOP;

  FOR v_id IN SELECT id FROM rallies WHERE status = 'MARCHING' AND arrives_at <= now()
              ORDER BY arrives_at LIMIT 20 LOOP
    PERFORM cf_resolve_rally(v_id); n := n + 1;
  END LOOP;

  FOR v_id IN SELECT id FROM marches WHERE status = 'MARCHING' AND march_type <> 'RALLY' AND arrives_at <= now()
              ORDER BY arrives_at LIMIT 50 LOOP
    IF (SELECT target_type FROM marches WHERE id = v_id) = 'LANDMARK' THEN
      PERFORM cf_resolve_landmark_march(v_id);
    ELSE
      PERFORM cf_resolve_march(v_id);
    END IF;
    n := n + 1;
  END LOOP;

  -- landmarks pay their garrisons every few minutes
  FOR v_lid IN SELECT id FROM landmarks
               WHERE holder_alliance_id IS NOT NULL AND last_paid_at < now() - interval '5 minutes'
               LIMIT 50 LOOP
    PERFORM cf_pay_landmark(v_lid);
  END LOOP;
  RETURN n;
END $$;
-- ===== 9e: permissions =====
REVOKE ALL ON FUNCTION cf_landmark_target(players, text, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_pay_landmark(text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_resolve_landmark_march(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION withdraw_landmark_garrison(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION withdraw_landmark_garrison(text) TO authenticated;
