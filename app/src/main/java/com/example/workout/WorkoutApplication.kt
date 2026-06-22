package dev.wwade.workout

import android.app.Application
import androidx.room.Room
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.db.MIGRATION_2_3
import dev.wwade.workout.data.db.MIGRATION_3_4
import dev.wwade.workout.data.repository.RoomExerciseDefinitionRepository
import dev.wwade.workout.data.repository.RoomWorkoutDataExportRepository
import dev.wwade.workout.data.repository.RoomSessionRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.model.DefaultWorkoutDrafts
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.ExerciseDefinitionRepository
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
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
    val exerciseDefinitionRepository: ExerciseDefinitionRepository
    val workoutRepository: WorkoutRepository
    val sessionRepository: SessionRepository
    val exportWorkoutDataUseCase: ExportWorkoutDataUseCase
}

private class DefaultAppContainer(
    application: Application,
) : AppContainer {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "workout-tracker.db",
    ).addMigrations(MIGRATION_2_3, MIGRATION_3_4)
        .fallbackToDestructiveMigration(true)
        .build()

    override val exerciseDefinitionRepository: ExerciseDefinitionRepository = RoomExerciseDefinitionRepository(
        exerciseDefinitionDao = database.exerciseDefinitionDao(),
    )

    override val workoutRepository: WorkoutRepository = RoomWorkoutRepository(
        workoutTemplateDao = database.workoutTemplateDao(),
    )

    override val sessionRepository: SessionRepository = RoomSessionRepository(
        workoutTemplateDao = database.workoutTemplateDao(),
        workoutSessionDao = database.workoutSessionDao(),
    )

    private val workoutDataExportRepository: WorkoutDataExportRepository =
        RoomWorkoutDataExportRepository(
            exerciseDefinitionDao = database.exerciseDefinitionDao(),
            workoutTemplateDao = database.workoutTemplateDao(),
            workoutSessionDao = database.workoutSessionDao(),
        )

    override val exportWorkoutDataUseCase: ExportWorkoutDataUseCase =
        ExportWorkoutDataUseCase(workoutDataExportRepository)

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
