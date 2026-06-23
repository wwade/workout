# Developing

This document is for contributors working on the Workout Tracker Android app.

## Requirements

- Android Studio with a recent stable Android Gradle Plugin compatible with the project
- JDK 17
- Android SDK installed locally
- An emulator or physical Android device for manual testing

## First-Time Setup

1. Open the project root in Android Studio.
2. Allow Android Studio to install or select JDK 17 if prompted.
3. Install the Android SDK platform required by `compileSdk`.
4. Sync the project so Gradle can resolve dependencies.
5. Run the app on a device or emulator.

If the project does not sync:

- confirm network access for Gradle dependency download
- confirm the Android SDK is installed
- confirm Android Studio is using JDK 17

## Common Commands

From Android Studio:

- Sync project
- Run app
- Run unit tests
- Run instrumentation tests
- Use the debug Android test variant for device-backed instrumentation runs

From a terminal, once Gradle is available:

```bash
./gradlew test
./gradlew connectedAndroidTest
./gradlew assembleDebug
./gradlew assembleE2e
./gradlew app:connectedDebugAndroidTest
```

On Windows with the wrapper present:

```powershell
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleE2e
.\gradlew.bat app:connectedDebugAndroidTest
```

Build identities:

- `release`: `dev.wwade.workout`
- `debug`: `dev.wwade.workout.debug`
- `e2e`: `dev.wwade.workout.e2e`

Instrumentation tests currently live under the debug Android test variant and are run with `app:connectedDebugAndroidTest` or `connectedAndroidTest`. The `e2e` build variant assembles a dedicated app id, but this Gradle project does not currently expose an e2e-specific connected Android test task.

## Architecture

The project is intentionally simple for the MVP:

- `app/src/main/java/dev/wwade/workout/data`
  - Room entities, DAOs, database, repository implementations
- `app/src/main/java/dev/wwade/workout/domain`
  - domain models, repository interfaces, validation, import parsing, use cases
- `app/src/main/java/dev/wwade/workout/ui`
  - Compose screens, navigation, viewmodels, UI state

Important entry points:

- `WorkoutApplication.kt`: dependency container and Room database setup
- `MainActivity.kt`: app entry activity
- `WorkoutNavHost.kt`: navigation graph

## Key Behavior Rules

- A workout must contain at least one circuit.
- A circuit must contain at least one exercise.
- Every exercise must have at least one set.
- All exercises in the same circuit must use the same set count.
- Imported workout templates use the same validation rules as editor-created templates.
- Template imports append new templates and do not overwrite, merge, or skip duplicate names.
- Full backup imports replace current workout data with the imported backup.
- Imported exercises reuse exercise library definitions by normalized name and create missing definitions.
- Session execution is round-based and interleaved inside each circuit.
- Completed sessions are stored as snapshots so history is preserved even if templates change later.
- Active-session prefill and exercise history use shared exercise definition ids across workouts.

## Import Notes

Workout import is implemented as a domain-level importer plus a workout-list UI flow:

- `ImportWorkoutsUseCase` coordinates fetching/parsing, validation, and saving.
- `WorkoutImportParser` accepts JSON or YAML, either as `{"workouts":[...]}` / `workouts: [...]` or one single workout object.
- Full JSON backups are detected by top-level `schemaVersion`; supported full backups use export `schemaVersion` `1` or `2`.
- `RoomWorkoutDataImportRepository` restores full backups in one replace-all Room transaction with preserved ids.
- URL imports use `HttpURLConnection` and require `android.permission.INTERNET`.
- Local file imports use `ActivityResultContracts.OpenDocument` and read the selected `Uri` once through `ContentResolver`.
- Import DTOs are intentionally separate from Room entities and domain models.

The import schema maps to workout drafts, then save-time resolution creates or reuses exercise definitions. JSON and YAML use the same field names. Required structural fields are workout `circuits` and circuit `exercises`; exercise fields otherwise fall back to the same defaults as `ExerciseDraft` where possible. Exercise name matching trims, collapses whitespace, and compares case-insensitively.

