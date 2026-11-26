-- ===== Crownfall part 7: strongholds, garrisons, calls for aid and pacts =====

ALTER TABLE strongholds ADD COLUMN IF NOT EXISTS last_attacked_at timestamptz;

-- Two alliances with an active pact are at peace.
CREATE OR REPLACE FUNCTION cf_at_peace(a uuid, b uuid) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT a IS NOT NULL AND b IS NOT NULL AND EXISTS (
    SELECT 1 FROM alliance_pacts
     WHERE status = 'ACTIVE' AND alliance_a_id = LEAST(a, b) AND alliance_b_id = GREATEST(a, b))
$$;

CREATE OR REPLACE FUNCTION cf_add_troops(a jsonb, b jsonb) RETURNS jsonb
LANGUAGE sql IMMUTABLE AS $$
  SELECT cf_troops(cf_tq(a,'infantry') + cf_tq(b,'infantry'), cf_tq(a,'cavalry') + cf_tq(b,'cavalry'),
                   cf_tq(a,'archers') + cf_tq(b,'archers'), cf_tq(a,'siege') + cf_tq(b,'siege'))
$$;

CREATE OR REPLACE FUNCTION cf_stronghold_name(p_alliance uuid) RETURNS text
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT coalesce('[' || (SELECT tag FROM alliances WHERE id = p_alliance) || '] Stronghold', 'Stronghold')
$$;

-- Garrison troops plus the walls (800 per level), boosted by the owner's defense tech.
CREATE OR REPLACE FUNCTION cf_stronghold_defense(p_sh uuid) RETURNS bigint
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT round(coalesce((SELECT sum(troop_power) FROM stronghold_garrisons WHERE stronghold_id = s.id), 0)
               * (1 + cf_tech_level(s.alliance_id, 'DEFENSE') * 0.06))::bigint + s.level * 800
    FROM strongholds s WHERE s.id = p_sh
$$;

-- Own alliance and pact allies can always reinforce; anyone can while a call for aid is up.
CREATE OR REPLACE FUNCTION cf_can_reinforce(p players, sh strongholds) RETURNS boolean
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT p.alliance_id = sh.alliance_id OR cf_at_peace(p.alliance_id, sh.alliance_id) OR sh.call_for_aid_active
$$;

