package me.xiaozhangup.cardtable.api

/** Physical turn direction viewed from above the table. */
enum class TurnDirection { CLOCKWISE, COUNTERCLOCKWISE }

/** Optional turn direction for the shared table display, read on the server thread. */
interface TableTurnOrder {
    /** Current rule direction, or null while waiting or when the game has no direction. */
    val turnDirection: TurnDirection?
}
