-- Crownfall part 17: upload your own avatar
-- A public "avatars" storage bucket; each lord may only write inside their own folder (their user id).
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES ('avatars', 'avatars', true, 524288, ARRAY['image/jpeg', 'image/png', 'image/webp'])
ON CONFLICT (id) DO UPDATE SET public = true, file_size_limit = EXCLUDED.file_size_limit,
  allowed_mime_types = EXCLUDED.allowed_mime_types;

DROP POLICY IF EXISTS "avatars are public" ON storage.objects;
DROP POLICY IF EXISTS "lords upload own avatar" ON storage.objects;
DROP POLICY IF EXISTS "lords replace own avatar" ON storage.objects;
DROP POLICY IF EXISTS "lords delete own avatar" ON storage.objects;
CREATE POLICY "avatars are public" ON storage.objects FOR SELECT
  USING (bucket_id = 'avatars');
CREATE POLICY "lords upload own avatar" ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "lords replace own avatar" ON storage.objects FOR UPDATE TO authenticated
  USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text)
  WITH CHECK (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);
CREATE POLICY "lords delete own avatar" ON storage.objects FOR DELETE TO authenticated
  USING (bucket_id = 'avatars' AND (storage.foldername(name))[1] = auth.uid()::text);

-- set_avatar now also takes 'custom' with the uploaded picture's address, which must be in your own folder.
DROP FUNCTION IF EXISTS set_avatar(text);
CREATE OR REPLACE FUNCTION set_avatar(p_avatar text, p_url text DEFAULT NULL) RETURNS players
LANGUAGE plpgsql SECURITY DEFINER SET search_path = public AS $$
DECLARE me players := cf_me_for_update(); v_url text;
BEGIN
  IF p_avatar IS NULL OR p_avatar !~ '^(crest:([1-9]|1[0-2])|portrait:([1-9]|1[0-9]|2[0-4])|google|custom)$' THEN
    RAISE EXCEPTION 'Unknown avatar.';
  END IF;
  IF p_avatar = 'google' THEN
    SELECT coalesce(raw_user_meta_data ->> 'avatar_url', raw_user_meta_data ->> 'picture')
      INTO v_url FROM auth.users WHERE id = me.id;
    IF v_url IS NULL OR v_url !~ '^https://' THEN
      RAISE EXCEPTION 'Your Google account has no profile photo.';
    END IF;
  ELSIF p_avatar = 'custom' THEN
    IF p_url IS NULL OR p_url !~ ('^https://[^/]+/storage/v1/object/public/avatars/' || me.id::text || '/[A-Za-z0-9._-]+$') THEN
      RAISE EXCEPTION 'That picture is not one of your uploads.';
    END IF;
    v_url := p_url;
  END IF;
  UPDATE players SET avatar = p_avatar, avatar_url = v_url WHERE id = me.id RETURNING * INTO me;
  RETURN me;
END $$;
REVOKE ALL ON FUNCTION set_avatar(text, text) FROM PUBLIC, anon;
GRANT EXECUTE ON FUNCTION set_avatar(text, text) TO authenticated;