-- Tells the stronghold's alliance and every pact ally.
CREATE OR REPLACE FUNCTION cf_herald_with_allies(p_alliance uuid, p_body text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v uuid;
BEGIN
  PERFORM cf_system_message(p_alliance, left(p_body, 300));
  FOR v IN SELECT CASE WHEN alliance_a_id = p_alliance THEN alliance_b_id ELSE alliance_a_id END
             FROM alliance_pacts WHERE status = 'ACTIVE' AND p_alliance IN (alliance_a_id, alliance_b_id) LOOP
    PERFORM cf_system_message(v, left(p_body, 300));
  END LOOP;
END $$;

-- One assault on a stronghold. Wears the garrison and the walls; at 0 walls it falls.
CREATE OR REPLACE FUNCTION cf_stronghold_battle(p_sh uuid, p_atk numeric, p_att_name text,
  OUT ok boolean, OUT victory boolean, OUT att_loss numeric, OUT d numeric,
  OUT def_losses bigint, OUT damage bigint, OUT destroyed boolean,
  OUT sh_name text, OUT owner_alliance uuid)
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE sh strongholds; g stronghold_garrisons; c record; v_left jsonb;
BEGIN
  ok := false; victory := false; att_loss := 0; d := 0; def_losses := 0; damage := 0; destroyed := false;
  SELECT * INTO sh FROM strongholds WHERE id = p_sh FOR UPDATE;
  IF NOT FOUND THEN RETURN; END IF;
  ok := true; owner_alliance := sh.alliance_id; sh_name := cf_stronghold_name(sh.alliance_id);

  d := cf_stronghold_defense(sh.id);
  c := cf_combat(p_atk, d);
  victory := c.victory; att_loss := c.att_loss;

  FOR g IN SELECT * FROM stronghold_garrisons WHERE stronghold_id = sh.id FOR UPDATE LOOP
    v_left := cf_scale_troops(g.troops_json, 1 - c.def_loss);
    def_losses := def_losses + cf_unit_count(g.troops_json) - cf_unit_count(v_left);
    IF cf_unit_count(v_left) = 0 THEN
      DELETE FROM stronghold_garrisons WHERE id = g.id;
    ELSE
      UPDATE stronghold_garrisons SET troops_json = v_left, troop_power = cf_troop_power(v_left) WHERE id = g.id;
    END IF;
  END LOOP;

  IF victory THEN damage := round(p_atk * 0.6 + (p_atk - d)); END IF;
  UPDATE strongholds SET durability = GREATEST(durability - damage, 0), is_under_siege = true,
         siege_started_at = coalesce(siege_started_at, now()), last_attacked_at = now()
   WHERE id = sh.id RETURNING * INTO sh;

  IF sh.durability = 0 THEN
    destroyed := true;
    FOR g IN SELECT * FROM stronghold_garrisons WHERE stronghold_id = sh.id LOOP
      PERFORM cf_return_troops(g.player_id, g.troops_json);
    END LOOP;
    DELETE FROM strongholds WHERE id = sh.id;
    PERFORM cf_herald_with_allies(sh.alliance_id,
      format('%s has FALLEN to %s! The surviving garrison marches home.', sh_name, p_att_name));
  ELSE
    PERFORM cf_herald_with_allies(sh.alliance_id,
      format('%s assaulted by %s and %s. Walls %s / %s.', sh_name, p_att_name,
             CASE WHEN victory THEN 'breached' ELSE 'repelled' END, sh.durability, sh.max_durability));
  END IF;
END $$;

-- ===== 7b: founding, garrisons and calls for aid =====

CREATE OR REPLACE FUNCTION found_stronghold(p_x int, p_y int) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v_id uuid; v_near text;
BEGIN
  IF me.alliance_role <> 'LEADER' THEN RAISE EXCEPTION 'Only the alliance leader can found a stronghold.'; END IF;
  IF EXISTS (SELECT 1 FROM strongholds WHERE alliance_id = me.alliance_id) THEN
    RAISE EXCEPTION 'Your alliance already has a stronghold.';
  END IF;
  IF p_x NOT BETWEEN 3 AND 96 OR p_y NOT BETWEEN 3 AND 96 THEN RAISE EXCEPTION 'Too close to the edge of the realm.'; END IF;
  IF me.stone < 3000 OR me.gold < 2000 THEN RAISE EXCEPTION 'A stronghold costs 3,000 Stone and 2,000 Gold.'; END IF;

  SELECT name INTO v_near FROM world_nodes WHERE sqrt(power(map_x - p_x, 2) + power(map_y - p_y, 2)) < 4 LIMIT 1;
  IF v_near IS NOT NULL THEN RAISE EXCEPTION 'Too close to %.', v_near; END IF;
  SELECT username INTO v_near FROM players
   WHERE home_realm_id = me.home_realm_id AND sqrt(power(pos_x / 14.0 - p_x, 2) + power(pos_y / 14.0 - p_y, 2)) < 3 LIMIT 1;
  IF v_near IS NOT NULL THEN RAISE EXCEPTION 'Too close to the castle of %.', v_near; END IF;
  IF EXISTS (SELECT 1 FROM strongholds WHERE home_realm_id = me.home_realm_id
               AND sqrt(power(pos_x / 14.0 - p_x, 2) + power(pos_y / 14.0 - p_y, 2)) < 8) THEN
    RAISE EXCEPTION 'Too close to another stronghold.';
  END IF;

  UPDATE players SET stone = stone - 3000, gold = gold - 2000 WHERE id = me.id;
  INSERT INTO strongholds (alliance_id, home_realm_id, pos_x, pos_y, level, durability, max_durability)
  VALUES (me.alliance_id, me.home_realm_id, p_x * 14, p_y * 14, 1, 15000, 15000)
  RETURNING id INTO v_id;
  PERFORM cf_system_message(me.alliance_id,
    format('%s raised the alliance stronghold at (%s, %s)! Send troops to garrison it.', me.username, p_x, p_y));
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION withdraw_garrison(p_stronghold uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); g stronghold_garrisons;
BEGIN
  SELECT * INTO g FROM stronghold_garrisons WHERE stronghold_id = p_stronghold AND player_id = me.id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'You have no troops in that stronghold.'; END IF;
  PERFORM cf_return_troops(me.id, g.troops_json);
  DELETE FROM stronghold_garrisons WHERE id = g.id;
END $$;

CREATE OR REPLACE FUNCTION call_for_aid(p_message text) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); sh strongholds; v_msg text;
BEGIN
  IF me.alliance_role NOT IN ('LEADER','OFFICER') THEN RAISE EXCEPTION 'Only leaders and officers can call for aid.'; END IF;
  SELECT * INTO sh FROM strongholds WHERE alliance_id = me.alliance_id FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'Your alliance has no stronghold.'; END IF;
  v_msg := left(coalesce(nullif(trim(p_message), ''), 'Our stronghold needs defenders!'), 140);
  UPDATE strongholds SET call_for_aid_active = true, aid_message = v_msg WHERE id = sh.id;
  PERFORM cf_herald_with_allies(me.alliance_id,
    format('%s calls for aid at (%s, %s): "%s" Any kingdom may now send relief troops.',
           cf_stronghold_name(me.alliance_id), sh.pos_x / 14, sh.pos_y / 14, v_msg));
