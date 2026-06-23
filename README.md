# Workout Tracker

Workout Tracker is a native Android app for defining workout templates and logging completed workout sessions.

The app is built around two modes:

- Template mode: create workouts, circuits, and exercises.
- Session mode: perform a workout and enter actual set results.

## MVP Features

- Create, edit, and delete workout templates
- Organize workouts into circuits and exercises
- Manage an exercise library shared across workout templates
- Select shared exercises inside workouts, with workout-specific guidance and prescription values
- Validate that all exercises inside a circuit use the same set count
- Start a workout session from a saved template
- Perform circuits in strict interleaved rounds
- Save reps, load, notes, and skipped status for each set
- Resume an in-progress workout
- Save completed workout history
- Prefill set inputs and notes from previous sets and completed sessions for the same shared exercise
- Review recent set history for an exercise while working out, including uses in other workouts
- Import workout templates from a local JSON or YAML file, or a direct JSON/YAML URL
- Export all templates and session history to a local JSON backup file
- Restore a full JSON backup exported from the app
- Save and restore rolling Google Drive backups in the app-specific Drive data folder

## Importing Workouts

Use the Import action on the workout list screen to import workout data from either:

- a local JSON or YAML file selected through Android's document picker
- a direct-download URL that returns JSON or YAML

Template imports are append-only. They do not import session history or active workouts, and they never overwrite existing templates. If an imported workout has the same name as an existing workout, the app creates another copy with the imported name.

Imported exercises are matched to the exercise library by normalized name: leading/trailing whitespace is ignored, repeated whitespace is collapsed, and matching is case-insensitive. Matching exercises reuse the existing library entry, including archived entries. New exercise names create library entries, and imported guidance is saved on the workout exercise while also seeding default guidance for newly created library entries.

The supported payload shape is:

```json
{
  "workouts": [
    {
      "name": "Push Day",
      "circuits": [
        {
          "name": "Cycle 1",
          "exercises": [
            {
              "name": "Dumbbell Press",
              "guidance": "Use controlled reps.",
              "repMin": 6,
              "repMax": 8,
              "loadKind": "WEIGHT",
              "loadMin": 20.0,
              "loadMax": 40.0,
              "loadUnit": "LB",
              "restTimeSeconds": 60,
              "setCount": 3
            }
          ]
        }
      ]
    }
  ]
}
```

YAML uses the same field names:

```yaml
workouts:
  - name: Push Day
    circuits:
      - name: Cycle 1
        exercises:
          - name: Dumbbell Press
            guidance: Use controlled reps.
            repMin: 6
            repMax: 8
            loadKind: WEIGHT
            loadMin: 20.0
            loadMax: 40.0
            loadUnit: LB
            restTimeSeconds: 60
            setCount: 3
```

A single workout object with `name` and `circuits` is also accepted as a convenience in either JSON or YAML. Import validation uses the same rules as the workout editor.

Full JSON backups exported by the app can also be imported from the same Import action. A full backup import is a restore: it replaces current workout data, including workout templates, exercise definitions, active sessions, completed sessions, snapshots, and set entries. Full backup import supports the current JSON export schema and legacy schema version 1 backups; legacy imports derive missing exercise definitions from template and session snapshots.

## Exporting Data

Use the Export action on the workout list screen to save a JSON backup through Android's document picker.

Exports include:

- exercise library definitions
- all workout templates
- active, abandoned, and completed workout sessions
- session snapshots and set entries

The current export schema is JSON-only, uses top-level `schemaVersion: 2`, and includes canonical exercise ids so shared exercise history can be reconstructed. The importer also accepts legacy `schemaVersion: 1` backups that were exported before canonical exercise ids were added. Early `schemaVersion: 2` backups with missing historical session exercise ids are repaired during import by matching session snapshots to canonical exercise names.

## Google Drive Backup

Use the Drive backup action on the workout list screen to grant app-specific Google Drive permission. When enabled, the app saves a JSON backup to the hidden Drive `appDataFolder` each time a workout is completed. Debug, e2e, and release builds can share that same hidden Drive app folder when their OAuth clients belong to the same Google Cloud project, so backup files include the build variant in the filename. Each variant keeps its newest five Drive snapshots and deletes only older snapshots for that same variant after a successful upload.

Legacy Drive backups named without an explicit variant are treated as release backups.

The same Drive backup action can restore one of the available snapshots. Drive restore uses the full-backup restore path, so it replaces the current workout data with the selected snapshot.

## Data Model

- A workout has one or more circuits
- A circuit has one or more workout exercise prescriptions
- A workout exercise points at one shared exercise definition
- A workout exercise has one or more sets

Exercise definitions include:

- name
- default guidance text
- archive status

Workout exercise prescriptions include:

- workout-specific guidance override
- rep range
- load range
- load kind: `WEIGHT` or `DURATION`
- load unit: `LB`, `KG`, or `SEC`
- number of sets

Performed sets include:

- actual reps
- actual load
- notes
- skipped flag

## Tech Stack

- Kotlin
- Jetpack Compose
- Kotlin serialization
- SnakeYAML Engine
- Room
- Navigation Compose
- ViewModel
- StateFlow

## Project Structure

The app uses a single `app` module and is split into three main layers:

- `data`: Room database, DAOs, entity mappings, repository implementations
- `domain`: models, repository interfaces, use cases, validation
- `ui`: Compose screens, navigation, viewmodels, UI state

## Getting Started

1. Open the project in Android Studio.
2. Make sure the Android SDK is installed.
3. Run `./gradlew testDebugUnitTest` or let Android Studio sync the project.
4. Run the `app` configuration on an emulator or Android device.

For device-backed instrumentation runs, use `./gradlew app:connectedDebugAndroidTest` with a connected emulator or device. The `e2e` build variant can still be assembled separately with `./gradlew assembleE2e`, but this Gradle project does not currently expose an e2e-specific connected Android test task.

## Current Status

This repo contains the MVP scaffold and core flows. The checked-in Gradle wrapper works, the unit-test build passes, and the debug APK assembles successfully.

Instrumentation tests are included, but they require a connected emulator or Android device. The project now includes separate app ids for `debug` and `e2e` builds so device tests can run without colliding with a normal installed app.

See [DEVELOPING.md](DEVELOPING.md) for setup details and implementation notes.
