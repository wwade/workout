package dev.wwade.workout

import android.app.Application
import androidx.room.Room
import dev.wwade.workout.data.db.AppDatabase
import dev.wwade.workout.data.db.MIGRATION_2_3
import dev.wwade.workout.data.db.MIGRATION_3_4
import dev.wwade.workout.data.repository.DriveAppDataBackupRepository
import dev.wwade.workout.data.repository.GoogleDriveBackupAccessTokenProvider
import dev.wwade.workout.data.repository.RoomExerciseDefinitionRepository
import dev.wwade.workout.data.repository.RoomWorkoutDataExportRepository
import dev.wwade.workout.data.repository.RoomWorkoutDataImportRepository
import dev.wwade.workout.data.repository.RoomSessionRepository
import dev.wwade.workout.data.repository.RoomWorkoutRepository
import dev.wwade.workout.data.repository.SharedPreferencesDriveBackupSettingsRepository
import dev.wwade.workout.domain.backup.BackupNowAfterWorkoutCompletionUseCase
import dev.wwade.workout.domain.backup.DriveBackupRepository
import dev.wwade.workout.domain.backup.DriveBackupSettingsRepository
import dev.wwade.workout.domain.backup.ListDriveBackupSnapshotsUseCase
import dev.wwade.workout.domain.backup.RestoreDriveBackupUseCase
import dev.wwade.workout.domain.backup.SetDriveBackupEnabledUseCase
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.model.DefaultWorkoutDrafts
import dev.wwade.workout.domain.repository.SessionRepository
import dev.wwade.workout.domain.repository.ExerciseDefinitionRepository
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
import dev.wwade.workout.domain.repository.WorkoutDataImportRepository
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
    val importWorkoutsUseCase: ImportWorkoutsUseCase
    val driveBackupSettingsRepository: DriveBackupSettingsRepository
    val setDriveBackupEnabledUseCase: SetDriveBackupEnabledUseCase
    val backupNowAfterWorkoutCompletionUseCase: BackupNowAfterWorkoutCompletionUseCase
    val listDriveBackupSnapshotsUseCase: ListDriveBackupSnapshotsUseCase
    val restoreDriveBackupUseCase: RestoreDriveBackupUseCase
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

    private val workoutDataImportRepository: WorkoutDataImportRepository =
        RoomWorkoutDataImportRepository(database)

    override val exportWorkoutDataUseCase: ExportWorkoutDataUseCase =
        ExportWorkoutDataUseCase(workoutDataExportRepository)

    override val importWorkoutsUseCase: ImportWorkoutsUseCase =
        ImportWorkoutsUseCase(
            workoutRepository = workoutRepository,
            importRepository = workoutDataImportRepository,
        )

    private val driveBackupRepository: DriveBackupRepository = DriveAppDataBackupRepository()

    override val driveBackupSettingsRepository: DriveBackupSettingsRepository =
        SharedPreferencesDriveBackupSettingsRepository(application)

    override val setDriveBackupEnabledUseCase: SetDriveBackupEnabledUseCase =
        SetDriveBackupEnabledUseCase(driveBackupSettingsRepository)

    override val backupNowAfterWorkoutCompletionUseCase: BackupNowAfterWorkoutCompletionUseCase =
        BackupNowAfterWorkoutCompletionUseCase(
            exportWorkoutDataUseCase = exportWorkoutDataUseCase,
            driveBackupRepository = driveBackupRepository,
            settingsRepository = driveBackupSettingsRepository,
            accessTokenProvider = GoogleDriveBackupAccessTokenProvider(application),
        )

    override val listDriveBackupSnapshotsUseCase: ListDriveBackupSnapshotsUseCase =
        ListDriveBackupSnapshotsUseCase(driveBackupRepository)

    override val restoreDriveBackupUseCase: RestoreDriveBackupUseCase =
        RestoreDriveBackupUseCase(
            driveBackupRepository = driveBackupRepository,
            importWorkoutsUseCase = importWorkoutsUseCase,
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
