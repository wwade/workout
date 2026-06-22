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
- Imports append new templates and do not overwrite, merge, or skip duplicate names.
- Imported exercises reuse exercise library definitions by normalized name and create missing definitions.
- Session execution is round-based and interleaved inside each circuit.
- Completed sessions are stored as snapshots so history is preserved even if templates change later.
- Active-session prefill and exercise history use shared exercise definition ids across workouts.

## Import Notes

Workout template import is implemented as a domain-level importer plus a workout-list UI flow:

- `ImportWorkoutsUseCase` coordinates fetching/parsing, validation, and saving.
- `WorkoutImportParser` accepts JSON or YAML, either as `{"workouts":[...]}` / `workouts: [...]` or one single workout object.
- URL imports use `HttpURLConnection` and require `android.permission.INTERNET`.
- Local file imports use `ActivityResultContracts.OpenDocument` and read the selected `Uri` once through `ContentResolver`.
- Import DTOs are intentionally separate from Room entities and domain models.

The import schema maps to workout drafts, then save-time resolution creates or reuses exercise definitions. JSON and YAML use the same field names. Required structural fields are workout `circuits` and circuit `exercises`; exercise fields otherwise fall back to the same defaults as `ExerciseDraft` where possible. Exercise name matching trims, collapses whitespace, and compares case-insensitively.

## Export Notes

Full data export is implemented as a separate domain-level exporter plus a workout-list save flow:

- `ExportWorkoutDataUseCase` gathers templates and session history and serializes JSON.
- Export DTOs live under `domain/exporter` and stay separate from Room entities and domain models.
- Local export uses `ActivityResultContracts.CreateDocument` and writes the selected `Uri` once through `ContentResolver`.
- The current export schema is JSON-only and includes exercise definitions, templates, sessions, snapshot rows, and set entries.
- Export `schemaVersion` is `2`; exercise template and session exercise rows include `exerciseDefinitionId`.

## Persistence Notes

- Template definitions and performed sessions are stored in Room.
- Shared exercises live in `exercise_definitions`; workout exercises reference them from `exercise_templates`.
- Session start creates snapshot rows in the session tables.
- Prefill for active sessions comes from the previous set in the current session, then the latest completed session for the same `exerciseDefinitionId` and `setIndex`.
- Exercise history shown during workouts aggregates completed sets by `exerciseDefinitionId`, including matching exercises from other workouts.
- Only one active session is expected at a time in this MVP.
- Imports save each valid workout through `WorkoutRepository.saveWorkout` with a new template id.

## Testing

Current test coverage includes:

- workout validation unit tests
- workout import parser and import use case unit tests
- workout list import state unit tests
- session progression unit tests
- Room relationship and snapshot instrumentation tests
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
- add import support for full-data restores once the export schema is ready for round-tripping
