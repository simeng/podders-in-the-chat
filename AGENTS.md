# AGENTS.md

Conventions for AI agents (and humans) working in this repo. Read before making changes.

## Project

"Podders in the Chat" — a native Android podcast player.
Stack: Kotlin, Jetpack Compose (Material 3), Media3/ExoPlayer, Room, OkHttp, Coil, WorkManager.
Package: `com.poddersinthechat.app`. Entry points: `PoddersApp.kt` (DI wiring), `MainActivity.kt`.

## Build & test

```bash
./gradlew assembleDebug          # APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # unit tests
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Database migrations — NEVER make users re-add their subscriptions

**Rule: whenever you change the Room schema (bump `AppDatabase` version), add an explicit
`Migration` to `AppDatabase.MIGRATION_x_y` and register it with `.addMigrations(...)` in the
same change.**

- The user's subscribed feeds, episode playback progress, and offline downloads are precious
  state. An upgrade must preserve them.
- `fallbackToDestructiveMigration()` stays only as a last resort for versions that have no
  explicit migration — it must never be the planned path for a version bump you're making.
- Hand-written DDL in a migration must match exactly what Room generates for the entities
  (column names, types, NOT NULL, primary key, indices). Check the generated
  `app/build/generated/ksp/debug/java/.../AppDatabase_Impl.java` (`createAllTables`) for the
  canonical DDL — a mismatch fails Room's schema assertion on upgrade.
- If an entity is renamed/dropped, migrate the data (e.g. `ALTER TABLE ... RENAME TO ...`,
  `INSERT INTO ... SELECT ...`) rather than recreating tables.
- Schema snapshots: `exportSchema = true` is on; exports land in
  `app/schemas/com.poddersinthechat.app.data.db.AppDatabase/` (committed) and are copied to
  `app/src/main/assets/schema/<version>.json` for the Robolectric migration test.
  After ANY schema change: `./gradlew kspDebugKotlin`, copy the new JSON to both places,
  add a case to `app/src/test/java/com/poddersinthechat/app/data/db/MigrationTest.kt`
  proving the upgrade preserves user data, and run `./gradlew testDebugUnitTest`.
  Note: the migration test downloads Robolectric artifacts on first run (network needed).

## UI conventions

- **24-hour time format, no AM/PM** — this app does 24h. Use `FormatUtils.describeHour`
  (`"%02d:00"`). Applies to all user-facing times (settings, player, notifications).
- Offline downloads are an opt-in *side channel* (long-press → download button). They must
  never block or alter normal streaming, and they are temporary (retention setting, default
  7 days, can be set to "Never"). Keep it that way.
- Settings (`SettingsSheet`) is the single place for "when / after X days" behavior —
  scheduled refresh time, auto-play-next, download retention. Anything with a timer or
  threshold should be toggleable there.

## Repo hygiene

- Keep the repo lean: no generated files in git (recommendations.json, build output, .gradle —
  all gitignored).
- Commits: split logical features into separate commits; each commit should compile.
