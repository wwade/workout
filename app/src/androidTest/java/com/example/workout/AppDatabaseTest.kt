package dev.wwade.workout

import android.content.Context
import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.db.MIGRATION_2_3
import dev.wwade.workout.data.db.MIGRATION_3_4
import dev.wwade.workout.data.db.SetEntryEntity
import dev.wwade.workout.data.repository.RoomWorkoutDataImportRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutImportRequest
import dev.wwade.workout.domain.importer.WorkoutImportSource
import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.SessionStatus
import dev.wwade.workout.domain.model.WorkoutDraft
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
    @get:Rule
    val migrationHelper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AppDatabase::class.java,
    )

    private lateinit var database: AppDatabase

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun persistsWorkoutGraphAndLoadsRelations() = runBlocking {
        val workoutId = database.workoutTemplateDao().upsertWorkoutGraph(
            WorkoutDraft(
                name = "Part one",
                circuits = listOf(
                    CircuitDraft(
                        name = "Warmup",
                        exercises = listOf(
                            ExerciseDraft(name = "First", restTimeSeconds = 45),
                            ExerciseDraft(name = "Second"),
                        ),
                    ),
                ),
            ),
        )

        val workout = database.workoutTemplateDao().getWorkoutTemplateGraph(workoutId)

        assertThat(workout?.circuits).hasSize(1)
        assertThat(workout?.circuits?.single()?.exercises).hasSize(2)
        val firstExercise = workout?.circuits?.single()?.exercises?.first()
        assertThat(firstExercise?.exercise?.restTimeSeconds).isEqualTo(45)
        assertThat(firstExercise?.definition?.name).isEqualTo("First")
    }

    @Test
    fun startsSessionFromTemplateSnapshot() = runBlocking {
        val workoutId = database.workoutTemplateDao().upsertWorkoutGraph(
            WorkoutDraft(
                name = "Part one",
                circuits = listOf(
                    CircuitDraft(
                        name = "Warmup",
                        exercises = listOf(
                            ExerciseDraft(name = "First", restTimeSeconds = 75, setCount = 2),
                            ExerciseDraft(name = "Second", setCount = 2),
                        ),
                    ),
                ),
            ),
        )
        val template = database.workoutTemplateDao().getWorkoutTemplateGraph(workoutId)!!

        val sessionId = database.workoutSessionDao().startWorkoutSession(template)
        val session = database.workoutSessionDao().getSessionDetail(sessionId)

        assertThat(session?.circuits).hasSize(1)
        assertThat(session?.circuits?.single()?.circuit?.setCount).isEqualTo(2)
        assertThat(session?.circuits?.single()?.exercises).hasSize(2)
        val firstSessionExercise = session?.circuits?.single()?.exercises?.first()?.exercise
        assertThat(firstSessionExercise?.restTimeSecondsSnapshot).isEqualTo(75)
        assertThat(firstSessionExercise?.exerciseDefinitionId).isNotNull()
    }

    @Test
    fun exportQueriesReturnTemplatesAndSessions() = runBlocking {
        val firstWorkoutId = database.workoutTemplateDao().upsertWorkoutGraph(
            WorkoutDraft(
                name = "Push",
                circuits = listOf(
                    CircuitDraft(
                        name = "A",
                        exercises = listOf(
                            ExerciseDraft(name = "Press", setCount = 2),
                        ),
                    ),
                ),
            ),
        )
        val secondWorkoutId = database.workoutTemplateDao().upsertWorkoutGraph(
            WorkoutDraft(
                name = "Pull",
                circuits = listOf(
                    CircuitDraft(
                        name = "B",
                        exercises = listOf(
                            ExerciseDraft(name = "Row", setCount = 3),
                        ),
                    ),
                ),
            ),
        )

        val firstTemplate = database.workoutTemplateDao().getWorkoutTemplateGraph(firstWorkoutId)!!
        val secondTemplate = database.workoutTemplateDao().getWorkoutTemplateGraph(secondWorkoutId)!!

        val activeSessionId = database.workoutSessionDao().startWorkoutSession(firstTemplate)
        val completedSessionId = database.workoutSessionDao().startWorkoutSession(secondTemplate)
        database.workoutSessionDao().updateSessionStatus(
            sessionId = completedSessionId,
            status = SessionStatus.COMPLETED,
            completedAt = 999L,
        )

        val workouts = database.workoutTemplateDao().getAllWorkoutTemplateGraphs()
        val sessions = database.workoutSessionDao().getAllSessionDetails()

        assertThat(workouts.map { it.workout.name }).containsExactly("Push", "Pull").inOrder()
        assertThat(sessions.map { it.session.id }).containsExactly(activeSessionId, completedSessionId).inOrder()
        assertThat(sessions.map { it.session.status }).containsExactly(
            SessionStatus.IN_PROGRESS,
            SessionStatus.COMPLETED,
        ).inOrder()
    }

    @Test
    fun fullBackupImportReplacesExistingDataAndRestoresRelationships() = runBlocking {
        database.workoutTemplateDao().upsertWorkoutGraph(
            WorkoutDraft(
                name = "Existing",
                circuits = listOf(
                    CircuitDraft(
                        name = "Old",
                        exercises = listOf(ExerciseDraft(name = "Old Press")),
                    ),
                ),
            ),
        )
        val useCase = ImportWorkoutsUseCase(
            workoutRepository = RoomWorkoutRepository(database.workoutTemplateDao()),
            importRepository = RoomWorkoutDataImportRepository(database),
        )

        val result = useCase(
            WorkoutImportRequest(
                WorkoutImportSource.RawJson(validWorkoutDataBackupJson()),
            ),
        )

        assertThat(result.restoredCounts?.exerciseDefinitionCount).isEqualTo(1)
        assertThat(result.restoredCounts?.workoutCount).isEqualTo(1)
        assertThat(result.restoredCounts?.sessionCount).isEqualTo(1)

        val workouts = database.workoutTemplateDao().getAllWorkoutTemplateGraphs()
        assertThat(workouts.map { it.workout.name }).containsExactly("Push Day")
        assertThat(workouts.single().workout.id).isEqualTo(1L)
        assertThat(workouts.single().circuits.single().circuit.id).isEqualTo(2L)
        assertThat(workouts.single().circuits.single().exercises.single().exercise.id).isEqualTo(3L)
        assertThat(workouts.single().circuits.single().exercises.single().definition.id).isEqualTo(30L)

        val session = database.workoutSessionDao().getSessionDetail(11L)
        assertThat(session?.session?.workoutNameSnapshot).isEqualTo("Push Day")
        assertThat(session?.circuits?.single()?.circuit?.id).isEqualTo(12L)
        val restoredExercise = session?.circuits?.single()?.exercises?.single()
        assertThat(restoredExercise?.exercise?.id).isEqualTo(13L)
        assertThat(restoredExercise?.exercise?.exerciseTemplateId).isEqualTo(3L)
        assertThat(restoredExercise?.exercise?.exerciseDefinitionId).isEqualTo(30L)
        assertThat(restoredExercise?.sets?.single()?.id).isEqualTo(14L)
        assertThat(restoredExercise?.sets?.single()?.notes).isEqualTo("Felt good")
    }

    @Test
    fun deleteCompletedSessionsRemovesSessionGraph() = runBlocking {
        val sessionId = createSession(status = SessionStatus.COMPLETED)
        val exerciseSessionId = database.workoutSessionDao()
            .getSessionDetail(sessionId)!!
            .circuits.single()
            .exercises.single()
            .exercise.id
        database.workoutSessionDao().upsertSetEntries(
            listOf(
                SetEntryEntity(
                    exerciseSessionId = exerciseSessionId,
                    setIndex = 0,
                    repsActual = 8,
                    loadActual = 20.0,
                    notes = "Done",
                    skipped = false,
                ),
            ),
        )

        database.workoutSessionDao().deleteCompletedSessions(setOf(sessionId))

        assertThat(database.workoutSessionDao().getSessionDetail(sessionId)).isNull()
        database.openHelper.readableDatabase.query("SELECT COUNT(*) FROM set_entries").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(0)
        }
    }

    @Test
    fun deleteCompletedSessionsDoesNotDeleteActiveSession() = runBlocking {
        val sessionId = createSession(status = SessionStatus.IN_PROGRESS)

        database.workoutSessionDao().deleteCompletedSessions(setOf(sessionId))

        assertThat(database.workoutSessionDao().getSessionDetail(sessionId)).isNotNull()
    }

    @Test
    fun migration2To3BackfillsExerciseDefinitions() {
        val dbName = "migration-2-3"
        migrationHelper.createDatabase(dbName, 2).apply {
            seedVersion2WorkoutAndSession()
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(dbName, 3, true, MIGRATION_2_3)

        migrated.query("SELECT name, normalizedName FROM exercise_definitions ORDER BY id").use { cursor ->
            assertThat(cursor.count).isEqualTo(1)
            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo("Press")
            assertThat(cursor.getString(1)).isEqualTo("press")
        }
        migrated.query("SELECT COUNT(DISTINCT exerciseDefinitionId) FROM exercise_templates").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.getInt(0)).isEqualTo(1)
        }
        migrated.query("SELECT exerciseDefinitionId, exerciseNameSnapshot FROM exercise_sessions").use { cursor ->
            cursor.moveToFirst()
            assertThat(cursor.isNull(0)).isFalse()
            assertThat(cursor.getString(1)).isEqualTo("Press")
        }
        migrated.close()
    }

    @Test
    fun migration3To4BackfillsMissingSessionDefinitionIdsFromSnapshots() {
        val dbName = "migration-3-4"
        migrationHelper.createDatabase(dbName, 3).apply {
            seedVersion3WorkoutAndLegacySessions()
            close()
        }

        val migrated = migrationHelper.runMigrationsAndValidate(dbName, 4, true, MIGRATION_3_4)

        migrated.query(
            """
            SELECT es.exerciseNameSnapshot, ed.name, ed.normalizedName, se.repsActual
            FROM exercise_sessions es
            INNER JOIN exercise_definitions ed ON ed.id = es.exerciseDefinitionId
            INNER JOIN set_entries se ON se.exerciseSessionId = es.id
            ORDER BY es.sortOrder ASC
            """.trimIndent(),
        ).use { cursor ->
            assertThat(cursor.count).isEqualTo(2)

            cursor.moveToFirst()
            assertThat(cursor.getString(0)).isEqualTo(" HINGE - Glute Bridge Marching ")
            assertThat(cursor.getString(1)).isEqualTo("Hinge - Glute Bridge Marching")
            assertThat(cursor.getString(2)).isEqualTo("hinge - glute bridge marching")
            assertThat(cursor.getInt(3)).isEqualTo(11)

            cursor.moveToNext()
            assertThat(cursor.getString(0)).isEqualTo("Legacy Curl")
            assertThat(cursor.getString(1)).isEqualTo("Legacy Curl")
            assertThat(cursor.getString(2)).isEqualTo("legacy curl")
            assertThat(cursor.getInt(3)).isEqualTo(12)
        }
        migrated.close()
    }

    private fun SupportSQLiteDatabase.seedVersion2WorkoutAndSession() {
        execSQL("INSERT INTO workout_templates(id, name, sortOrder, createdAt, updatedAt) VALUES (1, 'A', 0, 1, 1)")
        execSQL("INSERT INTO workout_templates(id, name, sortOrder, createdAt, updatedAt) VALUES (2, 'B', 1, 1, 1)")
        execSQL("INSERT INTO circuit_templates(id, workoutId, name, sortOrder) VALUES (10, 1, 'Main', 0)")
        execSQL("INSERT INTO circuit_templates(id, workoutId, name, sortOrder) VALUES (20, 2, 'Main', 0)")
        execSQL(
            """
            INSERT INTO exercise_templates(
                id, circuitId, name, guidance, repMin, repMax, loadKind, loadMin, loadMax,
                loadUnit, restTimeSeconds, setCount, sortOrder
            ) VALUES (100, 10, 'Press', 'First guidance', 6, 8, 'WEIGHT', 10.0, 20.0, 'LB', 60, 2, 0)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO exercise_templates(
                id, circuitId, name, guidance, repMin, repMax, loadKind, loadMin, loadMax,
                loadUnit, restTimeSeconds, setCount, sortOrder
            ) VALUES (200, 20, ' press ', 'Second guidance', 6, 8, 'WEIGHT', 10.0, 20.0, 'LB', 60, 2, 0)
            """.trimIndent(),
        )
        execSQL(
            "INSERT INTO workout_sessions(id, workoutTemplateId, workoutNameSnapshot, startedAt, completedAt, status) VALUES (1000, 1, 'A', 1, 2, 'COMPLETED')",
        )
        execSQL(
            "INSERT INTO circuit_sessions(id, workoutSessionId, circuitTemplateId, circuitNameSnapshot, sortOrder, setCount) VALUES (1001, 1000, 10, 'Main', 0, 2)",
        )
        execSQL(
            """
            INSERT INTO exercise_sessions(
                id, circuitSessionId, exerciseTemplateId, exerciseNameSnapshot, guidanceSnapshot,
                repMinSnapshot, repMaxSnapshot, loadKindSnapshot, loadMinSnapshot, loadMaxSnapshot,
                loadUnitSnapshot, restTimeSecondsSnapshot, sortOrder
            ) VALUES (1002, 1001, 100, 'Press', 'First guidance', 6, 8, 'WEIGHT', 10.0, 20.0, 'LB', 60, 0)
            """.trimIndent(),
        )
        execSQL(
            "INSERT INTO set_entries(id, exerciseSessionId, setIndex, repsActual, loadActual, notes, skipped) VALUES (1003, 1002, 0, 8, 15.0, 'ok', 0)",
        )
    }

    private fun SupportSQLiteDatabase.seedVersion3WorkoutAndLegacySessions() {
        execSQL(
            """
            INSERT INTO exercise_definitions(id, name, normalizedName, defaultGuidance, archived, createdAt, updatedAt)
            VALUES (1, 'Hinge - Glute Bridge Marching', 'hinge - glute bridge marching', 'Existing guidance', 0, 1, 1)
            """.trimIndent(),
        )
        execSQL("INSERT INTO workout_templates(id, name, sortOrder, createdAt, updatedAt) VALUES (1, 'Day 2', 0, 1, 1)")
        execSQL("INSERT INTO circuit_templates(id, workoutId, name, sortOrder) VALUES (10, 1, 'Circuit A', 0)")
        execSQL(
            """
            INSERT INTO exercise_templates(
                id, circuitId, exerciseDefinitionId, guidance, repMin, repMax, loadKind, loadMin, loadMax,
                loadUnit, restTimeSeconds, setCount, sortOrder
            ) VALUES (100, 10, 1, 'Existing guidance', 10, 15, 'WEIGHT', 0.0, 0.0, 'LB', 15, 2, 0)
            """.trimIndent(),
        )
        execSQL(
            "INSERT INTO workout_sessions(id, workoutTemplateId, workoutNameSnapshot, startedAt, completedAt, status) VALUES (1000, 1, 'Day 2', 1, 2, 'COMPLETED')",
        )
        execSQL(
            "INSERT INTO circuit_sessions(id, workoutSessionId, circuitTemplateId, circuitNameSnapshot, sortOrder, setCount) VALUES (1001, 1000, 10, 'Circuit A', 0, 2)",
        )
        execSQL(
            """
            INSERT INTO exercise_sessions(
                id, circuitSessionId, exerciseTemplateId, exerciseDefinitionId, exerciseNameSnapshot, guidanceSnapshot,
                repMinSnapshot, repMaxSnapshot, loadKindSnapshot, loadMinSnapshot, loadMaxSnapshot,
                loadUnitSnapshot, restTimeSecondsSnapshot, sortOrder
            ) VALUES (1002, 1001, 999, NULL, ' HINGE - Glute Bridge Marching ', 'Snapshot guidance', 10, 15, 'WEIGHT', 0.0, 0.0, 'LB', 15, 0)
            """.trimIndent(),
        )
        execSQL(
            """
            INSERT INTO exercise_sessions(
                id, circuitSessionId, exerciseTemplateId, exerciseDefinitionId, exerciseNameSnapshot, guidanceSnapshot,
                repMinSnapshot, repMaxSnapshot, loadKindSnapshot, loadMinSnapshot, loadMaxSnapshot,
                loadUnitSnapshot, restTimeSecondsSnapshot, sortOrder
            ) VALUES (1003, 1001, NULL, NULL, 'Legacy Curl', 'Curl guidance', 8, 12, 'WEIGHT', 5.0, 10.0, 'LB', 30, 1)
            """.trimIndent(),
        )
        execSQL(
            "INSERT INTO set_entries(id, exerciseSessionId, setIndex, repsActual, loadActual, notes, skipped) VALUES (1004, 1002, 0, 11, 0.0, 'ok', 0)",
        )
        execSQL(
            "INSERT INTO set_entries(id, exerciseSessionId, setIndex, repsActual, loadActual, notes, skipped) VALUES (1005, 1003, 0, 12, 5.0, 'ok', 0)",
        )
    }

    private suspend fun createSession(status: SessionStatus): Long {
        val workoutId = database.workoutTemplateDao().upsertWorkoutGraph(
            WorkoutDraft(
                name = "Delete test",
                circuits = listOf(
                    CircuitDraft(
                        name = "Main",
                        exercises = listOf(ExerciseDraft(name = "Press", setCount = 1)),
                    ),
                ),
            ),
        )
        val template = database.workoutTemplateDao().getWorkoutTemplateGraph(workoutId)!!
        val sessionId = database.workoutSessionDao().startWorkoutSession(template)
        if (status != SessionStatus.IN_PROGRESS) {
            database.workoutSessionDao().updateSessionStatus(
                sessionId = sessionId,
                status = status,
                completedAt = 999L,
            )
        }
        return sessionId
    }
}

private fun validWorkoutDataBackupJson(): String {
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
