package me.xiaozhangup.cardtable.table

import org.bukkit.Location

/** Persisted room settings; rule-specific options belong to the selected provider. */
data class TableConfig(
    val id: String,
    val center: Location,
    val bet: Double,
    var skin: String,
    val turnSeconds: Int,
    val game: String = "doudizhu",
    val options: Map<String, String> = emptyMap()
)
