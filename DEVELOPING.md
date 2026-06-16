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
- Prefer the `e2e` build variant for device-backed integration runs

From a terminal, once Gradle is available:

```bash
./gradlew test
./gradlew connectedAndroidTest
./gradlew assembleDebug
./gradlew assembleE2e
./gradlew connectedE2eAndroidTest
```

On Windows with the wrapper present:

```powershell
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat assembleDebug
.\gradlew.bat assembleE2e
.\gradlew.bat connectedE2eAndroidTest
```

Build identities:

- `release`: `dev.wwade.workout`
- `debug`: `dev.wwade.workout.debug`
- `e2e`: `dev.wwade.workout.e2e`

Use `connectedE2eAndroidTest` when you want instrumentation tests to run against the dedicated `e2e` app install instead of the regular debug install.

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
- Session execution is round-based and interleaved inside each circuit.
- Completed sessions are stored as snapshots so history is preserved even if templates change later.

## Import Notes

Workout template import is implemented as a domain-level importer plus a workout-list UI flow:

- `ImportWorkoutsUseCase` coordinates fetching/parsing, validation, and saving.
- `WorkoutImportParser` accepts either `{"workouts":[...]}` or one single workout object.
- URL imports use `HttpURLConnection` and require `android.permission.INTERNET`.
- Local file imports use `ActivityResultContracts.OpenDocument` and read the selected `Uri` once through `ContentResolver`.
- JSON DTOs are intentionally separate from Room entities and domain models.

The v1 import schema maps directly to `WorkoutDraft`, `CircuitDraft`, and `ExerciseDraft`. Required structural fields are workout `circuits` and circuit `exercises`; exercise fields otherwise fall back to the same defaults as `ExerciseDraft` where possible.

## Persistence Notes

- Template definitions and performed sessions are stored in Room.
- Session start creates snapshot rows in the session tables.
- Prefill for active sessions comes from the latest completed session for the same `exerciseTemplateId` and `setIndex`.
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
- add export or sync planning once the local MVP is stable
