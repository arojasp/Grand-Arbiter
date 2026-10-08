package es.alejandro.grandarbiter.data.cards.update

import es.alejandro.grandarbiter.data.cards.local.CardDataSource
import es.alejandro.grandarbiter.data.cards.local.CardDataVersion
import es.alejandro.grandarbiter.data.cards.local.CardImportResult
import es.alejandro.grandarbiter.data.cards.local.CardImporter
import es.alejandro.grandarbiter.data.cards.local.CardsDao

/** What the card updater needs from cards.db. Narrow on purpose: faked in unit tests. */
interface InstalledCards {
    suspend fun version(): CardDataVersion?
    suspend fun import(source: CardDataSource): CardImportResult
}

class RoomInstalledCards(private val importer: CardImporter, private val dao: CardsDao) : InstalledCards {
    override suspend fun version(): CardDataVersion? =
        dao.meta()?.let { CardDataVersion(it.oracleUpdatedAt, it.rulingsUpdatedAt) }
    override suspend fun import(source: CardDataSource): CardImportResult = importer.import(source)
}
