package es.alejandro.grandarbiter.data.local.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "comprehensive_rules")
data class CREntity(
    @PrimaryKey
    val ruleNumber: String,
    val ruleText: String,
    val fragment: String,
    val examples: List<String>,
    @Embedded(prefix = "nav_")
    val navigation: NavigationEntity?
)

data class NavigationEntity(
    val previousRule: String? = null,
    val nextRule: String? = null
)
