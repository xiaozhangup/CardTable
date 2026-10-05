package me.xiaozhangup.cardtable.api

import org.bukkit.entity.Player

/** Optional player entry with native seating completed before a participant is committed. */
interface TablePlayerJoin {
    /**
     * Validate entry and choose the seat before invoking [prepareSeat] once, synchronously.
     * Commit the new participant, including any bot replacement, only after it returns.
     * If [prepareSeat] throws, the existing seats must remain unchanged.
     */
    fun join(player: Player, prepareSeat: (Participant) -> Unit)
}
