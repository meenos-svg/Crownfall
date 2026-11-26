CREATE OR REPLACE FUNCTION cf_recount_alliance(p_alliance uuid) RETURNS void
LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  UPDATE alliances a SET
    member_count = GREATEST(s.n, 0),
    total_power  = s.p
  FROM (SELECT count(*)::int AS n, coalesce(sum(power), 0)::bigint AS p
        FROM players WHERE alliance_id = p_alliance) s
  WHERE a.id = p_alliance
$$;
REVOKE ALL ON FUNCTION cf_recount_alliance(uuid) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION cf_alliance_stats_trigger() RETURNS trigger
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  IF TG_OP = 'UPDATE' AND NEW.alliance_id IS NOT DISTINCT FROM OLD.alliance_id
     AND NEW.power = OLD.power THEN
    RETURN NEW;
  END IF;
  IF TG_OP = 'UPDATE' AND OLD.alliance_id IS NOT NULL THEN
    PERFORM cf_recount_alliance(OLD.alliance_id);
  END IF;
  IF NEW.alliance_id IS NOT NULL THEN
    PERFORM cf_recount_alliance(NEW.alliance_id);
  END IF;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_players_alliance_stats ON players;
CREATE TRIGGER trg_players_alliance_stats AFTER INSERT OR UPDATE ON players
  FOR EACH ROW EXECUTE FUNCTION cf_alliance_stats_trigger();

CREATE OR REPLACE FUNCTION cf_system_message(p_alliance uuid, p_body text, p_type text DEFAULT 'SYSTEM')
RETURNS void LANGUAGE sql SECURITY DEFINER SET search_path = public AS $$
  INSERT INTO alliance_messages (alliance_id, sender_id, sender_name, sender_role, body, message_type)
  VALUES (p_alliance, NULL, 'Herald', 'SYSTEM', left(p_body, 300), p_type)
$$;
REVOKE ALL ON FUNCTION cf_system_message(uuid, text, text) FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION create_alliance(p_name text, p_tag text, p_motto text, p_crest text, p_open boolean)
RETURNS uuid LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  v_id uuid;
  v_name text := trim(p_name);
  v_tag text := upper(trim(p_tag));
BEGIN
  IF p.alliance_id IS NOT NULL THEN RAISE EXCEPTION 'Leave your current alliance first.'; END IF;
  IF char_length(v_name) NOT BETWEEN 3 AND 32 THEN RAISE EXCEPTION 'Alliance name must be 3-32 characters.'; END IF;
  IF v_tag !~ '^[A-Z0-9]{2,5}$' THEN RAISE EXCEPTION 'Tag must be 2-5 letters or numbers.'; END IF;
  IF EXISTS (SELECT 1 FROM alliances WHERE realm_id = p.home_realm_id AND (tag = v_tag OR lower(name) = lower(v_name))) THEN
    RAISE EXCEPTION 'That alliance name or tag is already taken.';
  END IF;
  INSERT INTO alliances (realm_id, name, tag, motto, crest_color, leader_id, open_join, total_power)
  VALUES (p.home_realm_id, v_name, v_tag,
          left(coalesce(nullif(trim(p_motto), ''), 'Forged in Obsidian and Gold'), 120),
          CASE WHEN p_crest IN ('CRIMSON','GOLD','OBSIDIAN','EMERALD','ROYAL') THEN p_crest ELSE 'CRIMSON' END,
          p.id, coalesce(p_open, true), p.power)
  RETURNING id INTO v_id;
  UPDATE players SET alliance_id = v_id, alliance_role = 'LEADER' WHERE id = p.id;
  PERFORM cf_system_message(v_id, p.username || ' founded [' || v_tag || '] ' || v_name || '!');
  RETURN v_id;
END $$;

CREATE OR REPLACE FUNCTION join_alliance(p_alliance uuid, p_invite uuid DEFAULT NULL)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  a alliances;
  v_invited boolean;
