package me.xiaozhangup.cardtable.api

import me.xiaozhangup.cardtable.table.TableConfig
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

/** Notification after a real player has successfully joined and been seated. */
class TablePlayerJoinEvent(val table: TableConfig, val player: Player, participants: List<Participant>) : Event() {
    val participants: List<Participant> = participants.toList()
    override fun getHandlers(): HandlerList = handlerList
    companion object {
        private val handlerList = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlerList
    }
}

/** One event per funded/dealt round, including free rounds; redealing does not start a new round. */
class TableRoundStartEvent(val table: TableConfig, val roundId: String, participants: List<Participant>) : Event() {
    val participants: List<Participant> = participants.toList()
    override fun getHandlers(): HandlerList = handlerList
    companion object {
        private val handlerList = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlerList
    }
}

enum class TableRoundEndReason { WIN, FORFEIT, CANCELLED }

/** Public result snapshot. Winners include bots and teammates; cancellations have no winners. */
class TableRoundEndEvent(
    val table: TableConfig,
    val roundId: String,
    participants: List<Participant>,
    winners: List<Participant>,
    val reason: TableRoundEndReason
) : Event() {
    val participants: List<Participant> = participants.toList()
    val winners: List<Participant> = winners.toList()
    override fun getHandlers(): HandlerList = handlerList
    companion object {
        private val handlerList = HandlerList()
        @JvmStatic fun getHandlerList(): HandlerList = handlerList
    }
}
