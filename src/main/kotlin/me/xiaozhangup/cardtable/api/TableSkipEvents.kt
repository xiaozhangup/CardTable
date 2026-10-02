package me.xiaozhangup.cardtable.api

/** A real skipped turn at a physical seat; sequence increases throughout one session's lifetime. */
data class TableSkip(val sequence: Long, val seat: Int)

/** Optional presentation event for skipped turns, read on the server thread. */
interface TableSkipEvents {
    /** Keep the latest skip until another skip or a new round/reset; a winning final card does not skip. */
    val lastTableSkip: TableSkip?
}
