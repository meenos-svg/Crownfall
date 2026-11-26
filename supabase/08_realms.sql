-- ===== Crownfall part 8: realms, Waygate expeditions and visitor camps =====

INSERT INTO realms (id, name, biome_theme, opened_at, protection_ends_at)
VALUES ('realm-3', 'Emberfall Wastes', 'Volcanic Ash', now(), now())
ON CONFLICT (id) DO NOTHING;

CREATE TABLE IF NOT EXISTS camps (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  player_id    uuid NOT NULL UNIQUE REFERENCES players(id) ON DELETE CASCADE,
  realm_id     text NOT NULL REFERENCES realms(id),
  pos_x        int  NOT NULL,
  pos_y        int  NOT NULL,
  troops_json  jsonb  NOT NULL DEFAULT '{}'::jsonb,
  troop_power  bigint NOT NULL DEFAULT 0 CHECK (troop_power >= 0),
  status       text NOT NULL DEFAULT 'TRAVELING' CHECK (status IN ('TRAVELING','CAMPED','RETURNING')),
  arrives_at   timestamptz NOT NULL DEFAULT now(),
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_camps_realm ON camps (realm_id);
ALTER TABLE camps ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "read camps" ON camps;
CREATE POLICY "read camps" ON camps FOR SELECT TO authenticated USING (true);
GRANT SELECT ON camps TO authenticated;

ALTER TABLE marches ADD COLUMN IF NOT EXISTS camp_id uuid REFERENCES camps(id) ON DELETE SET NULL;
ALTER TABLE marches DROP CONSTRAINT IF EXISTS marches_target_type_check;
ALTER TABLE marches ADD CONSTRAINT marches_target_type_check
  CHECK (target_type IN ('KINGDOM','BASTION','RESOURCE','STRONGHOLD','TILE','CAMP'));

-- Waygate travel: 90 seconds, 4 seconds faster per Waygate level, never under 30.
CREATE OR REPLACE FUNCTION cf_waygate_seconds(p players) RETURNS int
LANGUAGE sql STABLE AS $$ SELECT GREATEST(90 - coalesce(p.waygate_level, 1) * 4, 30) $$;

CREATE OR REPLACE FUNCTION cf_take_camp_troops(c camps, i bigint, cv bigint, a bigint, s bigint) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v_left jsonb;
BEGIN
  i := coalesce(i, 0); cv := coalesce(cv, 0); a := coalesce(a, 0); s := coalesce(s, 0);
  IF LEAST(i, cv, a, s) < 0 THEN RAISE EXCEPTION 'Troop counts cannot be negative.'; END IF;
  IF i + cv + a + s <= 0 THEN RAISE EXCEPTION 'Select at least 1 troop to march.'; END IF;
  IF i > cf_tq(c.troops_json,'infantry') OR cv > cf_tq(c.troops_json,'cavalry')
     OR a > cf_tq(c.troops_json,'archers') OR s > cf_tq(c.troops_json,'siege') THEN
    RAISE EXCEPTION 'Your camp does not have that many troops.';
  END IF;
  v_left := cf_troops(cf_tq(c.troops_json,'infantry') - i, cf_tq(c.troops_json,'cavalry') - cv,
                      cf_tq(c.troops_json,'archers') - a, cf_tq(c.troops_json,'siege') - s);
  UPDATE camps SET troops_json = v_left, troop_power = cf_troop_power(v_left) WHERE id = c.id;
  RETURN cf_troops(i, cv, a, s);
END $$;

-- Survivors go back to the camp they marched from if it is still standing, otherwise home.
CREATE OR REPLACE FUNCTION cf_march_home(m marches, t jsonb) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF m.camp_id IS NOT NULL THEN
    UPDATE camps SET troops_json = cf_add_troops(troops_json, t),
                     troop_power = cf_troop_power(cf_add_troops(troops_json, t))
     WHERE id = m.camp_id AND status = 'CAMPED';
    IF FOUND THEN RETURN; END IF;
  END IF;
  PERFORM cf_return_troops(m.player_id, t);
END $$;

CREATE OR REPLACE FUNCTION send_expedition(p_realm text, p_x int, p_y int,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v_troops jsonb; v_id uuid; v_name text;
BEGIN
  SELECT name INTO v_name FROM realms WHERE id = p_realm;
  IF NOT FOUND THEN RAISE EXCEPTION 'Unknown realm.'; END IF;
  IF p_realm = me.home_realm_id THEN RAISE EXCEPTION 'Expeditions go to other realms. This is your home.'; END IF;
  IF EXISTS (SELECT 1 FROM camps WHERE player_id = me.id) THEN
    RAISE EXCEPTION 'You already have an expedition out. Recall it first.';
  END IF;
  IF p_x NOT BETWEEN 3 AND 96 OR p_y NOT BETWEEN 3 AND 96 THEN RAISE EXCEPTION 'Too close to the edge of the realm.'; END IF;
  v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  INSERT INTO camps (player_id, realm_id, pos_x, pos_y, troops_json, troop_power, status, arrives_at)
  VALUES (me.id, p_realm, p_x * 14, p_y * 14, v_troops, cf_troop_power(v_troops), 'TRAVELING',
          now() + make_interval(secs => cf_waygate_seconds(me)))
  RETURNING id INTO v_id;
  IF me.alliance_id IS NOT NULL THEN
    PERFORM cf_system_message(me.alliance_id, left(format('%s led an expedition through the Waygate to %s.', me.username, v_name), 300));
  END IF;
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION recall_camp() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); c camps;
BEGIN
  SELECT * INTO c FROM camps WHERE player_id = me.id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'You have no expedition out.'; END IF;
  IF c.status = 'RETURNING' THEN RAISE EXCEPTION 'Your expedition is already on its way home.'; END IF;
  UPDATE camps SET status = 'RETURNING', arrives_at = now() + make_interval(secs => cf_waygate_seconds(me))
   WHERE id = c.id;
END $$;

-- Old signatures are replaced by versions with an extra optional argument.
DROP FUNCTION IF EXISTS dispatch_march(text, text, text, bigint, bigint, bigint, bigint);
DROP FUNCTION IF EXISTS found_stronghold(int, int);
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
    SELECT r.* INTO v_realm FROM realms r WHERE r.accepting_new_players
      ORDER BY (SELECT count(*) FROM players p WHERE p.home_realm_id = r.id), r.opened_at LIMIT 1;
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

CREATE OR REPLACE FUNCTION cf_war_target(p_me players, p_type text, p_id text,
  OUT x int, OUT y int, OUT tname text, OUT owner uuid, OUT owner_alliance uuid,
  OUT defense bigint, OUT node world_nodes)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE d players; sh strongholds; cp camps;
BEGIN
  IF p_type = 'KINGDOM' THEN
    SELECT * INTO d FROM players WHERE id::text = p_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'That kingdom no longer exists.'; END IF;
    IF d.id = p_me.id THEN RAISE EXCEPTION 'You cannot attack your own castle.'; END IF;
    IF d.home_realm_id <> p_me.home_realm_id THEN
      RAISE EXCEPTION 'That kingdom is in another realm. Set up a camp there first.';
    END IF;
    IF p_me.alliance_id IS NOT NULL AND d.alliance_id = p_me.alliance_id THEN
      RAISE EXCEPTION 'You cannot attack a member of your own alliance.';
    END IF;
    IF cf_at_peace(p_me.alliance_id, d.alliance_id) THEN
      RAISE EXCEPTION 'Your alliance has a pact with theirs.';
    END IF;
    IF d.shield_until IS NOT NULL AND d.shield_until > now() THEN
      RAISE EXCEPTION 'That kingdom is protected by a Peace Shield.';
    END IF;
    x := d.pos_x; y := d.pos_y; tname := d.username; owner := d.id; owner_alliance := d.alliance_id;
    defense := round((d.infantry*10 + d.cavalry*16 + d.archers*12 + d.siege*30)
                     * (1 + cf_tech_level(d.alliance_id, 'DEFENSE') * 0.06)) + d.keep_level * 60;
  ELSIF p_type = 'STRONGHOLD' THEN
    SELECT * INTO sh FROM strongholds WHERE id::text = p_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'That stronghold has already fallen.'; END IF;
    IF sh.home_realm_id <> p_me.home_realm_id THEN
      RAISE EXCEPTION 'That stronghold is in another realm. Set up a camp there first.';
    END IF;
    IF sh.alliance_id = p_me.alliance_id THEN RAISE EXCEPTION 'That is your own alliance''s stronghold.'; END IF;
    IF cf_at_peace(p_me.alliance_id, sh.alliance_id) THEN RAISE EXCEPTION 'Your alliance has a pact with theirs.'; END IF;
    x := sh.pos_x; y := sh.pos_y; tname := cf_stronghold_name(sh.alliance_id);
    owner_alliance := sh.alliance_id; defense := cf_stronghold_defense(sh.id);
  ELSIF p_type = 'CAMP' THEN
    SELECT * INTO cp FROM camps WHERE id::text = p_id AND status = 'CAMPED';
    IF NOT FOUND THEN RAISE EXCEPTION 'That camp has packed up.'; END IF;
    SELECT * INTO d FROM players WHERE id = cp.player_id;
    IF d.id = p_me.id THEN RAISE EXCEPTION 'That is your own camp.'; END IF;
    IF cp.realm_id <> p_me.home_realm_id THEN RAISE EXCEPTION 'That camp is in another realm.'; END IF;
    IF p_me.alliance_id IS NOT NULL AND d.alliance_id = p_me.alliance_id THEN
      RAISE EXCEPTION 'You cannot attack a member of your own alliance.';
    END IF;
    IF cf_at_peace(p_me.alliance_id, d.alliance_id) THEN RAISE EXCEPTION 'Your alliance has a pact with theirs.'; END IF;
    x := cp.pos_x; y := cp.pos_y; tname := left(d.username || '''s camp', 48); owner := d.id; owner_alliance := d.alliance_id;
    defense := round(cp.troop_power * (1 + cf_tech_level(d.alliance_id, 'DEFENSE') * 0.06));
  ELSIF p_type IN ('BASTION','RESOURCE') THEN
    SELECT * INTO node FROM world_nodes WHERE id = p_id AND node_type = p_type;
    IF NOT FOUND THEN RAISE EXCEPTION 'Unknown target.'; END IF;
    x := node.map_x * 14; y := node.map_y * 14; tname := node.name; defense := node.garrison_power;
  ELSE
    RAISE EXCEPTION 'Unknown target type %', p_type;
  END IF;
END $$;

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
  IF p_type = 'RAID' AND p_target_type NOT IN ('KINGDOM','BASTION','STRONGHOLD','CAMP') THEN
    RAISE EXCEPTION 'Raids target kingdoms, bastions, strongholds or camps.';
  END IF;
  IF p_type = 'REINFORCE' AND p_target_type <> 'STRONGHOLD' THEN
    RAISE EXCEPTION 'Only strongholds can be reinforced.';
  END IF;
  IF p_type NOT IN ('RAID','GATHER','REINFORCE') THEN RAISE EXCEPTION 'Unknown march type %', p_type; END IF;
  IF (SELECT count(*) FROM marches WHERE player_id = me.id AND status = 'MARCHING') >= 3 THEN
    RAISE EXCEPTION 'All 3 march slots are busy. Wait for an army to return.';
  END IF;

  IF p_type = 'REINFORCE' THEN
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

CREATE OR REPLACE FUNCTION cf_resolve_march(p_march uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  m marches; att players; def players; node world_nodes; c record; sh strongholds; sb record; v_sh_alliance uuid; v_camp camps;
  v_atk numeric; v_def numeric; v_left jsonb; v_def_left jsonb; v_carry bigint;
  lf bigint := 0; lt bigint := 0; ls bigint := 0; lg bigint := 0;
  v_def_losses bigint := 0;
BEGIN
  SELECT * INTO m FROM marches WHERE id = p_march AND status = 'MARCHING' FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN; END IF;

  IF m.target_type = 'KINGDOM' THEN
    -- lock both castles in a fixed order so two crossing attacks can't deadlock
    PERFORM 1 FROM players WHERE id IN (m.player_id, m.target_id::uuid) ORDER BY id FOR UPDATE;
  ELSIF m.target_type = 'CAMP' THEN
    PERFORM 1 FROM players WHERE id = m.player_id OR id = (SELECT player_id FROM camps WHERE id::text = m.target_id)
      ORDER BY id FOR UPDATE;
  END IF;
  SELECT * INTO att FROM players WHERE id = m.player_id FOR UPDATE;
  IF NOT FOUND THEN UPDATE marches SET status = 'COMPLETED' WHERE id = m.id; RETURN; END IF;
  UPDATE marches SET status = 'COMPLETED', returns_at = now() WHERE id = m.id;

  -- Reinforcement: join the stronghold garrison
  IF m.march_type = 'REINFORCE' THEN
    SELECT * INTO sh FROM strongholds WHERE id::text = m.target_id FOR UPDATE;
    IF NOT FOUND OR NOT cf_can_reinforce(att, sh) THEN
      PERFORM cf_march_home(m, m.troops_json);
      RETURN;
    END IF;
    INSERT INTO stronghold_garrisons (stronghold_id, player_id, alliance_id, march_id, troops_json, troop_power, is_allied_relief)
    VALUES (sh.id, att.id, att.alliance_id, m.id, m.troops_json, m.troop_power, att.alliance_id IS DISTINCT FROM sh.alliance_id)
    ON CONFLICT (stronghold_id, player_id) DO UPDATE
      SET troops_json = cf_add_troops(stronghold_garrisons.troops_json, EXCLUDED.troops_json),
          troop_power = stronghold_garrisons.troop_power + EXCLUDED.troop_power,
          march_id = EXCLUDED.march_id, alliance_id = EXCLUDED.alliance_id;
    PERFORM cf_system_message(sh.alliance_id, left(format('%s %s the stronghold with %s power.', att.username,
      CASE WHEN att.alliance_id IS DISTINCT FROM sh.alliance_id THEN 'brought relief to' ELSE 'reinforced' END,
      m.troop_power), 300));
    RETURN;
  END IF;

  -- Gathering: no fight, collect the site's reward
  IF m.march_type = 'GATHER' THEN
    SELECT * INTO node FROM world_nodes WHERE id = m.target_id;
    PERFORM cf_march_home(m, m.troops_json);
    UPDATE players SET food = food + coalesce(node.reward_food, 0), timber = timber + coalesce(node.reward_timber, 0),
                       stone = stone + coalesce(node.reward_stone, 0), gold = gold + coalesce(node.reward_gold, 0),
                       honor = honor + 10
     WHERE id = att.id;
    INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
      defender_name, defender_kind, victory, attacker_power, march_id,
      loot_food, loot_timber, loot_stone, loot_gold)
    VALUES (m.realm_id, 'GATHER', att.id, att.username, att.alliance_id,
      coalesce(node.name, 'Resource site'), 'RESOURCE', true, m.troop_power, m.id,
      coalesce(node.reward_food,0), coalesce(node.reward_timber,0), coalesce(node.reward_stone,0), coalesce(node.reward_gold,0));
    RETURN;
  END IF;

  v_atk := (m.troop_power + CASE WHEN m.target_type IN ('KINGDOM','BASTION','STRONGHOLD','CAMP') THEN cf_tq(m.troops_json,'siege') * 12 ELSE 0 END)
           * (1 + cf_tech_level(att.alliance_id, 'ATTACK') * 0.06);

  IF m.target_type = 'BASTION' THEN
    SELECT * INTO node FROM world_nodes WHERE id = m.target_id;
    v_def := coalesce(node.garrison_power, 1200);
    c := cf_combat(v_atk, v_def);
    v_left := cf_scale_troops(m.troops_json, 1 - c.att_loss);
    IF c.victory THEN
      lf := node.reward_food; lt := node.reward_timber; ls := node.reward_stone; lg := node.reward_gold;
    END IF;
    PERFORM cf_march_home(m, v_left);
    UPDATE players SET food = food + lf, timber = timber + lt, stone = stone + ls, gold = gold + lg,
                       honor = honor + CASE WHEN c.victory THEN 80 + coalesce(node.level, 1) * 40 ELSE 15 END
     WHERE id = att.id;
    INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
      defender_name, defender_kind, victory, attacker_power, defender_power, attacker_losses,
      loot_food, loot_timber, loot_stone, loot_gold, march_id)
    VALUES (m.realm_id, 'RAID', att.id, att.username, att.alliance_id,
      coalesce(node.name, 'Shadow Bastion'), 'BASTION', c.victory, round(v_atk), round(v_def),
      cf_unit_count(m.troops_json) - cf_unit_count(v_left), lf, lt, ls, lg, m.id);
    RETURN;
  END IF;

  IF m.target_type = 'STRONGHOLD' THEN
    SELECT alliance_id INTO v_sh_alliance FROM strongholds WHERE id::text = m.target_id;
    IF v_sh_alliance IS NULL OR v_sh_alliance = att.alliance_id OR cf_at_peace(att.alliance_id, v_sh_alliance) THEN
      PERFORM cf_march_home(m, m.troops_json);   -- fallen, or now a friend: army turns back
      RETURN;
    END IF;
    sb := cf_stronghold_battle(m.target_id::uuid, v_atk, att.username);
    v_left := cf_scale_troops(m.troops_json, 1 - sb.att_loss);
    PERFORM cf_march_home(m, v_left);
    UPDATE players SET honor = honor + CASE WHEN sb.destroyed THEN 400 WHEN sb.victory THEN 120 ELSE 15 END,
                       gold = gold + CASE WHEN sb.destroyed THEN 1500 ELSE 0 END
     WHERE id = att.id;
    INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
      defender_name, defender_alliance_id, defender_kind, stronghold_id, victory,
      attacker_power, defender_power, attacker_losses, defender_losses, loot_gold, march_id)
    VALUES (m.realm_id, 'SIEGE', att.id, att.username, att.alliance_id,
      sb.sh_name, sb.owner_alliance, 'STRONGHOLD', CASE WHEN sb.destroyed THEN NULL ELSE m.target_id::uuid END, sb.victory,
      round(v_atk), round(sb.d), cf_unit_count(m.troops_json) - cf_unit_count(v_left), sb.def_losses,
      CASE WHEN sb.destroyed THEN 1500 ELSE 0 END, m.id);
    RETURN;
  END IF;

  IF m.target_type = 'CAMP' THEN
    SELECT * INTO v_camp FROM camps WHERE id::text = m.target_id AND status = 'CAMPED' FOR UPDATE;
    IF NOT FOUND THEN PERFORM cf_march_home(m, m.troops_json); RETURN; END IF;
    SELECT * INTO def FROM players WHERE id = v_camp.player_id FOR UPDATE;
    IF (att.alliance_id IS NOT NULL AND def.alliance_id = att.alliance_id) OR cf_at_peace(att.alliance_id, def.alliance_id) THEN
      PERFORM cf_march_home(m, m.troops_json); RETURN;
    END IF;
    v_def := round(v_camp.troop_power * (1 + cf_tech_level(def.alliance_id, 'DEFENSE') * 0.06));
    c := cf_combat(v_atk, v_def);
    v_left := cf_scale_troops(m.troops_json, 1 - c.att_loss);
    v_def_left := cf_scale_troops(v_camp.troops_json, 1 - c.def_loss);
    v_def_losses := cf_unit_count(v_camp.troops_json) - cf_unit_count(v_def_left);
    IF c.victory OR cf_unit_count(v_def_left) = 0 THEN
      PERFORM cf_return_troops(def.id, v_def_left);     -- routed: survivors flee home through the Waygate
      DELETE FROM camps WHERE id = v_camp.id;
    ELSE
      UPDATE camps SET troops_json = v_def_left, troop_power = cf_troop_power(v_def_left) WHERE id = v_camp.id;
      UPDATE players SET honor = honor + 40 WHERE id = def.id;
    END IF;
    PERFORM cf_march_home(m, v_left);
    UPDATE players SET honor = honor + CASE WHEN c.victory THEN 80 ELSE 10 END WHERE id = att.id;
    INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
      defender_id, defender_name, defender_alliance_id, defender_kind, victory,
      attacker_power, defender_power, attacker_losses, defender_losses, march_id)
    VALUES (m.realm_id, 'RAID', att.id, att.username, att.alliance_id,
      def.id, left(def.username || '''s camp', 48), def.alliance_id, 'CAMP', c.victory,
      round(v_atk), round(v_def), cf_unit_count(m.troops_json) - cf_unit_count(v_left), v_def_losses, m.id);
    IF def.alliance_id IS NOT NULL THEN
      PERFORM cf_system_message(def.alliance_id, left(format('%s''s camp abroad was raided by %s and %s!',
        def.username, att.username, CASE WHEN c.victory THEN 'routed' ELSE 'held firm' END), 300));
    END IF;
    RETURN;
  END IF;

  -- Kingdom raid
  SELECT * INTO def FROM players WHERE id::text = m.target_id;
  IF NOT FOUND OR (def.shield_until IS NOT NULL AND def.shield_until > now())
     OR (att.alliance_id IS NOT NULL AND def.alliance_id = att.alliance_id)
     OR cf_at_peace(att.alliance_id, def.alliance_id) THEN
    PERFORM cf_march_home(m, m.troops_json);   -- target gone, shielded or now an ally: army turns back
    RETURN;
  END IF;

  v_def := round((def.infantry*10 + def.cavalry*16 + def.archers*12 + def.siege*30)
                 * (1 + cf_tech_level(def.alliance_id, 'DEFENSE') * 0.06)) + def.keep_level * 60;
  c := cf_combat(v_atk, v_def);
  v_left := cf_scale_troops(m.troops_json, 1 - c.att_loss);
  v_def_left := cf_scale_troops(cf_troops(def.infantry, def.cavalry, def.archers, def.siege), 1 - c.def_loss);
  v_def_losses := (def.infantry + def.cavalry + def.archers + def.siege) - cf_unit_count(v_def_left);

  IF c.victory THEN
    v_carry := GREATEST(cf_unit_count(v_left) * 20, 200) / 4;   -- each resource up to a quarter of carry
    lf := LEAST(floor(def.food * 0.15)::bigint, v_carry);
    lt := LEAST(floor(def.timber * 0.15)::bigint, v_carry);
    ls := LEAST(floor(def.stone * 0.15)::bigint, v_carry);
    lg := LEAST(floor(def.gold * 0.15)::bigint, v_carry);
  END IF;

  UPDATE players SET infantry = cf_tq(v_def_left,'infantry'), cavalry = cf_tq(v_def_left,'cavalry'),
                     archers = cf_tq(v_def_left,'archers'), siege = cf_tq(v_def_left,'siege'),
                     food = food - lf, timber = timber - lt, stone = stone - ls, gold = gold - lg,
                     honor = honor + CASE WHEN c.victory THEN 0 ELSE 60 END
   WHERE id = def.id;
  PERFORM cf_march_home(m, v_left);
  UPDATE players SET food = food + lf, timber = timber + lt, stone = stone + ls, gold = gold + lg,
                     honor = honor + CASE WHEN c.victory THEN 120 ELSE 15 END
   WHERE id = att.id;

  INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
    defender_id, defender_name, defender_alliance_id, defender_kind, victory,
    attacker_power, defender_power, attacker_losses, defender_losses,
    loot_food, loot_timber, loot_stone, loot_gold, march_id)
  VALUES (m.realm_id, 'RAID', att.id, att.username, att.alliance_id,
    def.id, def.username, def.alliance_id, 'PLAYER', c.victory,
    round(v_atk), round(v_def), cf_unit_count(m.troops_json) - cf_unit_count(v_left), v_def_losses,
    lf, lt, ls, lg, m.id);

  IF def.alliance_id IS NOT NULL THEN
    PERFORM cf_system_message(def.alliance_id,
      left(format('%s was raided by %s and %s!', def.username, att.username,
                  CASE WHEN c.victory THEN 'fell' ELSE 'held the walls' END), 300));
  END IF;
