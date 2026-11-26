DROP TABLE IF EXISTS stronghold_garrisons CASCADE;
DROP TABLE IF EXISTS alliance_pacts       CASCADE;
DROP TABLE IF EXISTS strongholds          CASCADE;
DROP TABLE IF EXISTS battle_logs          CASCADE;
DROP TABLE IF EXISTS marches              CASCADE;
DROP TABLE IF EXISTS rally_participants   CASCADE;
DROP TABLE IF EXISTS rallies              CASCADE;
DROP TABLE IF EXISTS aid_requests         CASCADE;
DROP TABLE IF EXISTS alliance_invites     CASCADE;
DROP TABLE IF EXISTS alliance_messages    CASCADE;
DROP TABLE IF EXISTS players              CASCADE;
DROP TABLE IF EXISTS alliances            CASCADE;
DROP TABLE IF EXISTS realms               CASCADE;

CREATE OR REPLACE FUNCTION set_updated_at() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
  NEW.updated_at := now();
  RETURN NEW;
END $$;

CREATE TABLE realms (
  id                text PRIMARY KEY,
  name              text NOT NULL,
  biome_theme       text NOT NULL DEFAULT 'Dark Medieval',
  map_width         int  NOT NULL DEFAULT 1400 CHECK (map_width  BETWEEN 100 AND 5000),
  map_height        int  NOT NULL DEFAULT 1400 CHECK (map_height BETWEEN 100 AND 5000),
  opened_at         timestamptz NOT NULL DEFAULT now(),
  protection_ends_at timestamptz NOT NULL DEFAULT now() + interval '14 days',
  accepting_new_players boolean NOT NULL DEFAULT true
);

