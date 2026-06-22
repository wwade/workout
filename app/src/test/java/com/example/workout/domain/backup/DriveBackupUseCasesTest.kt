package dev.wwade.workout.domain.backup

import com.google.common.truth.Truth.assertThat
import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.exporter.WorkoutDataExportSnapshot
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutDataRestoreCounts
import dev.wwade.workout.domain.importer.WorkoutDataRestoreSnapshot
import dev.wwade.workout.domain.importer.validWorkoutDataBackupJson
import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.model.WorkoutListItem
import dev.wwade.workout.domain.model.WorkoutTemplate
import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
import dev.wwade.workout.domain.repository.WorkoutDataImportRepository
import dev.wwade.workout.domain.repository.WorkoutRepository
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DriveBackupUseCasesTest {
    @Test
    fun backupAfterCompletionUploadsAndKeepsNewestFiveSnapshots() = runTest {
        val driveRepository = FakeDriveBackupRepository(
            snapshots = (1L..6L).map { index ->
                DriveBackupSnapshot(
                    id = "old-$index",
                    fileName = driveBackupFileName(index),
                    exportedAt = index,
                    modifiedTime = index,
                    sizeBytes = 10,
                )
            },
        )
        val settingsRepository = FakeDriveBackupSettingsRepository(DriveBackupSettings(enabled = true))
        val useCase = BackupNowAfterWorkoutCompletionUseCase(
            exportWorkoutDataUseCase = emptyExportUseCase(),
            driveBackupRepository = driveRepository,
            settingsRepository = settingsRepository,
            accessTokenProvider = object : DriveBackupAccessTokenProvider {
                override suspend fun getAccessTokenOrNull(): String = "token"
            },
        )

        useCase()

        assertThat(driveRepository.uploadedFileNames).containsExactly(driveBackupFileName(1_782_000_000_000))
        assertThat(driveRepository.deletedSnapshotIds).containsExactly("old-1", "old-2")
        assertThat(settingsRepository.settings.value.lastFailureMessage).isNull()
        assertThat(settingsRepository.settings.value.lastSuccessFileName)
            .isEqualTo(driveBackupFileName(1_782_000_000_000))
    }

    @Test
    fun backupAfterCompletionRecordsFailureWhenAuthorizationRequiresUserAction() = runTest {
        val driveRepository = FakeDriveBackupRepository()
        val settingsRepository = FakeDriveBackupSettingsRepository(DriveBackupSettings(enabled = true))
        val useCase = BackupNowAfterWorkoutCompletionUseCase(
            exportWorkoutDataUseCase = emptyExportUseCase(),
            driveBackupRepository = driveRepository,
            settingsRepository = settingsRepository,
            accessTokenProvider = object : DriveBackupAccessTokenProvider {
                override suspend fun getAccessTokenOrNull(): String? = null
            },
        )

        useCase()

        assertThat(driveRepository.uploadedFileNames).isEmpty()
        assertThat(settingsRepository.settings.value.lastFailureMessage)
            .isEqualTo("Drive backup needs Google Drive permission again.")
    }

    @Test
    fun restoreDownloadsSnapshotAndImportsFullBackup() = runTest {
        val importRepository = FakeWorkoutDataImportRepository()
        val driveRepository = FakeDriveBackupRepository(downloadPayload = validWorkoutDataBackupJson())
        val useCase = RestoreDriveBackupUseCase(
            driveBackupRepository = driveRepository,
            importWorkoutsUseCase = ImportWorkoutsUseCase(
                workoutRepository = FakeWorkoutRepository(),
                importRepository = importRepository,
            ),
        )

        val result = useCase("token", "snapshot-1")

        assertThat(driveRepository.downloadedSnapshotIds).containsExactly("snapshot-1")
        assertThat(importRepository.restoredSnapshot?.workouts?.single()?.name).isEqualTo("Push Day")
        assertThat(result.restoredCounts?.workoutCount).isEqualTo(1)
    }

    private fun emptyExportUseCase(): ExportWorkoutDataUseCase {
        return ExportWorkoutDataUseCase(
            exportRepository = object : WorkoutDataExportRepository {
                override suspend fun exportSnapshot(): WorkoutDataExportSnapshot {
                    return WorkoutDataExportSnapshot(
                        exerciseDefinitions = emptyList(),
                        workouts = emptyList(),
                        sessions = emptyList(),
                    )
                }
            },
            clock = Clock.fixed(Instant.parse("2026-06-21T00:00:00Z"), ZoneOffset.UTC),
        )
    }
}

private class FakeDriveBackupRepository(
    snapshots: List<DriveBackupSnapshot> = emptyList(),
    private val downloadPayload: String = "{}",
) : DriveBackupRepository {
    private val snapshots = snapshots.toMutableList()
    val uploadedFileNames = mutableListOf<String>()
    val deletedSnapshotIds = mutableListOf<String>()
    val downloadedSnapshotIds = mutableListOf<String>()

    override suspend fun uploadSnapshot(
        accessToken: String,
        fileName: String,
        exportedAt: Long,
        json: String,
    ): DriveBackupSnapshot {
        uploadedFileNames += fileName
        val snapshot = DriveBackupSnapshot(
            id = "uploaded",
            fileName = fileName,
            exportedAt = exportedAt,
            modifiedTime = exportedAt,
            sizeBytes = json.length.toLong(),
        )
        snapshots += snapshot
        return snapshot
    }

    override suspend fun listSnapshots(accessToken: String): List<DriveBackupSnapshot> = snapshots

    override suspend fun downloadSnapshot(accessToken: String, snapshotId: String): String {
        downloadedSnapshotIds += snapshotId
        return downloadPayload
    }

    override suspend fun deleteSnapshot(accessToken: String, snapshotId: String) {
        deletedSnapshotIds += snapshotId
        snapshots.removeAll { it.id == snapshotId }
    }
}

private class FakeDriveBackupSettingsRepository(
    initialSettings: DriveBackupSettings = DriveBackupSettings(),
) : DriveBackupSettingsRepository {
    val settings = MutableStateFlow(initialSettings)

    override fun observeSettings(): Flow<DriveBackupSettings> = settings

    override suspend fun getSettings(): DriveBackupSettings = settings.value

    override suspend fun setEnabled(enabled: Boolean) {
        settings.value = settings.value.copy(enabled = enabled)
    }

    override suspend fun recordBackupSuccess(snapshot: DriveBackupSnapshot) {
        settings.value = settings.value.copy(
            lastSuccessAt = snapshot.modifiedTime,
            lastSuccessFileName = snapshot.fileName,
            lastFailureMessage = null,
        )
    }

    override suspend fun recordBackupFailure(message: String) {
        settings.value = settings.value.copy(lastFailureMessage = message)
    }
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

private class FakeWorkoutRepository : WorkoutRepository {
    override fun observeWorkouts(): Flow<List<WorkoutListItem>> = MutableStateFlow(emptyList())
    override suspend fun getWorkout(workoutId: Long): WorkoutTemplate? = null
    override suspend fun saveWorkout(workout: WorkoutDraft): Long = 1
    override suspend fun deleteWorkout(workoutId: Long) = Unit
}