END $$;

CREATE OR REPLACE FUNCTION end_call_for_aid() RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update();
BEGIN
  IF me.alliance_role NOT IN ('LEADER','OFFICER') THEN RAISE EXCEPTION 'Only leaders and officers can do that.'; END IF;
  UPDATE strongholds SET call_for_aid_active = false, aid_message = NULL WHERE alliance_id = me.alliance_id;
END $$;

-- ===== 7c: pacts between alliances =====

CREATE OR REPLACE FUNCTION propose_pact(p_alliance uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v_other alliances;
BEGIN
  IF me.alliance_role <> 'LEADER' THEN RAISE EXCEPTION 'Only the alliance leader can propose a pact.'; END IF;
  SELECT * INTO v_other FROM alliances WHERE id = p_alliance;
  IF NOT FOUND OR v_other.id = me.alliance_id THEN RAISE EXCEPTION 'Choose another alliance.'; END IF;
  IF EXISTS (SELECT 1 FROM alliance_pacts WHERE status IN ('PENDING','ACTIVE')
               AND alliance_a_id = LEAST(me.alliance_id, p_alliance) AND alliance_b_id = GREATEST(me.alliance_id, p_alliance)) THEN
    RAISE EXCEPTION 'A pact with [%] is already proposed or active.', v_other.tag;
  END IF;
  IF (SELECT count(*) FROM alliance_pacts WHERE status = 'ACTIVE'
        AND me.alliance_id IN (alliance_a_id, alliance_b_id)) >= 3 THEN
    RAISE EXCEPTION 'Your alliance already has 3 pacts.';
  END IF;
  INSERT INTO alliance_pacts (alliance_a_id, alliance_b_id, requested_by)
  VALUES (LEAST(me.alliance_id, p_alliance), GREATEST(me.alliance_id, p_alliance), me.alliance_id);
  PERFORM cf_system_message(p_alliance, format('[%s] proposes a pact with your alliance. Your leader can accept it.',
    (SELECT tag FROM alliances WHERE id = me.alliance_id)));
  PERFORM cf_system_message(me.alliance_id, format('Pact proposed to [%s].', v_other.tag));
END $$;

CREATE OR REPLACE FUNCTION respond_pact(p_pact uuid, p_accept boolean) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); pa alliance_pacts; v_text text;
BEGIN
  SELECT * INTO pa FROM alliance_pacts WHERE id = p_pact FOR UPDATE;
  IF NOT FOUND OR pa.status <> 'PENDING' THEN RAISE EXCEPTION 'That pact offer is no longer open.'; END IF;
  IF me.alliance_role <> 'LEADER' OR me.alliance_id NOT IN (pa.alliance_a_id, pa.alliance_b_id)
     OR me.alliance_id = pa.requested_by THEN
    RAISE EXCEPTION 'Only the other alliance''s leader can answer this pact.';
  END IF;
  IF p_accept AND (SELECT count(*) FROM alliance_pacts WHERE status = 'ACTIVE'
        AND me.alliance_id IN (alliance_a_id, alliance_b_id)) >= 3 THEN
    RAISE EXCEPTION 'Your alliance already has 3 pacts.';
  END IF;
  UPDATE alliance_pacts SET status = CASE WHEN p_accept THEN 'ACTIVE' ELSE 'ENDED' END WHERE id = pa.id;
  v_text := format('The pact between [%s] and [%s] was %s.',
    (SELECT tag FROM alliances WHERE id = pa.alliance_a_id), (SELECT tag FROM alliances WHERE id = pa.alliance_b_id),
    CASE WHEN p_accept THEN 'SEALED' ELSE 'declined' END);
  PERFORM cf_system_message(pa.alliance_a_id, v_text);
  PERFORM cf_system_message(pa.alliance_b_id, v_text);