CREATE TABLE alliances (
  id            uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  realm_id      text NOT NULL REFERENCES realms(id),
  name          text NOT NULL CHECK (char_length(name) BETWEEN 3 AND 32),
  tag           text NOT NULL CHECK (tag ~ '^[A-Z0-9]{2,5}$'),
  motto         text NOT NULL DEFAULT '' CHECK (char_length(motto) <= 120),
  crest_color   text NOT NULL DEFAULT 'CRIMSON'
                CHECK (crest_color IN ('CRIMSON','GOLD','OBSIDIAN','EMERALD','ROYAL')),
  leader_id     uuid NOT NULL,
  open_join     boolean NOT NULL DEFAULT true,
  member_count  int    NOT NULL DEFAULT 1 CHECK (member_count BETWEEN 0 AND 30),
  total_power   bigint NOT NULL DEFAULT 0 CHECK (total_power >= 0),
  tech_march_level   int NOT NULL DEFAULT 0 CHECK (tech_march_level   BETWEEN 0 AND 10),
  tech_yield_level   int NOT NULL DEFAULT 0 CHECK (tech_yield_level   BETWEEN 0 AND 10),
  tech_attack_level  int NOT NULL DEFAULT 0 CHECK (tech_attack_level  BETWEEN 0 AND 10),
  tech_defense_level int NOT NULL DEFAULT 0 CHECK (tech_defense_level BETWEEN 0 AND 10),
  tech_xp       bigint NOT NULL DEFAULT 0 CHECK (tech_xp >= 0),
  created_at    timestamptz NOT NULL DEFAULT now(),
  updated_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (realm_id, tag),
  UNIQUE (realm_id, name)
);
CREATE TRIGGER trg_alliances_updated BEFORE UPDATE ON alliances
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE players (
  id              uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  username        text NOT NULL CHECK (char_length(username) BETWEEN 2 AND 28),
  home_realm_id   text NOT NULL REFERENCES realms(id),
  pos_x           int  NOT NULL CHECK (pos_x BETWEEN 0 AND 4999),
  pos_y           int  NOT NULL CHECK (pos_y BETWEEN 0 AND 4999),

  food            bigint NOT NULL DEFAULT 5000 CHECK (food   >= 0),
  timber          bigint NOT NULL DEFAULT 5000 CHECK (timber >= 0),
  stone           bigint NOT NULL DEFAULT 4000 CHECK (stone  >= 0),
  gold            bigint NOT NULL DEFAULT 2500 CHECK (gold   >= 0),
  speedups        int    NOT NULL DEFAULT 10   CHECK (speedups >= 0),
  last_harvest_at timestamptz NOT NULL DEFAULT now(),

  keep_level          int NOT NULL DEFAULT 1 CHECK (keep_level          BETWEEN 1 AND 25),
  barracks_level      int NOT NULL DEFAULT 1 CHECK (barracks_level      BETWEEN 1 AND 25),
  stables_level       int NOT NULL DEFAULT 1 CHECK (stables_level       BETWEEN 1 AND 25),
  archery_level       int NOT NULL DEFAULT 1 CHECK (archery_level       BETWEEN 1 AND 25),
  siege_level         int NOT NULL DEFAULT 1 CHECK (siege_level         BETWEEN 1 AND 25),
  alliance_hall_level int NOT NULL DEFAULT 1 CHECK (alliance_hall_level BETWEEN 1 AND 25),
  farm_level          int NOT NULL DEFAULT 1 CHECK (farm_level          BETWEEN 1 AND 25),
  lumber_level        int NOT NULL DEFAULT 1 CHECK (lumber_level        BETWEEN 1 AND 25),
  quarry_level        int NOT NULL DEFAULT 1 CHECK (quarry_level        BETWEEN 1 AND 25),
  mint_level          int NOT NULL DEFAULT 1 CHECK (mint_level          BETWEEN 1 AND 25),
  waygate_level       int NOT NULL DEFAULT 1 CHECK (waygate_level       BETWEEN 1 AND 25),

  infantry        bigint NOT NULL DEFAULT 80 CHECK (infantry >= 0),
  cavalry         bigint NOT NULL DEFAULT 30 CHECK (cavalry  >= 0),
  archers         bigint NOT NULL DEFAULT 50 CHECK (archers  >= 0),
  siege           bigint NOT NULL DEFAULT 8  CHECK (siege    >= 0),

  build_key       text,                                   -- e.g. 'KEEP'
  build_ends_at   timestamptz,
  train_key       text,                                   -- e.g. 'CAVALRY'
  train_qty       int CHECK (train_qty IS NULL OR train_qty BETWEEN 1 AND 50000),
  train_ends_at   timestamptz,

  power           bigint NOT NULL DEFAULT 0 CHECK (power >= 0),
  honor           bigint NOT NULL DEFAULT 0 CHECK (honor >= 0),
  shield_until    timestamptz,

  alliance_id     uuid REFERENCES alliances(id) ON DELETE SET NULL,
  alliance_role   text NOT NULL DEFAULT 'NONE'
                  CHECK (alliance_role IN ('NONE','MEMBER','OFFICER','LEADER')),

  active_visit_realm_id text REFERENCES realms(id),
  visit_expires_at      timestamptz,

  created_at      timestamptz NOT NULL DEFAULT now(),
  updated_at      timestamptz NOT NULL DEFAULT now(),

  UNIQUE (home_realm_id, pos_x, pos_y),
  CHECK ((alliance_id IS NULL) = (alliance_role = 'NONE')),
  CHECK ((active_visit_realm_id IS NULL) = (visit_expires_at IS NULL)),
  CHECK (active_visit_realm_id IS NULL OR active_visit_realm_id <> home_realm_id)
);
CREATE TRIGGER trg_players_updated BEFORE UPDATE ON players
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_players_realm_pos ON players (home_realm_id, pos_x, pos_y);
CREATE INDEX idx_players_alliance  ON players (alliance_id);
CREATE INDEX idx_players_visiting  ON players (active_visit_realm_id) WHERE active_visit_realm_id IS NOT NULL;
CREATE INDEX idx_players_power     ON players (home_realm_id, power DESC);

ALTER TABLE alliances
  ADD CONSTRAINT alliances_leader_fk FOREIGN KEY (leader_id)
  REFERENCES players(id) DEFERRABLE INITIALLY DEFERRED;