BEGIN
  IF p.alliance_id IS NOT NULL THEN RAISE EXCEPTION 'You are already in an alliance.'; END IF;
  SELECT * INTO a FROM alliances WHERE id = p_alliance FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'That alliance no longer exists.'; END IF;
  IF a.realm_id <> p.home_realm_id THEN RAISE EXCEPTION 'That alliance is in another realm.'; END IF;
  IF (SELECT count(*) FROM players WHERE alliance_id = a.id) >= 30 THEN
    RAISE EXCEPTION 'This alliance is already at 30/30 members.';
  END IF;
  v_invited := EXISTS (SELECT 1 FROM alliance_invites WHERE alliance_id = a.id AND target_id = p.id AND status = 'PENDING');
  IF NOT a.open_join AND NOT v_invited THEN RAISE EXCEPTION 'This alliance is invite-only.'; END IF;
  UPDATE players SET alliance_id = a.id, alliance_role = 'MEMBER' WHERE id = p.id;
  UPDATE alliance_invites SET status = 'ACCEPTED' WHERE alliance_id = a.id AND target_id = p.id AND status = 'PENDING';
  UPDATE alliance_invites SET status = 'EXPIRED' WHERE target_id = p.id AND status = 'PENDING';
  PERFORM cf_system_message(a.id, p.username || ' swore fealty to [' || a.tag || ']!');
END $$;

