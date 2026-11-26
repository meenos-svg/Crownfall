-- ===== Crownfall part 6: marches, rallies and battles on the server =====

ALTER TABLE rallies ADD COLUMN IF NOT EXISTS arrives_at timestamptz;
ALTER TABLE rallies ADD COLUMN IF NOT EXISTS travel_seconds int NOT NULL DEFAULT 20;
ALTER TABLE rallies ADD COLUMN IF NOT EXISTS target_power bigint NOT NULL DEFAULT 0;
ALTER TABLE battle_logs ADD COLUMN IF NOT EXISTS march_id uuid;
ALTER TABLE battle_logs ADD COLUMN IF NOT EXISTS rally_id uuid;
ALTER TABLE battle_logs ADD COLUMN IF NOT EXISTS defender_kind text NOT NULL DEFAULT 'PLAYER';
ALTER TABLE battle_logs DROP CONSTRAINT IF EXISTS battle_logs_battle_type_check;
ALTER TABLE battle_logs ADD CONSTRAINT battle_logs_battle_type_check
  CHECK (battle_type IN ('RAID','ATTACK','RALLY','SIEGE','DEFENSE','GATHER'));
CREATE INDEX IF NOT EXISTS idx_battles_march ON battle_logs (march_id);
CREATE INDEX IF NOT EXISTS idx_battles_rally ON battle_logs (rally_id);

CREATE TABLE IF NOT EXISTS world_nodes (
  id             text PRIMARY KEY,
  name           text NOT NULL,
  node_type      text NOT NULL CHECK (node_type IN ('BASTION','RESOURCE')),
  level          int  NOT NULL DEFAULT 1,
  map_x          int  NOT NULL,
  map_y          int  NOT NULL,
  garrison_power bigint NOT NULL DEFAULT 0,
  reward_food    bigint NOT NULL DEFAULT 0,
  reward_timber  bigint NOT NULL DEFAULT 0,
  reward_stone   bigint NOT NULL DEFAULT 0,
  reward_gold    bigint NOT NULL DEFAULT 0
);
ALTER TABLE world_nodes ENABLE ROW LEVEL SECURITY;
DROP POLICY IF EXISTS "read world nodes" ON world_nodes;
CREATE POLICY "read world nodes" ON world_nodes FOR SELECT TO authenticated USING (true);

INSERT INTO world_nodes VALUES
 ('bastion_1','Ashen Watchtower','BASTION',1,28,34,850,600,500,400,260),
 ('bastion_2','Ironfang Outpost','BASTION',2,66,26,1650,950,820,680,420),
 ('bastion_3','Crimson Redoubt','BASTION',3,44,62,2800,1450,1250,1050,680),
 ('bastion_4','Dreadspire Keep','BASTION',4,74,58,4500,2100,1850,1600,1050),
 ('bastion_5','Obsidian Citadel','BASTION',5,50,50,7200,3200,2800,2500,1800),
 ('bastion_6','Blighted Fortress','BASTION',2,22,72,1800,1000,900,740,450),
 ('bastion_7','Gilded Bastion','BASTION',3,80,78,3100,1500,1300,1100,950),
 ('bastion_8','Shattered Crown Fort','BASTION',4,30,18,5200,2400,2100,1900,1300),
 ('res_food_1','Golden Grain Vale','RESOURCE',2,36,42,0,950,0,0,0),
 ('res_food_2','Riverbend Pastures','RESOURCE',3,58,70,0,1400,0,0,0),
 ('res_food_3','Sunfall Orchards','RESOURCE',1,20,48,0,650,0,0,0),
 ('res_timber_1','Darkwood Glade','RESOURCE',2,60,40,0,0,950,0,0),
 ('res_timber_2','Elder Pine Ridge','RESOURCE',3,26,56,0,0,1400,0,0),
 ('res_timber_3','Thornbark Thicket','RESOURCE',1,76,36,0,0,650,0,0),
 ('res_stone_1','Basalt Crag Vein','RESOURCE',2,48,28,0,0,0,850,0),
 ('res_stone_2','Obsidian Quarry','RESOURCE',3,68,66,0,0,0,1250,0),
 ('res_stone_3','Granite Chasm','RESOURCE',1,34,78,0,0,0,600,0),
 ('res_gold_1','Sovereign Gold Seam','RESOURCE',3,52,46,0,0,0,0,750),
 ('res_gold_2','Ember Vault Mine','RESOURCE',2,40,20,0,0,0,0,520),
 ('res_gold_3','Crownfall Aureate Pit','RESOURCE',4,82,50,0,0,0,0,1100)
ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, node_type = EXCLUDED.node_type,
  level = EXCLUDED.level, map_x = EXCLUDED.map_x, map_y = EXCLUDED.map_y,
  garrison_power = EXCLUDED.garrison_power, reward_food = EXCLUDED.reward_food,
  reward_timber = EXCLUDED.reward_timber, reward_stone = EXCLUDED.reward_stone,
  reward_gold = EXCLUDED.reward_gold;

