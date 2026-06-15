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

From a terminal, once Gradle is available:

```bash
./gradlew test
./gradlew connectedAndroidTest
./gradlew assembleDebug
```

On Windows with the wrapper present:

```powershell
.\gradlew.bat test
.\gradlew.bat connectedAndroidTest
.\gradlew.bat assembleDebug
```

## Architecture

The project is intentionally simple for the MVP:

- `app/src/main/java/com/example/workout/data`
  - Room entities, DAOs, database, repository implementations
- `app/src/main/java/com/example/workout/domain`
  - domain models, repository interfaces, validation, use cases
- `app/src/main/java/com/example/workout/ui`
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
- Session execution is round-based and interleaved inside each circuit.
- Completed sessions are stored as snapshots so history is preserved even if templates change later.

## Persistence Notes

- Template definitions and performed sessions are stored in Room.
- Session start creates snapshot rows in the session tables.
- Prefill for active sessions comes from the latest completed session for the same `exerciseTemplateId` and `setIndex`.
- Only one active session is expected at a time in this MVP.

## Testing

Current test coverage includes:

- workout validation unit tests
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
- add import/export or sync planning once the local MVP is stable
