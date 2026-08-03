package es.alejandro.grandarbiter.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import es.alejandro.grandarbiter.data.local.dao.CRDao
import es.alejandro.grandarbiter.data.local.dao.CardDao
import es.alejandro.grandarbiter.data.local.entities.CREntity
import es.alejandro.grandarbiter.data.local.entities.CardCacheEntity
import es.alejandro.grandarbiter.data.local.entities.GlossaryEntity
import es.alejandro.grandarbiter.data.local.entities.TOCEntity

@Database(
    entities = [
        CREntity::class,
        CardCacheEntity::class,
        TOCEntity::class,
        GlossaryEntity::class
    ],
    version = 6,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun crDao(): CRDao
    abstract fun cardDao(): CardDao
}