-- ===== 6b: small helpers =====

CREATE OR REPLACE FUNCTION cf_troops(i bigint, c bigint, a bigint, s bigint) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT jsonb_build_object('infantry', i, 'cavalry', c, 'archers', a, 'siege', s)
$$;

CREATE OR REPLACE FUNCTION cf_tq(t jsonb, k text) RETURNS bigint
LANGUAGE sql IMMUTABLE AS $$ SELECT coalesce((t->>k)::bigint, 0) $$;

CREATE OR REPLACE FUNCTION cf_troop_power(t jsonb) RETURNS bigint
LANGUAGE sql IMMUTABLE AS $$
  SELECT cf_tq(t,'infantry')*10 + cf_tq(t,'cavalry')*16 + cf_tq(t,'archers')*12 + cf_tq(t,'siege')*30
$$;

CREATE OR REPLACE FUNCTION cf_unit_count(t jsonb) RETURNS bigint
LANGUAGE sql IMMUTABLE AS $$
  SELECT cf_tq(t,'infantry') + cf_tq(t,'cavalry') + cf_tq(t,'archers') + cf_tq(t,'siege')
$$;

CREATE OR REPLACE FUNCTION cf_scale_troops(t jsonb, keep numeric) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT cf_troops(floor(cf_tq(t,'infantry') * keep)::bigint, floor(cf_tq(t,'cavalry') * keep)::bigint,
                   floor(cf_tq(t,'archers') * keep)::bigint, floor(cf_tq(t,'siege') * keep)::bigint)
$$;

CREATE OR REPLACE FUNCTION cf_tech_level(p_alliance uuid, p_kind text) RETURNS int
LANGUAGE sql STABLE AS $$
  SELECT coalesce((SELECT CASE p_kind
      WHEN 'MARCH' THEN tech_march_level WHEN 'YIELD' THEN tech_yield_level
      WHEN 'ATTACK' THEN tech_attack_level ELSE tech_defense_level END
    FROM alliances WHERE id = p_alliance), 0)
$$;

-- Same formula as the app: 10s + 0.45s per map square, 8..45s, faster with march tech.
CREATE OR REPLACE FUNCTION cf_travel_seconds(x1 int, y1 int, x2 int, y2 int, p_alliance uuid) RETURNS int
LANGUAGE sql STABLE AS $$
  SELECT LEAST(GREATEST(round(
    (10 + sqrt(power(x2 - x1, 2) + power(y2 - y1, 2)) / 14.0 * 0.45)
    * (1 - LEAST(cf_tech_level(p_alliance, 'MARCH') * 0.05, 0.5)))::int, 8), 45)
$$;

CREATE OR REPLACE FUNCTION cf_combat(a numeric, d numeric,
  OUT victory boolean, OUT att_loss numeric, OUT def_loss numeric)
LANGUAGE plpgsql IMMUTABLE AS $$
DECLARE ratio numeric;
BEGIN
  victory := a >= d;
  ratio := LEAST(GREATEST(d / GREATEST(a, 1), 0.08), 1.4);
  att_loss := CASE WHEN victory THEN LEAST(GREATEST(ratio * 0.14, 0.03), 0.22)
                   ELSE LEAST(0.28 + ratio * 0.12, 0.45) END;
  IF d <= 0 THEN def_loss := 0;
  ELSIF victory THEN def_loss := LEAST(GREATEST(0.12 + (a / d) * 0.08, 0.12), 0.30);
  ELSE def_loss := LEAST(GREATEST((a / d) * 0.15, 0.02), 0.20);
  END IF;
