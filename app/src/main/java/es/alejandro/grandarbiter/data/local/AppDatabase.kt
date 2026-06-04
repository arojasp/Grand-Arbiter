package es.alejandro.grandarbiter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import es.alejandro.grandarbiter.data.local.dao.CardDao
import es.alejandro.grandarbiter.data.local.dao.DocumentDao
import es.alejandro.grandarbiter.data.local.dao.QuickReferenceDao
import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity
import es.alejandro.grandarbiter.data.local.entities.DocumentEntity
import es.alejandro.grandarbiter.data.local.entities.DocumentSectionEntity
import es.alejandro.grandarbiter.data.local.entities.QuickReferenceEntity

@Database(
    entities = [
        DocumentEntity::class,
        DocumentSectionEntity::class,
        CardCacheEntity::class,
        QuickReferenceEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun documentDao(): DocumentDao
    abstract fun cardDao(): CardDao
    abstract fun quickReferenceDao(): QuickReferenceDao
}
