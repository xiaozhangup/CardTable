package me.xiaozhangup.cardtable.api

enum class TablePileLayout { STACKED, SCATTERED }

/** Optional public pile for the physical table, read on the server thread. */
interface TableCardPile {
    /** Presentation only; STACKED keeps a compact discard pile, SCATTERED spreads cards around the center. */
    val tablePileLayout: TablePileLayout get() = TablePileLayout.STACKED

    /** Cards still on the table in play order, oldest first; excludes private hands. */
    fun tableCards(): List<CardFace>
}
