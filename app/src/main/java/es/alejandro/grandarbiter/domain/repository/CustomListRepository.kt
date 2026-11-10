package es.alejandro.grandarbiter.domain.repository

import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import kotlinx.coroutines.flow.Flow

interface CustomListRepository {
    fun observeAll(): Flow<List<CustomList>>
    suspend fun get(id: Long): CustomList?
    /** Creates (id null) or replaces the list with all its entries. Returns its id. */
    suspend fun save(list: CustomList): Long
    suspend fun delete(id: Long)
}
