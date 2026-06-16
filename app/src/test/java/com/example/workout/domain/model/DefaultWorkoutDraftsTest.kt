package dev.wwade.workout.domain.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DefaultWorkoutDraftsTest {
    @Test
    fun debugWorkoutHasExpectedCycleLayout() {
        val workout = DefaultWorkoutDrafts.debugWorkoutDraft()

        assertThat(workout.circuits).hasSize(2)
        assertThat(workout.circuits[0].exercises).hasSize(3)
        assertThat(workout.circuits[1].exercises).hasSize(1)
        assertThat(workout.circuits[1].exercises.single().loadKind).isEqualTo(LoadKind.DURATION)
        assertThat(workout.circuits[1].exercises.single().loadUnit).isEqualTo(LoadUnit.SEC)
    }
}
