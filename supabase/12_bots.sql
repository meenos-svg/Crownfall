-- ===== Crownfall part 12: AI lords =====
-- Bot kingdoms live in every realm: they grow, train, gather, raid bastions, fight over landmarks
-- and sometimes attack players. They act whenever any player's app asks the server to settle battles.

-- Bots have no login account, so castles no longer have to belong to one.
-- A trigger keeps the old behaviour: deleting a real account still deletes its castle.
ALTER TABLE players DROP CONSTRAINT IF EXISTS players_id_fkey;
ALTER TABLE players ADD COLUMN IF NOT EXISTS is_bot boolean NOT NULL DEFAULT false;
ALTER TABLE players ADD COLUMN IF NOT EXISTS bot_style text;
ALTER TABLE players ADD COLUMN IF NOT EXISTS bot_keep_cap int;
ALTER TABLE players ADD COLUMN IF NOT EXISTS bot_next_at timestamptz;
CREATE INDEX IF NOT EXISTS idx_players_bot_next ON players (bot_next_at) WHERE is_bot;

CREATE OR REPLACE FUNCTION cf_auth_user_deleted() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  DELETE FROM public.players WHERE id = OLD.id;
  RETURN OLD;
END $$;

DO $$
BEGIN
  DROP TRIGGER IF EXISTS cf_on_auth_user_deleted ON auth.users;
  CREATE TRIGGER cf_on_auth_user_deleted AFTER DELETE ON auth.users
    FOR EACH ROW EXECUTE FUNCTION cf_auth_user_deleted();
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'Could not add the account-deletion trigger: %', SQLERRM;
END $$;

-- Troops a bot aims to keep at a given Keep level (power roughly 2.8k at 2, 26k at 7, 53k at 10).
CREATE OR REPLACE FUNCTION cf_bot_troop_target(l int) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT cf_troops(round(60 * (0.5 + 0.35 * l * l))::bigint, round(20 * (0.5 + 0.35 * l * l))::bigint,
                   round(35 * (0.5 + 0.35 * l * l))::bigint, round(5 * (0.5 + 0.35 * l * l))::bigint)
$$;

-- ===== 12b: bringing the AI lords into the world =====

CREATE OR REPLACE FUNCTION cf_seed_bots() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  rl record; i int; v_id uuid; v_spot record; v_t jsonb; v_lvl int; v_style text;
  v_names text[]; v_alliance uuid; v_aname text; v_atag text; n int := 0;
  lv int[] := ARRAY[10, 7, 6, 3, 2, 5];
  st text[] := ARRAY['TURTLE', 'RAIDER', 'BUILDER', 'BUILDER', 'TURTLE', 'RAIDER'];
