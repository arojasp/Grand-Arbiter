package es.alejandro.grandarbiter.data.customlists

import es.alejandro.grandarbiter.data.local.dao.CustomListDao
import es.alejandro.grandarbiter.data.local.entities.CustomListEntity
import es.alejandro.grandarbiter.data.local.entities.CustomListEntryEntity
import es.alejandro.grandarbiter.domain.model.customlists.CustomList
import es.alejandro.grandarbiter.domain.model.customlists.CustomListEntry
import es.alejandro.grandarbiter.domain.model.customlists.ListStatus
import es.alejandro.grandarbiter.domain.repository.CustomListRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

class CustomListRepositoryImpl(private val dao: CustomListDao) : CustomListRepository {

    override fun observeAll(): Flow<List<CustomList>> =
        combine(dao.observeLists(), dao.observeEntries()) { lists, entries ->
            val byList = entries.groupBy { it.listId }
            lists.map { CustomListMapping.toDomain(it, byList[it.id].orEmpty()) }
        }

    override suspend fun get(id: Long): CustomList? =
        dao.get(id)?.let { CustomListMapping.toDomain(it, dao.entries(id)) }

    override suspend fun save(list: CustomList): Long =
        dao.save(list.id, list.name.trim(), list.note.trim(), list.entries.map(CustomListMapping::toEntity))

    override suspend fun delete(id: Long) = dao.delete(id)
}

/** Rows <-> domain. Pure, unit-tested. A row with an unknown status or bad points is skipped. */
object CustomListMapping {
    private const val BANNED = "banned"
    private const val RESTRICTED = "restricted"
    private const val POINTS = "points"

    fun toDomain(list: CustomListEntity, entries: List<CustomListEntryEntity>) = CustomList(
        id = list.id,
        name = list.name,
        note = list.note,
        entries = entries.mapNotNull { e -> status(e)?.let { CustomListEntry(e.oracleId, e.name, it) } },
    )

    fun toEntity(entry: CustomListEntry) = when (val s = entry.status) {
        ListStatus.Banned -> CustomListEntryEntity(listId = 0, oracleId = entry.oracleId, name = entry.name, status = BANNED, points = null)
        ListStatus.Restricted -> CustomListEntryEntity(listId = 0, oracleId = entry.oracleId, name = entry.name, status = RESTRICTED, points = null)
        is ListStatus.Points -> CustomListEntryEntity(listId = 0, oracleId = entry.oracleId, name = entry.name, status = POINTS, points = s.value)
    }

    private fun status(e: CustomListEntryEntity): ListStatus? = when (e.status) {
        BANNED -> ListStatus.Banned
        RESTRICTED -> ListStatus.Restricted
        POINTS -> e.points?.takeIf { it in ListStatus.POINTS_RANGE }?.let { ListStatus.Points(it) }
        else -> null
    }
}
