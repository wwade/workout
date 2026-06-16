# Workout Tracker

Workout Tracker is a native Android app for defining workout templates and logging completed workout sessions.

The app is built around two modes:

- Template mode: create workouts, circuits, and exercises.
- Session mode: perform a workout and enter actual set results.

## MVP Features

- Create, edit, and delete workout templates
- Organize workouts into circuits and exercises
- Define exercise guidance, rep ranges, load ranges, units, and set counts
- Validate that all exercises inside a circuit use the same set count
- Start a workout session from a saved template
- Perform circuits in strict interleaved rounds
- Save reps, load, notes, and skipped status for each set
- Resume an in-progress workout
- Save completed workout history
- Prefill set inputs from the latest completed session for the same exercise and set index

## Data Model

- A workout has one or more circuits
- A circuit has one or more exercises
- An exercise has one or more sets

Exercise templates include:

- guidance text
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

For device-backed integration runs, prefer the dedicated `e2e` build so tests do not touch the debug app's data. Use `./gradlew connectedE2eAndroidTest`.

## Current Status

This repo contains the MVP scaffold and core flows. The checked-in Gradle wrapper works, the unit-test build passes, and the debug APK assembles successfully.

Instrumentation tests are included, but they require a connected emulator or Android device. The project now includes separate app ids for `debug` and `e2e` builds so device tests can run without colliding with a normal installed app.

See [DEVELOPING.md](DEVELOPING.md) for setup details and implementation notes.