BEGIN
  FOR rl IN SELECT id FROM realms ORDER BY id LOOP
    CONTINUE WHEN (SELECT count(*) FROM players WHERE is_bot AND home_realm_id = rl.id) >= 6;
    v_names := CASE rl.id
      WHEN 'realm-1' THEN ARRAY['Lord Varric Thorne','Lady Elspeth Vane','Baron Aldric Crowe','Duke Rowan Hale','Lady Mirela Ashford','Sir Gideon Marsh']
      WHEN 'realm-2' THEN ARRAY['Warden Caelum','Lady Isolde Frost','Baron Tamsin Vale','Thane Orrin Grey','Lady Seren Mist','Lord Halvard Peak']
      WHEN 'realm-3' THEN ARRAY['Warlord Kael Emberhide','Lady Vex Cinder','Baron Morg Ashfall','Duke Ravok Slag','Lady Nyx Smolder','Lord Brannoc Soot']
      ELSE ARRAY['Lord Corvin','Lady Wren','Baron Hask','Duke Merrow','Lady Tilde','Sir Osric'] END;
    v_aname := CASE rl.id WHEN 'realm-1' THEN 'Iron Covenant' WHEN 'realm-2' THEN 'Mistborn Pact'
                          WHEN 'realm-3' THEN 'Ashen Host' ELSE 'Shadow Court' END;
    v_atag := CASE rl.id WHEN 'realm-1' THEN 'IRON' WHEN 'realm-2' THEN 'MIST' WHEN 'realm-3' THEN 'ASH' ELSE 'SHDW' END;
    v_alliance := NULL;

    FOR i IN 1..6 LOOP
      CONTINUE WHEN EXISTS (SELECT 1 FROM players WHERE username = v_names[i] AND home_realm_id = rl.id);
      v_lvl := lv[i]; v_style := st[i];
      v_id := gen_random_uuid();
      SELECT * INTO v_spot FROM cf_random_castle_spot(rl.id);
      v_t := cf_bot_troop_target(v_lvl);
      INSERT INTO players (id, username, home_realm_id, pos_x, pos_y,
        food, timber, stone, gold,
        keep_level, barracks_level, stables_level, archery_level, siege_level, alliance_hall_level,
        farm_level, lumber_level, quarry_level, mint_level, waygate_level,
        infantry, cavalry, archers, siege, honor,
        is_bot, bot_style, bot_keep_cap, bot_next_at)
      VALUES (v_id, v_names[i], rl.id, v_spot.px, v_spot.py,
        v_lvl * 3000, v_lvl * 3000, v_lvl * 2500, v_lvl * 1500,
        v_lvl, v_lvl, v_lvl, v_lvl, v_lvl, v_lvl, v_lvl, v_lvl, v_lvl, v_lvl, GREATEST(v_lvl - 2, 1),
        cf_tq(v_t,'infantry'), cf_tq(v_t,'cavalry'), cf_tq(v_t,'archers'), cf_tq(v_t,'siege'), v_lvl * 300,
        true, v_style, LEAST(v_lvl + 4, 18), now() + make_interval(secs => i * 40));
      n := n + 1;

      -- the three strongest lords of each realm form an alliance
      IF i = 1 THEN
        SELECT id INTO v_alliance FROM alliances WHERE realm_id = rl.id AND tag = v_atag;
        IF v_alliance IS NULL THEN
          INSERT INTO alliances (realm_id, name, tag, motto, crest_color, leader_id, open_join)
          VALUES (rl.id, v_aname, v_atag, 'The old powers of this realm.', 'OBSIDIAN', v_id, false)
          RETURNING id INTO v_alliance;
          UPDATE players SET alliance_id = v_alliance, alliance_role = 'LEADER' WHERE id = v_id;
        END IF;
      ELSIF i <= 3 AND v_alliance IS NOT NULL THEN
        UPDATE players SET alliance_id = v_alliance, alliance_role = CASE WHEN i = 2 THEN 'OFFICER' ELSE 'MEMBER' END
         WHERE id = v_id;
      END IF;
    END LOOP;
  END LOOP;
  RETURN n;
END $$;

-- ===== 12c: a bot sends an army (it briefly acts as itself, like a signed-in player) =====

CREATE OR REPLACE FUNCTION cf_bot_dispatch(p_bot uuid, p_type text, p_ttype text, p_tid text, p_share numeric)
RETURNS boolean
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_sub text; v_claims text; b players; ok boolean := true;
BEGIN
  v_sub := current_setting('request.jwt.claim.sub', true);
  v_claims := current_setting('request.jwt.claims', true);
  PERFORM set_config('request.jwt.claim.sub', p_bot::text, true);
  PERFORM set_config('request.jwt.claims', json_build_object('sub', p_bot::text)::text, true);
  BEGIN
    SELECT * INTO b FROM players WHERE id = p_bot;
    PERFORM dispatch_march(p_type, p_ttype, p_tid,
      floor(b.infantry * p_share)::bigint, floor(b.cavalry * p_share)::bigint,
      floor(b.archers * p_share)::bigint, floor(b.siege * p_share)::bigint);
  EXCEPTION WHEN OTHERS THEN
    ok := false;
  END;
  PERFORM set_config('request.jwt.claim.sub', coalesce(v_sub, ''), true);
  PERFORM set_config('request.jwt.claims', coalesce(v_claims, ''), true);
  RETURN ok;
END $$;

-- ===== 12d: one turn for one AI lord =====

CREATE OR REPLACE FUNCTION cf_bot_act(p_bot uuid) RETURNS text
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  b players; v_inc bigint; v_t jsonb; v_add numeric; v_units bigint; v_cost bigint;
  v_power numeric; v_roll numeric; v_target text; v_ttype text; v_type text := 'RAID'; v_share numeric := 0.7;
  v_busy int;
