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
    val restoredCounts: WorkoutDataRestoreCounts? = null,
) {
    val isSuccess: Boolean
        get() = restoredCounts != null || (importedCount > 0 && failedCount == 0)

    val isPartialSuccess: Boolean
        get() = importedCount > 0 && failedCount > 0

    val isRestore: Boolean
        get() = restoredCounts != null
}

data class WorkoutDataRestoreCounts(
    val exerciseDefinitionCount: Int,
    val workoutCount: Int,
    val sessionCount: Int,
)

class WorkoutImportException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
