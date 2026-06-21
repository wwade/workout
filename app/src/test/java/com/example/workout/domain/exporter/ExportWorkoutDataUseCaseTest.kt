package dev.wwade.workout.domain.exporter

import dev.wwade.workout.domain.model.CircuitSessionDetail
import dev.wwade.workout.domain.model.CircuitTemplate
import dev.wwade.workout.domain.model.ExerciseSessionDetail
import dev.wwade.workout.domain.model.ExerciseTemplate
import dev.wwade.workout.domain.model.LoadKind
import dev.wwade.workout.domain.model.LoadUnit
import dev.wwade.workout.domain.model.SessionStatus
import dev.wwade.workout.domain.model.SetEntry
import dev.wwade.workout.domain.model.WorkoutSessionDetail
import dev.wwade.workout.domain.model.WorkoutTemplate
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
import com.google.common.truth.Truth.assertThat
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ExportWorkoutDataUseCaseTest {
    @Test
    fun exportsTemplatesAndSessionsAsJson() = runTest {
        val useCase = ExportWorkoutDataUseCase(
            exportRepository = object : WorkoutDataExportRepository {
                override suspend fun exportSnapshot(): WorkoutDataExportSnapshot {
                    return WorkoutDataExportSnapshot(
                        workouts = listOf(
                            WorkoutTemplate(
                                id = 1,
                                name = "Push Day",
                                sortOrder = 0,
                                createdAt = 10,
                                updatedAt = 20,
                                circuits = listOf(
                                    CircuitTemplate(
                                        id = 2,
                                        workoutId = 1,
                                        name = "Cycle 1",
                                        sortOrder = 0,
                                        exercises = listOf(
                                            ExerciseTemplate(
                                                id = 3,
                                                circuitId = 2,
                                                name = "Press",
                                                guidance = "Control the eccentric",
                                                repMin = 6,
                                                repMax = 8,
                                                loadKind = LoadKind.WEIGHT,
                                                loadMin = 40.0,
                                                loadMax = 60.0,
                                                loadUnit = LoadUnit.LB,
                                                restTimeSeconds = 90,
                                                setCount = 3,
                                                sortOrder = 0,
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        sessions = listOf(
                            WorkoutSessionDetail(
                                sessionId = 11,
                                workoutTemplateId = 1,
                                workoutName = "Push Day",
                                startedAt = 100,
                                completedAt = null,
                                status = SessionStatus.IN_PROGRESS,
                                circuits = listOf(
                                    CircuitSessionDetail(
                                        circuitSessionId = 12,
                                        circuitTemplateId = 2,
                                        name = "Cycle 1",
                                        sortOrder = 0,
                                        setCount = 3,
                                        exercises = listOf(
                                            ExerciseSessionDetail(
                                                exerciseSessionId = 13,
                                                exerciseTemplateId = 3,
                                                name = "Press",
                                                guidance = "Control the eccentric",
                                                repMin = 6,
                                                repMax = 8,
                                                loadKind = LoadKind.WEIGHT,
                                                loadMin = 40.0,
                                                loadMax = 60.0,
                                                loadUnit = LoadUnit.LB,
                                                restTimeSeconds = 90,
                                                sortOrder = 0,
                                                sets = listOf(
                                                    SetEntry(
                                                        id = 14,
                                                        exerciseSessionId = 13,
                                                        setIndex = 0,
                                                        repsActual = 7,
                                                        loadActual = 50.0,
                                                        notes = "Felt good",
                                                        skipped = false,
                                                    ),
                                                ),
                                            ),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    )
                }
            },
            clock = Clock.fixed(Instant.parse("2026-06-21T00:00:00Z"), ZoneOffset.UTC),
        )

        val result = useCase()

        assertThat(result.exportedAt).isEqualTo(1_782_000_000_000)
        assertThat(result.json).contains("\"schemaVersion\": 1")
        assertThat(result.json).contains("\"name\": \"Push Day\"")
        assertThat(result.json).contains("\"status\": \"IN_PROGRESS\"")
        assertThat(result.json).contains("\"notes\": \"Felt good\"")
    }
}