END $$;

CREATE OR REPLACE FUNCTION end_pact(p_pact uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); pa alliance_pacts; v_text text;
BEGIN
  SELECT * INTO pa FROM alliance_pacts WHERE id = p_pact FOR UPDATE;
  IF NOT FOUND OR pa.status = 'ENDED' THEN RAISE EXCEPTION 'That pact has already ended.'; END IF;
  IF me.alliance_role <> 'LEADER' OR me.alliance_id NOT IN (pa.alliance_a_id, pa.alliance_b_id) THEN
    RAISE EXCEPTION 'Only a leader of one of the two alliances can end this pact.';
  END IF;
  UPDATE alliance_pacts SET status = 'ENDED' WHERE id = pa.id;
  v_text := format('The pact between [%s] and [%s] has ended.',
    (SELECT tag FROM alliances WHERE id = pa.alliance_a_id), (SELECT tag FROM alliances WHERE id = pa.alliance_b_id));
  PERFORM cf_system_message(pa.alliance_a_id, v_text);
  PERFORM cf_system_message(pa.alliance_b_id, v_text);
END $$;

-- ===== 7d: war functions updated for strongholds and pacts =====

CREATE OR REPLACE FUNCTION cf_war_target(p_me players, p_type text, p_id text,
  OUT x int, OUT y int, OUT tname text, OUT owner uuid, OUT owner_alliance uuid,
  OUT defense bigint, OUT node world_nodes)
LANGUAGE plpgsql STABLE SECURITY DEFINER SET search_path = public AS $$
DECLARE d players; sh strongholds;
BEGIN
  IF p_type = 'KINGDOM' THEN
    SELECT * INTO d FROM players WHERE id::text = p_id;
    IF NOT FOUND THEN RAISE EXCEPTION 'That kingdom no longer exists.'; END IF;
    IF d.id = p_me.id THEN RAISE EXCEPTION 'You cannot attack your own castle.'; END IF;
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
    IF sh.alliance_id = p_me.alliance_id THEN RAISE EXCEPTION 'That is your own alliance''s stronghold.'; END IF;
    IF cf_at_peace(p_me.alliance_id, sh.alliance_id) THEN RAISE EXCEPTION 'Your alliance has a pact with theirs.'; END IF;
    x := sh.pos_x; y := sh.pos_y; tname := cf_stronghold_name(sh.alliance_id);
    owner_alliance := sh.alliance_id; defense := cf_stronghold_defense(sh.id);
  ELSIF p_type IN ('BASTION','RESOURCE') THEN
    SELECT * INTO node FROM world_nodes WHERE id = p_id AND node_type = p_type;
    IF NOT FOUND THEN RAISE EXCEPTION 'Unknown target.'; END IF;
    x := node.map_x * 14; y := node.map_y * 14; tname := node.name; defense := node.garrison_power;
  ELSE
    RAISE EXCEPTION 'Unknown target type %', p_type;
  END IF;
END $$;

