package dev.wwade.workout.domain.importer

import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class WorkoutImportParserTest {
    private val parser = WorkoutImportParser()

    @Test
    fun parsesMultiWorkoutPayload() {
        val workouts = parser.parse(
            """
            {
              "workouts": [
                {
                  "name": "Push",
                  "circuits": [
                    {
                      "name": "Cycle 1",
                      "exercises": [
                        {
                          "name": "Press",
                          "guidance": "Slow eccentric",
                          "repMin": 6,
                          "repMax": 8,
                          "loadKind": "WEIGHT",
                          "loadMin": 20.0,
                          "loadMax": 40.0,
                          "loadUnit": "LB",
                          "restTimeSeconds": 90,
                          "setCount": 3
                        }
                      ]
                    }
                  ]
                },
                {
                  "name": "Core",
                  "circuits": [
                    {
                      "name": "Hold",
                      "exercises": [
                        {
                          "name": "Plank",
                          "loadKind": "DURATION",
                          "loadMin": 30.0,
                          "loadMax": 45.0,
                          "loadUnit": "SEC"
                        }
                      ]
                    }
                  ]
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(workouts.map { it.name }).containsExactly("Push", "Core").inOrder()
        assertThat(workouts.first().circuits.single().exercises.single().name).isEqualTo("Press")
        assertThat(workouts.last().circuits.single().exercises.single().loadKind).isEqualTo(LoadKind.DURATION)
    }

    @Test
    fun parsesSingleWorkoutPayload() {
        val workouts = parser.parse(validSingleWorkoutJson(name = "Solo"))

        assertThat(workouts).hasSize(1)
        assertThat(workouts.single().name).isEqualTo("Solo")
        assertThat(workouts.single().circuits.single().exercises.single().loadUnit).isEqualTo(LoadUnit.LB)
    }

    @Test
    fun parsesMultiWorkoutYamlPayload() {
        val workouts = parser.parse(
            """
            workouts:
              - name: Push
                circuits:
                  - name: Cycle 1
                    exercises:
                      - name: Press
                        guidance: Slow eccentric
                        repMin: 6
                        repMax: 8
                        loadKind: WEIGHT
                        loadMin: 20.0
                        loadMax: 40.0
                        loadUnit: LB
                        restTimeSeconds: 90
                        setCount: 3
              - name: Core
                circuits:
                  - name: Hold
                    exercises:
                      - name: Plank
                        loadKind: DURATION
                        loadMin: 30.0
                        loadMax: 45.0
                        loadUnit: SEC
            """.trimIndent(),
        )

        assertThat(workouts.map { it.name }).containsExactly("Push", "Core").inOrder()
        assertThat(workouts.first().circuits.single().exercises.single().name).isEqualTo("Press")
        assertThat(workouts.last().circuits.single().exercises.single().loadKind).isEqualTo(LoadKind.DURATION)
    }

    @Test
    fun parsesSingleWorkoutYamlPayload() {
        val workouts = parser.parse(validSingleWorkoutYaml(name = "Yaml Solo"))

        assertThat(workouts).hasSize(1)
        assertThat(workouts.single().name).isEqualTo("Yaml Solo")
        assertThat(workouts.single().circuits.single().exercises.single().loadUnit).isEqualTo(LoadUnit.LB)
    }

    @Test
    fun parsesFullWorkoutDataBackupPayload() {
        val parsedImport = parser.parseImport(validWorkoutDataBackupJson())

        assertThat(parsedImport).isInstanceOf(ParsedWorkoutImport.FullBackup::class.java)
        val snapshot = (parsedImport as ParsedWorkoutImport.FullBackup).snapshot
        assertThat(snapshot.exerciseDefinitions.map { it.id }).containsExactly(30L)
        assertThat(snapshot.workouts.single().id).isEqualTo(1L)
        assertThat(snapshot.workouts.single().circuits.single().exercises.single().exerciseDefinitionId)
            .isEqualTo(30L)
        assertThat(snapshot.sessions.single().sessionId).isEqualTo(11L)
        assertThat(snapshot.sessions.single().circuits.single().exercises.single().sets.single().id)
            .isEqualTo(14L)
    }

    @Test
    fun parsesFullBackupWithHistoricalSessionsForDeletedTemplates() {
        val backupJson = validWorkoutDataBackupJson()
            .replace("\"workoutTemplateId\": 1", "\"workoutTemplateId\": 99")
            .replace("\"circuitTemplateId\": 2", "\"circuitTemplateId\": 98")
            .replace("\"exerciseTemplateId\": 3", "\"exerciseTemplateId\": 97")

        val parsedImport = parser.parseImport(backupJson)

        assertThat(parsedImport).isInstanceOf(ParsedWorkoutImport.FullBackup::class.java)
        val snapshot = (parsedImport as ParsedWorkoutImport.FullBackup).snapshot
        assertThat(snapshot.sessions.single().workoutTemplateId).isEqualTo(99L)
        assertThat(snapshot.sessions.single().circuits.single().circuitTemplateId).isEqualTo(98L)
        assertThat(snapshot.sessions.single().circuits.single().exercises.single().exerciseTemplateId)
            .isEqualTo(97L)
    }

    @Test
    fun rejectsMalformedJson() {
        val error = runCatching { parser.parse("{not json") }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("not valid workout JSON")
    }

    @Test
    fun rejectsUnknownEnumValues() {
        val error = runCatching {
            parser.parse(validSingleWorkoutJson().replace("\"WEIGHT\"", "\"PACE\""))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("does not match")
    }

    @Test
    fun rejectsUnknownYamlEnumValues() {
        val error = runCatching {
            parser.parse(validSingleWorkoutYaml().replace("loadKind: WEIGHT", "loadKind: PACE"))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("unknown value")
    }

    @Test
    fun rejectsMissingNestedLists() {
        val error = runCatching {
            parser.parse("""{"name":"Broken"}""")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("missing circuits")
    }

    @Test
    fun rejectsUnsupportedFullBackupSchemaVersion() {
        val error = runCatching {
            parser.parseImport(validWorkoutDataBackupJson().replace("\"schemaVersion\": 2", "\"schemaVersion\": 99"))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("Unsupported workout data backup schemaVersion 99")
    }

    @Test
    fun rejectsFullBackupMissingRequiredCollections() {
        val error = runCatching {
            parser.parseImport("""{"schemaVersion": 2, "exportedAt": 100, "workouts": [], "sessions": []}""")
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("missing exerciseDefinitions")
    }

    @Test
    fun rejectsFullBackupBrokenReferences() {
        val error = runCatching {
            parser.parseImport(validWorkoutDataBackupJson().replace("\"exerciseDefinitionId\": 30", "\"exerciseDefinitionId\": 999"))
        }.exceptionOrNull()

        assertThat(error).isInstanceOf(WorkoutImportException::class.java)
        assertThat(error).hasMessageThat().contains("references missing exercise definition 999")
    }
}

internal fun validSingleWorkoutJson(name: String = "Imported Workout"): String {
    return """
        {
          "name": "$name",
          "circuits": [
            {
              "name": "Cycle 1",
              "exercises": [
                {
                  "name": "Press",
                  "guidance": "Controlled reps",
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
    """.trimIndent()
}

internal fun validSingleWorkoutYaml(name: String = "Imported Workout"): String {
    return """
        name: $name
        circuits:
          - name: Cycle 1
            exercises:
              - name: Press
                guidance: Controlled reps
                repMin: 6
                repMax: 8
                loadKind: WEIGHT
                loadMin: 20.0
                loadMax: 40.0
                loadUnit: LB
                restTimeSeconds: 60
                setCount: 3
    """.trimIndent()
}

internal fun validWorkoutDataBackupJson(): String {
    return """
        {
          "schemaVersion": 2,
          "exportedAt": 1782000000000,
          "exerciseDefinitions": [
            {
              "id": 30,
              "name": "Press",
              "defaultGuidance": "Control the eccentric",
              "archived": false,
              "createdAt": 10,
              "updatedAt": 20
            }
          ],
          "workouts": [
            {
              "id": 1,
              "name": "Push Day",
              "sortOrder": 0,
              "createdAt": 10,
              "updatedAt": 20,
              "circuits": [
                {
                  "id": 2,
                  "workoutId": 1,
                  "name": "Cycle 1",
                  "sortOrder": 0,
                  "exercises": [
                    {
                      "id": 3,
                      "circuitId": 2,
                      "exerciseDefinitionId": 30,
                      "name": "Press",
                      "guidance": "Control the eccentric",
                      "guidanceOverride": "Control the eccentric",
                      "repMin": 6,
                      "repMax": 8,
                      "loadKind": "WEIGHT",
                      "loadMin": 40.0,
                      "loadMax": 60.0,
                      "loadUnit": "LB",
                      "restTimeSeconds": 90,
                      "setCount": 3,
                      "sortOrder": 0
                    }
                  ]
                }
              ]
            }
          ],
          "sessions": [
            {
              "sessionId": 11,
              "workoutTemplateId": 1,
              "workoutName": "Push Day",
              "startedAt": 100,
              "completedAt": 200,
              "status": "COMPLETED",
              "circuits": [
                {
                  "circuitSessionId": 12,
                  "circuitTemplateId": 2,
                  "name": "Cycle 1",
                  "sortOrder": 0,
                  "setCount": 3,
                  "exercises": [
                    {
                      "exerciseSessionId": 13,
                      "exerciseTemplateId": 3,
                      "exerciseDefinitionId": 30,
                      "name": "Press",
                      "guidance": "Control the eccentric",
                      "repMin": 6,
                      "repMax": 8,
                      "loadKind": "WEIGHT",
                      "loadMin": 40.0,
                      "loadMax": 60.0,
                      "loadUnit": "LB",
                      "restTimeSeconds": 90,
                      "sortOrder": 0,
                      "sets": [
                        {
                          "id": 14,
                          "exerciseSessionId": 13,
                          "setIndex": 0,
                          "repsActual": 7,
                          "loadActual": 50.0,
                          "notes": "Felt good",
                          "skipped": false
                        }
                      ]
                    }
                  ]
                }
              ]
            }
          ]
        }
    """.trimIndent()
}