END $$;

CREATE OR REPLACE FUNCTION recall_march(p_march uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE m marches;
BEGIN
  PERFORM cf_me_for_update();
  SELECT * INTO m FROM marches
   WHERE id = p_march AND player_id = auth.uid() AND status = 'MARCHING' AND march_type <> 'RALLY'
   FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'That march can no longer be recalled.'; END IF;
  UPDATE marches SET status = 'RECALLED', returns_at = now() WHERE id = m.id;
  PERFORM cf_march_home(m, m.troops_json);
END $$;

CREATE OR REPLACE FUNCTION found_stronghold(p_x int, p_y int, p_realm text DEFAULT NULL) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v_id uuid; v_near text; v_realm text;
BEGIN
  v_realm := coalesce(nullif(p_realm, ''), me.home_realm_id);
  IF v_realm <> me.home_realm_id AND NOT EXISTS (
       SELECT 1 FROM camps WHERE player_id = me.id AND realm_id = v_realm AND status = 'CAMPED') THEN
    RAISE EXCEPTION 'Set up your camp in that realm before raising a stronghold there.';
  END IF;
  IF me.alliance_role <> 'LEADER' THEN RAISE EXCEPTION 'Only the alliance leader can found a stronghold.'; END IF;
  IF EXISTS (SELECT 1 FROM strongholds WHERE alliance_id = me.alliance_id) THEN
    RAISE EXCEPTION 'Your alliance already has a stronghold.';
  END IF;
  IF p_x NOT BETWEEN 3 AND 96 OR p_y NOT BETWEEN 3 AND 96 THEN RAISE EXCEPTION 'Too close to the edge of the realm.'; END IF;
  IF me.stone < 3000 OR me.gold < 2000 THEN RAISE EXCEPTION 'A stronghold costs 3,000 Stone and 2,000 Gold.'; END IF;

  SELECT name INTO v_near FROM world_nodes WHERE sqrt(power(map_x - p_x, 2) + power(map_y - p_y, 2)) < 4 LIMIT 1;
  IF v_near IS NOT NULL THEN RAISE EXCEPTION 'Too close to %.', v_near; END IF;
  SELECT username INTO v_near FROM players
   WHERE home_realm_id = v_realm AND sqrt(power(pos_x / 14.0 - p_x, 2) + power(pos_y / 14.0 - p_y, 2)) < 3 LIMIT 1;
  IF v_near IS NOT NULL THEN RAISE EXCEPTION 'Too close to the castle of %.', v_near; END IF;
  IF EXISTS (SELECT 1 FROM strongholds WHERE home_realm_id = v_realm
               AND sqrt(power(pos_x / 14.0 - p_x, 2) + power(pos_y / 14.0 - p_y, 2)) < 8) THEN
    RAISE EXCEPTION 'Too close to another stronghold.';
  END IF;

  UPDATE players SET stone = stone - 3000, gold = gold - 2000 WHERE id = me.id;
  INSERT INTO strongholds (alliance_id, home_realm_id, pos_x, pos_y, level, durability, max_durability)
  VALUES (me.alliance_id, v_realm, p_x * 14, p_y * 14, 1, 15000, 15000)
  RETURNING id INTO v_id;
  PERFORM cf_system_message(me.alliance_id,
    format('%s raised the alliance stronghold at (%s, %s)! Send troops to garrison it.', me.username, p_x, p_y));
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION resolve_due_battles() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; v_id uuid; n int := 0; lx int; ly int;
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
    PERFORM cf_resolve_march(v_id); n := n + 1;
  END LOOP;
  RETURN n;
END $$;
-- ===== permissions =====
REVOKE ALL ON FUNCTION cf_take_camp_troops(camps, bigint, bigint, bigint, bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_march_home(marches, jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION send_expedition(text, int, int, bigint, bigint, bigint, bigint) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION recall_camp() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION dispatch_march(text, text, text, bigint, bigint, bigint, bigint, boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION found_stronghold(int, int, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION send_expedition(text, int, int, bigint, bigint, bigint, bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION recall_camp() TO authenticated;
GRANT EXECUTE ON FUNCTION dispatch_march(text, text, text, bigint, bigint, bigint, bigint, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION found_stronghold(int, int, text) TO authenticated;