END $$;

CREATE OR REPLACE FUNCTION cf_take_troops(p players, i bigint, c bigint, a bigint, s bigint) RETURNS jsonb
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  i := coalesce(i, 0); c := coalesce(c, 0); a := coalesce(a, 0); s := coalesce(s, 0);
  IF LEAST(i, c, a, s) < 0 THEN RAISE EXCEPTION 'Troop counts cannot be negative.'; END IF;
  IF i + c + a + s <= 0 THEN RAISE EXCEPTION 'Select at least 1 troop to march.'; END IF;
  IF i > p.infantry OR c > p.cavalry OR a > p.archers OR s > p.siege THEN
    RAISE EXCEPTION 'You do not have that many troops at home.';
  END IF;
  UPDATE players SET infantry = infantry - i, cavalry = cavalry - c,
                     archers = archers - a, siege = siege - s
   WHERE id = p.id;
  RETURN cf_troops(i, c, a, s);
END $$;

CREATE OR REPLACE FUNCTION cf_return_troops(p_player uuid, t jsonb) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  UPDATE players SET infantry = infantry + cf_tq(t,'infantry'), cavalry = cavalry + cf_tq(t,'cavalry'),
                     archers = archers + cf_tq(t,'archers'), siege = siege + cf_tq(t,'siege')
   WHERE id = p_player;
$$;

-- Finds a target and checks it can be attacked by p_me.
CREATE OR REPLACE FUNCTION cf_war_target(p_me players, p_type text, p_id text,
  OUT x int, OUT y int, OUT tname text, OUT owner uuid, OUT owner_alliance uuid,
  OUT defense bigint, OUT node world_nodes)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE d players;
BEGIN
  IF p_type = 'KINGDOM' THEN
    SELECT * INTO d FROM players WHERE id::text = p_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'That kingdom no longer exists.'; END IF;
    IF d.id = p_me.id THEN RAISE EXCEPTION 'You cannot attack your own castle.'; END IF;
    IF p_me.alliance_id IS NOT NULL AND d.alliance_id = p_me.alliance_id THEN
      RAISE EXCEPTION 'You cannot attack a member of your own alliance.';
    END IF;
    IF d.shield_until IS NOT NULL AND d.shield_until > now() THEN
      RAISE EXCEPTION 'That kingdom is protected by a Peace Shield.';
    END IF;
    x := d.pos_x; y := d.pos_y; tname := d.username; owner := d.id; owner_alliance := d.alliance_id;
    defense := round((d.infantry*10 + d.cavalry*16 + d.archers*12 + d.siege*30)
                     * (1 + cf_tech_level(d.alliance_id, 'DEFENSE') * 0.06)) + d.keep_level * 60;
  ELSIF p_type IN ('BASTION','RESOURCE') THEN
    SELECT * INTO node FROM world_nodes WHERE id = p_id AND node_type = p_type;
    IF NOT FOUND THEN RAISE EXCEPTION 'Unknown target.'; END IF;
    x := node.map_x * 14; y := node.map_y * 14; tname := node.name; defense := node.garrison_power;
  ELSE
    RAISE EXCEPTION 'Unknown target type %', p_type;
  END IF;
END $$;

-- ===== 6c: sending and recalling marches =====

