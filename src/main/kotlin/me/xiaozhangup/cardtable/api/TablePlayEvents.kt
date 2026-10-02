package me.xiaozhangup.cardtable.api

/** A successfully played public group; sequence increases throughout one session's lifetime. */
data class TablePlay(val sequence: Long, val seat: Int, val cards: List<CardFace>)

/** Optional presentation event for public cards, read on the server thread. */
interface TablePlayEvents {
    /** Retain after a normal win; clear on a new round, cancellation or close. Opening cards are not plays. */
    val lastTablePlay: TablePlay?
}
