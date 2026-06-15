package com.example.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.workout.data.db.AppDatabase
import com.example.workout.data.repository.RoomSessionRepository
import com.example.workout.data.repository.RoomWorkoutRepository
import com.example.workout.domain.model.CircuitDraft
import com.example.workout.domain.model.ExerciseDraft
import com.example.workout.domain.model.SetEntryDraft
import com.example.workout.domain.model.WorkoutDraft
import com.example.workout.domain.usecase.SessionProgressCalculator
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionPrefillWorkflowTest {
    private lateinit var database: AppDatabase
    private lateinit var workoutRepository: RoomWorkoutRepository
    private lateinit var sessionRepository: RoomSessionRepository

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        workoutRepository = RoomWorkoutRepository(database.workoutTemplateDao())
        sessionRepository = RoomSessionRepository(
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutSessionDao = database.workoutSessionDao(),
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun prefillsSecondRoundAndSecondWorkoutFromRecordedValues() = runBlocking {
        val workoutId = workoutRepository.saveWorkout(
            WorkoutDraft(
                name = "Regression workout",
                circuits = listOf(
                    CircuitDraft(
                        name = "Circuit 1",
                        exercises = listOf(
                            ExerciseDraft(name = "Exercise 1", setCount = 2),
                            ExerciseDraft(name = "Exercise 2", setCount = 2),
                        ),
                    ),
                ),
            ),
        )

        val firstSessionId = sessionRepository.startWorkout(workoutId)
        val firstSessionDetail = sessionRepository.getSessionDetail(firstSessionId)!!
        val firstCircuitExercises = firstSessionDetail.circuits.single().exercises.sortedBy { it.sortOrder }

        sessionRepository.saveRoundEntries(
            sessionId = firstSessionId,
            entries = listOf(
                SetEntryDraft(
                    exerciseSessionId = firstCircuitExercises[0].exerciseSessionId,
                    setIndex = 0,
                    repsActual = 8,
                    loadActual = 30.0,
                    notes = "",
                    skipped = false,
                ),
                SetEntryDraft(
                    exerciseSessionId = firstCircuitExercises[1].exerciseSessionId,
                    setIndex = 0,
                    repsActual = 10,
                    loadActual = 40.0,
                    notes = "",
                    skipped = false,
                ),
            ),
        )

        val secondRoundSnapshot = snapshotFor(firstSessionId)
        assertThat(secondRoundSnapshot.currentSetIndex).isEqualTo(1)
        assertThat(secondRoundSnapshot.exercises[0].suggestedReps).isEqualTo(8)
        assertThat(secondRoundSnapshot.exercises[0].suggestedLoad).isEqualTo(30.0)
        assertThat(secondRoundSnapshot.exercises[1].suggestedReps).isEqualTo(10)
        assertThat(secondRoundSnapshot.exercises[1].suggestedLoad).isEqualTo(40.0)

        sessionRepository.saveRoundEntries(
            sessionId = firstSessionId,
            entries = listOf(
                SetEntryDraft(
                    exerciseSessionId = firstCircuitExercises[0].exerciseSessionId,
                    setIndex = 1,
                    repsActual = 8,
                    loadActual = 30.0,
                    notes = "",
                    skipped = false,
                ),
                SetEntryDraft(
                    exerciseSessionId = firstCircuitExercises[1].exerciseSessionId,
                    setIndex = 1,
                    repsActual = 10,
                    loadActual = 40.0,
                    notes = "",
                    skipped = false,
                ),
            ),
        )

        val completedFirstSession = sessionRepository.getSessionDetail(firstSessionId)!!
        assertThat(SessionProgressCalculator.isCompleted(completedFirstSession)).isTrue()

        val secondSessionId = sessionRepository.startWorkout(workoutId)
        val secondWorkoutSnapshot = snapshotFor(secondSessionId)
        assertThat(secondWorkoutSnapshot.currentSetIndex).isEqualTo(0)
        assertThat(secondWorkoutSnapshot.exercises[0].suggestedReps).isEqualTo(8)
        assertThat(secondWorkoutSnapshot.exercises[0].suggestedLoad).isEqualTo(30.0)
        assertThat(secondWorkoutSnapshot.exercises[1].suggestedReps).isEqualTo(10)
        assertThat(secondWorkoutSnapshot.exercises[1].suggestedLoad).isEqualTo(40.0)
    }

    private suspend fun snapshotFor(sessionId: Long) =
        SessionProgressCalculator.buildSnapshot(
            detail = sessionRepository.getSessionDetail(sessionId)!!,
            prefillProvider = { exerciseTemplateId, setIndex ->
                exerciseTemplateId?.let { sessionRepository.getLatestCompletedSetEntry(it, setIndex) }
            },
        )
}
