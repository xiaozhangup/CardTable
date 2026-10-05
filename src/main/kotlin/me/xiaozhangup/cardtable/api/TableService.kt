package me.xiaozhangup.cardtable.api

import org.bukkit.entity.Player
import java.util.UUID

/** Player entry through the shared seating and display layer. Call on the server thread. */
interface TableService {
    val tables: Collection<GameSession>
    fun tableOf(playerId: UUID): GameSession?
    /** Remote entry uses the normal native seat teleport; local entry still requires proximity. */
    fun join(player: Player, id: String, teleportToTable: Boolean = false)
    fun leave(player: Player)
}
