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
- `.\gradlew.bat testDebugUnitTest --warning-mode all`
- `.\gradlew.bat help --warning-mode all`

To capture logs for inspection:

```powershell
.\gradlew.bat assembleDebug --warning-mode all *> gradle-assemble.log
.\gradlew.bat testDebugUnitTest --warning-mode all *> gradle-test.log
```

## Notes

- If Gradle fails with `JAVA_HOME is not set`, use the setup above before retrying.
- First-time wrapper or dependency downloads may require network access.
- Gradle can emit structured warnings into `build/reports/problems/problems-report.html` even when stdout is quiet.
