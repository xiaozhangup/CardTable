package me.xiaozhangup.cardtable.api

/** Physical turn direction viewed from above the table. */
enum class TurnDirection { CLOCKWISE, COUNTERCLOCKWISE }

enum class TurnDirectionPlacement { TABLETOP, HEADER }

/** Optional turn direction for the shared table display, read on the server thread. */
interface TableTurnOrder {
    /** Fixed for the session lifetime; the default keeps the direction on the table surface. */
    val directionPlacement: TurnDirectionPlacement get() = TurnDirectionPlacement.TABLETOP

    /** Current rule direction, or null while waiting or when the game has no direction. */
    val turnDirection: TurnDirection?
}
