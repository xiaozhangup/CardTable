package me.xiaozhangup.cardtable.ui

import io.papermc.paper.datacomponent.DataComponentTypes
import io.papermc.paper.datacomponent.item.ResolvableProfile
import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.GameControl
import me.xiaozhangup.cardtable.api.GameSession
import me.xiaozhangup.cardtable.api.PlayerView
import me.xiaozhangup.cardtable.util.ext.submitTask
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale
import java.util.UUID

/** Containers provide settings only; play actions belong to the world table. */
class TableMenus(private val plugin: CardTablePlugin) : Listener {
    private class MenuHolder(
        private val title: Component,
        val tableId: String?,
        val viewer: UUID,
        var page: Int = 0,
    ) : InventoryHolder {
        private lateinit var layout: CharArray
        lateinit var contents: Inventory
            private set
        val actions = mutableMapOf<Int, GameControl>()

        fun map(vararg rows: String) {
            layout = rows.joinToString("").toCharArray()
            if (!::contents.isInitialized) contents = Bukkit.createInventory(this, layout.size, title)
        }

        fun getSlots(key: Char): List<Int> = layout.indices.filter { layout[it] == key }

        fun set(key: Char, item: ItemStack, control: GameControl? = null) {
            getSlots(key).forEach { slot -> set(slot, item, control) }
        }

        fun set(slot: Int, item: ItemStack, control: GameControl? = null) {
            contents.setItem(slot, item)
            if (control == null) actions.remove(slot) else actions[slot] = control
        }

        override fun getInventory(): Inventory = contents
    }
    private val items get() = plugin.items

    fun open(player: Player, session: GameSession) {
        val menu = MenuHolder(Component.text("牌桌设置  ${session.table.id}", NamedTextColor.DARK_GRAY), session.table.id, player.uniqueId)
        render(menu, session)
        player.openInventory(menu.contents)
    }

    fun lobby(player: Player, page: Int = 0) {
        val rooms = plugin.tables.rooms.values.toList()
        val entries = "TTTTTTTTT"
        val lastPage = (rooms.size - 1).coerceAtLeast(0) / entries.length
        val currentPage = page.coerceIn(0, lastPage)
        val title = if (lastPage == 0) "牌桌列表" else "牌桌列表 ${currentPage + 1}/${lastPage + 1}"
        val menu = MenuHolder(Component.text(title, NamedTextColor.DARK_GRAY), null, player.uniqueId, currentPage)
        val rows = if (lastPage == 0) arrayOf("========X", entries)
            else arrayOf("========X", entries, "=======<>")
        menu.map(*rows)
        renderLobby(menu, rooms, lastPage)
        player.openInventory(menu.contents)
    }

    private fun renderLobby(menu: MenuHolder, rooms: List<GameSession>, lastPage: Int) {
        frame(menu)
        val slots = menu.getSlots('T')
        button(menu, 'X', GameControl("close", "关闭菜单", ""))
        rooms.drop(menu.page * slots.size).take(slots.size).forEachIndexed { index, room ->
            menu.set(slots[index], items.menuIcon(Material.OAK_SLAB, room.table.id, buildList {
                add(items.parameter("玩法", plugin.tables.provider(room.table.game)!!.displayName))
                add(items.parameter("人数", "${room.participants.size}/${room.seatCount}"))
                add(items.parameter("状态", if (room.active) "对局中" else "等待准备"))
                if (room.table.bet > 0) add(items.parameter("底注", bet(room.table.bet)))
                add(items.description(""))
                add(items.hint("单击打开设置"))
            }), GameControl("settings", "", "", room.table.id))
        }
        if (rooms.isEmpty()) menu.set(slots[slots.size / 2], items.menuIcon(Material.PAPER, "暂无牌桌",
            listOf(items.description("请联系管理员设置牌桌"))))
        if (lastPage > 0) {
            pageButton(menu, '<', "上一页", menu.page - 1, menu.page > 0)
            pageButton(menu, '>', "下一页", menu.page + 1, menu.page < lastPage)
        }
    }

    fun refresh(session: GameSession) {
        Bukkit.getOnlinePlayers().forEach { player ->
            val menu = player.openInventory.topInventory.holder as? MenuHolder ?: return@forEach
            if (menu.tableId == session.table.id) render(menu, session)
        }
    }