BEGIN
  SELECT * INTO b FROM players WHERE id = p_bot AND is_bot FOR UPDATE;
  IF NOT FOUND THEN RETURN 'gone'; END IF;

  -- Economy: a few minutes of income, scaled by the resource buildings.
  v_inc := 150 + (b.farm_level + b.lumber_level + b.quarry_level + b.mint_level) * 30;
  UPDATE players SET food = food + round(v_inc * 1.3), timber = timber + v_inc,
                     stone = stone + round(v_inc * 0.8), gold = gold + round(v_inc * 0.5)
   WHERE id = b.id;

  -- Growth: now and then the Keep (and every building with it) goes up a level, up to the bot's cap.
  IF b.keep_level < coalesce(b.bot_keep_cap, 10) AND random() < 0.03
     AND b.stone >= b.keep_level * 800 AND b.gold >= b.keep_level * 400 THEN
    UPDATE players SET keep_level = keep_level + 1, barracks_level = keep_level + 1, stables_level = keep_level + 1,
           archery_level = keep_level + 1, siege_level = keep_level + 1, alliance_hall_level = keep_level + 1,
           farm_level = keep_level + 1, lumber_level = keep_level + 1, quarry_level = keep_level + 1,
           mint_level = keep_level + 1, stone = stone - keep_level * 800, gold = gold - keep_level * 400
     WHERE id = b.id;
  END IF;

  -- Training: rebuild towards the troop target for its Keep level, paying food for it.
  SELECT * INTO b FROM players WHERE id = p_bot;
  v_t := cf_bot_troop_target(b.keep_level);
  IF b.infantry + b.cavalry + b.archers + b.siege < cf_unit_count(v_t) * 0.95 THEN
    v_add := 0.08;
    v_units := round(cf_unit_count(v_t) * v_add);
    v_cost := v_units * 3;
    IF b.food >= v_cost THEN
      UPDATE players SET
        infantry = LEAST(infantry + round(cf_tq(v_t,'infantry') * v_add), cf_tq(v_t,'infantry')),
        cavalry  = LEAST(cavalry  + round(cf_tq(v_t,'cavalry')  * v_add), cf_tq(v_t,'cavalry')),
        archers  = LEAST(archers  + round(cf_tq(v_t,'archers')  * v_add), cf_tq(v_t,'archers')),
        siege    = LEAST(siege    + round(cf_tq(v_t,'siege')    * v_add), cf_tq(v_t,'siege')),
        food = food - v_cost
       WHERE id = b.id;
    END IF;
  END IF;

  -- Military: only with a free march slot and a decent army at home.
  SELECT * INTO b FROM players WHERE id = p_bot;
  SELECT count(*) INTO v_busy FROM marches WHERE player_id = b.id AND status = 'MARCHING';
  IF v_busy >= 2 OR random() < 0.3 THEN RETURN 'resting'; END IF;
  IF b.infantry + b.cavalry + b.archers + b.siege < cf_unit_count(cf_bot_troop_target(b.keep_level)) * 0.5 THEN
    RETURN 'rebuilding';
  END IF;
  v_power := cf_troop_power(cf_troops(b.infantry, b.cavalry, b.archers, b.siege));
  v_roll := random();

  -- Raid a player: RAIDERs most, TURTLEs rarely, BUILDERs never.
  IF (b.bot_style = 'RAIDER' AND v_roll < 0.40) OR (b.bot_style = 'TURTLE' AND v_roll < 0.10) THEN
    SELECT p.id::text INTO v_target FROM players p
     WHERE p.home_realm_id = b.home_realm_id AND p.id <> b.id
       AND (p.alliance_id IS NULL OR p.alliance_id IS DISTINCT FROM b.alliance_id)
       AND NOT cf_at_peace(b.alliance_id, p.alliance_id)
       AND (p.shield_until IS NULL OR p.shield_until < now())
       AND p.keep_level BETWEEN b.keep_level - 3 AND b.keep_level + 2
       AND (p.infantry*10 + p.cavalry*16 + p.archers*12 + p.siege*30 + p.keep_level*60) < v_power * 0.7 * 0.7
       -- real players: only lords who struck a bot recently, or keep 4+ not hit by any bot in 8 hours
       AND (p.is_bot
            OR EXISTS (SELECT 1 FROM battle_logs l JOIN players d ON d.id = l.defender_id
                        WHERE l.attacker_id = p.id AND d.is_bot AND l.created_at > now() - interval '24 hours')
            OR (p.keep_level >= 4 AND NOT EXISTS (
                 SELECT 1 FROM battle_logs l JOIN players a ON a.id = l.attacker_id
                  WHERE l.defender_id = p.id AND a.is_bot AND l.created_at > now() - interval '8 hours')))
     ORDER BY (SELECT count(*) FROM battle_logs l WHERE l.attacker_id = p.id AND l.defender_id = b.id
                  AND l.created_at > now() - interval '24 hours') DESC,   -- grudges first
              random()
     LIMIT 1;
    IF v_target IS NOT NULL THEN v_ttype := 'KINGDOM'; v_share := 0.7; END IF;
  END IF;

  -- Hold or seize a landmark (alliance bots only).
  IF v_target IS NULL AND b.alliance_id IS NOT NULL AND random() < 0.45 THEN
    IF b.bot_style = 'TURTLE' OR random() < 0.3 THEN
      SELECT l.id INTO v_target FROM landmarks l
       WHERE l.realm_id = b.home_realm_id AND l.holder_alliance_id = b.alliance_id
         AND coalesce((SELECT sum(troop_power) FROM landmark_garrisons g WHERE g.landmark_id = l.id), 0)
             < LEAST(v_power, 2500 + b.keep_level * 600)     -- keep bot garrisons beatable
       ORDER BY random() LIMIT 1;
      IF v_target IS NOT NULL THEN v_ttype := 'LANDMARK'; v_type := 'REINFORCE'; v_share := 0.3; END IF;
    END IF;
    -- each bot alliance holds at most 2 landmarks per realm, leaving the rest for players
    IF v_target IS NULL AND (SELECT count(*) FROM landmarks WHERE holder_alliance_id = b.alliance_id
                              AND realm_id = b.home_realm_id) < 2 THEN
      SELECT l.id INTO v_target FROM landmarks l
       WHERE l.realm_id = b.home_realm_id
         AND (l.holder_alliance_id IS NULL OR (l.holder_alliance_id <> b.alliance_id
              AND NOT cf_at_peace(b.alliance_id, l.holder_alliance_id)))
         AND cf_landmark_defense(l.id) < v_power * 0.75 * 0.7
       ORDER BY random() LIMIT 1;
      IF v_target IS NOT NULL THEN v_ttype := 'LANDMARK'; v_type := 'RAID'; v_share := 0.75; END IF;
    END IF;
  END IF;

  -- Clear a Shadow Bastion it can beat.
  IF v_target IS NULL AND random() < 0.5 THEN
    SELECT id INTO v_target FROM world_nodes
     WHERE node_type = 'BASTION' AND garrison_power < v_power * 0.7 * 0.8
     ORDER BY random() LIMIT 1;
    IF v_target IS NOT NULL THEN v_ttype := 'BASTION'; v_type := 'RAID'; v_share := 0.7; END IF;
  END IF;

  -- Otherwise gather.
  IF v_target IS NULL THEN
    SELECT id INTO v_target FROM world_nodes WHERE node_type = 'RESOURCE' ORDER BY random() LIMIT 1;
    v_ttype := 'RESOURCE'; v_type := 'GATHER'; v_share := 0.25;
  END IF;

  IF cf_bot_dispatch(b.id, v_type, v_ttype, v_target, v_share) THEN
    RETURN v_type || ' ' || v_ttype || ' ' || v_target;
  END IF;
  RETURN 'failed ' || v_type || ' ' || v_ttype;
