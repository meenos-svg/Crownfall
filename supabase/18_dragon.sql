-- ===== Crownfall part 18: the Red Dragon world boss =====
-- One dragon lands somewhere in each realm. Anyone can march on it, alliances can rally on it.
-- Every hit chips its health and burns some of your troops. When it falls, the treasure hoard is
-- shared by damage dealt. If nobody kills it within 24 hours it flies off. Two hours later it returns.

CREATE TABLE IF NOT EXISTS dragons (
  id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  realm_id          text NOT NULL REFERENCES realms(id),
  name              text NOT NULL,
  level             int  NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 10),
  max_hp            bigint NOT NULL CHECK (max_hp > 0),
  hp                bigint NOT NULL CHECK (hp >= 0),
  map_x             int  NOT NULL,                 -- map squares 0-99
  map_y             int  NOT NULL,
  status            text NOT NULL DEFAULT 'ALIVE' CHECK (status IN ('ALIVE','SLAIN','FLED')),
  spawned_at        timestamptz NOT NULL DEFAULT now(),
  flees_at          timestamptz NOT NULL DEFAULT now() + interval '24 hours',
  ended_at          timestamptz,
  respawn_at        timestamptz,
  slain_by          uuid REFERENCES players(id) ON DELETE SET NULL,
  slain_by_name     text,
  slain_by_alliance uuid REFERENCES alliances(id) ON DELETE SET NULL
);
CREATE UNIQUE INDEX IF NOT EXISTS idx_one_live_dragon ON dragons (realm_id) WHERE status = 'ALIVE';
CREATE INDEX IF NOT EXISTS idx_dragons_realm ON dragons (realm_id, spawned_at DESC);

CREATE TABLE IF NOT EXISTS dragon_damage (
  dragon_id   uuid NOT NULL REFERENCES dragons(id) ON DELETE CASCADE,
  player_id   uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  alliance_id uuid REFERENCES alliances(id) ON DELETE SET NULL,
  damage      bigint NOT NULL DEFAULT 0,
  hits        int    NOT NULL DEFAULT 0,
  last_hit_at timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (dragon_id, player_id)
);

ALTER TABLE dragons ENABLE ROW LEVEL SECURITY;
ALTER TABLE dragon_damage ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "read dragons" ON dragons;
DROP POLICY IF EXISTS "read dragon damage" ON dragon_damage;
CREATE POLICY "read dragons" ON dragons FOR SELECT TO authenticated USING (true);
CREATE POLICY "read dragon damage" ON dragon_damage FOR SELECT TO authenticated USING (true);
GRANT SELECT ON dragons, dragon_damage TO authenticated;

ALTER TABLE marches DROP CONSTRAINT IF EXISTS marches_target_type_check;
ALTER TABLE marches ADD CONSTRAINT marches_target_type_check
  CHECK (target_type IN ('KINGDOM','BASTION','RESOURCE','STRONGHOLD','TILE','CAMP','LANDMARK','DRAGON'));
ALTER TABLE rallies DROP CONSTRAINT IF EXISTS rallies_target_type_check;
ALTER TABLE rallies ADD CONSTRAINT rallies_target_type_check
  CHECK (target_type IN ('KINGDOM','BASTION','STRONGHOLD','DRAGON'));

-- ===== 18b: dragon helpers =====

CREATE OR REPLACE FUNCTION cf_dragon_max_hp(p_level int) RETURNS bigint
LANGUAGE sql IMMUTABLE AS $$ SELECT (250000 * (1 + 0.6 * (p_level - 1)))::bigint $$;

-- Share of the army the dragon's fire burns away on one hit.
CREATE OR REPLACE FUNCTION cf_dragon_burn(p_level int) RETURNS numeric
LANGUAGE sql IMMUTABLE AS $$ SELECT LEAST(0.08 + 0.03 * p_level, 0.38) $$;

