# AGENTS

This repository is an Android app built with Gradle Kotlin DSL.

## Gradle Setup

When running Gradle from Codex on Windows, use the JDK bundled with Android Studio:

- `JAVA_HOME=C:\Program Files\Android\Android Studio\jbr`
- prepend `%JAVA_HOME%\bin` to `PATH`

Use a project-local Gradle home so wrapper and dependency downloads stay reusable inside the workspace:

- `GRADLE_USER_HOME=<project-root>\.gradle-user`

PowerShell pattern:

```powershell
$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
$env:GRADLE_USER_HOME=(Join-Path $PWD '.gradle-user')
.\gradlew.bat assembleDebug
```

## Known Good Commands

- `.\gradlew.bat assembleDebug --warning-mode all`
- `.\gradlew.bat assembleE2e --warning-mode all`
- `.\gradlew.bat testDebugUnitTest --warning-mode all`
- `.\gradlew.bat app:connectedDebugAndroidTest --warning-mode all`
- `.\gradlew.bat help --warning-mode all`

To capture logs for inspection:

```powershell
.\gradlew.bat assembleDebug --warning-mode all *> gradle-assemble.log
.\gradlew.bat testDebugUnitTest --warning-mode all *> gradle-test.log
.\gradlew.bat app:connectedDebugAndroidTest --warning-mode all *> gradle-connected-test.log
```

## Import Feature Notes

- The app imports workout templates from local JSON/YAML files or direct JSON/YAML URLs.
- Import code lives under `domain/importer`; keep import DTOs separate from Room entities and domain models.
- Imports are append-only: do not overwrite, merge, or skip matching workout names unless the product behavior is intentionally changed.
- Local file import uses Android's document picker and `ContentResolver`.
- URL import requires `android.permission.INTERNET` and currently expects unauthenticated direct JSON or YAML responses.
- Update README and DEVELOPING when changing the import schema or duplicate-handling behavior.

## Notes

- If Gradle fails with `JAVA_HOME is not set`, use the setup above before retrying.
- First-time wrapper or dependency downloads may require network access.
- If the project-local Gradle cache has a Windows file lock, use a second project-local cache such as `.gradle-user-import` and keep it ignored.
- Gradle can emit structured warnings into `build/reports/problems/problems-report.html` even when stdout is quiet.
- Package ids by build type are `dev.wwade.workout` for release, `dev.wwade.workout.debug` for debug, and `dev.wwade.workout.e2e` for the dedicated e2e app install.
- Instrumentation tests are exposed by the debug Android test task (`app:connectedDebugAndroidTest`). This Gradle project does not currently define an e2e-specific connected Android test task.
