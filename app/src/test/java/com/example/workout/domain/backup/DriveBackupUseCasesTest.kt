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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Test

class DriveBackupUseCasesTest {
    @Test
    fun driveBackupFileNameIncludesVariantKey() {
        assertThat(driveBackupFileName("release", 1_782_000_000_000))
            .isEqualTo("workout-tracker-backup-release-1782000000000.json")
        assertThat(driveBackupFileName("debug", 1_782_000_000_000))
            .isEqualTo("workout-tracker-backup-debug-1782000000000.json")
        assertThat(driveBackupFileName("e2e", 1_782_000_000_000))
            .isEqualTo("workout-tracker-backup-e2e-1782000000000.json")
    }

    @Test
    fun driveBackupExportedAtFromFileNameSupportsLegacyAndVariantNames() {
        assertThat(driveBackupExportedAtFromFileName("workout-tracker-backup-1782000000000.json"))
            .isEqualTo(1_782_000_000_000)
        assertThat(driveBackupExportedAtFromFileName("workout-tracker-backup-debug-1782000000000.json"))
            .isEqualTo(1_782_000_000_000)
        assertThat(driveBackupExportedAtFromFileName("not-a-drive-backup-1782000000000.json"))
            .isEqualTo(0L)
    }

    @Test
    fun backupAfterCompletionUploadsAndKeepsNewestFiveSnapshots() = runTest {
        val driveRepository = FakeDriveBackupRepository(
            snapshots = (1L..6L).map { index ->
                DriveBackupSnapshot(
                    id = "old-$index",
                    fileName = driveBackupFileName("debug", index),
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
            variantKey = "debug",
        )

        useCase()

        assertThat(driveRepository.uploadedFileNames).containsExactly(driveBackupFileName("debug", 1_782_000_000_000))
        assertThat(driveRepository.deletedSnapshotIds).containsExactly("old-1", "old-2")
        assertThat(settingsRepository.settings.value.lastFailureMessage).isNull()
        assertThat(settingsRepository.settings.value.lastSuccessFileName)
            .isEqualTo(driveBackupFileName("debug", 1_782_000_000_000))
    }

    @Test
    fun debugBackupPrunesOnlyDebugSnapshots() = runTest {
        val driveRepository = FakeDriveBackupRepository(
            snapshots = listOf(
                DriveBackupSnapshot("debug-1", driveBackupFileName("debug", 1), 1, 1, 10),
                DriveBackupSnapshot("debug-2", driveBackupFileName("debug", 2), 2, 2, 10),
                DriveBackupSnapshot("debug-3", driveBackupFileName("debug", 3), 3, 3, 10),
                DriveBackupSnapshot("debug-4", driveBackupFileName("debug", 4), 4, 4, 10),
                DriveBackupSnapshot("debug-5", driveBackupFileName("debug", 5), 5, 5, 10),
                DriveBackupSnapshot("release", driveBackupFileName("release", 1), 1, 1, 10),
                DriveBackupSnapshot("legacy", "workout-tracker-backup-1.json", 1, 1, 10),
                DriveBackupSnapshot("e2e", driveBackupFileName("e2e", 1), 1, 1, 10),
            ),
        )
        val useCase = BackupNowAfterWorkoutCompletionUseCase(
            exportWorkoutDataUseCase = emptyExportUseCase(),
            driveBackupRepository = driveRepository,
            settingsRepository = FakeDriveBackupSettingsRepository(DriveBackupSettings(enabled = true)),
            accessTokenProvider = object : DriveBackupAccessTokenProvider {
                override suspend fun getAccessTokenOrNull(): String = "token"
            },
            variantKey = "debug",
        )

        useCase()

        assertThat(driveRepository.deletedSnapshotIds).containsExactly("debug-1")
    }

    @Test
    fun releaseBackupPrunesReleaseAndLegacySnapshotsTogether() = runTest {
        val driveRepository = FakeDriveBackupRepository(
            snapshots = listOf(
                DriveBackupSnapshot("release-1", driveBackupFileName("release", 1), 1, 1, 10),
                DriveBackupSnapshot("release-2", driveBackupFileName("release", 2), 2, 2, 10),
                DriveBackupSnapshot("release-3", driveBackupFileName("release", 3), 3, 3, 10),
                DriveBackupSnapshot("legacy-4", "workout-tracker-backup-4.json", 4, 4, 10),
                DriveBackupSnapshot("legacy-5", "workout-tracker-backup-5.json", 5, 5, 10),
                DriveBackupSnapshot("legacy-6", "workout-tracker-backup-6.json", 6, 6, 10),
                DriveBackupSnapshot("debug", driveBackupFileName("debug", 1), 1, 1, 10),
            ),
        )
        val useCase = BackupNowAfterWorkoutCompletionUseCase(
            exportWorkoutDataUseCase = emptyExportUseCase(),
            driveBackupRepository = driveRepository,
            settingsRepository = FakeDriveBackupSettingsRepository(DriveBackupSettings(enabled = true)),
            accessTokenProvider = object : DriveBackupAccessTokenProvider {
                override suspend fun getAccessTokenOrNull(): String = "token"
            },
            variantKey = "release",
        )

        useCase()

        assertThat(driveRepository.deletedSnapshotIds).containsExactly("release-1", "release-2")
    }

    @Test
    fun listSnapshotsShowsOnlyCurrentVariantWithLegacyReleaseOnly() = runTest {
        val driveRepository = FakeDriveBackupRepository(
            snapshots = listOf(
                DriveBackupSnapshot("debug", driveBackupFileName("debug", 2), 2, 2, 10),
                DriveBackupSnapshot("release", driveBackupFileName("release", 3), 3, 3, 10),
                DriveBackupSnapshot("legacy", "workout-tracker-backup-4.json", 4, 4, 10),
                DriveBackupSnapshot("e2e", driveBackupFileName("e2e", 5), 5, 5, 10),
            ),
        )

        val debugSnapshots = ListDriveBackupSnapshotsUseCase(driveRepository, "debug")("token")
        val releaseSnapshots = ListDriveBackupSnapshotsUseCase(driveRepository, "release")("token")

        assertThat(debugSnapshots.map { it.id }).containsExactly("debug")
        assertThat(releaseSnapshots.map { it.id }).containsExactly("legacy", "release").inOrder()
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
            variantKey = "debug",
        )

        useCase()

        assertThat(driveRepository.uploadedFileNames).isEmpty()
        assertThat(settingsRepository.settings.value.lastFailureMessage)
            .isEqualTo("Drive backup needs Google Drive permission again.")
    }

    @Test
    fun backupAfterCompletionDoesNotRecordFailureWhenCancelled() = runTest {
        val driveRepository = FakeDriveBackupRepository(
            uploadError = CancellationException("Job was cancelled"),
        )
        val settingsRepository = FakeDriveBackupSettingsRepository(
            DriveBackupSettings(
                enabled = true,
                lastFailureMessage = "previous failure",
            ),
        )
        val useCase = BackupNowAfterWorkoutCompletionUseCase(
            exportWorkoutDataUseCase = emptyExportUseCase(),
            driveBackupRepository = driveRepository,
            settingsRepository = settingsRepository,
            accessTokenProvider = object : DriveBackupAccessTokenProvider {
                override suspend fun getAccessTokenOrNull(): String = "token"
            },
            variantKey = "debug",
        )

        try {
            useCase()
        } catch (_: CancellationException) {
        }

        assertThat(settingsRepository.settings.value.lastFailureMessage).isEqualTo("previous failure")
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
    private val uploadError: Throwable? = null,
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
        uploadError?.let { throw it }
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
