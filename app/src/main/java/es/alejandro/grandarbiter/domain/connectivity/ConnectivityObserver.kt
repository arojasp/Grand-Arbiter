package es.alejandro.grandarbiter.domain.connectivity

import kotlinx.coroutines.flow.Flow

interface ConnectivityObserver {
    fun observe(): Flow<Status>
    suspend fun isConnected(): Boolean

    enum class Status {
        Available, Unavailable, Losing, Lost
    }
}
