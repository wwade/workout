package dev.wwade.workout.domain.importer

sealed interface WorkoutImportSource {
    data class RawJson(val json: String) : WorkoutImportSource
    data class Url(val url: String) : WorkoutImportSource
}

data class WorkoutImportRequest(
    val source: WorkoutImportSource,
)

data class WorkoutImportResult(
    val importedCount: Int,
    val failedCount: Int,
    val workoutErrors: List<String>,
) {
    val isSuccess: Boolean
        get() = importedCount > 0 && failedCount == 0

    val isPartialSuccess: Boolean
        get() = importedCount > 0 && failedCount > 0
}

class WorkoutImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