    private fun render(menu: MenuHolder, room: GameSession) {
        val view = room.view(null)
        val players = view.players.sortedBy { it.participant.seat }
        val entries = "TTTTTTTTT"
        val lastPage = (players.size - 1).coerceAtLeast(0) / entries.length
        menu.page = menu.page.coerceIn(0, lastPage)
        menu.map(
            "=========",
            entries,
            if (lastPage == 0) "SMF======" else "SMF====<>",
        )
        frame(menu)
        val slots = menu.getSlots('T')
        players.drop(menu.page * slots.size).take(slots.size).forEachIndexed { index, player ->
            menu.set(slots[index], playerHead(room.table.id, player, room.active))
        }
        val skin = plugin.skin(menu.viewer, room.table.skin)
        if (plugin.settings.skins.size > 1) {
            button(menu, 'S', GameControl("skin", "牌面", "", description = listOf("当前: ${skinName(skin)}")))
        }
        if (plugin.settings.music) {
            button(menu, 'M', GameControl("music", "音乐", "", description = listOf(
                "当前: ${if (plugin.audio.isMuted(menu.viewer)) "关闭" else "开启"}",
            )))
        }
        val own = room.participants.any { it.id == menu.viewer }
        val emptySeats = room.seatCount - room.participants.size
        if (room.supportsBots && own && !room.active && room.table.bet == 0.0
            && emptySeats > 0 && plugin.botAI.available && plugin.botNames.canPick(room.participants, emptySeats)) {
            button(menu, 'F', GameControl("bot_fill", "补齐机器人", "", description = listOf("原版人偶入座, 自动准备", "机器人只参与免费对局")))
        }
        if (lastPage > 0) {
            pageButton(menu, '<', "上一页", menu.page - 1, menu.page > 0)
            pageButton(menu, '>', "下一页", menu.page + 1, menu.page < lastPage)
        }
    }

    private fun playerHead(tableId: String, view: PlayerView, active: Boolean): ItemStack {
        val participant = view.participant
        val lore = buildList {
            add(items.parameter("状态", when {
                view.current -> "正在操作"
                active -> "等待回合"
                view.ready -> "准备就绪"
                else -> "未准备"
            }))
            if (active) {
                if (view.role !in setOf("玩家", "机器人")) add(items.parameter("身份", if (view.role == "UNO") "UNO!" else view.role))
                add(items.parameter("手牌", "${view.count} 张"))
            }
            add(items.description(if (participant.bot) "机器人" else "玩家")
                .color(if (participant.bot) NamedTextColor.GOLD else NamedTextColor.YELLOW))
        }
        val profile = if (participant.bot) plugin.renderer.botProfile(tableId, participant.id)
            else Bukkit.getPlayer(participant.id)?.let { ResolvableProfile.resolvableProfile(it.playerProfile) }
        return items.menuIcon(Material.PLAYER_HEAD, participant.name, lore).apply {
            profile?.let { setData(DataComponentTypes.PROFILE, it) }
        }
    }

    private fun frame(menu: MenuHolder) {
        menu.contents.clear()
        menu.actions.clear()
        val background = items.decoration(Material.BLACK_STAINED_GLASS_PANE)
        "=SMF<>".forEach { key -> menu.set(key, background) }
    }

    private fun pageButton(menu: MenuHolder, key: Char, title: String, page: Int, enabled: Boolean) {
        if (enabled) button(menu, key, GameControl("page", title, "", page.toString()))
    }

    private fun button(menu: MenuHolder, key: Char, control: GameControl) {
        val material = when (control.action) {
            "close" -> Material.BARRIER
            "bot_fill" -> Material.ARMOR_STAND
            "page" -> Material.ARROW
            "skin" -> Material.ITEM_FRAME
            "music" -> Material.MUSIC_DISC_CAT
            else -> Material.PAPER
        }
        val lore = control.description.map { line ->
            val separator = line.indexOf(": ")
            if (separator > 0) items.parameter(line.substring(0, separator), line.substring(separator + 2)) else items.description(line)
        } + listOf(items.description(""), items.hint(when (control.action) {
            "skin" -> "单击切换牌面"
            "music" -> "单击切换音乐"
            else -> "单击${control.title}"
        }))
        menu.set(key, items.menuIcon(material, control.title, lore), control)
    }

    private fun skinName(skin: String): String = when (skin) { "classic" -> "经典"; "jade" -> "青玉"; else -> skin }
    private fun bet(value: Double): String = if (value == 0.0) "免费" else String.format(Locale.ROOT, "%.2f", value)

    @EventHandler
    fun click(event: InventoryClickEvent) {
        val menu = event.view.topInventory.holder as? MenuHolder ?: return
        event.isCancelled = true
        val player = event.whoClicked as Player
        val control = menu.actions[event.rawSlot] ?: return
        submitTask(delay = 1) {
            if (!player.isOnline || player.openInventory.topInventory !== menu.contents) return@submitTask
            plugin.attempt(player) {
                when (control.action) {
                    "settings" -> open(player, plugin.tables.room(control.argument!!))
                    "page" -> if (menu.tableId == null) lobby(player, control.argument!!.toInt()) else {
                        menu.page = control.argument!!.toInt()
                        render(menu, plugin.tables.room(menu.tableId))
                    }
                    "skin" -> {
                        val room = plugin.tables.room(menu.tableId!!)
                        val choices = plugin.settings.skins.keys.toList()
                        val current = plugin.skin(player.uniqueId, room.table.skin)
                        plugin.setSkin(player, choices[(choices.indexOf(current) + 1) % choices.size])
                        render(menu, room)
                    }
                    "music" -> {
                        plugin.audio.toggle(player)
                        render(menu, plugin.tables.room(menu.tableId!!))
                    }
                    "close" -> player.closeInventory()
                    "bot_fill" -> plugin.tables.bots(player, "fill")
                }
            }
        }
    }

    @EventHandler
    fun drag(event: InventoryDragEvent) { if (event.view.topInventory.holder is MenuHolder) event.isCancelled = true }
    fun close(player: Player) { if (player.openInventory.topInventory.holder is MenuHolder) player.closeInventory() }
}
