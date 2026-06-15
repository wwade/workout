package com.example.workout.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider

object AppViewModelFactory {
    inline fun <reified VM : ViewModel> create(
        crossinline builder: () -> VM,
    ): ViewModelProvider.Factory {
        return object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = builder() as T
        }
    }
}
