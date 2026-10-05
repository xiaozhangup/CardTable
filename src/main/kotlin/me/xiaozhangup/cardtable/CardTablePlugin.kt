package me.xiaozhangup.cardtable

import me.xiaozhangup.crab.CrabPlugin
import me.xiaozhangup.cardtable.ai.ExternalCardAI
import me.xiaozhangup.cardtable.api.GameRegistry
import me.xiaozhangup.cardtable.game.doudizhu.DoudizhuProvider
import me.xiaozhangup.cardtable.game.uno.UnoProvider
import me.xiaozhangup.cardtable.table.*
import me.xiaozhangup.cardtable.ui.*
import net.kyori.adventure.text.Component
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import me.xiaozhangup.crab.configuration.Configuration
import me.xiaozhangup.cardtable.util.InputException
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.cardtable.util.sendTableMessage
import me.xiaozhangup.cardtable.util.ext.info
import me.xiaozhangup.cardtable.util.ext.warning
import me.xiaozhangup.cardtable.util.ext.submitTask
import me.xiaozhangup.cardtable.util.ext.releaseResourceFile
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.entity.Player
import org.bukkit.plugin.ServicePriority
import org.bukkit.event.server.PluginDisableEvent
import java.util.UUID

class CardTablePlugin : CrabPlugin(), Listener {
    init { instance = this }

    internal lateinit var configuration: Configuration
        private set
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
    internal lateinit var botNames: BotNames
        private set
    internal lateinit var botSkins: BotSkins
        private set
    private val skinPreferences = mutableMapOf<UUID, String>()

    override fun enable() {
        configuration = crab.configurations.load()
        releaseResourceFile("music.yml")
        settings = Settings.read(configuration)
        economy = EconomyService()
        botAI = ExternalCardAI(this).also { it.start() }
        val nameMCCache = NameMCCache()
        botNames = BotNames(nameMCCache).also { it.start() }
        botSkins = BotSkins(nameMCCache).also { it.start() }
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
        info("Registered the table service with Dou Dizhu and UNO. Extensions can register through GameRegistry.")
    }

    override fun active() {
        loadTables()
        submitTask(period = 20) { tables.tick(); economy.tick() }
        submitTask(period = 1) { audio.tick(); renderer.tick() }
    }

    fun loadTables() {
        storage.load().forEach { table ->
            try { tables.add(table) } catch (error: IllegalArgumentException) {
                warning("Failed to load table ${table.id}; its saved configuration was retained.", error.stackTraceToString())
            }
        }
        info("Loaded ${tables.rooms.size} tables.")
    }

    fun reloadSettings() {
        requireInput(tables.rooms.values.all { it.participants.isEmpty() }, "All players must leave their tables before reloading.") { "请等所有玩家离桌后再重载" }
        configuration.reload()
        val next = Settings.read(configuration)
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
        if (::botNames.isInitialized) botNames.close()
        if (::botSkins.isInitialized) botSkins.close()
        if (::tables.isInitialized) tables.shutdown()
        if (::renderer.isInitialized) renderer.shutdown()
        if (::seating.isInitialized) seating.shutdown()
        if (::economy.isInitialized) economy.shutdown()
        if (::botAI.isInitialized) botAI.close()
        Bukkit.getServicesManager().unregisterAll(this)
    }

    fun tell(sender: CommandSender, text: String) = sendTableMessage(sender, text)
    fun attempt(sender: CommandSender, action: () -> Unit) {
        try { action() } catch (error: IllegalArgumentException) {
            if (sender is Player) tell(sender, (error as? InputException)?.playerMessage ?: error.message ?: "参数无效")
            else sender.sendMessage(Component.text("[CardTable] ${error.message ?: "Invalid arguments."}"))
        }
    }

    fun skin(playerId: UUID, tableDefault: String): String = skinPreferences[playerId]?.takeIf { it in settings.skins } ?: tableDefault
    fun setSkin(player: Player, name: String) {
        require(name in settings.skins) { "未知牌面: $name" }
        skinPreferences[player.uniqueId] = name
        tables.tableOf(player.uniqueId)?.let { room -> renderer.invalidate(room.table.id); renderer.refresh(room); menus.refresh(room) }
        tell(player, "已切换为 $name 牌面")
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

    companion object {
        internal lateinit var instance: CardTablePlugin
            private set
    }
}
