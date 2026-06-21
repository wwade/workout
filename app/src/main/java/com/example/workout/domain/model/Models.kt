package dev.wwade.workout.domain.model

enum class LoadKind {
    WEIGHT,
    DURATION,
}

enum class LoadUnit {
    LB,
    KG,
    SEC,
}

enum class SessionStatus {
    IN_PROGRESS,
    COMPLETED,
    ABANDONED,
}

data class WorkoutTemplate(
    val id: Long,
    val name: String,
    val sortOrder: Int,
    val createdAt: Long,
    val updatedAt: Long,
    val circuits: List<CircuitTemplate>,
)

data class CircuitTemplate(
    val id: Long,
    val workoutId: Long,
    val name: String,
    val sortOrder: Int,
    val exercises: List<ExerciseTemplate>,
)

data class ExerciseTemplate(
    val id: Long,
    val circuitId: Long,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val setCount: Int,
    val sortOrder: Int,
)

data class WorkoutDraft(
    val id: Long? = null,
    val name: String = "",
    val circuits: List<CircuitDraft> = emptyList(),
)

data class CircuitDraft(
    val id: Long? = null,
    val name: String = "",
    val exercises: List<ExerciseDraft> = emptyList(),
)

data class ExerciseDraft(
    val id: Long? = null,
    val name: String = "",
    val guidance: String = "",
    val repMin: Int = 6,
    val repMax: Int = 8,
    val loadKind: LoadKind = LoadKind.WEIGHT,
    val loadMin: Double = 0.0,
    val loadMax: Double = 0.0,
    val loadUnit: LoadUnit = LoadUnit.LB,
    val restTimeSeconds: Int = 60,
    val setCount: Int = 3,
)

data class WorkoutListItem(
    val id: Long,
    val name: String,
    val circuitCount: Int,
    val exerciseCount: Int,
    val updatedAt: Long,
)

data class WorkoutSessionDetail(
    val sessionId: Long,
    val workoutTemplateId: Long?,
    val workoutName: String,
    val startedAt: Long,
    val completedAt: Long?,
    val status: SessionStatus,
    val circuits: List<CircuitSessionDetail>,
)

data class CircuitSessionDetail(
    val circuitSessionId: Long,
    val circuitTemplateId: Long?,
    val name: String,
    val sortOrder: Int,
    val setCount: Int,
    val exercises: List<ExerciseSessionDetail>,
)

data class ExerciseSessionDetail(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val name: String,
    val guidance: String,
    val repMin: Int,
    val repMax: Int,
    val loadKind: LoadKind,
    val loadMin: Double,
    val loadMax: Double,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val sortOrder: Int,
    val sets: List<SetEntry>,
)

data class SetEntry(
    val id: Long,
    val exerciseSessionId: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)

data class ExerciseSetHistoryItem(
    val workoutSessionId: Long,
    val workoutName: String,
    val completedAt: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)

data class SetEntryDraft(
    val exerciseSessionId: Long,
    val setIndex: Int,
    val repsActual: Int?,
    val loadActual: Double?,
    val notes: String,
    val skipped: Boolean,
)

data class CompletedSessionListItem(
    val sessionId: Long,
    val workoutName: String,
    val completedAt: Long,
    val circuitCount: Int,
)

data class SessionProgressSnapshot(
    val sessionId: Long,
    val workoutName: String,
    val currentCircuitIndex: Int,
    val currentCircuitName: String,
    val currentSetIndex: Int,
    val totalSetsInCircuit: Int,
    val isLastRound: Boolean,
    val isLastCircuit: Boolean,
    val isCompleted: Boolean,
    val availablePositions: List<SessionPositionSnapshot>,
    val exercises: List<ExerciseProgressSnapshot>,
)

data class SessionPositionSnapshot(
    val circuitIndex: Int,
    val setIndex: Int,
    val circuitName: String,
    val totalSetsInCircuit: Int,
    val isSaved: Boolean,
    val isCurrentTarget: Boolean,
    val isSelectable: Boolean,
)

data class ExerciseProgressSnapshot(
    val exerciseSessionId: Long,
    val exerciseTemplateId: Long?,
    val exerciseName: String,
    val guidance: String,
    val repRangeLabel: String,
    val loadRangeLabel: String,
    val loadUnit: LoadUnit,
    val restTimeSeconds: Int,
    val suggestedReps: Int?,
    val suggestedLoad: Double?,
    val suggestedNotes: String,
    val suggestedSkipped: Boolean,
)
