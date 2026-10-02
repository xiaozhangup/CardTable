package me.xiaozhangup.cardtable

import me.xiaozhangup.crab.CrabPlugin
import me.xiaozhangup.cardtable.ai.ExternalCardAI
import me.xiaozhangup.cardtable.api.GameRegistry
import me.xiaozhangup.cardtable.game.doudizhu.DoudizhuProvider
import me.xiaozhangup.cardtable.game.uno.UnoProvider
import me.xiaozhangup.cardtable.table.*
import me.xiaozhangup.cardtable.ui.*
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import org.bukkit.event.server.PluginDisableEvent

class CardTablePlugin : CrabPlugin(), Listener {
    lateinit var settings: Settings
        private set
    lateinit var economy: EconomyService
        private set
    lateinit var tables: CardTableService
        private set
    lateinit var storage: TableStorage
        private set
    lateinit var renderer: WorldTableRenderer
        private set
    lateinit var menus: TableMenus
        private set
    lateinit var items: ItemProvider
        private set
    lateinit var audio: TableAudio
        private set
    lateinit var seating: SeatingHook
        private set
    lateinit var botAI: ExternalCardAI
        private set
    private lateinit var preferences: YamlConfiguration

    override fun enable() {
        saveDefaultConfig()
        if (!dataFolder.resolve("music.yml").exists()) saveResource("music.yml", false)
        settings = Settings.read(config)
        preferences = YamlConfiguration.loadConfiguration(dataFolder.resolve("preferences.yml"))
        economy = EconomyService(this)
        botAI = ExternalCardAI(this).also { it.start() }
        tables = CardTableService(this)
        items = ItemProvider(this)
        menus = TableMenus(this)
        renderer = WorldTableRenderer(this)
        audio = TableAudio(this)
        seating = SeatingHook(this)
        storage = TableStorage(this)
        tables.register(DoudizhuProvider(), this)
        tables.register(UnoProvider(), this)
        Bukkit.getServicesManager().register(GameRegistry::class.java, tables, this, ServicePriority.Normal)
        listOf(this, menus, renderer).forEach { Bukkit.getPluginManager().registerEvents(it, this) }
        if (Bukkit.getPluginManager().isPluginEnabled("CraftEngine")) CraftEngineRefresh.register(this)
        TableCommands(this).register()
        logger.info("公共牌桌服务已注册，内置斗地主与 UNO。游戏扩展可通过 Bukkit GameRegistry 注册。")
    }

    override fun active() {
        loadTables()
        crab.submitTask(period = 20) { tables.tick(); economy.tick() }
        crab.submitTask(period = 1) { audio.tick(); renderer.tick() }
    }

    fun loadTables() {
        storage.load().forEach { table ->
            try { tables.add(table) } catch (error: IllegalArgumentException) { logger.warning("牌桌 ${table.id} 未加载：${error.message}，原配置已保留。") }
        }
        logger.info("已加载 ${tables.rooms.size} 张牌桌。")
    }

    fun reloadSettings() {
        require(tables.rooms.values.all { it.participants.isEmpty() }) { "请等所有玩家离桌后再重载。" }
        reloadConfig()
        val next = Settings.read(config)
        tables.shutdown()
        seating.shutdown()
        botAI.close()
        botAI = ExternalCardAI(this).also { it.start() }
        settings = next
        seating = SeatingHook(this)
        audio = TableAudio(this)
        loadTables()
    }

    override fun disable() {
        if (::tables.isInitialized) tables.shutdown()
        if (::renderer.isInitialized) renderer.shutdown()
        if (::seating.isInitialized) seating.shutdown()
        if (::economy.isInitialized) economy.shutdown()
        if (::botAI.isInitialized) botAI.close()
        Bukkit.getServicesManager().unregisterAll(this)
    }

    fun tell(sender: CommandSender, text: String) = sender.sendMessage(
        Component.text("[", NamedTextColor.DARK_GRAY)
            .append(Component.text("牌桌", TextColor.color(0x9BC7B6)))
            .append(Component.text("] ", NamedTextColor.DARK_GRAY))
            .append(LegacyComponentSerializer.legacySection().deserialize(text).colorIfAbsent(TextColor.color(0xE9F1EE)))
    )
    fun attempt(sender: CommandSender, action: () -> Unit) { try { action() } catch (error: IllegalArgumentException) { tell(sender, error.message ?: "参数无效。") } }

    fun skin(playerId: java.util.UUID, tableDefault: String): String = preferences.getString(playerId.toString())?.takeIf { it in settings.skins } ?: tableDefault
    fun setSkin(player: Player, name: String) {
        require(name in settings.skins) { "未知牌面：$name。" }
        preferences.set(player.uniqueId.toString(), name)
        preferences.save(dataFolder.resolve("preferences.yml"))
        tables.tableOf(player.uniqueId)?.let { room -> renderer.invalidate(room.table.id); renderer.refresh(room); menus.refresh(room) }
        tell(player, "已切换为 $name 牌面。")
    }
    fun cycleSkin(player: Player) {
        val choices = settings.skins.keys.toList()
        val current = skin(player.uniqueId, tables.tableOf(player.uniqueId)?.table?.skin ?: settings.defaultSkin)
        setSkin(player, choices[(choices.indexOf(current) + 1) % choices.size])
    }

    @EventHandler
    fun join(event: PlayerJoinEvent) { economy.retry(event.player.uniqueId) }
    @EventHandler
    fun quit(event: PlayerQuitEvent) { tables.leave(event.player); audio.forget(event.player.uniqueId); renderer.forget(event.player.uniqueId) }
    @EventHandler
    fun death(event: PlayerDeathEvent) { tables.leave(event.entity) }
    @EventHandler
    fun dependencyDisabled(event: PluginDisableEvent) { if (event.plugin != this) tables.unregisterOwnedBy(event.plugin) }
}
