-- Crownfall part 15: AI lords take painted portraits (1-10) instead of crests
UPDATE players SET avatar = 'portrait:' || (1 + floor(random() * 10))::int WHERE is_bot;
SELECT username, avatar FROM players WHERE is_bot ORDER BY home_realm_id, username;
