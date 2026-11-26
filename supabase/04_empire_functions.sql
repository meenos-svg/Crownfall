ALTER TABLE players ADD COLUMN IF NOT EXISTS harvest_food_at   timestamptz NOT NULL DEFAULT now();
ALTER TABLE players ADD COLUMN IF NOT EXISTS harvest_timber_at timestamptz NOT NULL DEFAULT now();
ALTER TABLE players ADD COLUMN IF NOT EXISTS harvest_stone_at  timestamptz NOT NULL DEFAULT now();
ALTER TABLE players ADD COLUMN IF NOT EXISTS harvest_gold_at   timestamptz NOT NULL DEFAULT now();

CREATE OR REPLACE FUNCTION cf_power_trigger() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.power := NEW.keep_level * 450
    + (NEW.barracks_level + NEW.stables_level + NEW.archery_level + NEW.siege_level + NEW.alliance_hall_level) * 160
    + (NEW.farm_level + NEW.lumber_level + NEW.quarry_level + NEW.mint_level) * 110
    + NEW.infantry * 10 + NEW.cavalry * 16 + NEW.archers * 12 + NEW.siege * 30
    + NEW.honor;
  RETURN NEW;
END $$;

DROP TRIGGER IF EXISTS trg_players_power ON players;
CREATE TRIGGER trg_players_power BEFORE INSERT OR UPDATE ON players
  FOR EACH ROW EXECUTE FUNCTION cf_power_trigger();

UPDATE players SET honor = honor;

CREATE OR REPLACE FUNCTION cf_building_base(p_key text,
  OUT food int, OUT timber int, OUT stone int, OUT gold int, OUT dur int)
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
  CASE p_key
    WHEN 'KEEP'          THEN food:=320; timber:=320; stone:=280; gold:=150; dur:=20;
    WHEN 'BARRACKS'      THEN food:=240; timber:=220; stone:=180; gold:=90;  dur:=15;
    WHEN 'STABLES'       THEN food:=260; timber:=240; stone:=160; gold:=110; dur:=16;
    WHEN 'ARCHERY'       THEN food:=220; timber:=260; stone:=160; gold:=100; dur:=15;
    WHEN 'SIEGE'         THEN food:=240; timber:=300; stone:=260; gold:=160; dur:=22;
    WHEN 'ALLIANCE_HALL' THEN food:=200; timber:=240; stone:=220; gold:=140; dur:=18;
    WHEN 'FARM'          THEN food:=120; timber:=180; stone:=120; gold:=40;  dur:=12;
    WHEN 'LUMBER'        THEN food:=160; timber:=120; stone:=140; gold:=40;  dur:=12;
    WHEN 'QUARRY'        THEN food:=180; timber:=180; stone:=120; gold:=60;  dur:=14;
    WHEN 'MINT'          THEN food:=200; timber:=200; stone:=180; gold:=80;  dur:=16;
    ELSE RAISE EXCEPTION 'Unknown building %', p_key;
  END CASE;
END $$;

CREATE OR REPLACE FUNCTION cf_troop_base(p_key text,
  OUT food int, OUT timber int, OUT stone int, OUT gold int, OUT sec_per_unit int, OUT building text)
LANGUAGE plpgsql IMMUTABLE AS $$
BEGIN
  CASE p_key
    WHEN 'INFANTRY' THEN food:=12; timber:=8;  stone:=4;  gold:=3;  sec_per_unit:=1; building:='BARRACKS';
    WHEN 'CAVALRY'  THEN food:=18; timber:=10; stone:=6;  gold:=8;  sec_per_unit:=1; building:='STABLES';
    WHEN 'ARCHERS'  THEN food:=10; timber:=14; stone:=4;  gold:=5;  sec_per_unit:=1; building:='ARCHERY';
    WHEN 'SIEGE'    THEN food:=14; timber:=28; stone:=24; gold:=16; sec_per_unit:=2; building:='SIEGE';
    ELSE RAISE EXCEPTION 'Unknown troop type %', p_key;
  END CASE;
END $$;

CREATE OR REPLACE FUNCTION cf_accrued(p_rate bigint, p_since timestamptz) RETURNS bigint
LANGUAGE sql STABLE AS $$
  SELECT round(p_rate * (LEAST(GREATEST(extract(epoch FROM now() - p_since), 0), 8 * 3600) / 3600.0) * 10)::bigint
$$;

CREATE OR REPLACE FUNCTION cf_me_for_update() RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE v players;
BEGIN
  IF auth.uid() IS NULL THEN RAISE EXCEPTION 'Not signed in'; END IF;
  SELECT * INTO v FROM players WHERE id = auth.uid() FOR UPDATE;
  IF NOT FOUND THEN RAISE EXCEPTION 'No empire found. Restart the app.'; END IF;
  RETURN v;
END $$;
REVOKE ALL ON FUNCTION cf_me_for_update() FROM PUBLIC, anon, authenticated;

CREATE OR REPLACE FUNCTION collect_resources(p_resource text DEFAULT NULL) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  y numeric := 1;
  f bigint := 0; t bigint := 0; s bigint := 0; g bigint := 0;
