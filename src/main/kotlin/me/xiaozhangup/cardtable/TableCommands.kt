package me.xiaozhangup.cardtable

import me.xiaozhangup.crab.command.Notify
import me.xiaozhangup.crab.command.PermissionDefault
import me.xiaozhangup.crab.command.component.CommandComponent
import me.xiaozhangup.cardtable.table.TableConfig
import me.xiaozhangup.cardtable.table.TableStorage
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class TableCommands(private val plugin: CardTablePlugin) {
    fun register() = plugin.crab.command("cardtable", aliases = listOf("ct", "ddz"), permission = "cardtable.play",
        permissionDefault = PermissionDefault.TRUE, notify = Notify("牌桌", "#9bc7b6"), description = "实体牌桌与牌类游戏") {
        execute<CommandSender> { sender, _, _ ->
            if (sender is Player) plugin.tables.tableOf(sender.uniqueId)?.let { plugin.menus.open(sender, it) } ?: plugin.menus.lobby(sender)
            else plugin.tell(sender, "/ct list | games | createat <桌名> <世界> <x> <y> <z> <游戏> | delete <桌名>")
        }
        literal("list") { execute<CommandSender> { sender, _, _ ->
            plugin.tell(sender, plugin.tables.rooms.values.joinToString("；") { "${it.table.id}[${it.table.game}] ${it.participants.size}/${it.seatCount} ${it.view(null).status}" }.ifEmpty { "尚未设置牌桌。" })
        } }
        literal("games") { execute<CommandSender> { sender, _, _ -> plugin.tell(sender, plugin.tables.providers.joinToString("；") { "${it.id}：${it.displayName}" }) } }
        literal("join") { tableArgument { execute<Player> { player, _, id -> plugin.attempt(player) { plugin.tables.join(player, id) } } } }
        literal("open") {
            execute<Player> { player, _, _ -> plugin.tables.tableOf(player.uniqueId)?.let { plugin.menus.open(player, it) } ?: plugin.menus.lobby(player) }
            tableArgument { execute<Player> { player, _, id -> plugin.attempt(player) { plugin.menus.open(player, plugin.tables.room(id)) } } }
        }
        listOf("ready", "play", "pass", "hint", "draw", "uno", "catch", "challenge", "accept").forEach { action -> literal(action) {
            execute<Player> { player, _, _ -> plugin.attempt(player) { plugin.tables.act(player, action) } }
        } }
        literal("leave") { execute<Player> { player, _, _ -> plugin.attempt(player) { plugin.tables.leave(player) } } }
        literal("bot") {
            execute<Player> { player, _, _ -> plugin.attempt(player) { plugin.tables.bots(player, "fill") } }
            dynamic("action") { suggestion<CommandSender> { _, _ -> listOf("add", "fill", "remove", "clear") }
                execute<Player> { player, _, action -> plugin.attempt(player) { plugin.tables.bots(player, action) } }
            }
        }
        literal("bid") { dynamic("score") { suggestion<CommandSender> { _, _ -> listOf("0", "1", "2", "3") }
            execute<Player> { player, _, score -> plugin.attempt(player) { plugin.tables.act(player, "bid", score) } }
        } }
        literal("color") { dynamic("color") { suggestion<CommandSender> { _, _ -> listOf("red", "yellow", "green", "blue") }
            execute<Player> { player, _, color -> plugin.attempt(player) { plugin.tables.act(player, "color", color) } }
        } }
        literal("select") { dynamic("tokens") {
            execute<Player> { player, _, tokens -> plugin.attempt(player) {
                val room = plugin.tables.tableOf(player.uniqueId) ?: throw IllegalArgumentException("请先加入牌桌。")
                val selected = tokens.split(',')
                val handTokens = room.view(player.uniqueId).hand.map { it.token }.toSet()
                require(selected.distinct().size == selected.size && selected.all { it in handTokens }) { "使用 /ct select <牌编号,牌编号>，编号必须来自自己的手牌。" }
                selected.forEach { plugin.tables.act(player, "select", it) }
            } }
        } }
        literal("music") { execute<Player> { player, _, _ -> plugin.audio.toggle(player) } }
        literal("skin") { dynamic("skin") { suggestion<CommandSender> { _, _ -> plugin.settings.skins.keys.toList() }
            execute<Player> { player, _, skin -> plugin.attempt(player) { plugin.setSkin(player, skin) } }
        } }
        literal("create", permission = "cardtable.admin") { dynamic("table") {
            execute<Player> { player, _, id -> plugin.attempt(player) { create(id, player.location, "doudizhu"); plugin.tell(player, "已创建 $id。点击椅子或 /ct join $id 入座。") } }
            dynamic("game") { suggestion<CommandSender> { _, _ -> plugin.tables.providers.map { it.id } }
                execute<Player> { player, context, game -> plugin.attempt(player) { create(context["table"], player.location, game); plugin.tell(player, "牌桌已创建。") } }
            }
        } }
        literal("createat", permission = "cardtable.admin") {
            dynamic("table") { dynamic("world") { suggestion<CommandSender> { _, _ -> Bukkit.getWorlds().map { it.name } }
                dynamic("x") { dynamic("y") { dynamic("z") { dynamic("game") {
                    suggestion<CommandSender> { _, _ -> plugin.tables.providers.map { it.id } }
                    execute<CommandSender> { sender, context, game -> plugin.attempt(sender) {
                        val world = Bukkit.getWorld(context["world"]) ?: throw IllegalArgumentException("找不到世界。")
                        val xyz = listOf("x", "y", "z").map { context[it].toDoubleOrNull() ?: throw IllegalArgumentException("坐标必须是数字。") }
                        require(xyz.all { it.isFinite() } && kotlin.math.abs(xyz[0]) < 29_999_980 && kotlin.math.abs(xyz[2]) < 29_999_980 && xyz[1] in world.minHeight.toDouble()..world.maxHeight.toDouble()) { "坐标超出世界范围。" }
                        create(context["table"], Location(world, xyz[0], xyz[1], xyz[2]), game)
                        plugin.tell(sender, "已创建 ${context["table"]}。")
                    } }
                } } } }
            } }
        }
        literal("delete", permission = "cardtable.admin") { tableArgument {
            execute<CommandSender> { sender, _, id -> plugin.attempt(sender) { plugin.tables.remove(id); plugin.storage.delete(id); plugin.tell(sender, "已移除 $id，未完局预扣款已退回。") } }
        } }
        literal("bet", permission = "cardtable.admin") { tableArgument { dynamic("amount") {
            execute<CommandSender> { sender, context, amount -> plugin.attempt(sender) {
                val value = amount.toDoubleOrNull() ?: throw IllegalArgumentException("底注必须为数字。")
                require(value.isFinite() && value in 0.0..1_000_000.0) { "底注必须在0..1000000之间。" }
                update(context["table"]) { it.copy(bet = value) }; plugin.tell(sender, "底注已设为 $value。")
            } }
        } } }
        literal("game", permission = "cardtable.admin") { tableArgument { dynamic("game") { suggestion<CommandSender> { _, _ -> plugin.tables.providers.map { it.id } }
            execute<CommandSender> { sender, context, game -> plugin.attempt(sender) {
                require(plugin.tables.provider(game) != null) { "该游戏尚未注册。" }
                update(context["table"]) { it.copy(game = game, options = emptyMap()) }; plugin.tell(sender, "牌桌游戏已设为 $game。")
            } }
        } } }
        literal("option", permission = "cardtable.admin") { tableArgument { dynamic("key") { dynamic("value") {
            execute<CommandSender> { sender, context, value -> plugin.attempt(sender) {
                val key = context["key"]
                require(key.matches(Regex("[a-z0-9_-]{1,32}")) && value.length <= 128) { "选项键格式或值长度无效。" }
                update(context["table"]) { it.copy(options = it.options + (key to value)) }; plugin.tell(sender, "游戏选项 $key=$value 已保存。")
            } }
        } } } }
        literal("setskin", permission = "cardtable.admin") { tableArgument { dynamic("skin") { suggestion<CommandSender> { _, _ -> plugin.settings.skins.keys.toList() }
            execute<CommandSender> { sender, context, skin -> plugin.attempt(sender) {
                require(skin in plugin.settings.skins) { "找不到牌面 $skin。" }
                val room = plugin.tables.room(context["table"])
                room.table.skin = skin; plugin.storage.put(room.table)
                plugin.renderer.invalidate(room.table.id); plugin.renderer.refresh(room); plugin.menus.refresh(room)
                plugin.tell(sender, "默认牌面已更新。")
            } }
        } } }
        literal("time", permission = "cardtable.admin") { tableArgument { dynamic("seconds") {
            execute<CommandSender> { sender, context, seconds -> plugin.attempt(sender) {
                val value = seconds.toIntOrNull() ?: throw IllegalArgumentException("时间必须为整数。")
                require(value in 5..300) { "时间必须在5..300秒之间。" }
                update(context["table"]) { it.copy(turnSeconds = value) }; plugin.tell(sender, "回合时间已更新。")
            } }
        } } }
        literal("reload", permission = "cardtable.admin") { execute<CommandSender> { sender, _, _ -> plugin.attempt(sender) { plugin.reloadSettings(); plugin.tell(sender, "配置及牌桌已重载。") } } }
    }

    private fun CommandComponent.tableArgument(block: me.xiaozhangup.crab.command.component.CommandComponentDynamic.() -> Unit) = dynamic("table") {
        suggestion<CommandSender> { _, _ -> plugin.tables.rooms.keys.toList() }
        block()
    }

    private fun create(id: String, location: Location, game: String) {
        TableStorage.validateId(id)
        val table = TableConfig(id, location.clone().apply { pitch = 0f }, 0.0, plugin.settings.defaultSkin, plugin.settings.turnSeconds, game)
        plugin.tables.add(table)
        plugin.storage.put(table)
    }

    private fun update(id: String, change: (TableConfig) -> TableConfig) {
        val room = plugin.tables.room(id)
        require(room.participants.isEmpty()) { "请等玩家全部离桌后修改游戏规则或底注。" }
        val next = change(room.table)
        plugin.tables.provider(next.game)!!.validate(next)
        plugin.tables.remove(id)
        try {
            plugin.tables.add(next)
        } catch (error: IllegalArgumentException) {
            // Enlarging a table can fail the shared clearance check. Keep its existing room usable.
            plugin.tables.add(room.table)
            throw error
        }
        plugin.storage.put(next)
    }
}