-- Lands a dragon in a realm: a random open spot on land, health set by its level.
CREATE OR REPLACE FUNCTION cf_spawn_dragon(p_realm text, p_level int) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE s record; v_id uuid; v_name text; a uuid; v_lvl int := LEAST(GREATEST(p_level, 1), 10);
BEGIN
  s := cf_random_castle_spot(p_realm);
  v_name := (ARRAY['Ignareth the Red','Skarvoss Emberwing','Morrakai Ashmaw','Vyrmox the Scorched'])
            [1 + floor(random() * 4)::int];
  INSERT INTO dragons (realm_id, name, level, max_hp, hp, map_x, map_y)
  VALUES (p_realm, v_name, v_lvl, cf_dragon_max_hp(v_lvl), cf_dragon_max_hp(v_lvl), s.px / 14, s.py / 14)
  ON CONFLICT DO NOTHING
  RETURNING id INTO v_id;
  IF v_id IS NULL THEN RETURN NULL; END IF;      -- another tick beat us to it
  FOR a IN SELECT DISTINCT alliance_id FROM players
            WHERE home_realm_id = p_realm AND alliance_id IS NOT NULL AND NOT is_bot LOOP
    PERFORM cf_system_message(a, format('THE RED DRAGON! %s (level %s) has landed at (%s, %s). Rally your alliance and claim its hoard!',
      v_name, v_lvl, s.px / 14, s.py / 14), 'RALLY');
  END LOOP;
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION cf_dragon_target(p_src players, p_id text, OUT x int, OUT y int, OUT tname text, OUT hp bigint)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE d dragons;
BEGIN
  SELECT * INTO d FROM dragons WHERE id::text = p_id;
  IF NOT FOUND OR d.status <> 'ALIVE' THEN RAISE EXCEPTION 'The dragon has already gone.'; END IF;
  IF d.realm_id <> p_src.home_realm_id THEN
    RAISE EXCEPTION '% is in another realm. Set up a camp there first.', d.name;
  END IF;
  x := d.map_x * 14 + 7; y := d.map_y * 14 + 7; tname := d.name; hp := d.hp;
END $$;

