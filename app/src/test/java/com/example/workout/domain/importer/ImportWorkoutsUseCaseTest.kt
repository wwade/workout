package dev.wwade.workout.domain.importer

import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.model.WorkoutTemplate
import dev.wwade.workout.domain.repository.WorkoutDataImportRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ImportWorkoutsUseCaseTest {
    @Test
    fun importsValidWorkoutsAndRejectsInvalidWorkoutDrafts() = runTest {
        val repository = FakeWorkoutRepository()
        val useCase = ImportWorkoutsUseCase(workoutRepository = repository)

        val result = useCase(
            WorkoutImportRequest(
                WorkoutImportSource.RawJson(
                    """
                    {
                      "workouts": [
                        ${validSingleWorkoutJson("Valid")},
                        {
                          "name": "No Circuits",
                          "circuits": []
                        },
                        {
                          "name": "Mismatched Sets",
                          "circuits": [
                            {
                              "name": "Cycle 1",
                              "exercises": [
                                {"name":"A","setCount":3},
                                {"name":"B","setCount":4}
                              ]
                            }
                          ]
                        },
                        {
                          "name": "Bad Duration",
                          "circuits": [
                            {
                              "name": "Cycle 1",
                              "exercises": [
                                {"name":"Hold","loadKind":"DURATION","loadUnit":"KG"}
                              ]
                            }
                          ]
                        }
                      ]
                    }
                    """.trimIndent(),
                ),
            ),
        )

        assertThat(result.importedCount).isEqualTo(1)
        assertThat(result.failedCount).isEqualTo(3)
        assertThat(repository.savedWorkouts.map { it.name }).containsExactly("Valid")
        assertThat(result.workoutErrors.joinToString()).contains("A workout needs at least one circuit")
        assertThat(result.workoutErrors.joinToString()).contains("same set count")
        assertThat(result.workoutErrors.joinToString()).contains("Duration exercises must use seconds")
    }

    @Test
    fun duplicateNamesCreateCopies() = runTest {
        val repository = FakeWorkoutRepository(
            initialWorkouts = listOf(validDraft("Repeat")),
        )
        val useCase = ImportWorkoutsUseCase(workoutRepository = repository)

        val result = useCase(
            WorkoutImportRequest(
                WorkoutImportSource.RawJson(validSingleWorkoutJson("Repeat")),
            ),
        )

        assertThat(result.importedCount).isEqualTo(1)
        assertThat(repository.savedWorkouts.map { it.name }).containsExactly("Repeat", "Repeat").inOrder()
    }

    @Test
    fun importsFromUrlSourceUsingFetcher() = runTest {
        val repository = FakeWorkoutRepository()
        val useCase = ImportWorkoutsUseCase(
            workoutRepository = repository,
            jsonFetcher = object : WorkoutImportJsonFetcher {
                override suspend fun fetch(url: String): String {
                    assertThat(url).isEqualTo("https://example.test/workout.json")
                    return validSingleWorkoutJson("Remote")
                }
            },
        )

        val result = useCase(
            WorkoutImportRequest(
                WorkoutImportSource.Url(" https://example.test/workout.json "),
            ),
        )

        assertThat(result.importedCount).isEqualTo(1)
        assertThat(repository.savedWorkouts.single().name).isEqualTo("Remote")
    }

    @Test
    fun restoresFullBackupUsingImportRepository() = runTest {
        val repository = FakeWorkoutRepository()
        val importRepository = FakeWorkoutDataImportRepository()
        val useCase = ImportWorkoutsUseCase(
            workoutRepository = repository,
            importRepository = importRepository,
        )

        val result = useCase(
            WorkoutImportRequest(
                WorkoutImportSource.RawJson(validWorkoutDataBackupJson()),
            ),
        )

        assertThat(repository.savedWorkouts).isEmpty()
        assertThat(importRepository.restoredSnapshot?.workouts?.single()?.name).isEqualTo("Push Day")
        assertThat(result.importedCount).isEqualTo(0)
        assertThat(result.restoredCounts?.exerciseDefinitionCount).isEqualTo(1)
        assertThat(result.restoredCounts?.workoutCount).isEqualTo(1)
        assertThat(result.restoredCounts?.sessionCount).isEqualTo(1)
    }
}

internal class FakeWorkoutRepository(
    initialWorkouts: List<WorkoutDraft> = emptyList(),
) : WorkoutRepository {
    val savedWorkouts = initialWorkouts.toMutableList()
    private val workoutsFlow = MutableStateFlow<List<WorkoutListItem>>(emptyList())

    override fun observeWorkouts(): Flow<List<WorkoutListItem>> = workoutsFlow

    override suspend fun getWorkout(workoutId: Long): WorkoutTemplate? = null

    override suspend fun saveWorkout(workout: WorkoutDraft): Long {
        savedWorkouts += workout
        return savedWorkouts.size.toLong()
    }

    override suspend fun deleteWorkout(workoutId: Long) = Unit
}

private fun validDraft(name: String): WorkoutDraft {
    return WorkoutImportParser().parse(validSingleWorkoutJson(name)).single()
}

private class FakeWorkoutDataImportRepository : WorkoutDataImportRepository {
    var restoredSnapshot: WorkoutDataRestoreSnapshot? = null
        private set

    override suspend fun restoreSnapshot(snapshot: WorkoutDataRestoreSnapshot): WorkoutDataRestoreCounts {
        restoredSnapshot = snapshot
        return WorkoutDataRestoreCounts(
            exerciseDefinitionCount = snapshot.exerciseDefinitions.size,
            workoutCount = snapshot.workouts.size,
            sessionCount = snapshot.sessions.size,
        )
    }
}
