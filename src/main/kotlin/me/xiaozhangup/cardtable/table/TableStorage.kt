package me.xiaozhangup.cardtable.table

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.util.ext.warning
import me.xiaozhangup.cardtable.util.ext.getDataFolder
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.crab.configuration.Configuration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.World
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TableStorage(private val plugin: CardTablePlugin) {
    private val file = getDataFolder().resolve("tables.yml")
    private var yaml = Configuration.empty(concurrent = false)

    fun load(world: World? = null): List<TableConfig> {
        yaml = if (file.exists()) Configuration.loadFromFile(file, concurrent = false) else Configuration.empty(concurrent = false)
        return yaml.getConfigurationSection("tables")?.getKeys(false)?.mapNotNull { id ->
            try {
                validateId(id)
                val section = requireNotNull(yaml.getConfigurationSection("tables.$id")) { "Table configuration must be a section." }
                val worldName = requireNotNull(section.getString("world")) { "Table world is missing." }
                if (world != null && world.name != worldName) return@mapNotNull null
                // Unloaded worlds are deferred until WorldLoadEvent; their configuration remains saved.
                val tableWorld = world ?: Bukkit.getWorld(worldName) ?: return@mapNotNull null
                val bet = section.getDouble("bet")
                require(bet.isFinite() && bet in 0.0..1_000_000.0) { "Base bet must be finite and between 0 and 1000000." }
                val seconds = section.getInt("turn-seconds")
                require(seconds in 5..300) { "Turn duration must be between 5 and 300 seconds." }
                val skin = requireNotNull(section.getString("skin")) { "Table skin is missing." }
                require(skin in plugin.settings.skins) { "Unknown card skin '$skin'." }
                val options = section.getConfigurationSection("options")?.getKeys(false)
                    ?.associateWith { section.getString("options.$it")!! }.orEmpty()
                val coordinates = listOf(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"))
                require(coordinates.all { it.isFinite() } && kotlin.math.abs(coordinates[0]) <= 29_999_980 && kotlin.math.abs(coordinates[2]) <= 29_999_980) { "Table coordinates are invalid." }
                TableConfig(id, Location(tableWorld, coordinates[0], coordinates[1], coordinates[2], section.getDouble("yaw").toFloat(), 0f),
                    bet, skin, seconds, requireNotNull(section.getString("game")) { "Table game is missing." }, options)
            } catch (error: IllegalArgumentException) {
                warning("Failed to load table '$id'; its configuration has been preserved.", error.stackTraceToString())
                null
            }
        }.orEmpty()
    }

    fun put(table: TableConfig) {
        val path = "tables.${table.id}"
        yaml.set(path, null)
        mapOf("world" to table.center.world.name, "x" to table.center.x, "y" to table.center.y,
            "z" to table.center.z, "yaw" to table.center.yaw, "bet" to table.bet,
            "skin" to table.skin, "turn-seconds" to table.turnSeconds, "game" to table.game,
            "options" to table.options).forEach { (key, value) -> yaml.set("$path.$key", value) }
        save()
    }

    fun delete(id: String) { yaml.set("tables.$id", null); save() }

    private fun save() {
        val staging = file.resolveSibling("tables.yml.tmp")
        yaml.saveToFile(staging)
        Files.move(staging.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        private val ID_PATTERN = Regex("[a-z0-9_-]{1,32}")

        fun validateId(id: String) = requireInput(id.matches(ID_PATTERN), "Table IDs must contain 1 to 32 lowercase letters, digits, underscores or hyphens.") {
            "牌桌名只能包含小写字母, 数字, 下划线和连字符, 最多32字"
        }
    }
}
