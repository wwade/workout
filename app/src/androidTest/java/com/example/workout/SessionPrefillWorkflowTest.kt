package dev.wwade.workout

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.repository.RoomSessionRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.SetEntryDraft
import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.usecase.SessionProgressCalculator
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
        val previousWorkoutSets = sessionRepository.getPreviousWorkoutSetEntries(
            exerciseDefinitionId = firstCircuitExercises[0].exerciseDefinitionId!!,
        )
        assertThat(previousWorkoutSets.map { it.setIndex to it.repsActual }).containsExactly(
            0 to 8,
            1 to 8,
        ).inOrder()

        val secondWorkoutId = workoutRepository.saveWorkout(
            WorkoutDraft(
                name = "Regression workout alternate",
                circuits = listOf(
                    CircuitDraft(
                        name = "Circuit 1",
                        exercises = listOf(
                            ExerciseDraft(name = " Exercise 1 ", setCount = 2),
                            ExerciseDraft(name = "exercise 2", setCount = 2),
                        ),
                    ),
                ),
            ),
        )
        val secondSessionId = sessionRepository.startWorkout(secondWorkoutId)
        val secondWorkoutSnapshot = snapshotFor(secondSessionId)
        assertThat(secondWorkoutSnapshot.currentSetIndex).isEqualTo(0)
        assertThat(secondWorkoutSnapshot.exercises[0].suggestedReps).isEqualTo(8)
        assertThat(secondWorkoutSnapshot.exercises[0].suggestedLoad).isEqualTo(30.0)
        assertThat(secondWorkoutSnapshot.exercises[1].suggestedReps).isEqualTo(10)
        assertThat(secondWorkoutSnapshot.exercises[1].suggestedLoad).isEqualTo(40.0)
    }

    @Test
    fun day2CircuitAPrefillsFromLegacyCompletedHistoryWithMissingDefinitionIds() = runBlocking {
        val workoutId = workoutRepository.saveWorkout(day2Workout())
        val sessionId = sessionRepository.startWorkout(workoutId)
        val newSessionDetail = sessionRepository.getSessionDetail(sessionId)!!
        val newCircuitAExercises = newSessionDetail.circuits
            .single { it.name == "Circuit A" }
            .exercises
            .sortedBy { it.sortOrder }

        assertThat(newCircuitAExercises.map { it.exerciseDefinitionId }).doesNotContain(null)

        val legacySessionId = seedLegacyCompletedDay2CircuitAHistory()
        val legacyCircuitAExercises = sessionRepository.getSessionDetail(legacySessionId)!!
            .circuits
            .single { it.name == "Circuit A" }
            .exercises
            .sortedBy { it.sortOrder }

        assertThat(legacyCircuitAExercises.map { it.exerciseDefinitionId }).containsExactly(null, null, null)
        assertThat(legacyCircuitAExercises.map { exercise -> exercise.sets.map { it.repsActual } }).containsExactly(
            listOf(11, 12),
            listOf(10, 10),
            listOf(8, 8),
        ).inOrder()

        val warmupExercises = newSessionDetail.circuits
            .single { it.name == "Warm up" }
            .exercises
            .sortedBy { it.sortOrder }
        sessionRepository.saveRoundEntries(
            sessionId = sessionId,
            entries = warmupExercises.map { exercise ->
                SetEntryDraft(
                    exerciseSessionId = exercise.exerciseSessionId,
                    setIndex = 0,
                    repsActual = 6,
                    loadActual = 0.0,
                    notes = "",
                    skipped = false,
                )
            },
        )
        sessionRepository.saveRoundEntries(
            sessionId = sessionId,
            entries = warmupExercises.map { exercise ->
                SetEntryDraft(
                    exerciseSessionId = exercise.exerciseSessionId,
                    setIndex = 1,
                    repsActual = 6,
                    loadActual = 0.0,
                    notes = "",
                    skipped = false,
                )
            },
        )

        val circuitASnapshot = snapshotFor(sessionId)

        assertThat(circuitASnapshot.currentCircuitName).isEqualTo("Circuit A")
        assertThat(circuitASnapshot.currentSetIndex).isEqualTo(0)
        assertThat(circuitASnapshot.exercises.map { it.exerciseName }).containsExactly(
            "Hinge - Glute Bridge Marching",
            "Shoulder - Prone W to Y",
            "Shoulder - Scapular CARs in 1/2 Plank (+Full Plank Demo)",
        ).inOrder()
        assertThat(circuitASnapshot.exercises.map { it.suggestedReps }).containsExactly(11, 10, 8).inOrder()
        assertThat(circuitASnapshot.exercises.map { it.suggestedLoad }).containsExactly(0.0, 0.0, 0.0).inOrder()
    }

    private suspend fun snapshotFor(sessionId: Long) =
        SessionProgressCalculator.buildSnapshot(
            detail = sessionRepository.getSessionDetail(sessionId)!!,
            prefillProvider = { exerciseDefinitionId, setIndex ->
                exerciseDefinitionId?.let { sessionRepository.getLatestCompletedSetEntry(it, setIndex) }
            },
        )

    private fun day2Workout(): WorkoutDraft {
        return WorkoutDraft(
            name = "Day 2",
            circuits = listOf(
                CircuitDraft(
                    name = "Warm up",
                    exercises = listOf(
                        ExerciseDraft(
                            name = "Bow and Arrow",
                            repMin = 6,
                            repMax = 6,
                            loadMin = 0.0,
                            loadMax = 0.0,
                            setCount = 2,
                        ),
                    ),
                ),
                CircuitDraft(
                    name = "Circuit A",
                    exercises = listOf(
                        ExerciseDraft(
                            name = "Hinge - Glute Bridge Marching",
                            repMin = 10,
                            repMax = 15,
                            loadMin = 0.0,
                            loadMax = 0.0,
                            restTimeSeconds = 15,
                            setCount = 2,
                        ),
                        ExerciseDraft(
                            name = "Shoulder - Prone W to Y",
                            repMin = 6,
                            repMax = 10,
                            loadMin = 0.0,
                            loadMax = 2.0,
                            restTimeSeconds = 30,
                            setCount = 2,
                        ),
                        ExerciseDraft(
                            name = "Shoulder - Scapular CARs in 1/2 Plank (+Full Plank Demo)",
                            repMin = 5,
                            repMax = 8,
                            loadMin = 0.0,
                            loadMax = 0.0,
                            restTimeSeconds = 60,
                            setCount = 2,
                        ),
                    ),
                ),
            ),
        )
    }

    private fun seedLegacyCompletedDay2CircuitAHistory(): Long {
        val db = database.openHelper.writableDatabase
        val sessionId = db.insertReturningId(
            """
            INSERT INTO workout_sessions(workoutTemplateId, workoutNameSnapshot, startedAt, completedAt, status)
            VALUES (4, 'Day 2', 1781723504655, 1781728295760, 'COMPLETED')
            """.trimIndent(),
        )
        val circuitSessionId = db.insertReturningId(
            """
            INSERT INTO circuit_sessions(workoutSessionId, circuitTemplateId, circuitNameSnapshot, sortOrder, setCount)
            VALUES ($sessionId, 12, 'Circuit A', 1, 2)
            """.trimIndent(),
        )

        seedLegacyExercise(
            db = db,
            circuitSessionId = circuitSessionId,
            exerciseTemplateId = 24,
            name = "Hinge - Glute Bridge Marching",
            guidance = "Focus: Glutes, hamstrings, and core. Perform reps per side.",
            repMin = 10,
            repMax = 15,
            loadMin = 0.0,
            loadMax = 0.0,
            restTimeSeconds = 15,
            sortOrder = 0,
            setEntries = listOf(11 to 0.0, 12 to 0.0),
        )
        seedLegacyExercise(
            db = db,
            circuitSessionId = circuitSessionId,
            exerciseTemplateId = 25,
            name = "Shoulder - Prone W to Y",
            guidance = "Focus: Shoulder and midback.",
            repMin = 6,
            repMax = 10,
            loadMin = 0.0,
            loadMax = 2.0,
            restTimeSeconds = 30,
            sortOrder = 1,
            setEntries = listOf(10 to 0.0, 10 to 0.0),
        )
        seedLegacyExercise(
            db = db,
            circuitSessionId = circuitSessionId,
            exerciseTemplateId = 26,
            name = "Shoulder - Scapular CARs in 1/2 Plank (+Full Plank Demo)",
            guidance = "Focus: Shoulders, serratus anterior, rhomboids, lats, and traps.",
            repMin = 5,
            repMax = 8,
            loadMin = 0.0,
            loadMax = 0.0,
            restTimeSeconds = 60,
            sortOrder = 2,
            setEntries = listOf(8 to 0.0, 8 to 0.0),
        )
        return sessionId
    }

    private fun seedLegacyExercise(
        db: SupportSQLiteDatabase,
        circuitSessionId: Long,
        exerciseTemplateId: Long,
        name: String,
        guidance: String,
        repMin: Int,
        repMax: Int,
        loadMin: Double,
        loadMax: Double,
        restTimeSeconds: Int,
        sortOrder: Int,
        setEntries: List<Pair<Int, Double>>,
    ) {
        val exerciseSessionId = db.insertReturningId(
            """
            INSERT INTO exercise_sessions(
                circuitSessionId, exerciseTemplateId, exerciseDefinitionId, exerciseNameSnapshot,
                guidanceSnapshot, repMinSnapshot, repMaxSnapshot, loadKindSnapshot, loadMinSnapshot,
                loadMaxSnapshot, loadUnitSnapshot, restTimeSecondsSnapshot, sortOrder
            )
            VALUES (?, ?, NULL, ?, ?, ?, ?, 'WEIGHT', ?, ?, 'LB', ?, ?)
            """.trimIndent(),
            arrayOf<Any>(
                circuitSessionId,
                exerciseTemplateId,
                name,
                guidance,
                repMin,
                repMax,
                loadMin,
                loadMax,
                restTimeSeconds,
                sortOrder,
            ),
        )
        setEntries.forEachIndexed { setIndex, (reps, load) ->
            db.execSQL(
                """
                INSERT INTO set_entries(exerciseSessionId, setIndex, repsActual, loadActual, notes, skipped)
                VALUES (?, ?, ?, ?, '', 0)
                """.trimIndent(),
                arrayOf<Any>(exerciseSessionId, setIndex, reps, load),
            )
        }
    }

    private fun SupportSQLiteDatabase.insertReturningId(
        sql: String,
        bindArgs: Array<Any> = emptyArray(),
    ): Long {
        if (bindArgs.isEmpty()) {
            execSQL(sql)
        } else {
            execSQL(sql, bindArgs)
        }
        query("SELECT last_insert_rowid()").use { cursor ->
            cursor.moveToFirst()
            return cursor.getLong(0)
        }
    }
}
