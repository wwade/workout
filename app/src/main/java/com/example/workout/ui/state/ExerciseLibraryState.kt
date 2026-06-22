package dev.wwade.workout.ui.state

import dev.wwade.workout.domain.model.ExerciseDefinition

data class ExerciseLibraryState(
    val exercises: List<ExerciseDefinition> = emptyList(),
    val searchQuery: String = "",
    val saveError: String? = null,
) {
    val filteredExercises: List<ExerciseDefinition>
        get() {
            val query = searchQuery.trim()
            return if (query.isBlank()) {
                exercises
            } else {
                exercises.filter {
                    it.name.contains(query, ignoreCase = true) ||
                        it.defaultGuidance.contains(query, ignoreCase = true)
                }
            }
        }
}
