package dev.wwade.workout

import android.app.Application
import androidx.room.Room
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.domain.model.DefaultWorkoutDrafts
import dev.wwade.workout.data.repository.RoomSessionRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class WorkoutApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

interface AppContainer {
    val workoutRepository: WorkoutRepository
    val sessionRepository: SessionRepository
}

private class DefaultAppContainer(
    application: Application,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "workout-tracker.db",
    ).fallbackToDestructiveMigration().build()

    override val workoutRepository: WorkoutRepository = RoomWorkoutRepository(
        workoutTemplateDao = database.workoutTemplateDao(),
    )

    override val sessionRepository: SessionRepository = RoomSessionRepository(
        workoutTemplateDao = database.workoutTemplateDao(),
        workoutSessionDao = database.workoutSessionDao(),
    )

    init {
        if (BuildConfig.DEBUG) {
            applicationScope.launch {
                if (database.workoutTemplateDao().countWorkoutTemplates() == 0) {
                    database.workoutTemplateDao().upsertWorkoutGraph(DefaultWorkoutDrafts.debugWorkoutDraft())
                }
            }
        }
    }
}
