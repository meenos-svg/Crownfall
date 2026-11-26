-- Crownfall part 14: lord avatars
ALTER TABLE players ADD COLUMN IF NOT EXISTS avatar text NOT NULL DEFAULT 'crest:1';
ALTER TABLE players ADD COLUMN IF NOT EXISTS avatar_url text;

-- crest:1-12 (heraldic crests), portrait:1-24 (painted portraits), or google (your Google profile photo)
DROP FUNCTION IF EXISTS set_avatar(text);
CREATE OR REPLACE FUNCTION set_avatar(p_avatar text) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v_url text;
BEGIN
  IF p_avatar IS NULL OR p_avatar !~ '^(crest:([1-9]|1[0-2])|portrait:([1-9]|1[0-9]|2[0-4])|google)$' THEN
    RAISE EXCEPTION 'Unknown avatar.';
  END IF;
  IF p_avatar = 'google' THEN
    SELECT coalesce(raw_user_meta_data ->> 'avatar_url', raw_user_meta_data ->> 'picture')
      INTO v_url FROM auth.users WHERE id = me.id;
    IF v_url IS NULL OR v_url !~ '^https://' THEN
      RAISE EXCEPTION 'Your Google account has no profile photo.';
    END IF;
  END IF;
  UPDATE players SET avatar = p_avatar, avatar_url = v_url WHERE id = me.id RETURNING * INTO me;
  RETURN me;
END $$;
REVOKE ALL ON FUNCTION set_avatar(text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION set_avatar(text) TO authenticated;

-- give each AI lord its own crest
UPDATE players SET avatar = 'crest:' || (1 + floor(random() * 12))::int WHERE is_bot;
