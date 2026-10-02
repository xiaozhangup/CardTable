package me.xiaozhangup.cardtable.ui

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.GameControl
import me.xiaozhangup.cardtable.api.GameSession
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
import java.util.Locale
import java.util.UUID

/** Containers provide settings only; play actions belong to the world table. */
class TableMenus(private val plugin: CardTablePlugin) : Listener {
    private class Menu(val tableId: String?, val viewer: UUID, var page: Int = 0) : InventoryHolder {
        lateinit var contents: Inventory
        val actions = mutableMapOf<Int, GameControl>()
        override fun getInventory(): Inventory = contents
    }
    private val items get() = plugin.items

    fun open(player: Player, session: GameSession) {
        val menu = Menu(session.table.id, player.uniqueId)
        menu.contents = Bukkit.createInventory(menu, 27, Component.text("牌桌设置  ${session.table.id}", NamedTextColor.DARK_GRAY))
        render(menu, session)
        player.openInventory(menu.contents)
    }

    fun lobby(player: Player, page: Int = 0) {
        val menu = Menu(null, player.uniqueId, page)
        menu.contents = Bukkit.createInventory(menu, 27, Component.text("牌桌设置列表", NamedTextColor.DARK_GRAY))
        renderLobby(menu)
        player.openInventory(menu.contents)
    }

    private fun renderLobby(menu: Menu) {
        frame(menu)
        val rooms = plugin.tables.rooms.values.toList()
        val lastPage = ((rooms.size - 1).coerceAtLeast(0) / 9)
        menu.page = menu.page.coerceIn(0, lastPage)
        info(menu, 0, "牌桌设置列表", Material.MAP, listOf(
            items.parameter("牌桌", "${rooms.size} 张"),
            items.description("选择牌桌后查看桌况与调整个人设置"),
            items.description("请在世界牌桌空位处入座")
        ))
        plugin.tables.tableOf(menu.viewer)?.let { room ->
            button(menu, 8, GameControl("settings", "返回当前桌设置", "", room.table.id,
                listOf("当前牌桌: ${room.table.id}")))
        } ?: button(menu, 8, GameControl("close", "返回世界", ""))
        rooms.drop(menu.page * 9).take(9).forEachIndexed { index, room ->
            val view = room.view(null)
            menu.contents.setItem(9 + index, items.icon("doudizhu:table", room.table.id, listOf(
                items.parameter("玩法", plugin.tables.provider(room.table.game)!!.displayName),
                items.parameter("人数", "${room.participants.size}/${room.seatCount}"),
                items.parameter("状态", view.status), items.parameter("底注", bet(room.table.bet)),
                items.parameter("本桌牌面", skinName(room.table.skin)), items.description(""),
                items.hint("单击打开本桌设置")
            ), Material.OAK_SLAB))
            menu.actions[9 + index] = GameControl("settings", "", "", room.table.id)
        }
        if (rooms.isEmpty()) info(menu, 13, "暂无牌桌", Material.PAPER, listOf(items.description("请联系管理员设置牌桌")))
        info(menu, 18, "设置列表页码", Material.PAPER, listOf(items.parameter("当前", "${menu.page + 1}/${lastPage + 1}")))
        button(menu, 22, GameControl("close", "返回世界", "", description = listOf("关闭设置界面，保留当前座位")))
        pageButton(menu, 25, "上一页", menu.page - 1, menu.page > 0)
        pageButton(menu, 26, "下一页", menu.page + 1, menu.page < lastPage)
    }

    fun refresh(session: GameSession) {
        Bukkit.getOnlinePlayers().forEach { player ->
            val menu = player.openInventory.topInventory.holder as? Menu ?: return@forEach
            if (menu.tableId == session.table.id) render(menu, session)
        }
    }

    private fun render(menu: Menu, room: GameSession) {
        frame(menu)
        val view = room.view(null)
        val own = room.participants.any { it.id == menu.viewer }
        val skin = plugin.skin(menu.viewer, room.table.skin)
        info(menu, 0, "本桌情况", Material.OAK_SLAB, buildList {
            add(items.parameter("牌桌", room.table.id))
            add(items.parameter("玩法", plugin.tables.provider(room.table.game)!!.displayName))
            add(items.parameter("底注", bet(room.table.bet)))
            add(items.parameter("状态", view.status))
            if (view.remainingSeconds > 0) add(items.parameter("剩余", "${view.remainingSeconds} 秒"))
            add(items.description(if (own) "你已入座这张牌桌" else "请到世界牌桌的空位处入座"))
        })
        info(menu, 4, "本桌玩家", Material.PLAYER_HEAD, view.players.map { player ->
            items.parameter(player.participant.name, "${player.role}  ${player.count} 张  ${
                if (player.current) "正在操作" else if (room.active) "等待回合" else if (player.ready) "已准备" else "未准备"}")
        }.ifEmpty { listOf(items.description("暂无玩家")) })
        button(menu, 8, GameControl("lobby", "返回设置列表", ""))

        button(menu, 10, GameControl("skin", "切换个人牌面", "doudizhu:skin", description = listOf("当前牌面: ${skinName(skin)}", "为你的手牌选择显示样式")))
        button(menu, 12, GameControl("music", "牌桌音乐", "doudizhu:music", description = listOf("切换你当前在线期间的牌桌音乐")))
        if (room.supportsBots && own) {
            if (room.active) info(menu, 14, "人机席位", Material.GRAY_DYE, listOf(items.description("本局结束后可调整人机席位")))
            else {
                if (room.participants.size < room.seatCount) {
                    if (room.table.bet == 0.0) button(menu, 14, GameControl("bot_fill", "补齐人机", "", description = listOf("原版人偶入座，自动准备", "人机只参与免费对局")))
                    else info(menu, 14, "本桌无法添加人机", Material.GRAY_DYE, listOf(items.description("人机只支持底注为 0 的免费桌")))
                }
                if (room.participants.any { it.bot }) button(menu, 16, GameControl("bot_clear", "移除人机", "", description = listOf("移除本桌所有人机")))
            }
        }
        info(menu, 18, "世界牌桌操作", Material.BOOK, listOf(
            items.description("选牌与出牌均在世界牌桌操作"),
            items.description("使用桌面全息按钮完成回合操作")
        ))
        button(menu, 22, GameControl("close", "返回世界牌桌", "", description = listOf("关闭设置界面，保留当前座位")))
    }

