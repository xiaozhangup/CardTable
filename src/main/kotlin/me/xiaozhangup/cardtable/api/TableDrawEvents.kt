package me.xiaozhangup.cardtable.api

/** An actual draw at a physical seat; sequence increases throughout one session's lifetime. */
data class TableDraw(val sequence: Long, val seat: Int, val count: Int)

/** Optional presentation event for draws, read on the server thread. */
interface TableDrawEvents {
    /** Exposes only positive counts; clear on a new round/reset, retain a final penalty draw after a win. */
    val lastTableDraw: TableDraw?
}
