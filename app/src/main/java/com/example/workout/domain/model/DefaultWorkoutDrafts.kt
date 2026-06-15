package com.example.workout.domain.model

object DefaultWorkoutDrafts {
    fun emptyWorkoutDraft(): WorkoutDraft {
        return WorkoutDraft(
            circuits = listOf(
                CircuitDraft(
                    name = "Circuit 1",
                    exercises = listOf(ExerciseDraft()),
                ),
            ),
        )
    }

    fun debugWorkoutDraft(): WorkoutDraft {
        return WorkoutDraft(
            name = "Debug Workout",
            circuits = listOf(
                CircuitDraft(
                    name = "Cycle 1",
                    exercises = listOf(
                        ExerciseDraft(
                            name = "Goblet Squat",
                            guidance = "Drive through the full foot and keep your chest tall.",
                            repMin = 8,
                            repMax = 10,
                            loadMin = 35.0,
                            loadMax = 50.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 45,
                            setCount = 3,
                        ),
                        ExerciseDraft(
                            name = "Push-Up",
                            guidance = "Keep a straight line from shoulders to heels.",
                            repMin = 10,
                            repMax = 15,
                            loadMin = 0.0,
                            loadMax = 0.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 45,
                            setCount = 3,
                        ),
                        ExerciseDraft(
                            name = "Bent-Over Row",
                            guidance = "Pause briefly at the top of each rep.",
                            repMin = 10,
                            repMax = 12,
                            loadMin = 25.0,
                            loadMax = 40.0,
                            loadUnit = LoadUnit.LB,
                            restTimeSeconds = 60,
                            setCount = 3,
                        ),
                    ),
                ),
                CircuitDraft(
                    name = "Cycle 2",
                    exercises = listOf(
                        ExerciseDraft(
                            name = "Plank Hold",
                            guidance = "Brace your core and breathe steadily.",
                            repMin = 1,
                            repMax = 1,
                            loadKind = LoadKind.DURATION,
                            loadMin = 30.0,
                            loadMax = 45.0,
                            loadUnit = LoadUnit.SEC,
                            restTimeSeconds = 60,
                            setCount = 3,
                        ),
                    ),
                ),
            ),
        )
    }
}
