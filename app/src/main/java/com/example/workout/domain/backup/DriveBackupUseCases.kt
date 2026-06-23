package dev.wwade.workout.domain.backup

import dev.wwade.workout.domain.exporter.ExportWorkoutDataUseCase
import dev.wwade.workout.domain.importer.ImportWorkoutsUseCase
import dev.wwade.workout.domain.importer.WorkoutImportRequest
import dev.wwade.workout.domain.importer.WorkoutImportResult
import dev.wwade.workout.domain.importer.WorkoutImportSource
import kotlinx.coroutines.CancellationException

class SetDriveBackupEnabledUseCase(
    private val settingsRepository: DriveBackupSettingsRepository,
) {
    suspend operator fun invoke(enabled: Boolean) {
        settingsRepository.setEnabled(enabled)
    }
}

class BackupNowAfterWorkoutCompletionUseCase(
    private val exportWorkoutDataUseCase: ExportWorkoutDataUseCase,
    private val driveBackupRepository: DriveBackupRepository,
    private val settingsRepository: DriveBackupSettingsRepository,
    private val accessTokenProvider: DriveBackupAccessTokenProvider,
) {
    suspend operator fun invoke() {
        if (!settingsRepository.getSettings().enabled) return

        val accessToken = accessTokenProvider.getAccessTokenOrNull()
        if (accessToken.isNullOrBlank()) {
            settingsRepository.recordBackupFailure("Drive backup needs Google Drive permission again.")
            return
        }

        runCatching {
            val artifact = exportWorkoutDataUseCase()
            val snapshot = driveBackupRepository.uploadSnapshot(
                accessToken = accessToken,
                fileName = driveBackupFileName(artifact.exportedAt),
                exportedAt = artifact.exportedAt,
                json = artifact.json,
            )
            pruneOldSnapshots(accessToken)
            settingsRepository.recordBackupSuccess(snapshot)
        }.onFailure { error ->
            if (error is CancellationException) throw error
            settingsRepository.recordBackupFailure(error.message ?: "Unable to back up workout data to Drive.")
        }
    }

    private suspend fun pruneOldSnapshots(accessToken: String) {
        driveBackupRepository.listSnapshots(accessToken)
            .sortedWith(compareByDescending<DriveBackupSnapshot> { it.exportedAt }.thenByDescending { it.modifiedTime })
            .drop(DRIVE_BACKUP_LIMIT)
            .forEach { snapshot ->
                driveBackupRepository.deleteSnapshot(accessToken, snapshot.id)
            }
    }
}

class ListDriveBackupSnapshotsUseCase(
    private val driveBackupRepository: DriveBackupRepository,
) {
    suspend operator fun invoke(accessToken: String): List<DriveBackupSnapshot> {
        return driveBackupRepository.listSnapshots(accessToken)
            .sortedWith(compareByDescending<DriveBackupSnapshot> { it.exportedAt }.thenByDescending { it.modifiedTime })
    }
}

class RestoreDriveBackupUseCase(
    private val driveBackupRepository: DriveBackupRepository,
    private val importWorkoutsUseCase: ImportWorkoutsUseCase,
) {
    suspend operator fun invoke(accessToken: String, snapshotId: String): WorkoutImportResult {
        val json = driveBackupRepository.downloadSnapshot(accessToken, snapshotId)
        return importWorkoutsUseCase(WorkoutImportRequest(WorkoutImportSource.RawJson(json)))
    }
}

fun driveBackupFileName(exportedAt: Long): String {
    return "$DRIVE_BACKUP_FILE_PREFIX$exportedAt.json"
}