BEGIN
  IF p_resource IS NOT NULL AND p_resource NOT IN ('FOOD','TIMBER','STONE','GOLD') THEN
    RAISE EXCEPTION 'Unknown resource %', p_resource;
  END IF;
  IF p.alliance_id IS NOT NULL THEN
    SELECT 1 + tech_yield_level * 0.08 INTO y FROM alliances WHERE id = p.alliance_id;
    y := coalesce(y, 1);
  END IF;

  IF p_resource IS NULL OR p_resource = 'FOOD'   THEN f := cf_accrued(round((360 + p.farm_level   * 220) * y)::bigint, p.harvest_food_at);   END IF;
  IF p_resource IS NULL OR p_resource = 'TIMBER' THEN t := cf_accrued(round((340 + p.lumber_level * 210) * y)::bigint, p.harvest_timber_at); END IF;
  IF p_resource IS NULL OR p_resource = 'STONE'  THEN s := cf_accrued(round((280 + p.quarry_level * 185) * y)::bigint, p.harvest_stone_at);  END IF;
  IF p_resource IS NULL OR p_resource = 'GOLD'   THEN g := cf_accrued(round((180 + p.mint_level   * 135) * y)::bigint, p.harvest_gold_at);   END IF;

  UPDATE players SET
    food   = LEAST(food   + f, 999999999),
    timber = LEAST(timber + t, 999999999),
    stone  = LEAST(stone  + s, 999999999),
    gold   = LEAST(gold   + g, 999999999),
    harvest_food_at   = CASE WHEN p_resource IS NULL OR p_resource = 'FOOD'   THEN now() ELSE harvest_food_at   END,
    harvest_timber_at = CASE WHEN p_resource IS NULL OR p_resource = 'TIMBER' THEN now() ELSE harvest_timber_at END,
    harvest_stone_at  = CASE WHEN p_resource IS NULL OR p_resource = 'STONE'  THEN now() ELSE harvest_stone_at  END,
    harvest_gold_at   = CASE WHEN p_resource IS NULL OR p_resource = 'GOLD'   THEN now() ELSE harvest_gold_at   END,
    last_harvest_at   = CASE WHEN p_resource IS NULL THEN now() ELSE last_harvest_at END
  WHERE id = p.id
  RETURNING * INTO p;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION start_building_upgrade(p_building text) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  b record;
  col text := lower(p_building) || '_level';
  lvl int;
  factor numeric;
  c_food bigint; c_timber bigint; c_stone bigint; c_gold bigint;
  secs int;
BEGIN
  b := cf_building_base(p_building);
  IF p.build_key IS NOT NULL THEN RAISE EXCEPTION 'A construction queue is already active.'; END IF;
  EXECUTE format('SELECT %I FROM players WHERE id = $1', col) INTO lvl USING p.id;
  IF lvl >= 25 THEN RAISE EXCEPTION 'Already at max level 25.'; END IF;
  IF p_building <> 'KEEP' AND lvl >= p.keep_level + 2 THEN
    RAISE EXCEPTION 'Upgrade your Keep first.';
  END IF;

  factor   := 1 + (lvl - 1) * 0.55;
  c_food   := round(b.food * factor);
  c_timber := round(b.timber * factor);
  c_stone  := round(b.stone * factor);
  c_gold   := round(b.gold * factor);
  IF p.food < c_food OR p.timber < c_timber OR p.stone < c_stone OR p.gold < c_gold THEN
    RAISE EXCEPTION 'Insufficient resources for upgrade.';
  END IF;
  secs := LEAST(GREATEST(round(b.dur * (1 + (lvl - 1) * 0.45))::int, 10), 180);

  UPDATE players SET
    food = food - c_food, timber = timber - c_timber, stone = stone - c_stone, gold = gold - c_gold,
    build_key = p_building,
    build_ends_at = now() + make_interval(secs => secs)
  WHERE id = p.id
  RETURNING * INTO p;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION complete_building_upgrade(p_use_speedup boolean DEFAULT false) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  col text;
BEGIN
  IF p.build_key IS NULL THEN RAISE EXCEPTION 'No active construction.'; END IF;
  IF p_use_speedup THEN
    IF p.speedups > 0 THEN
      UPDATE players SET speedups = speedups - 1 WHERE id = p.id;
    ELSIF p.gold >= 100 THEN
      UPDATE players SET gold = gold - 100 WHERE id = p.id;
    ELSE
      RAISE EXCEPTION 'Need 1 Speedup Hourglass or 100 Gold.';
    END IF;
  ELSIF p.build_ends_at > now() + interval '2 seconds' THEN
    RAISE EXCEPTION 'Construction is not finished yet.';
  END IF;

  col := lower(p.build_key) || '_level';
  EXECUTE format(
    'UPDATE players SET %1$I = LEAST(%1$I + 1, 25), build_key = NULL, build_ends_at = NULL WHERE id = $1 RETURNING *',
    col) INTO p USING p.id;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION start_troop_training(p_troop text, p_qty int) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  t record;
  q int := LEAST(GREATEST(coalesce(p_qty, 1), 1), 500);
  blvl int;
  speed numeric;
  secs int;