Full backup import is JSON-only and is the inverse of full export. It clears workout/session/exercise data, then restores exercise definitions, templates, sessions, snapshot rows, and set entries from the backup. Schema version 1 backups predate exercise definitions, so the parser derives missing definitions from exported template and session snapshots before Room restore. Early schema version 2 backups can contain null historical session `exerciseDefinitionId` values; the parser backfills them from the referenced template when present, then by normalized session snapshot name.

## Export Notes

Full data export is implemented as a separate domain-level exporter plus a workout-list save flow:

- `ExportWorkoutDataUseCase` gathers templates and session history and serializes JSON.
- Export DTOs live under `domain/exporter` and stay separate from Room entities and domain models.
- Local export uses `ActivityResultContracts.CreateDocument` and writes the selected `Uri` once through `ContentResolver`.
- The current export schema is JSON-only and includes exercise definitions, templates, sessions, snapshot rows, and set entries.
- Export `schemaVersion` is `2`; exercise template and session exercise rows include `exerciseDefinitionId`. Older `schemaVersion` `1` exports did not include those fields and are handled by the import compatibility path.

## Google Drive Backup Notes

- Drive backups reuse the full JSON export/import schema; there is no separate backup payload format.
- Drive storage is scoped to `https://www.googleapis.com/auth/drive.appdata`, so snapshots live in the app-specific hidden Drive data folder.
- Drive authorization requires Google Cloud setup outside this repository. Enable the Google Drive API in the Cloud project, then create Android OAuth clients for each installed app id you need to test:
  - `dev.wwade.workout` for release
  - `dev.wwade.workout.debug` for debug
  - `dev.wwade.workout.e2e` for e2e
- Each Android OAuth client must use the SHA-1 certificate fingerprint for the keystore that signed that variant. For local debug/e2e builds, run `./gradlew signingReport` or `.\gradlew.bat signingReport` and copy the matching variant's SHA-1. For release, use the release signing certificate SHA-1.
- `UNREGISTERED_ON_API_CONSOLE` during Drive permission means the installed package name and signing SHA-1 do not match an Android OAuth client in the Cloud project, or the Drive API is not enabled.
- Automatic backup is enabled only after explicit user authorization from the workout list screen.
- `RoomSessionRepository.saveRoundEntries` returns whether the save newly completed the session. `ActiveSessionViewModel` uses that transition to launch a non-blocking Drive backup.
- `BackupNowAfterWorkoutCompletionUseCase` uploads a new snapshot, then prunes older snapshots so only the newest five remain.
- Restore from Drive downloads the selected snapshot and uses the same destructive full-backup restore path as local backup import.
- If silent authorization is unavailable during automatic backup, completion still succeeds and the backup failure is stored in Drive backup settings for the home screen.

## Persistence Notes

- Template definitions and performed sessions are stored in Room.
- Shared exercises live in `exercise_definitions`; workout exercises reference them from `exercise_templates`.
- Session start creates snapshot rows in the session tables.
- Prefill for active sessions comes from the previous set in the current session, then the latest completed session for the same `exerciseDefinitionId` and `setIndex`.
- Exercise history shown during workouts aggregates completed sets by `exerciseDefinitionId`, including matching exercises from other workouts.
- Only one active session is expected at a time in this MVP.
- Template imports save each valid workout through `WorkoutRepository.saveWorkout` with a new template id.
- Full backup imports preserve exported ids so template/session relationships round-trip.

## Testing

Current test coverage includes:

- workout validation unit tests
- workout import parser and import use case unit tests
- workout list import state unit tests
- session progression unit tests
- Room relationship, snapshot, and full backup restore instrumentation tests
- a basic Compose UI instrumentation test for the workout list empty state

Add tests alongside new behavior when possible:

- use `src/test` for pure Kotlin and domain logic
- use `src/androidTest` for Room, Compose, and device-backed behavior

## Known Gaps

- The app currently uses a lightweight manual dependency container instead of a DI framework.
- The editor uses dialogs and move buttons instead of drag-and-drop or a multi-screen editing flow.
- Instrumentation and Compose device tests still require a connected emulator or physical device.

## Suggested Next Improvements

- improve form UX for editing circuits and exercises
- add richer session and history tests
- add conflict-aware preview/confirmation UI for full backup restores