END $$;

-- Up to three AI lords whose turn has come act now; each waits 2-6 minutes before its next turn.
CREATE OR REPLACE FUNCTION cf_bot_tick() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_id uuid; n int := 0;
BEGIN
  FOR v_id IN SELECT id FROM players WHERE is_bot AND (bot_next_at IS NULL OR bot_next_at <= now())
               ORDER BY bot_next_at NULLS FIRST LIMIT 3 FOR UPDATE SKIP LOCKED LOOP
    UPDATE players SET bot_next_at = now() + make_interval(secs => 120 + floor(random() * 240)::int) WHERE id = v_id;
    BEGIN
      PERFORM cf_bot_act(v_id);
    EXCEPTION WHEN OTHERS THEN
      RAISE NOTICE 'bot % turn failed: %', v_id, SQLERRM;
    END;
    n := n + 1;
  END LOOP;
  RETURN n;
END $$;

-- ===== 12e: the battle resolver also runs the AI lords' turns =====

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
  -- AI lords take their turns
  PERFORM cf_bot_tick();
  RETURN n;
END $$;
REVOKE ALL ON FUNCTION cf_seed_bots() FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_bot_dispatch(uuid, text, text, text, numeric) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_bot_act(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_bot_tick() FROM PUBLIC, anon, authenticated;

-- Bring the AI lords into every realm.
SELECT cf_seed_bots() AS ai_lords_created;