BEGIN
  t := cf_troop_base(p_troop);
  IF p.train_key IS NOT NULL THEN RAISE EXCEPTION 'Troops are already training.'; END IF;
  IF p.food < t.food * q OR p.timber < t.timber * q OR p.stone < t.stone * q OR p.gold < t.gold * q THEN
    RAISE EXCEPTION 'Insufficient resources to train % troops.', q;
  END IF;
  EXECUTE format('SELECT %I FROM players WHERE id = $1', lower(t.building) || '_level') INTO blvl USING p.id;
  speed := GREATEST(1 - (blvl - 1) * 0.04, 0.45);
  secs  := LEAST(GREATEST(round(q * t.sec_per_unit * 0.65 * speed)::int, 8), 120);

  UPDATE players SET
    food = food - t.food * q, timber = timber - t.timber * q,
    stone = stone - t.stone * q, gold = gold - t.gold * q,
    train_key = p_troop, train_qty = q,
    train_ends_at = now() + make_interval(secs => secs)
  WHERE id = p.id
  RETURNING * INTO p;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION complete_troop_training(p_use_speedup boolean DEFAULT false) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
BEGIN
  IF p.train_key IS NULL THEN RAISE EXCEPTION 'No active training queue.'; END IF;
  IF p_use_speedup THEN
    IF p.speedups > 0 THEN
      UPDATE players SET speedups = speedups - 1 WHERE id = p.id;
    ELSIF p.gold >= 80 THEN
      UPDATE players SET gold = gold - 80 WHERE id = p.id;
    ELSE
      RAISE EXCEPTION 'Need 1 Speedup Hourglass or 80 Gold.';
    END IF;
  ELSIF p.train_ends_at > now() + interval '2 seconds' THEN
    RAISE EXCEPTION 'Training is not finished yet.';
  END IF;

  EXECUTE format(
    'UPDATE players SET %1$I = LEAST(%1$I + $2, 9999999), train_key = NULL, train_qty = NULL, train_ends_at = NULL WHERE id = $1 RETURNING *',
    lower(p.train_key)) INTO p USING p.id, p.train_qty;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION rename_lord(p_name text) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  n text := trim(p_name);
BEGIN
  IF char_length(n) NOT BETWEEN 2 AND 28 THEN
    RAISE EXCEPTION 'Lord name must be 2 to 28 characters.';
  END IF;
  UPDATE players SET username = n WHERE id = p.id RETURNING * INTO p;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION activate_peace_shield() RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
BEGIN
  IF p.gold < 250 THEN RAISE EXCEPTION 'Need 250 Gold for an 8h Peace Shield.'; END IF;
  UPDATE players SET
    gold = gold - 250,
    shield_until = GREATEST(coalesce(shield_until, now()), now()) + interval '8 hours'
  WHERE id = p.id
  RETURNING * INTO p;
  RETURN p;
END $$;

CREATE OR REPLACE FUNCTION relocate_kingdom(p_x int, p_y int) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE
  p players := cf_me_for_update();
  r realms;
BEGIN
  SELECT * INTO r FROM realms WHERE id = p.home_realm_id;
  IF p_x NOT BETWEEN 0 AND r.map_width - 1 OR p_y NOT BETWEEN 0 AND r.map_height - 1 THEN
    RAISE EXCEPTION 'That location is outside the realm.';
  END IF;
  IF p.gold < 150 THEN RAISE EXCEPTION 'Need 150 Gold to relocate your Keep.'; END IF;
  IF EXISTS (SELECT 1 FROM players WHERE home_realm_id = p.home_realm_id
             AND pos_x = p_x AND pos_y = p_y AND id <> p.id) THEN
    RAISE EXCEPTION 'That tile is already occupied.';
  END IF;
  UPDATE players SET gold = gold - 150, pos_x = p_x, pos_y = p_y
  WHERE id = p.id RETURNING * INTO p;
  RETURN p;
END $$;

REVOKE ALL ON FUNCTION collect_resources(text)              FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION start_building_upgrade(text)         FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION complete_building_upgrade(boolean)   FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION start_troop_training(text, int)      FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION complete_troop_training(boolean)     FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION rename_lord(text)                    FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION activate_peace_shield()              FROM PUBLIC, anon;
REVOKE ALL ON FUNCTION relocate_kingdom(int, int)           FROM PUBLIC, anon;

GRANT EXECUTE ON FUNCTION collect_resources(text)            TO authenticated;
GRANT EXECUTE ON FUNCTION start_building_upgrade(text)       TO authenticated;
GRANT EXECUTE ON FUNCTION complete_building_upgrade(boolean) TO authenticated;
GRANT EXECUTE ON FUNCTION start_troop_training(text, int)    TO authenticated;
GRANT EXECUTE ON FUNCTION complete_troop_training(boolean)   TO authenticated;
GRANT EXECUTE ON FUNCTION rename_lord(text)                  TO authenticated;
GRANT EXECUTE ON FUNCTION activate_peace_shield()            TO authenticated;
GRANT EXECUTE ON FUNCTION relocate_kingdom(int, int)         TO authenticated;
