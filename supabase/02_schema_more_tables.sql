CREATE TABLE alliance_messages (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alliance_id  uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  sender_id    uuid REFERENCES players(id) ON DELETE SET NULL,
  sender_name  text NOT NULL,
  sender_role  text NOT NULL CHECK (sender_role IN ('LEADER','OFFICER','MEMBER','SYSTEM')),
  body         text NOT NULL CHECK (char_length(body) BETWEEN 1 AND 300),
  message_type text NOT NULL DEFAULT 'CHAT'
               CHECK (message_type IN ('CHAT','RALLY','AID','SYSTEM')),
  created_at   timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_messages_alliance_time ON alliance_messages (alliance_id, created_at DESC);

CREATE TABLE alliance_invites (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alliance_id  uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  inviter_id   uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  target_id    uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  status       text NOT NULL DEFAULT 'PENDING'
               CHECK (status IN ('PENDING','ACCEPTED','DECLINED','EXPIRED')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  CHECK (inviter_id <> target_id)
);
CREATE UNIQUE INDEX uq_invite_pending ON alliance_invites (alliance_id, target_id)
  WHERE status = 'PENDING';
CREATE INDEX idx_invites_target ON alliance_invites (target_id) WHERE status = 'PENDING';

CREATE TABLE aid_requests (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alliance_id      uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  requester_id     uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  resource_type    text NOT NULL CHECK (resource_type IN ('FOOD','TIMBER','STONE','GOLD')),
  requested_amount bigint NOT NULL CHECK (requested_amount BETWEEN 100 AND 1000000),
  fulfilled_amount bigint NOT NULL DEFAULT 0 CHECK (fulfilled_amount >= 0),
  status           text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','FULFILLED','CANCELLED')),
  donor_ids        uuid[] NOT NULL DEFAULT '{}',
  created_at       timestamptz NOT NULL DEFAULT now(),
  updated_at       timestamptz NOT NULL DEFAULT now(),
  CHECK (fulfilled_amount <= requested_amount)
);
CREATE TRIGGER trg_aid_updated BEFORE UPDATE ON aid_requests
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_aid_alliance_open ON aid_requests (alliance_id) WHERE status = 'OPEN';

CREATE TABLE rallies (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alliance_id  uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  leader_id    uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  realm_id     text NOT NULL REFERENCES realms(id),
  target_type  text NOT NULL CHECK (target_type IN ('KINGDOM','BASTION','STRONGHOLD')),
  target_id    text NOT NULL,
  target_name  text NOT NULL CHECK (char_length(target_name) BETWEEN 1 AND 48),
  target_x     int  NOT NULL,
  target_y     int  NOT NULL,
  total_power  bigint NOT NULL DEFAULT 0 CHECK (total_power >= 0),
  launch_at    timestamptz NOT NULL,
  status       text NOT NULL DEFAULT 'GATHERING'
               CHECK (status IN ('GATHERING','MARCHING','RESOLVED','CANCELLED')),
  created_at   timestamptz NOT NULL DEFAULT now(),
  updated_at   timestamptz NOT NULL DEFAULT now()
);
CREATE TRIGGER trg_rallies_updated BEFORE UPDATE ON rallies
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_rallies_alliance ON rallies (alliance_id, status);
CREATE INDEX idx_rallies_launch   ON rallies (launch_at) WHERE status = 'GATHERING';

CREATE TABLE rally_participants (
  rally_id     uuid NOT NULL REFERENCES rallies(id) ON DELETE CASCADE,
  player_id    uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  troops_json  jsonb  NOT NULL DEFAULT '{}'::jsonb,
  power        bigint NOT NULL DEFAULT 0 CHECK (power >= 0),
  joined_at    timestamptz NOT NULL DEFAULT now(),
  PRIMARY KEY (rally_id, player_id)               -- can't join the same rally twice
);

CREATE TABLE marches (
  id           uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  player_id    uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  realm_id     text NOT NULL REFERENCES realms(id),
  march_type   text NOT NULL CHECK (march_type IN
               ('RAID','ATTACK','GATHER','RALLY','REINFORCE','FORTIFY','VISITOR_CAMP')),
  target_type  text NOT NULL CHECK (target_type IN
               ('KINGDOM','BASTION','RESOURCE','STRONGHOLD','TILE')),
  target_id    text,
  rally_id     uuid REFERENCES rallies(id) ON DELETE SET NULL,
  start_x      int NOT NULL,
  start_y      int NOT NULL,
  target_x     int NOT NULL,
  target_y     int NOT NULL,
  departed_at  timestamptz NOT NULL DEFAULT now(),
  arrives_at   timestamptz NOT NULL,
  returns_at   timestamptz,
  troops_json  jsonb NOT NULL DEFAULT '{}'::jsonb,
  troop_power  bigint NOT NULL DEFAULT 0 CHECK (troop_power >= 0),
  status       text NOT NULL DEFAULT 'MARCHING'
               CHECK (status IN ('MARCHING','ARRIVED','RETURNING','COMPLETED','RECALLED')),
  CHECK (arrives_at >= departed_at)
);
CREATE INDEX idx_marches_due    ON marches (arrives_at) WHERE status = 'MARCHING';
CREATE INDEX idx_marches_player ON marches (player_id, status);
CREATE INDEX idx_marches_realm  ON marches (realm_id) WHERE status IN ('MARCHING','RETURNING');

CREATE TABLE strongholds (
  id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alliance_id         uuid NOT NULL UNIQUE REFERENCES alliances(id) ON DELETE CASCADE,
  home_realm_id       text NOT NULL REFERENCES realms(id),
  pos_x               int NOT NULL,
  pos_y               int NOT NULL,
  level               int NOT NULL DEFAULT 1 CHECK (level BETWEEN 1 AND 10),
  durability          bigint NOT NULL DEFAULT 100000,
  max_durability      bigint NOT NULL DEFAULT 100000 CHECK (max_durability > 0),
  is_under_siege      boolean NOT NULL DEFAULT false,
  siege_started_at    timestamptz,
  call_for_aid_active boolean NOT NULL DEFAULT false,
  aid_message         text CHECK (aid_message IS NULL OR char_length(aid_message) <= 140),
  created_at          timestamptz NOT NULL DEFAULT now(),
  updated_at          timestamptz NOT NULL DEFAULT now(),
  CHECK (durability BETWEEN 0 AND max_durability),
  UNIQUE (home_realm_id, pos_x, pos_y)
);
CREATE TRIGGER trg_strongholds_updated BEFORE UPDATE ON strongholds
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();
CREATE INDEX idx_strongholds_aid ON strongholds (home_realm_id) WHERE call_for_aid_active;

CREATE TABLE stronghold_garrisons (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  stronghold_id    uuid NOT NULL REFERENCES strongholds(id) ON DELETE CASCADE,
  player_id        uuid NOT NULL REFERENCES players(id) ON DELETE CASCADE,
  alliance_id      uuid REFERENCES alliances(id) ON DELETE SET NULL,
  march_id         uuid REFERENCES marches(id) ON DELETE SET NULL,
  troops_json      jsonb  NOT NULL DEFAULT '{}'::jsonb,
  troop_power      bigint NOT NULL DEFAULT 0 CHECK (troop_power >= 0),
  is_allied_relief boolean NOT NULL DEFAULT false,
  garrisoned_at    timestamptz NOT NULL DEFAULT now(),
  UNIQUE (stronghold_id, player_id)
);
CREATE INDEX idx_garrisons_stronghold ON stronghold_garrisons (stronghold_id);

CREATE TABLE alliance_pacts (
  id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  alliance_a_id  uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  alliance_b_id  uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  requested_by   uuid NOT NULL REFERENCES alliances(id) ON DELETE CASCADE,
  status         text NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING','ACTIVE','ENDED')),
  created_at     timestamptz NOT NULL DEFAULT now(),
  updated_at     timestamptz NOT NULL DEFAULT now(),
  CHECK (alliance_a_id < alliance_b_id),
  CHECK (requested_by IN (alliance_a_id, alliance_b_id))
);
CREATE UNIQUE INDEX uq_pacts_open ON alliance_pacts (alliance_a_id, alliance_b_id)
  WHERE status IN ('PENDING','ACTIVE');
CREATE TRIGGER trg_pacts_updated BEFORE UPDATE ON alliance_pacts
  FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE battle_logs (
  id               uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  realm_id         text NOT NULL REFERENCES realms(id),
  battle_type      text NOT NULL CHECK (battle_type IN ('RAID','ATTACK','RALLY','SIEGE','DEFENSE')),
  attacker_id      uuid REFERENCES players(id) ON DELETE SET NULL,
  attacker_name    text NOT NULL,
  attacker_alliance_id uuid REFERENCES alliances(id) ON DELETE SET NULL,
  defender_id      uuid REFERENCES players(id) ON DELETE SET NULL,
  defender_name    text NOT NULL,
  defender_alliance_id uuid REFERENCES alliances(id) ON DELETE SET NULL,
  stronghold_id    uuid REFERENCES strongholds(id) ON DELETE SET NULL,
  victory          boolean NOT NULL,
  attacker_power   bigint NOT NULL DEFAULT 0,
  defender_power   bigint NOT NULL DEFAULT 0,
  attacker_losses  bigint NOT NULL DEFAULT 0,
  defender_losses  bigint NOT NULL DEFAULT 0,
  loot_food        bigint NOT NULL DEFAULT 0,
  loot_timber      bigint NOT NULL DEFAULT 0,
  loot_stone       bigint NOT NULL DEFAULT 0,
  loot_gold        bigint NOT NULL DEFAULT 0,
  created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX idx_battles_attacker ON battle_logs (attacker_id, created_at DESC);
CREATE INDEX idx_battles_defender ON battle_logs (defender_id, created_at DESC);
CREATE INDEX idx_battles_att_all  ON battle_logs (attacker_alliance_id, created_at DESC);
CREATE INDEX idx_battles_def_all  ON battle_logs (defender_alliance_id, created_at DESC);
