package me.xiaozhangup.cardtable.api

/** Optional public pile for the physical table, read on the server thread. */
interface TableCardPile {
    /** Cards still on the table in play order, oldest first; excludes private hands. */
    fun tableCards(): List<CardFace>
}
