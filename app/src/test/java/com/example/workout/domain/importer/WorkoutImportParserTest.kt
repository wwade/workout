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