CREATE OR REPLACE FUNCTION decline_invite(p_invite uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
BEGIN
  UPDATE alliance_invites SET status = 'DECLINED'
  WHERE id = p_invite AND target_id = auth.uid() AND status = 'PENDING';
END $$;

CREATE OR REPLACE FUNCTION leave_alliance()
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  v_aid uuid := p.alliance_id;
  v_count int;
BEGIN
  IF v_aid IS NULL THEN RAISE EXCEPTION 'You are not in an alliance.'; END IF;
  SELECT count(*) INTO v_count FROM players WHERE alliance_id = v_aid;
  IF p.alliance_role = 'LEADER' AND v_count > 1 THEN
    RAISE EXCEPTION 'Transfer leadership before leaving.';
  END IF;
  UPDATE players SET alliance_id = NULL, alliance_role = 'NONE' WHERE id = p.id;
  IF p.alliance_role = 'LEADER' THEN
    DELETE FROM alliances WHERE id = v_aid;
  ELSE
    PERFORM cf_system_message(v_aid, p.username || ' has left the alliance.');
  END IF;
END $$;

CREATE OR REPLACE FUNCTION send_alliance_message(p_body text, p_type text DEFAULT 'CHAT')
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players;
  v_body text := trim(p_body);
BEGIN
  SELECT * INTO p FROM players WHERE id = auth.uid();
  IF p.alliance_id IS NULL THEN RAISE EXCEPTION 'You are not in an alliance.'; END IF;
  IF char_length(v_body) NOT BETWEEN 1 AND 300 THEN RAISE EXCEPTION 'Message must be 1-300 characters.'; END IF;
  INSERT INTO alliance_messages (alliance_id, sender_id, sender_name, sender_role, body, message_type)
  VALUES (p.alliance_id, p.id, p.username, p.alliance_role, v_body,
          CASE WHEN p_type IN ('CHAT','RALLY') THEN p_type ELSE 'CHAT' END);
END $$;

CREATE OR REPLACE FUNCTION invite_lord(p_target uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players;
  t players;
BEGIN
  SELECT * INTO p FROM players WHERE id = auth.uid();
  IF p.alliance_role NOT IN ('LEADER','OFFICER') THEN RAISE EXCEPTION 'Only leaders and officers can invite.'; END IF;
  SELECT * INTO t FROM players WHERE id = p_target;
  IF NOT FOUND THEN RAISE EXCEPTION 'That lord was not found.'; END IF;
  IF t.alliance_id IS NOT NULL THEN RAISE EXCEPTION 'That lord already serves an alliance.'; END IF;
  IF t.home_realm_id <> p.home_realm_id THEN RAISE EXCEPTION 'That lord lives in another realm.'; END IF;
  INSERT INTO alliance_invites (alliance_id, inviter_id, target_id)
  VALUES (p.alliance_id, p.id, t.id)
  ON CONFLICT DO NOTHING;
END $$;

CREATE OR REPLACE FUNCTION toggle_officer(p_target uuid)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players;
  t players;
BEGIN
  SELECT * INTO p FROM players WHERE id = auth.uid();
  IF p.alliance_role <> 'LEADER' THEN RAISE EXCEPTION 'Only the leader can appoint officers.'; END IF;
  SELECT * INTO t FROM players WHERE id = p_target AND alliance_id = p.alliance_id FOR UPDATE;
  IF NOT FOUND OR t.alliance_role = 'LEADER' THEN RAISE EXCEPTION 'That member cannot be changed.'; END IF;
  IF t.alliance_role = 'OFFICER' THEN
    UPDATE players SET alliance_role = 'MEMBER' WHERE id = t.id;
    RETURN t.username || ' is no longer an officer.';
  END IF;
  UPDATE players SET alliance_role = 'OFFICER' WHERE id = t.id;
  PERFORM cf_system_message(p.alliance_id, t.username || ' was promoted to officer.');
  RETURN t.username || ' promoted to officer.';
END $$;

CREATE OR REPLACE FUNCTION transfer_leadership(p_target uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  t players;
BEGIN
  IF p.alliance_role <> 'LEADER' THEN RAISE EXCEPTION 'Only the leader can transfer leadership.'; END IF;
  SELECT * INTO t FROM players WHERE id = p_target AND alliance_id = p.alliance_id FOR UPDATE;
  IF NOT FOUND OR t.id = p.id THEN RAISE EXCEPTION 'Choose another member of your alliance.'; END IF;
  UPDATE players SET alliance_role = 'OFFICER' WHERE id = p.id;
  UPDATE players SET alliance_role = 'LEADER' WHERE id = t.id;
  UPDATE alliances SET leader_id = t.id WHERE id = p.alliance_id;
  PERFORM cf_system_message(p.alliance_id, t.username || ' now leads the alliance.');
END $$;

CREATE OR REPLACE FUNCTION kick_member(p_target uuid)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players;
  t players;
BEGIN
  SELECT * INTO p FROM players WHERE id = auth.uid();
  IF p.alliance_role NOT IN ('LEADER','OFFICER') THEN RAISE EXCEPTION 'Only leaders and officers can remove members.'; END IF;
  SELECT * INTO t FROM players WHERE id = p_target AND alliance_id = p.alliance_id FOR UPDATE;
  IF NOT FOUND OR t.id = p.id THEN RAISE EXCEPTION 'That member cannot be removed.'; END IF;
  IF t.alliance_role = 'LEADER' OR (p.alliance_role = 'OFFICER' AND t.alliance_role = 'OFFICER') THEN
    RAISE EXCEPTION 'You cannot remove someone of equal or higher rank.';
  END IF;
  UPDATE players SET alliance_id = NULL, alliance_role = 'NONE' WHERE id = t.id;
  PERFORM cf_system_message(p.alliance_id, t.username || ' was removed from the alliance.');
END $$;

CREATE OR REPLACE FUNCTION request_aid(p_resource text, p_amount bigint)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players;
BEGIN
  SELECT * INTO p FROM players WHERE id = auth.uid();
  IF p.alliance_id IS NULL THEN RAISE EXCEPTION 'Join an alliance to request aid.'; END IF;
  IF p_resource NOT IN ('FOOD','TIMBER','STONE','GOLD') THEN RAISE EXCEPTION 'Unknown resource.'; END IF;
  IF (SELECT count(*) FROM aid_requests WHERE requester_id = p.id AND status = 'OPEN') >= 2 THEN
    RAISE EXCEPTION 'You already have 2 open aid requests.';
  END IF;
  INSERT INTO aid_requests (alliance_id, requester_id, resource_type, requested_amount)
  VALUES (p.alliance_id, p.id, p_resource, LEAST(GREATEST(coalesce(p_amount, 100), 100), 1000000));
  PERFORM cf_system_message(p.alliance_id, p.username || ' requests ' || LEAST(GREATEST(coalesce(p_amount, 100), 100), 1000000) || ' ' || lower(p_resource) || '.', 'AID');
END $$;

CREATE OR REPLACE FUNCTION donate_aid(p_request uuid)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  r aid_requests;
  v_have bigint;
  v_gift bigint;
  v_col text;
BEGIN
  SELECT * INTO r FROM aid_requests WHERE id = p_request FOR UPDATE;
  IF NOT FOUND OR r.status <> 'OPEN' THEN RAISE EXCEPTION 'That request is no longer open.'; END IF;
  IF r.alliance_id IS DISTINCT FROM p.alliance_id THEN RAISE EXCEPTION 'That request is from another alliance.'; END IF;
  IF r.requester_id = p.id THEN RAISE EXCEPTION 'You cannot donate to your own request.'; END IF;
  v_col := lower(r.resource_type);
  EXECUTE format('SELECT %I FROM players WHERE id = $1', v_col) INTO v_have USING p.id;
  v_gift := LEAST(250, r.requested_amount - r.fulfilled_amount, v_have);
  IF v_gift <= 0 THEN RAISE EXCEPTION 'You have no % to give.', v_col; END IF;
  EXECUTE format('UPDATE players SET %1$I = %1$I - $2, honor = honor + 45 WHERE id = $1', v_col) USING p.id, v_gift;
  EXECUTE format('UPDATE players SET %1$I = LEAST(%1$I + $2, 999999999) WHERE id = $1', v_col) USING r.requester_id, v_gift;
  UPDATE aid_requests SET
    fulfilled_amount = fulfilled_amount + v_gift,
    status = CASE WHEN fulfilled_amount + v_gift >= requested_amount THEN 'FULFILLED' ELSE status END,
    donor_ids = CASE WHEN p.id = ANY(donor_ids) THEN donor_ids ELSE donor_ids || p.id END
  WHERE id = r.id;
  UPDATE alliances SET tech_xp = tech_xp + 75 WHERE id = r.alliance_id;
  RETURN 'Sent ' || v_gift || ' ' || v_col || '. +45 Honor, +75 alliance Tech XP!';
END $$;

CREATE OR REPLACE FUNCTION contribute_alliance_tech(p_tech text)
RETURNS text LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  a alliances;
  v_col text;
  v_level int;
  v_xp bigint;
  v_need bigint;
BEGIN
  IF p.alliance_id IS NULL THEN RAISE EXCEPTION 'Join an alliance first.'; END IF;
  v_col := CASE p_tech
    WHEN 'MARCH_SPEED' THEN 'tech_march_level'
    WHEN 'RESOURCE_YIELD' THEN 'tech_yield_level'
    WHEN 'RALLY_ATTACK' THEN 'tech_attack_level'
    WHEN 'CITADEL_DEFENSE' THEN 'tech_defense_level'
    ELSE NULL END;
  IF v_col IS NULL THEN RAISE EXCEPTION 'Unknown alliance tech.'; END IF;
  IF p.gold < 150 THEN RAISE EXCEPTION 'Contributing costs 150 Gold.'; END IF;
  SELECT * INTO a FROM alliances WHERE id = p.alliance_id FOR UPDATE;
  EXECUTE format('SELECT %I FROM alliances WHERE id = $1', v_col) INTO v_level USING a.id;
  IF v_level >= 10 THEN RAISE EXCEPTION 'That tech is already at max level.'; END IF;
  UPDATE players SET gold = gold - 150, honor = honor + 20 WHERE id = p.id;
  v_xp := a.tech_xp + 25;
  v_need := 100 + v_level * 50;
  IF v_xp >= v_need THEN
    EXECUTE format('UPDATE alliances SET %1$I = %1$I + 1, tech_xp = $2 WHERE id = $1', v_col) USING a.id, v_xp - v_need;
    PERFORM cf_system_message(a.id, p.username || ' completed research: ' || replace(initcap(replace(p_tech, '_', ' ')), '_', ' ') || ' level ' || (v_level + 1) || '!');
    RETURN 'Research complete! Level ' || (v_level + 1) || ' unlocked.';
  END IF;
  UPDATE alliances SET tech_xp = v_xp WHERE id = a.id;
  RETURN 'Contributed 150 Gold: ' || v_xp || '/' || v_need || ' Tech XP.';
END $$;

DO $$
DECLARE f text;
BEGIN
  FOREACH f IN ARRAY ARRAY[
    'create_alliance(text,text,text,text,boolean)', 'join_alliance(uuid,uuid)', 'decline_invite(uuid)',
    'leave_alliance()', 'send_alliance_message(text,text)', 'invite_lord(uuid)', 'toggle_officer(uuid)',
    'transfer_leadership(uuid)', 'kick_member(uuid)', 'request_aid(text,bigint)', 'donate_aid(uuid)',
    'contribute_alliance_tech(text)']
  LOOP
    EXECUTE 'REVOKE ALL ON FUNCTION ' || f || ' FROM PUBLIC, anon';
    EXECUTE 'GRANT EXECUTE ON FUNCTION ' || f || ' TO authenticated';
  END LOOP;
END $$;

DROP POLICY IF EXISTS "send chat in own alliance" ON alliance_messages;