CREATE OR REPLACE FUNCTION dispatch_march(p_type text, p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_id uuid;
BEGIN
  IF p_type = 'ATTACK' THEN p_type := 'RAID'; END IF;
  IF p_type = 'GATHER' AND p_target_type <> 'RESOURCE' THEN
    RAISE EXCEPTION 'Gathering only works on resource sites.';
  END IF;
  IF p_type = 'RAID' AND p_target_type NOT IN ('KINGDOM','BASTION') THEN
    RAISE EXCEPTION 'Raids target kingdoms or bastions.';
  END IF;
  IF p_type NOT IN ('RAID','GATHER') THEN RAISE EXCEPTION 'Unknown march type %', p_type; END IF;
  IF (SELECT count(*) FROM marches WHERE player_id = me.id AND status = 'MARCHING') >= 3 THEN
    RAISE EXCEPTION 'All 3 march slots are busy. Wait for an army to return.';
  END IF;

  t := cf_war_target(me, p_target_type, p_target_id);
  v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  IF p_target_type = 'KINGDOM' THEN
    UPDATE players SET shield_until = NULL WHERE id = me.id;  -- attacking drops your own shield
  END IF;
  v_secs := cf_travel_seconds(me.pos_x, me.pos_y, t.x, t.y, me.alliance_id);

  INSERT INTO marches (player_id, realm_id, march_type, target_type, target_id,
                       start_x, start_y, target_x, target_y, departed_at, arrives_at,
                       troops_json, troop_power, status)
  VALUES (me.id, me.home_realm_id, p_type, p_target_type, p_target_id,
          me.pos_x, me.pos_y, t.x, t.y, now(), now() + make_interval(secs => v_secs),
          v_troops, cf_troop_power(v_troops), 'MARCHING')
  RETURNING id INTO v_id;
  RETURN v_id;
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
  PERFORM cf_return_troops(m.player_id, m.troops_json);
END $$;

-- ===== 6d: resolving one march =====

CREATE OR REPLACE FUNCTION cf_resolve_march(p_march uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  m marches; att players; def players; node world_nodes; c record;
  v_atk numeric; v_def numeric; v_left jsonb; v_def_left jsonb; v_carry bigint;
  lf bigint := 0; lt bigint := 0; ls bigint := 0; lg bigint := 0;
  v_def_losses bigint := 0;
BEGIN
  SELECT * INTO m FROM marches WHERE id = p_march AND status = 'MARCHING' FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN; END IF;

  IF m.target_type = 'KINGDOM' THEN
    -- lock both castles in a fixed order so two crossing attacks can't deadlock
    PERFORM 1 FROM players WHERE id IN (m.player_id, m.target_id::uuid) ORDER BY id FOR UPDATE;
  END IF;
  SELECT * INTO att FROM players WHERE id = m.player_id FOR UPDATE;
  IF NOT FOUND THEN UPDATE marches SET status = 'COMPLETED' WHERE id = m.id; RETURN; END IF;
  UPDATE marches SET status = 'COMPLETED', returns_at = now() WHERE id = m.id;

  -- Gathering: no fight, collect the site's reward
  IF m.march_type = 'GATHER' THEN
    SELECT * INTO node FROM world_nodes WHERE id = m.target_id;
    PERFORM cf_return_troops(att.id, m.troops_json);
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

  v_atk := (m.troop_power + CASE WHEN m.target_type IN ('KINGDOM','BASTION') THEN cf_tq(m.troops_json,'siege') * 12 ELSE 0 END)
           * (1 + cf_tech_level(att.alliance_id, 'ATTACK') * 0.06);

  IF m.target_type = 'BASTION' THEN
    SELECT * INTO node FROM world_nodes WHERE id = m.target_id;
    v_def := coalesce(node.garrison_power, 1200);
    c := cf_combat(v_atk, v_def);
    v_left := cf_scale_troops(m.troops_json, 1 - c.att_loss);
    IF c.victory THEN
      lf := node.reward_food; lt := node.reward_timber; ls := node.reward_stone; lg := node.reward_gold;
    END IF;
    PERFORM cf_return_troops(att.id, v_left);
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

  -- Kingdom raid
  SELECT * INTO def FROM players WHERE id::text = m.target_id;
  IF NOT FOUND OR (def.shield_until IS NOT NULL AND def.shield_until > now())
     OR (att.alliance_id IS NOT NULL AND def.alliance_id = att.alliance_id) THEN
    PERFORM cf_return_troops(att.id, m.troops_json);   -- target gone, shielded or now an ally: army turns back
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
  PERFORM cf_return_troops(att.id, v_left);
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

-- ===== 6e: war rallies =====

CREATE OR REPLACE FUNCTION launch_rally(p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint,
  p_wait_seconds int DEFAULT 60) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_wait int; v_id uuid; v_power bigint;
BEGIN
  IF me.alliance_id IS NULL THEN RAISE EXCEPTION 'Join an alliance to sound a War Rally.'; END IF;
  IF p_target_type NOT IN ('KINGDOM','BASTION') THEN RAISE EXCEPTION 'Rallies target kingdoms or bastions.'; END IF;
  IF (SELECT count(*) FROM rallies WHERE alliance_id = me.alliance_id
        AND status IN ('GATHERING','MARCHING')) >= 3 THEN
    RAISE EXCEPTION 'Your alliance already has 3 rallies under way.';
  END IF;
  IF EXISTS (SELECT 1 FROM rallies WHERE leader_id = me.id AND status IN ('GATHERING','MARCHING')) THEN
    RAISE EXCEPTION 'You are already leading a rally.';
  END IF;

  t := cf_war_target(me, p_target_type, p_target_id);
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
          t.x, t.y, v_power, t.defense, now() + make_interval(secs => v_wait),
          now() + make_interval(secs => v_wait + v_secs), v_secs)
  RETURNING id INTO v_id;
  INSERT INTO rally_participants (rally_id, player_id, troops_json, power) VALUES (v_id, me.id, v_troops, v_power);

  PERFORM cf_system_message(me.alliance_id,
    left(format('WAR RALLY! %s calls the alliance against %s. Join within %s seconds!', me.username, t.tname, v_wait), 300),
    'RALLY');
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION join_rally(p_rally uuid,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); r rallies; v_troops jsonb; v_power bigint;
BEGIN
  SELECT * INTO r FROM rallies WHERE id = p_rally FOR UPDATE;
  IF NOT FOUND OR r.alliance_id IS DISTINCT FROM me.alliance_id THEN
    RAISE EXCEPTION 'That rally is not in your alliance.';
  END IF;
  IF r.status <> 'GATHERING' OR r.launch_at <= now() THEN RAISE EXCEPTION 'That rally has already marched.'; END IF;
  IF EXISTS (SELECT 1 FROM rally_participants WHERE rally_id = r.id AND player_id = me.id) THEN
    RAISE EXCEPTION 'You have already joined this rally.';
  END IF;
  IF r.target_type = 'KINGDOM' AND r.target_id = me.id::text THEN RAISE EXCEPTION 'You cannot rally against yourself.'; END IF;

  v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  v_power := cf_troop_power(v_troops);
  IF r.target_type = 'KINGDOM' THEN UPDATE players SET shield_until = NULL WHERE id = me.id; END IF;
  INSERT INTO rally_participants (rally_id, player_id, troops_json, power) VALUES (r.id, me.id, v_troops, v_power);
  UPDATE rallies SET total_power = total_power + v_power WHERE id = r.id;
END $$;

CREATE OR REPLACE FUNCTION launch_rally_now(p_rally uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies;
BEGIN
  SELECT * INTO r FROM rallies WHERE id = p_rally FOR UPDATE;
  IF NOT FOUND OR r.leader_id <> auth.uid() THEN RAISE EXCEPTION 'Only the rally leader can launch the strike.'; END IF;
  IF r.status <> 'GATHERING' THEN RAISE EXCEPTION 'That rally has already marched.'; END IF;
  UPDATE rallies SET launch_at = now(), arrives_at = now() + make_interval(secs => r.travel_seconds) WHERE id = r.id;
  PERFORM resolve_due_battles();
END $$;

CREATE OR REPLACE FUNCTION cancel_rally(p_rally uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; p rally_participants;
BEGIN
  SELECT * INTO r FROM rallies WHERE id = p_rally FOR UPDATE;
  IF NOT FOUND OR r.leader_id <> auth.uid() THEN RAISE EXCEPTION 'Only the rally leader can cancel it.'; END IF;
  IF r.status <> 'GATHERING' THEN RAISE EXCEPTION 'That rally has already marched.'; END IF;
  FOR p IN SELECT * FROM rally_participants WHERE rally_id = r.id LOOP
    PERFORM cf_return_troops(p.player_id, p.troops_json);
  END LOOP;
  UPDATE rallies SET status = 'CANCELLED' WHERE id = r.id;
  PERFORM cf_system_message(r.alliance_id, left(format('The rally against %s was called off.', r.target_name), 300));
END $$;

CREATE OR REPLACE FUNCTION cf_resolve_rally(p_rally uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  r rallies; p rally_participants; def players; node world_nodes; c record; v_tag text;
  v_siege bigint := 0; v_units bigint := 0; v_left_units bigint := 0;
  v_atk numeric; v_def numeric; v_def_left jsonb; v_def_losses bigint := 0; v_left jsonb;
  lf bigint := 0; lt bigint := 0; ls bigint := 0; lg bigint := 0; v_share numeric; v_carry bigint;
BEGIN
  SELECT * INTO r FROM rallies WHERE id = p_rally AND status = 'MARCHING' FOR UPDATE SKIP LOCKED;
  IF NOT FOUND THEN RETURN; END IF;
  UPDATE rallies SET status = 'RESOLVED' WHERE id = r.id;
  UPDATE marches SET status = 'COMPLETED', returns_at = now() WHERE rally_id = r.id AND status = 'MARCHING';
  SELECT tag INTO v_tag FROM alliances WHERE id = r.alliance_id;

  -- lock every castle involved in id order
  PERFORM 1 FROM players
   WHERE id IN (SELECT player_id FROM rally_participants WHERE rally_id = r.id)
      OR (r.target_type = 'KINGDOM' AND id::text = r.target_id)
   ORDER BY id FOR UPDATE;

  SELECT coalesce(sum(cf_tq(troops_json,'siege')), 0), coalesce(sum(cf_unit_count(troops_json)), 0)
    INTO v_siege, v_units FROM rally_participants WHERE rally_id = r.id;
  r.total_power := (SELECT coalesce(sum(power), 0) FROM rally_participants WHERE rally_id = r.id);
  v_atk := (r.total_power + v_siege * 12) * (1 + cf_tech_level(r.alliance_id, 'ATTACK') * 0.06);

  IF r.target_type = 'BASTION' THEN
    SELECT * INTO node FROM world_nodes WHERE id = r.target_id;
    v_def := coalesce(node.garrison_power, 1200);
  ELSE
    SELECT * INTO def FROM players WHERE id::text = r.target_id;
    IF NOT FOUND OR (def.shield_until IS NOT NULL AND def.shield_until > now()) OR def.alliance_id = r.alliance_id THEN
      FOR p IN SELECT * FROM rally_participants WHERE rally_id = r.id LOOP
        PERFORM cf_return_troops(p.player_id, p.troops_json);
      END LOOP;
      PERFORM cf_system_message(r.alliance_id,
        left(format('The rally on %s turned back: the target was shielded or gone.', r.target_name), 300));
      RETURN;
    END IF;
    v_def := round((def.infantry*10 + def.cavalry*16 + def.archers*12 + def.siege*30)
                   * (1 + cf_tech_level(def.alliance_id, 'DEFENSE') * 0.06)) + def.keep_level * 60;
  END IF;

  c := cf_combat(v_atk, v_def);

  IF c.victory THEN
    IF r.target_type = 'BASTION' THEN
      -- rallies on bastions pay out 1.5x, shared between everyone who joined
      lf := round(node.reward_food * 1.5); lt := round(node.reward_timber * 1.5);
      ls := round(node.reward_stone * 1.5); lg := round(node.reward_gold * 1.5);
    ELSE
      v_carry := GREATEST(round(v_units * (1 - c.att_loss)) * 20, 200)::bigint / 4;
      lf := LEAST(floor(def.food * 0.25)::bigint, v_carry);
      lt := LEAST(floor(def.timber * 0.25)::bigint, v_carry);
      ls := LEAST(floor(def.stone * 0.25)::bigint, v_carry);
      lg := LEAST(floor(def.gold * 0.25)::bigint, v_carry);
    END IF;
  END IF;

  IF r.target_type = 'KINGDOM' THEN
    v_def_left := cf_scale_troops(cf_troops(def.infantry, def.cavalry, def.archers, def.siege), 1 - c.def_loss);
    v_def_losses := (def.infantry + def.cavalry + def.archers + def.siege) - cf_unit_count(v_def_left);
    UPDATE players SET infantry = cf_tq(v_def_left,'infantry'), cavalry = cf_tq(v_def_left,'cavalry'),
                       archers = cf_tq(v_def_left,'archers'), siege = cf_tq(v_def_left,'siege'),
                       food = food - lf, timber = timber - lt, stone = stone - ls, gold = gold - lg,
                       honor = honor + CASE WHEN c.victory THEN 0 ELSE 100 END
     WHERE id = def.id;
  END IF;

  FOR p IN SELECT * FROM rally_participants WHERE rally_id = r.id LOOP
    v_left := cf_scale_troops(p.troops_json, 1 - c.att_loss);
    v_left_units := v_left_units + cf_unit_count(v_left);
    v_share := p.power::numeric / GREATEST(r.total_power, 1);
    PERFORM cf_return_troops(p.player_id, v_left);
    UPDATE players SET food = food + floor(lf * v_share), timber = timber + floor(lt * v_share),
                       stone = stone + floor(ls * v_share), gold = gold + floor(lg * v_share),
                       honor = honor + CASE WHEN c.victory THEN 60 ELSE 10 END
     WHERE id = p.player_id;
  END LOOP;

  INSERT INTO battle_logs (realm_id, battle_type, attacker_id, attacker_name, attacker_alliance_id,
    defender_id, defender_name, defender_alliance_id, defender_kind, victory,
    attacker_power, defender_power, attacker_losses, defender_losses,
    loot_food, loot_timber, loot_stone, loot_gold, rally_id)
  VALUES (r.realm_id, 'RALLY', r.leader_id, left(format('[%s] War Rally', v_tag), 28), r.alliance_id,
    def.id, r.target_name, def.alliance_id, CASE WHEN r.target_type = 'BASTION' THEN 'BASTION' ELSE 'PLAYER' END,
    c.victory, round(v_atk), round(v_def), v_units - v_left_units, v_def_losses, lf, lt, ls, lg, r.id);

  PERFORM cf_system_message(r.alliance_id, left(format('Rally %s against %s! Plunder: %s food, %s timber, %s stone, %s gold.',
    CASE WHEN c.victory THEN 'VICTORY' ELSE 'DEFEAT' END, r.target_name, lf, lt, ls, lg), 300));
  IF def.alliance_id IS NOT NULL THEN
    PERFORM cf_system_message(def.alliance_id, left(format('%s was hit by a [%s] rally and %s!',
      def.username, v_tag, CASE WHEN c.victory THEN 'fell' ELSE 'held the walls' END), 300));
  END IF;
END $$;

-- ===== 6f: settle everything that is due (any signed-in player's app calls this) =====

CREATE OR REPLACE FUNCTION resolve_due_battles() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; v_id uuid; n int := 0; lx int; ly int;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;

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

-- ===== 6g: permissions =====

REVOKE ALL ON FUNCTION cf_take_troops(players, bigint, bigint, bigint, bigint) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_return_troops(uuid, jsonb) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_war_target(players, text, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_resolve_march(uuid) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_resolve_rally(uuid) FROM PUBLIC, anon, authenticated;

REVOKE ALL ON FUNCTION dispatch_march(text, text, text, bigint, bigint, bigint, bigint) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION recall_march(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION launch_rally(text, text, bigint, bigint, bigint, bigint, int) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION join_rally(uuid, bigint, bigint, bigint, bigint) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION launch_rally_now(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION cancel_rally(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION resolve_due_battles() FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION dispatch_march(text, text, text, bigint, bigint, bigint, bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION recall_march(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION launch_rally(text, text, bigint, bigint, bigint, bigint, int) TO authenticated;
GRANT EXECUTE ON FUNCTION join_rally(uuid, bigint, bigint, bigint, bigint) TO authenticated;
GRANT EXECUTE ON FUNCTION launch_rally_now(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION cancel_rally(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION resolve_due_battles() TO authenticated;
GRANT SELECT ON world_nodes TO authenticated;
