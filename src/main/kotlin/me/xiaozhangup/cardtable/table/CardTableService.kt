package me.xiaozhangup.cardtable.table

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.util.InputException
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.cardtable.api.*
import me.xiaozhangup.cardtable.ui.TableLayout
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import org.bukkit.plugin.Plugin
import java.util.UUID

/** Shared room and provider registry. It has no dependency on a particular card game's rules. */
class CardTableService(private val plugin: CardTablePlugin) : GameRegistry {
    private val registered = linkedMapOf<String, GameProvider>()
    private val owners = mutableMapOf<String, Plugin>()
    val rooms: MutableMap<String, GameSession> = linkedMapOf()
    override val providers: Collection<GameProvider> get() = registered.values.toList()

    override fun register(provider: GameProvider, owner: Plugin) {
        require(provider.id.matches(Regex("[a-z0-9_-]+"))) { "Invalid game ID: ${provider.id}." }
        require(provider.id !in registered) { "Game ${provider.id} is already registered." }
        registered[provider.id] = provider
        owners[provider.id] = owner
    }

    override fun unregister(id: String) {
        rooms.values.filter { it.table.game == id }.map { it.table.id }.forEach(::remove)
        registered.remove(id)
        owners.remove(id)
    }

    fun unregisterOwnedBy(owner: Plugin) { owners.filterValues { it == owner }.keys.toList().forEach(::unregister) }

    override fun provider(id: String): GameProvider? = registered[id]
    fun room(id: String): GameSession = rooms[id] ?: throw InputException("Table $id was not found.", "找不到牌桌 $id")
    fun tableOf(playerId: UUID): GameSession? = rooms.values.firstOrNull { room -> room.participants.any { it.id == playerId } }

    fun add(table: TableConfig) {
        TableStorage.validateId(table.id)
        requireInput(table.id !in rooms, "Table ${table.id} already exists.") { "牌桌 ${table.id} 已存在" }
        val provider = provider(table.game) ?: throw InputException("Game ${table.game} is not registered.", "游戏 ${table.game} 尚未注册")
        provider.validate(table)
        val context = GameContext(plugin, plugin.economy, plugin.settings.maxMultiplier,
            changed = { changed(table.id) }, broadcast = { broadcast(table.id, it) }, voice = { plugin.audio.voice(table.id, it) }, botAI = plugin.botAI)
        val session = provider.create(context, table)
        requireInput(rooms.values.none { other ->
            val clearance = TableLayout.radius(session.seatCount) + TableLayout.radius(other.seatCount) + 0.8
            other.table.center.world == table.center.world && other.table.center.distanceSquared(table.center) < clearance * clearance
        }, "Table ${table.id} overlaps another table; leave enough clearance between tables.") { "与另一张牌桌太近, 请为桌椅和出入通道留出空间 (普通桌至少间隔 6.4 格)" }
        rooms[table.id] = session
        plugin.renderer.add(session)
        changed(table.id)
    }

    fun join(player: Player, id: String) {
        require(player.hasPermission("cardtable.play")) { "你没有游玩牌桌的权限" }
        require(tableOf(player.uniqueId) == null) { "请先离开当前牌桌" }
        val room = room(id)
        require(player.world == room.table.center.world && player.location.distanceSquared(room.table.center) <= plugin.settings.maxDistance * plugin.settings.maxDistance) { "请走到牌桌附近再入座" }
        if (room is TablePlayerJoin) {
            room.join(player) { participant ->
                plugin.seating.join(player, room.table, participant.seat, room.seatCount)
            }
        } else {
            room.join(player)
            val participant = room.participants.first { it.id == player.uniqueId }
            try {
                plugin.seating.join(player, room.table, participant.seat, room.seatCount)
            } catch (error: IllegalArgumentException) {
                room.leave(player)
                throw error
            }
        }
        plugin.menus.close(player)
        changed(room.table.id)
        plugin.tell(player, "已入座 ${room.table.id}. 右键手牌选中, 轮到你时再次右键已选牌即可出牌; 左键取消选择. 其他操作点全息按钮, Shift 离桌")
    }

    fun leave(player: Player) {
        val room = tableOf(player.uniqueId) ?: return
        room.leave(player)
        plugin.seating.leave(player)
        plugin.renderer.forget(player.uniqueId)
        plugin.menus.close(player)
    }

    fun act(player: Player, action: String, argument: String? = null) {
        val room = tableOf(player.uniqueId) ?: throw IllegalArgumentException("请先加入牌桌")
        room.act(player, action, argument)
    }

    fun bots(player: Player, action: String) {
        val room = tableOf(player.uniqueId) ?: throw IllegalArgumentException("请先加入牌桌")
        require(room.supportsBots) { "这个游戏尚未支持机器人" }
        require(!room.active) { "请等本局结束后调整机器人席位" }
        when (action) {
            "add", "fill" -> {
                require(room.table.bet == 0.0) { "机器人只支持免费桌, 请先将底注设为0" }
                require(plugin.botAI.available) { plugin.botAI.unavailableReason }
                val empty = room.seatCount - room.participants.size
                require(empty > 0) { "这张牌桌已经坐满了" }
                plugin.botNames.pick(room.participants, if (action == "fill") empty else 1).forEach { name ->
                    room.addBot(UUID.randomUUID(), name)
                }
            }
            "remove" -> {
                val bot = room.participants.lastOrNull { it.bot } ?: throw IllegalArgumentException("这张牌桌没有机器人")
                room.removeBot(bot.id)
            }
            "clear" -> room.participants.filter { it.bot }.forEach { room.removeBot(it.id) }
            else -> throw IllegalArgumentException("使用 /ct bot add|fill|remove|clear")
        }
        changed(room.table.id)
    }

    fun remove(id: String) {
        val room = room(id)
        room.participants.toList().forEach { Bukkit.getPlayer(it.id)?.let { player -> plugin.seating.leave(player); plugin.menus.close(player) } }
        room.close()
        plugin.renderer.remove(id)
        rooms.remove(id)
    }

    fun tick() {
        rooms.values.toList().forEach { room ->
            room.participants.filterNot { it.bot }.forEach { seat ->
                val player = Bukkit.getPlayer(seat.id)
                if (player != null && (player.world != room.table.center.world || player.location.distanceSquared(room.table.center) > plugin.settings.maxDistance * plugin.settings.maxDistance)) leave(player)
            }
            room.tick()
        }
    }

    private fun changed(id: String) {
        val room = rooms[id] ?: return // Provider construction can publish its initial view before registration finishes.
        plugin.renderer.refresh(room)
        plugin.menus.refresh(room)
    }

    private fun broadcast(id: String, text: String) {
        rooms[id]?.participants?.forEach { Bukkit.getPlayer(it.id)?.let { player -> plugin.tell(player, text) } }
    }

    fun shutdown() { rooms.keys.toList().forEach(::remove) }
}