-- Pays out the hoard when the dragon falls: resources by damage share, alliance tech XP by
-- alliance share, a bonus to whoever struck the killing blow. Then the next dragon is one level higher.
CREATE OR REPLACE FUNCTION cf_slay_dragon(p_dragon uuid, p_killer uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  d dragons; k players; v_total numeric; r record; v_share numeric; v_tag text;
  v_food bigint; v_timber bigint; v_stone bigint; v_gold bigint; v_xp bigint;
BEGIN
  SELECT * INTO d FROM dragons WHERE id = p_dragon FOR UPDATE;
  SELECT * INTO k FROM players WHERE id = p_killer;
  SELECT tag INTO v_tag FROM alliances WHERE id = k.alliance_id;
  UPDATE dragons SET status = 'SLAIN', hp = 0, ended_at = now(), respawn_at = now() + interval '2 hours',
         slain_by = k.id, slain_by_name = k.username, slain_by_alliance = k.alliance_id
   WHERE id = d.id;

  v_food := 40000 * d.level; v_timber := 40000 * d.level; v_stone := 40000 * d.level; v_gold := 25000 * d.level;
  v_xp := 3000 * d.level;
  SELECT GREATEST(sum(damage), 1) INTO v_total FROM dragon_damage WHERE dragon_id = d.id;

  FOR r IN SELECT player_id, damage FROM dragon_damage WHERE dragon_id = d.id ORDER BY player_id LOOP
    v_share := r.damage / v_total;
    UPDATE players SET food = food + floor(v_food * v_share), timber = timber + floor(v_timber * v_share),
                       stone = stone + floor(v_stone * v_share), gold = gold + floor(v_gold * v_share),
                       honor = honor + 50 + floor(500 * v_share)
     WHERE id = r.player_id;
  END LOOP;
  UPDATE players SET gold = gold + 5000 * d.level, honor = honor + 250 WHERE id = k.id;   -- killing blow

  FOR r IN SELECT alliance_id, sum(damage) AS dmg FROM dragon_damage
            WHERE dragon_id = d.id AND alliance_id IS NOT NULL GROUP BY alliance_id LOOP
    UPDATE alliances SET tech_xp = tech_xp + floor(v_xp * r.dmg / v_total) WHERE id = r.alliance_id;
    PERFORM cf_system_message(r.alliance_id, format('%s HAS FALLEN! %s%s struck the killing blow. Your alliance dealt %s%% of the damage. The hoard has been shared out.',
      d.name, k.username, coalesce(' [' || v_tag || ']', ''), round(100 * r.dmg / v_total)), 'RALLY');
  END LOOP;
END $$;

-- ===== 18c: hitting the dragon =====

-- One strike (a march or a whole rally). Returns the damage dealt.
-- p_parts: [{player_id, troops, power}] — one row for a march, every member for a rally.
CREATE OR REPLACE FUNCTION cf_strike_dragon(p_dragon uuid, p_leader uuid, p_alliance uuid, p_realm text,
  p_parts jsonb, p_is_rally boolean, p_march uuid, p_rally uuid, p_label text) RETURNS bigint
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  d dragons; part jsonb; v_power numeric := 0; v_siege numeric := 0; v_atk numeric; v_dmg bigint;
  v_burn numeric; v_left jsonb; v_units bigint := 0; v_left_units bigint := 0; v_share numeric;
  v_pid uuid; v_pdmg bigint; v_ally uuid; v_killed boolean := false;
BEGIN
  SELECT * INTO d FROM dragons WHERE id = p_dragon FOR UPDATE;
  FOR part IN SELECT * FROM jsonb_array_elements(p_parts) LOOP
    v_power := v_power + (part->>'power')::numeric;
    v_siege := v_siege + cf_tq(part->'troops', 'siege');
  END LOOP;

  -- Gone already (slain, fled or vanished): everyone marches home untouched.
  IF d.id IS NULL OR d.status <> 'ALIVE' THEN
    FOR part IN SELECT * FROM jsonb_array_elements(p_parts) LOOP
      IF p_march IS NOT NULL THEN
        PERFORM cf_march_home(m, part->'troops') FROM marches m WHERE m.id = p_march;
      ELSE
        PERFORM cf_return_troops((part->>'player_id')::uuid, part->'troops');
      END IF;
    END LOOP;
    IF p_alliance IS NOT NULL AND p_is_rally THEN
      PERFORM cf_system_message(p_alliance, 'The rally reached the lair, but the dragon was already gone.');
    END IF;
    RETURN 0;
  END IF;

  v_atk := (v_power + v_siege * 12) * (1 + cf_tech_level(p_alliance, 'ATTACK') * 0.06)
           * (0.9 + random() * 0.2) * CASE WHEN p_is_rally THEN 1.25 ELSE 1 END;
  v_dmg := LEAST(round(v_atk)::bigint, d.hp);
  v_burn := cf_dragon_burn(d.level) * CASE WHEN p_is_rally THEN 0.75 ELSE 1 END;

  FOR part IN SELECT * FROM jsonb_array_elements(p_parts) LOOP
    v_pid := (part->>'player_id')::uuid;
    v_left := cf_scale_troops(part->'troops', 1 - v_burn);
    v_units := v_units + cf_unit_count(part->'troops');
    v_left_units := v_left_units + cf_unit_count(v_left);
    IF p_march IS NOT NULL THEN
      PERFORM cf_march_home(m, v_left) FROM marches m WHERE m.id = p_march;
    ELSE
      PERFORM cf_return_troops(v_pid, v_left);
    END IF;
    v_share := (part->>'power')::numeric / GREATEST(v_power, 1);
    v_pdmg := floor(v_dmg * v_share);
    SELECT alliance_id INTO v_ally FROM players WHERE id = v_pid;
    INSERT INTO dragon_damage (dragon_id, player_id, alliance_id, damage, hits)
    VALUES (d.id, v_pid, v_ally, v_pdmg, 1)
    ON CONFLICT (dragon_id, player_id) DO UPDATE
      SET damage = dragon_damage.damage + EXCLUDED.damage, hits = dragon_damage.hits + 1,
          alliance_id = EXCLUDED.alliance_id, last_hit_at = now();
    UPDATE players SET honor = honor + 20 + LEAST(v_pdmg / 500, 300) WHERE id = v_pid;
  END LOOP;

  UPDATE dragons SET hp = hp - v_dmg WHERE id = d.id;
  IF d.hp - v_dmg <= 0 THEN
    PERFORM cf_slay_dragon(d.id, p_leader);
    v_killed := true;
  END IF;

  INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
    defender_name, defender_kind, victory, attacker_power, defender_power, attacker_losses, defender_losses,
    march_id, rally_id)
  VALUES (p_realm, CASE WHEN p_is_rally THEN 'RALLY' ELSE 'RAID' END, p_leader, left(p_label, 28), p_alliance,
    d.name, 'DRAGON', v_killed, round(v_atk), d.hp, v_units - v_left_units, v_dmg, p_march, p_rally);
  RETURN v_dmg;
