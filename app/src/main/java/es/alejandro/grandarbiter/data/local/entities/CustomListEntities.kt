package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/*
 * User data (AppDatabase): the SQL in AppDatabase.MIGRATION_8_9 must match these definitions
 * exactly (Room validates it); compare with app/schemas/…/9.json after a build.
 */

@Entity(tableName = "custom_list")
data class CustomListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val note: String,
    val position: Int,
)

/** One card per list: a second paste of the same card updates it. */
@Entity(
    tableName = "custom_list_entry",
    foreignKeys = [
        ForeignKey(
            entity = CustomListEntity::class,
            parentColumns = ["id"],
            childColumns = ["listId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["listId", "oracleId"], unique = true)],
)
data class CustomListEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val listId: Long,
    val oracleId: String,
    val name: String,
    val status: String,               // "banned" | "restricted" | "points"
    val points: Int?,                 // only with "points"
)
