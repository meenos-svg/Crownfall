-- Crownfall part 16: lord profiles
ALTER TABLE players ADD COLUMN IF NOT EXISTS motto text NOT NULL DEFAULT '';

-- Everything a profile card shows, including war record (battle reports are private, so this
-- function summarises them for anyone to see).
CREATE OR REPLACE FUNCTION lord_profile(p_player uuid) RETURNS jsonb
LANGUAGE sql STABLE SECURITY DEFINER SET search_path = public AS $$
  SELECT jsonb_build_object(
    'id', p.id, 'username', p.username, 'avatar', p.avatar, 'avatar_url', p.avatar_url,
    'motto', p.motto, 'keep_level', p.keep_level, 'power', p.power, 'honor', p.honor,
    'is_bot', p.is_bot, 'realm_id', p.home_realm_id, 'realm_name', r.name,
    'x', p.pos_x / 14, 'y', p.pos_y / 14,
    'alliance_id', a.id, 'alliance_tag', a.tag, 'alliance_name', a.name, 'alliance_role', p.alliance_role,
    'created_at', p.created_at,
    'attacks_won',   (SELECT count(*) FROM battle_logs WHERE attacker_id = p.id AND victory AND battle_type <> 'GATHER'),
    'attacks_lost',  (SELECT count(*) FROM battle_logs WHERE attacker_id = p.id AND NOT victory AND battle_type <> 'GATHER'),
    'defenses_won',  (SELECT count(*) FROM battle_logs WHERE defender_id = p.id AND NOT victory),
    'defenses_lost', (SELECT count(*) FROM battle_logs WHERE defender_id = p.id AND victory),
    'troops_slain',  coalesce((SELECT sum(defender_losses) FROM battle_logs WHERE attacker_id = p.id), 0)
                   + coalesce((SELECT sum(attacker_losses) FROM battle_logs WHERE defender_id = p.id), 0),
    'troops_lost',   coalesce((SELECT sum(attacker_losses) FROM battle_logs WHERE attacker_id = p.id), 0)
                   + coalesce((SELECT sum(defender_losses) FROM battle_logs WHERE defender_id = p.id), 0),
    'gathers',       (SELECT count(*) FROM battle_logs WHERE attacker_id = p.id AND battle_type = 'GATHER'),
    'rallies_joined',(SELECT count(*) FROM rally_participants WHERE player_id = p.id),
    'landmarks_garrisoned', (SELECT count(*) FROM landmark_garrisons WHERE player_id = p.id)
  )
  FROM players p
  LEFT JOIN realms r ON r.id = p.home_realm_id
  LEFT JOIN alliances a ON a.id = p.alliance_id
  WHERE p.id = p_player
$$;
REVOKE ALL ON FUNCTION lord_profile(uuid) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION lord_profile(uuid) TO authenticated;

CREATE OR REPLACE FUNCTION set_motto(p_motto text) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v text := trim(coalesce(p_motto, ''));
BEGIN
  IF char_length(v) > 120 THEN RAISE EXCEPTION 'A motto can be at most 120 characters.'; END IF;
  UPDATE players SET motto = v WHERE id = me.id RETURNING * INTO me;
  RETURN me;
END $$;
REVOKE ALL ON FUNCTION set_motto(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION set_motto(text) TO authenticated;

-- AI lords get mottos to match their styles
UPDATE players SET motto = CASE bot_style
    WHEN 'RAIDER'  THEN 'What is yours will be mine by morning.'
    WHEN 'TURTLE'  THEN 'Walls first. Glory after.'
    ELSE 'Grain, stone and gold win longer wars than swords.' END
 WHERE is_bot AND motto = '';
