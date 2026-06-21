package dev.wwade.workout.domain.exporter

import dev.wwade.workout.domain.repository.WorkoutDataExportRepository
import java.time.Clock
import kotlinx.serialization.json.Json

class ExportWorkoutDataUseCase(
    private val exportRepository: WorkoutDataExportRepository,
    private val clock: Clock = Clock.systemUTC(),
    private val json: Json = Json {
        prettyPrint = true
        encodeDefaults = true
    },
) {
    suspend operator fun invoke(): WorkoutDataExportArtifact {
        val exportedAt = clock.millis()
        val payload = exportRepository.exportSnapshot().toDto(exportedAt)
        return WorkoutDataExportArtifact(
            exportedAt = exportedAt,
            json = json.encodeToString(WorkoutDataExportDto.serializer(), payload),
        )
    }
}
