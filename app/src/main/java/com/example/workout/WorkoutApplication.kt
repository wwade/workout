package com.example.workout

import android.app.Application
import androidx.room.Room
import com.example.workout.data.db.AppDatabase
import com.example.workout.data.repository.RoomSessionRepository
import com.example.workout.data.repository.RoomWorkoutRepository
import com.example.workout.domain.repository.SessionRepository
import com.example.workout.domain.repository.WorkoutRepository

class WorkoutApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = DefaultAppContainer(this)
    }
}

interface AppContainer {
    val workoutRepository: WorkoutRepository
    val sessionRepository: SessionRepository
}

private class DefaultAppContainer(
    application: Application,
) : AppContainer {
    private val database = Room.databaseBuilder(
        application,
        AppDatabase::class.java,
        "workout-tracker.db",
    ).fallbackToDestructiveMigration().build()

    override val workoutRepository: WorkoutRepository = RoomWorkoutRepository(
        workoutTemplateDao = database.workoutTemplateDao(),
    )

    override val sessionRepository: SessionRepository = RoomSessionRepository(
        workoutTemplateDao = database.workoutTemplateDao(),
        workoutSessionDao = database.workoutSessionDao(),
    )
}