END $$;

CREATE OR REPLACE FUNCTION cf_resolve_dragon_march(p_march uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE m marches; att players;
BEGIN
  SELECT * INTO m FROM marches WHERE id = p_march AND status = 'MARCHING' FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN; END IF;
  UPDATE marches SET status = 'COMPLETED', returns_at = now() WHERE id = m.id;
  SELECT * INTO att FROM players WHERE id = m.player_id FOR UPDATE;
  PERFORM cf_strike_dragon(m.target_id::uuid, att.id, att.alliance_id, m.realm_id,
    jsonb_build_array(jsonb_build_object('player_id', att.id, 'troops', m.troops_json, 'power', m.troop_power)),
    false, m.id, NULL, att.username);
END $$;

CREATE OR REPLACE FUNCTION cf_resolve_dragon_rally(p_rally uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; v_parts jsonb; v_tag text; v_dmg bigint;
BEGIN
  SELECT * INTO r FROM rallies WHERE id = p_rally AND status = 'MARCHING' FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN; END IF;
  UPDATE rallies SET status = 'RESOLVED' WHERE id = r.id;
  UPDATE marches SET status = 'COMPLETED', returns_at = now() WHERE rally_id = r.id AND status = 'MARCHING';
  PERFORM 1 FROM players WHERE id IN (SELECT player_id FROM rally_participants WHERE rally_id = r.id)
   ORDER BY id FOR UPDATE;
  SELECT tag INTO v_tag FROM alliances WHERE id = r.alliance_id;
  SELECT coalesce(jsonb_agg(jsonb_build_object('player_id', player_id, 'troops', troops_json, 'power', power)), '[]'::jsonb)
    INTO v_parts FROM rally_participants WHERE rally_id = r.id;
  v_dmg := cf_strike_dragon(r.target_id::uuid, r.leader_id, r.alliance_id, r.realm_id, v_parts, true, NULL, r.id,
    format('[%s] War Rally', v_tag));
  IF v_dmg > 0 THEN
    PERFORM cf_system_message(r.alliance_id, format('The rally struck %s for %s damage!', r.target_name, v_dmg));
  END IF;
END $$;

-- Lands new dragons, sends old ones off when their 24 hours run out.
CREATE OR REPLACE FUNCTION cf_dragon_tick() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE d dragons; rl realms; last dragons; a uuid;
BEGIN
  FOR d IN SELECT * FROM dragons WHERE status = 'ALIVE' AND flees_at <= now() FOR UPDATE SKIP LOCKED LOOP
    UPDATE dragons SET status = 'FLED', ended_at = now(), respawn_at = now() + interval '2 hours' WHERE id = d.id;
    FOR a IN SELECT DISTINCT alliance_id FROM dragon_damage WHERE dragon_id = d.id AND alliance_id IS NOT NULL LOOP
      PERFORM cf_system_message(a, format('%s grew bored of the realm and flew off with its hoard untouched.', d.name));
    END LOOP;
  END LOOP;

  FOR rl IN SELECT * FROM realms LOOP
    CONTINUE WHEN EXISTS (SELECT 1 FROM dragons WHERE realm_id = rl.id AND status = 'ALIVE');
    SELECT * INTO last FROM dragons WHERE realm_id = rl.id ORDER BY spawned_at DESC LIMIT 1;
    IF last.id IS NULL THEN
      PERFORM cf_spawn_dragon(rl.id, 1);
    ELSIF last.respawn_at <= now() THEN
      -- killed: the next one is tougher. fled: the next one is weaker.
      PERFORM cf_spawn_dragon(rl.id, last.level + CASE WHEN last.status = 'SLAIN' THEN 1 ELSE -1 END);
    END IF;
  END LOOP;
END $$;

-- ===== 18d: dispatch and rallies learn about the dragon =====

CREATE OR REPLACE FUNCTION dispatch_march(p_type text, p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint,
  p_from_camp boolean DEFAULT false) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_id uuid; sh strongholds;
  v_camp camps; src players;
BEGIN
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
  IF p_type = 'RAID' AND p_target_type NOT IN ('KINGDOM','BASTION','STRONGHOLD','CAMP','LANDMARK','DRAGON') THEN
    RAISE EXCEPTION 'Raids target kingdoms, bastions, strongholds, camps, landmarks or the dragon.';
  END IF;
  IF p_type = 'REINFORCE' AND p_target_type NOT IN ('STRONGHOLD','LANDMARK') THEN
    RAISE EXCEPTION 'Only strongholds and landmarks can be reinforced.';
  END IF;
  IF p_type NOT IN ('RAID','GATHER','REINFORCE') THEN RAISE EXCEPTION 'Unknown march type %', p_type; END IF;
  IF (SELECT count(*) FROM marches WHERE player_id = me.id AND status = 'MARCHING') >= 3 THEN
    RAISE EXCEPTION 'All 3 march slots are busy. Wait for an army to return.';
  END IF;

  IF p_target_type = 'DRAGON' THEN
    t := cf_dragon_target(src, p_target_id);
  ELSIF p_target_type = 'LANDMARK' THEN
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

CREATE OR REPLACE FUNCTION launch_rally(p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint,
  p_wait_seconds int DEFAULT 60) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_wait int; v_id uuid; v_power bigint; v_def bigint;
BEGIN
  IF me.alliance_id IS NULL THEN RAISE EXCEPTION 'Join an alliance to sound a War Rally.'; END IF;
  IF p_target_type NOT IN ('KINGDOM','BASTION','DRAGON') THEN
    RAISE EXCEPTION 'Rallies target kingdoms, bastions or the dragon.';
  END IF;
  IF (SELECT count(*) FROM rallies WHERE alliance_id = me.alliance_id
        AND status IN ('GATHERING','MARCHING')) >= 3 THEN
    RAISE EXCEPTION 'Your alliance already has 3 rallies under way.';
  END IF;
  IF EXISTS (SELECT 1 FROM rallies WHERE leader_id = me.id AND status IN ('GATHERING','MARCHING')) THEN
    RAISE EXCEPTION 'You are already leading a rally.';
  END IF;

  IF p_target_type = 'DRAGON' THEN
    t := cf_dragon_target(me, p_target_id);
    v_def := t.hp;
  ELSE
    t := cf_war_target(me, p_target_type, p_target_id);
    v_def := t.defense;
  END IF;
  v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  IF p_target_type = 'KINGDOM' THEN
    UPDATE players SET shield_until = NULL WHERE id = me.id;
  END IF;
  v_wait := LEAST(GREATEST(coalesce(p_wait_seconds, 60), 30), 300);
  v_secs := cf_travel_seconds(me.pos_x, me.pos_y, t.x, t.y, me.alliance_id);
  v_power := cf_troop_power(v_troops);

  INSERT INTO rallies (alliance_id, leader_id, realm_id, target_type, target_id, target_name,
                       target_x, target_y, total_power, target_power, launch_at, arrives_at, travel_seconds)
  VALUES (me.alliance_id, me.id, me.home_realm_id, p_target_type, p_target_id, left(t.tname, 48),
          t.x, t.y, v_power, v_def, now() + make_interval(secs => v_wait),
          now() + make_interval(secs => v_wait + v_secs), v_secs)
  RETURNING id INTO v_id;
  INSERT INTO rally_participants (rally_id, player_id, troops_json, power) VALUES (v_id, me.id, v_troops, v_power);

  PERFORM cf_system_message(me.alliance_id,
    left(format('WAR RALLY! %s calls the alliance against %s. Join within %s seconds!', me.username, t.tname, v_wait), 300),
    'RALLY');
  RETURN v_id;
END $$;

-- The leaderboard for one dragon: top lords, top alliances, and where you stand.
CREATE OR REPLACE FUNCTION dragon_board(p_dragon uuid) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT jsonb_build_object(
    'total', (SELECT coalesce(sum(damage), 0) FROM dragon_damage WHERE dragon_id = p_dragon),
    'lords', coalesce((SELECT jsonb_agg(x ORDER BY (x->>'damage')::bigint DESC) FROM (
        SELECT jsonb_build_object('id', p.id, 'name', p.username, 'avatar', p.avatar, 'avatar_url', p.avatar_url,
                 'tag', a.tag, 'damage', dd.damage, 'hits', dd.hits) AS x
          FROM dragon_damage dd JOIN players p ON p.id = dd.player_id
          LEFT JOIN alliances a ON a.id = dd.alliance_id
         WHERE dd.dragon_id = p_dragon ORDER BY dd.damage DESC LIMIT 10) s), '[]'::jsonb),
    'alliances', coalesce((SELECT jsonb_agg(x ORDER BY (x->>'damage')::bigint DESC) FROM (
        SELECT jsonb_build_object('tag', a.tag, 'name', a.name, 'damage', sum(dd.damage)) AS x
          FROM dragon_damage dd JOIN alliances a ON a.id = dd.alliance_id
         WHERE dd.dragon_id = p_dragon GROUP BY a.id, a.tag, a.name ORDER BY sum(dd.damage) DESC LIMIT 5) s), '[]'::jsonb),
    'mine', (SELECT jsonb_build_object('damage', damage, 'hits', hits,
               'rank', (SELECT count(*) + 1 FROM dragon_damage o WHERE o.dragon_id = p_dragon AND o.damage > dd.damage))
               FROM dragon_damage dd WHERE dragon_id = p_dragon AND player_id = auth.uid())
  )
$$;

-- ===== 18e: the battle resolver handles the dragon and lands new ones =====

CREATE OR REPLACE FUNCTION resolve_due_battles() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; v_id uuid; v_lid text; n int := 0; lx int; ly int; v_tt text;
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
    IF (SELECT target_type FROM rallies WHERE id = v_id) = 'DRAGON' THEN
      PERFORM cf_resolve_dragon_rally(v_id);
    ELSE
      PERFORM cf_resolve_rally(v_id);
    END IF;
    n := n + 1;
  END LOOP;

  FOR v_id IN SELECT id FROM marches WHERE status = 'MARCHING' AND march_type <> 'RALLY' AND arrives_at <= now()
              ORDER BY arrives_at LIMIT 50 LOOP
    SELECT target_type INTO v_tt FROM marches WHERE id = v_id;
    IF v_tt = 'LANDMARK' THEN
      PERFORM cf_resolve_landmark_march(v_id);
    ELSIF v_tt = 'DRAGON' THEN
      PERFORM cf_resolve_dragon_march(v_id);
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
  -- dragons land and fly off
  PERFORM cf_dragon_tick();
  -- AI lords take their turns
  PERFORM cf_bot_tick();
  RETURN n;
END $$;

REVOKE ALL ON FUNCTION cf_spawn_dragon(text, int) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_dragon_target(players, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_slay_dragon(uuid, uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_strike_dragon(uuid, uuid, uuid, text, jsonb, boolean, uuid, uuid, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_resolve_dragon_march(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_resolve_dragon_rally(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_dragon_tick() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION dragon_board(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION dragon_board(uuid) TO authenticated;

-- Land the first dragons now.
SELECT cf_dragon_tick();
SELECT realm_id, name, level, hp, map_x, map_y FROM dragons WHERE status = 'ALIVE';