    private fun frame(menu: Menu) {
        menu.contents.clear()
        menu.actions.clear()
        (0..8).plus(18..26).forEach { menu.contents.setItem(it, items.decoration(Material.BLACK_STAINED_GLASS_PANE)) }
        (9..17).forEach { menu.contents.setItem(it, items.decoration(Material.GRAY_STAINED_GLASS_PANE)) }
    }

    private fun info(menu: Menu, slot: Int, title: String, material: Material, lore: List<Component>) {
        menu.contents.setItem(slot, items.icon("", title, lore, material))
    }

    private fun pageButton(menu: Menu, slot: Int, title: String, page: Int, enabled: Boolean) {
        if (enabled) button(menu, slot, GameControl("page", title, "", page.toString()))
        else info(menu, slot, if (title.startsWith("上一")) "没有上一页" else "没有下一页", Material.FEATHER,
            listOf(items.description(if (title.startsWith("上一")) "已到第一页" else "已到最后一页")))
    }

    private fun button(menu: Menu, slot: Int, control: GameControl) {
        val material = when (control.action) {
            "close" -> Material.BARRIER
            "bot_clear" -> Material.ORANGE_DYE
            "bot_fill" -> Material.ARMOR_STAND
            "lobby", "settings" -> Material.CLOCK
            "page" -> Material.ARROW
            "skin" -> Material.ITEM_FRAME
            "music" -> Material.NOTE_BLOCK
            else -> Material.PAPER
        }
        val lore = control.description.map { line ->
            val separator = line.indexOf(": ")
            if (separator > 0) items.parameter(line.substring(0, separator), line.substring(separator + 2)) else items.description(line)
        } + listOf(items.description(""), items.hint(if (control.action == "music") "单击切换音乐" else "单击${control.title}"))
        menu.contents.setItem(slot, items.icon(control.icon, control.title, lore, material,
            if (control.action == "bot_clear") NamedTextColor.RED else NamedTextColor.WHITE))
        menu.actions[slot] = control
    }

    private fun skinName(skin: String): String = when (skin) { "classic" -> "经典"; "jade" -> "青玉"; else -> skin }
    private fun bet(value: Double): String = if (value == 0.0) "免费" else String.format(Locale.ROOT, "%.2f", value)

    @EventHandler
    fun click(event: InventoryClickEvent) {
        val menu = event.view.topInventory.holder as? Menu ?: return
        event.isCancelled = true
        val player = event.whoClicked as Player
        val control = menu.actions[event.rawSlot] ?: return
        plugin.crab.submitTask(delay = 1) {
            if (!player.isOnline || player.openInventory.topInventory !== menu.contents) return@submitTask
            plugin.attempt(player) {
                when (control.action) {
                    "settings" -> open(player, plugin.tables.room(control.argument!!))
                    "page" -> lobby(player, control.argument!!.toInt())
                    "lobby" -> lobby(player)
                    "skin" -> {
                        val room = plugin.tables.room(menu.tableId!!)
                        val choices = plugin.settings.skins.keys.toList()
                        val current = plugin.skin(player.uniqueId, room.table.skin)
                        plugin.setSkin(player, choices[(choices.indexOf(current) + 1) % choices.size])
                        render(menu, room)
                    }
                    "music" -> plugin.audio.toggle(player)
                    "close" -> player.closeInventory()
                    "bot_fill" -> plugin.tables.bots(player, "fill")
                    "bot_clear" -> plugin.tables.bots(player, "clear")
                }
            }
        }
    }

    @EventHandler
    fun drag(event: InventoryDragEvent) { if (event.view.topInventory.holder is Menu) event.isCancelled = true }
    fun close(player: Player) { if (player.openInventory.topInventory.holder is Menu) player.closeInventory() }
}
