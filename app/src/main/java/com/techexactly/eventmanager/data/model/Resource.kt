package com.techexactly.eventmanager.data.model

/**
 * Simple wrapper used to push loading/success/error state out of the repository layer
 * and into ViewModels via [kotlinx.coroutines.flow.Flow] / StateFlow, without leaking
 * Firebase exception types into the UI layer.
 */
sealed class Resource<out T> {
    data object Loading : Resource<Nothing>()
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String) : Resource<Nothing>()
}
