package dev.wwade.workout.domain.importer

import dev.wwade.workout.domain.model.WorkoutDraft
import dev.wwade.workout.domain.repository.WorkoutRepository
import dev.wwade.workout.domain.usecase.ValidateWorkoutDraftUseCase

class ImportWorkoutsUseCase(
    private val workoutRepository: WorkoutRepository,
    private val parser: WorkoutImportParser = WorkoutImportParser(),
    private val jsonFetcher: WorkoutImportJsonFetcher = JavaNetWorkoutImportJsonFetcher(),
    private val validator: ValidateWorkoutDraftUseCase = ValidateWorkoutDraftUseCase(),
) {
    suspend operator fun invoke(request: WorkoutImportRequest): WorkoutImportResult {
        val rawJson = when (val source = request.source) {
            is WorkoutImportSource.RawJson -> source.json
            is WorkoutImportSource.Url -> jsonFetcher.fetch(source.url.trim())
        }
        val workouts = parser.parse(rawJson)

        var importedCount = 0
        val errors = mutableListOf<String>()
        workouts.forEachIndexed { index, workout ->
            val validationMessage = workout.validationMessage(index)
            if (validationMessage != null) {
                errors += validationMessage
                return@forEachIndexed
            }

            runCatching {
                workoutRepository.saveWorkout(workout.copy(id = null))
            }.onSuccess {
                importedCount += 1
            }.onFailure { error ->
                errors += "${workout.label(index)}: ${error.message ?: "Unable to save workout."}"
            }
        }

        return WorkoutImportResult(
            importedCount = importedCount,
            failedCount = errors.size,
            workoutErrors = errors,
        )
    }

    private fun WorkoutDraft.validationMessage(index: Int): String? {
        val validation = validator(this)
        if (validation.isValid) return null

        return "${label(index)}: ${validation.describeFirstError()}"
    }

    private fun WorkoutDraft.label(index: Int): String {
        return name.ifBlank { "Workout ${index + 1}" }
    }

    private fun dev.wwade.workout.domain.usecase.WorkoutValidationResult.describeFirstError(): String {
        workoutNameError?.let { return it }
        workoutError?.let { return it }
        circuitValidations.forEach { circuitValidation ->
            circuitValidation.nameError?.let { return it }
            circuitValidation.circuitError?.let { return it }
            if (circuitValidation.setCountMismatch) {
                return "All exercises in a circuit must use the same set count."
            }
            circuitValidation.exerciseValidations.forEach { exerciseValidation ->
                exerciseValidation.nameError?.let { return it }
                exerciseValidation.setCountError?.let { return it }
                exerciseValidation.repRangeError?.let { return it }
                exerciseValidation.loadRangeError?.let { return it }
                exerciseValidation.loadUnitError?.let { return it }
                exerciseValidation.restTimeError?.let { return it }
            }
        }
        return "Workout failed validation."
    }
}