CREATE OR REPLACE FUNCTION dispatch_march(p_type text, p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_id uuid; sh strongholds;
BEGIN
  IF p_type = 'ATTACK' THEN p_type := 'RAID'; END IF;
  IF p_type = 'GATHER' AND p_target_type <> 'RESOURCE' THEN
    RAISE EXCEPTION 'Gathering only works on resource sites.';
  END IF;
  IF p_type = 'RAID' AND p_target_type NOT IN ('KINGDOM','BASTION','STRONGHOLD') THEN
    RAISE EXCEPTION 'Raids target kingdoms, bastions or strongholds.';
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
    IF NOT cf_can_reinforce(me, sh) THEN
      RAISE EXCEPTION 'Only their alliance and pact allies can reinforce, unless they call for aid.';
    END IF;
    SELECT sh.pos_x AS x, sh.pos_y AS y INTO t;
  ELSE
    t := cf_war_target(me, p_target_type, p_target_id);
  END IF;
  v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  IF p_target_type IN ('KINGDOM','STRONGHOLD') AND p_type = 'RAID' THEN
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

CREATE OR REPLACE FUNCTION cf_resolve_march(p_march uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  m marches; att players; def players; node world_nodes; c record; sh strongholds; sb record; v_sh_alliance uuid;
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

  -- Reinforcement: join the stronghold garrison
  IF m.march_type = 'REINFORCE' THEN
    SELECT * INTO sh FROM strongholds WHERE id::text = m.target_id FOR UPDATE;
    IF NOT FOUND OR NOT cf_can_reinforce(att, sh) THEN
      PERFORM cf_return_troops(att.id, m.troops_json);
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

  v_atk := (m.troop_power + CASE WHEN m.target_type IN ('KINGDOM','BASTION','STRONGHOLD') THEN cf_tq(m.troops_json,'siege') * 12 ELSE 0 END)
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

  IF m.target_type = 'STRONGHOLD' THEN
    SELECT alliance_id INTO v_sh_alliance FROM strongholds WHERE id::text = m.target_id;
    IF v_sh_alliance IS NULL OR v_sh_alliance = att.alliance_id OR cf_at_peace(att.alliance_id, v_sh_alliance) THEN
      PERFORM cf_return_troops(att.id, m.troops_json);   -- fallen, or now a friend: army turns back
      RETURN;
    END IF;
    sb := cf_stronghold_battle(m.target_id::uuid, v_atk, att.username);
    v_left := cf_scale_troops(m.troops_json, 1 - sb.att_loss);
    PERFORM cf_return_troops(att.id, v_left);
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

  -- Kingdom raid
  SELECT * INTO def FROM players WHERE id::text = m.target_id;
  IF NOT FOUND OR (def.shield_until IS NOT NULL AND def.shield_until > now())
     OR (att.alliance_id IS NOT NULL AND def.alliance_id = att.alliance_id)
     OR cf_at_peace(att.alliance_id, def.alliance_id) THEN
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

CREATE OR REPLACE FUNCTION launch_rally(p_target_type text, p_target_id text,
  p_infantry bigint, p_cavalry bigint, p_archers bigint, p_siege bigint,
  p_wait_seconds int DEFAULT 60) RETURNS uuid
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  me players := cf_me_for_update();
  t record; v_troops jsonb; v_secs int; v_wait int; v_id uuid; v_power bigint;
BEGIN
  IF me.alliance_id IS NULL THEN RAISE EXCEPTION 'Join an alliance to sound a War Rally.'; END IF;
  IF p_target_type NOT IN ('KINGDOM','BASTION','STRONGHOLD') THEN RAISE EXCEPTION 'Rallies target kingdoms, bastions or strongholds.'; END IF;
  IF (SELECT count(*) FROM rallies WHERE alliance_id = me.alliance_id
        AND status IN ('GATHERING','MARCHING')) >= 3 THEN
    RAISE EXCEPTION 'Your alliance already has 3 rallies under way.';
  END IF;
  IF EXISTS (SELECT 1 FROM rallies WHERE leader_id = me.id AND status IN ('GATHERING','MARCHING')) THEN
    RAISE EXCEPTION 'You are already leading a rally.';
  END IF;

  t := cf_war_target(me, p_target_type, p_target_id);
  v_troops := cf_take_troops(me, p_infantry, p_cavalry, p_archers, p_siege);
  IF p_target_type IN ('KINGDOM','STRONGHOLD') THEN
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

CREATE OR REPLACE FUNCTION cf_resolve_rally(p_rally uuid) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  r rallies; p rally_participants; def players; node world_nodes; c record; v_tag text;
  v_siege bigint := 0; v_units bigint := 0; v_left_units bigint := 0;
  v_atk numeric; v_def numeric; v_def_left jsonb; v_def_losses bigint := 0; v_left jsonb;
  lf bigint := 0; lt bigint := 0; ls bigint := 0; lg bigint := 0; v_share numeric; v_carry bigint;
  sb record; v_sh_alliance uuid; v_def_alliance uuid; v_sh_id uuid; v_kind text;
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
    v_kind := 'BASTION';
  ELSIF r.target_type = 'STRONGHOLD' THEN
    SELECT alliance_id INTO v_sh_alliance FROM strongholds WHERE id::text = r.target_id;
    IF v_sh_alliance IS NULL OR v_sh_alliance = r.alliance_id OR cf_at_peace(r.alliance_id, v_sh_alliance) THEN
      FOR p IN SELECT * FROM rally_participants WHERE rally_id = r.id LOOP
        PERFORM cf_return_troops(p.player_id, p.troops_json);
      END LOOP;
      PERFORM cf_system_message(r.alliance_id,
        left(format('The rally on %s turned back: the stronghold has fallen or is now an ally.', r.target_name), 300));
      RETURN;
    END IF;
    sb := cf_stronghold_battle(r.target_id::uuid, v_atk, left(format('[%s] War Rally', v_tag), 28));
    v_def := sb.d; v_def_alliance := sb.owner_alliance; v_kind := 'STRONGHOLD';
    v_sh_id := CASE WHEN sb.destroyed THEN NULL ELSE r.target_id::uuid END;
  ELSE
    SELECT * INTO def FROM players WHERE id::text = r.target_id;
    IF NOT FOUND OR (def.shield_until IS NOT NULL AND def.shield_until > now()) OR def.alliance_id = r.alliance_id
       OR cf_at_peace(r.alliance_id, def.alliance_id) THEN
      FOR p IN SELECT * FROM rally_participants WHERE rally_id = r.id LOOP
        PERFORM cf_return_troops(p.player_id, p.troops_json);
      END LOOP;
      PERFORM cf_system_message(r.alliance_id,
        left(format('The rally on %s turned back: the target was shielded or gone.', r.target_name), 300));
      RETURN;
    END IF;
    v_def := round((def.infantry*10 + def.cavalry*16 + def.archers*12 + def.siege*30)
                   * (1 + cf_tech_level(def.alliance_id, 'DEFENSE') * 0.06)) + def.keep_level * 60;
    v_def_alliance := def.alliance_id; v_kind := 'PLAYER';
  END IF;

  IF r.target_type = 'STRONGHOLD' THEN
    SELECT sb.victory AS victory, sb.att_loss AS att_loss, 0::numeric AS def_loss INTO c;
    v_def_losses := sb.def_losses;
    IF sb.destroyed THEN lg := 3000; END IF;   -- spoils of a fallen stronghold, shared out
  ELSE
    c := cf_combat(v_atk, v_def);
  END IF;

  IF c.victory AND r.target_type <> 'STRONGHOLD' THEN
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
    def.id, r.target_name, v_def_alliance, v_kind,
    c.victory, round(v_atk), round(v_def), v_units - v_left_units, v_def_losses, lf, lt, ls, lg, r.id);
  UPDATE battle_logs SET stronghold_id = v_sh_id WHERE rally_id = r.id AND v_sh_id IS NOT NULL;

  PERFORM cf_system_message(r.alliance_id, left(format('Rally %s against %s! Plunder: %s food, %s timber, %s stone, %s gold.',
    CASE WHEN c.victory THEN 'VICTORY' ELSE 'DEFEAT' END, r.target_name, lf, lt, ls, lg), 300));
  IF def.alliance_id IS NOT NULL THEN
    PERFORM cf_system_message(def.alliance_id, left(format('%s was hit by a [%s] rally and %s!',
      def.username, v_tag, CASE WHEN c.victory THEN 'fell' ELSE 'held the walls' END), 300));
  END IF;
END $$;

CREATE OR REPLACE FUNCTION resolve_due_battles() RETURNS int
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE r rallies; v_id uuid; n int := 0; lx int; ly int;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;

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
-- ===== 7e: permissions =====
REVOKE ALL ON FUNCTION cf_stronghold_battle(uuid, numeric, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION cf_herald_with_allies(uuid, text) FROM PUBLIC, anon, authenticated;
REVOKE ALL ON FUNCTION found_stronghold(int, int) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION withdraw_garrison(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION call_for_aid(text) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION end_call_for_aid() FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION propose_pact(uuid) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION respond_pact(uuid, boolean) FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION end_pact(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION found_stronghold(int, int) TO authenticated;
GRANT EXECUTE ON FUNCTION withdraw_garrison(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION call_for_aid(text) TO authenticated;
GRANT EXECUTE ON FUNCTION end_call_for_aid() TO authenticated;
GRANT EXECUTE ON FUNCTION propose_pact(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION respond_pact(uuid, boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION end_pact(uuid) TO authenticated;
