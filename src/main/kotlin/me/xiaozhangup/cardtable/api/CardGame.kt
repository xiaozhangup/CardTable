package me.xiaozhangup.cardtable.api

import me.xiaozhangup.cardtable.table.TableConfig
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.plugin.Plugin
import java.util.UUID

/** Implement on the server thread. Register through Bukkit's GameRegistry service. */
interface GameProvider {
    val id: String
    val displayName: String
    fun validate(table: TableConfig) {}
    fun create(context: GameContext, table: TableConfig): GameSession
}

/** Broadcast messages accept legacy § color codes; §r restores the normal message color. */
data class GameContext(
    val plugin: JavaPlugin,
    val economy: GameEconomy,
    val maxMultiplier: Int,
    val changed: () -> Unit,
    val broadcast: (String) -> Unit,
    val voice: (String) -> Unit,
    val botAI: CardAI
)

/** One session belongs to one table; the shared layer owns menus, entities and seating. */
interface GameSession {
    val table: TableConfig
    val seatCount: Int
    val participants: List<Participant>
    val active: Boolean
    val supportsBots: Boolean get() = false
    fun addBot(id: UUID, name: String): Participant = throw IllegalArgumentException("这个游戏尚未支持机器人")
    fun removeBot(id: UUID): Unit = throw IllegalArgumentException("这个游戏尚未支持机器人")
    fun join(player: Player)
    fun leave(player: Player)
    fun act(player: Player, action: String, argument: String? = null)
    fun view(viewer: UUID?): GameView
    fun tick()
    fun close()
}

data class Participant(val id: UUID, val name: String, val seat: Int, val bot: Boolean = false)

/** asset is a face key appended to the chosen skin prefix; names also work without a pack. */
data class CardFace(val asset: String, val name: String, val token: String, val selected: Boolean = false)
data class PlayerView(val participant: Participant, val role: String, val count: Int, val ready: Boolean, val current: Boolean)
data class GameControl(val action: String, val title: String, val icon: String, val argument: String? = null, val description: List<String> = emptyList())
data class GameView(
    val status: String,
    val players: List<PlayerView>,
    val hand: List<CardFace>,
    val publicCards: List<CardFace>,
    val bottomCards: List<CardFace>,
    val controls: List<GameControl>,
    val remainingSeconds: Int = 0,
    val multiplier: String = "",
    val note: String = "",
    val backAsset: String = "back",
    val leaveDescription: List<String> = listOf("离桌, 断线或超出距离按本桌游戏规则处理")
)

interface GameEconomy {
    fun ensureAvailable()
    fun reserve(roundId: String, tableId: String, players: List<Pair<UUID, String>>, amount: Double)
    fun settle(roundId: String, netResults: Map<UUID, Double>)
    fun refund(roundId: String)
    fun retry(playerId: UUID? = null)
}

interface GameRegistry {
    val providers: Collection<GameProvider>
    fun register(provider: GameProvider, owner: Plugin)
    /** Close its rooms and refund their unfinished rounds; saved room configuration is retained. */
    fun unregister(id: String)
    fun provider(id: String): GameProvider?
}
