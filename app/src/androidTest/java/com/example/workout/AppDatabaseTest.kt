package dev.wwade.workout

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.domain.model.CircuitDraft
import dev.wwade.workout.domain.model.ExerciseDraft
import dev.wwade.workout.domain.model.WorkoutDraft
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppDatabaseTest {
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
        assertThat(workout?.circuits?.single()?.exercises?.first()?.restTimeSeconds).isEqualTo(45)
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
        assertThat(session?.circuits?.single()?.exercises?.first()?.exercise?.restTimeSecondsSnapshot).isEqualTo(75)
    }
}
