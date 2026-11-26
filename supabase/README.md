# Crownfall database (Supabase)

Every SQL change the game's server needs, in the order to run it.

**Rebuilding from scratch** (for example a new Supabase project): open SQL Editor and run the files
`01` → `18` in order, one per query. A few are long, so run each one on its own and wait for "Success".

**After running:**
- `13_scheduler.sql` needs the **pg_cron** extension (Database → Extensions → enable pg_cron first).
- `17_custom_avatars.sql` creates the `avatars` storage bucket and its rules.
- Google sign-in, the Android OAuth client and the app's Supabase URL/key are set up in the
  Supabase dashboard, not here. No secrets live in these files.

**Warning:** `01_schema_tables.sql` starts by dropping the game's tables. Only run it on an empty or
throwaway project, never on the live game.

| File | What it adds |
|---|---|
| 01–03 | Tables, security rules, realms |
| 04 | Empire actions (build, train, harvest) |
| 05 | Alliances, chat, aid |
| 06 | Marches, rallies, battles |
| 07 | Strongholds, garrisons, calls for aid, pacts |
| 08 | Multiple realms, expeditions and camps |
| 09 | Landmarks and territory |
| 10 | Map positions |
| 11 | Land masks: castles only on land |
| 12 | AI lords |
| 13 | Server tick every 30 s (pg_cron) |
| 14–15 | Avatars and AI lord portraits |
| 16 | Lord profiles and mottos |
| 17 | Uploaded avatar photos |
| 18 | Red Dragon world boss |
