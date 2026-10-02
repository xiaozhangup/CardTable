package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.CardTablePlugin
import org.bukkit.Bukkit
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.pow

class TableAudio(private val plugin: CardTablePlugin) {
    private data class Note(val tick: Int, val sound: Sound, val pitch: Float, val volume: Float)
    private val notes = YamlConfiguration.loadConfiguration(plugin.dataFolder.resolve("music.yml"))
    private val length = notes.getInt("length-ticks")
    private val events = notes.getMapList("events").map { row ->
        Note((row["tick"] as Number).toInt(), if (row["instrument"] == "bass") Sound.BLOCK_NOTE_BLOCK_BASS else Sound.BLOCK_NOTE_BLOCK_HARP,
            2.0.pow(((row["note"] as Number).toInt() - 12) / 12.0).toFloat(), (row["volume"] as Number).toFloat())
    }.groupBy { it.tick }
    private val muted = mutableSetOf<UUID>()
    private var tick = 0

    init { require(length > 0 && events.keys.all { it in 0 until length }) { "music.yml 的长度或音符位置无效。" } }

    fun voice(tableId: String, key: String) {
        if (!plugin.settings.voice) return
        val room = plugin.tables.rooms[tableId] ?: return
        val sound = if (':' in key) key else "doudizhu:voice.$key"
        room.participants.forEach { participant -> Bukkit.getPlayer(participant.id)?.let {
            it.playSound(room.table.center, sound, SoundCategory.VOICE, 0.9f, 1f)
        } }
    }

    fun tick() {
        if (plugin.settings.music) events[tick]?.let { chord ->
            plugin.tables.rooms.values.filter { it.active }.forEach { room ->
                room.participants.filter { it.id !in muted }.forEach { participant -> Bukkit.getPlayer(participant.id)?.let { player ->
                    chord.forEach { player.playSound(room.table.center, it.sound, SoundCategory.RECORDS, it.volume, it.pitch) }
                } }
            }
        }
        tick = (tick + 1) % length
    }

    fun toggle(player: Player) {
        if (!muted.add(player.uniqueId)) muted.remove(player.uniqueId)
        plugin.tell(player, if (player.uniqueId in muted) "本次在线期间已关闭牌桌音乐。" else "已开启牌桌音乐。")
    }
    fun forget(playerId: UUID) { muted.remove(playerId) }
}
