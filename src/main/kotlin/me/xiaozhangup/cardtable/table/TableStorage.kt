package me.xiaozhangup.cardtable.table

import me.xiaozhangup.cardtable.CardTablePlugin
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.nio.file.Files
import java.nio.file.StandardCopyOption

class TableStorage(private val plugin: CardTablePlugin) {
    private val file = plugin.dataFolder.resolve("tables.yml")
    private var yaml = YamlConfiguration.loadConfiguration(file)

    fun load(): List<TableConfig> {
        yaml = YamlConfiguration.loadConfiguration(file)
        return yaml.getConfigurationSection("tables")?.getKeys(false)?.mapNotNull { id ->
            val section = yaml.getConfigurationSection("tables.$id")!!
            try {
                validateId(id)
                val world = Bukkit.getWorld(section.getString("world")!!)
                    ?: throw IllegalArgumentException("世界尚未加载")
                val bet = section.getDouble("bet")
                require(bet.isFinite() && bet in 0.0..1_000_000.0) { "底注必须在 0..1000000 之间" }
                val seconds = section.getInt("turn-seconds", plugin.settings.turnSeconds)
                require(seconds in 5..300) { "回合时间必须在5..300秒之间" }
                val skin = section.getString("skin", plugin.settings.defaultSkin)!!
                require(skin in plugin.settings.skins) { "未知牌面 $skin" }
                val options = section.getConfigurationSection("options")?.getKeys(false)
                    ?.associateWith { section.getString("options.$it")!! }.orEmpty()
                val coordinates = listOf(section.getDouble("x"), section.getDouble("y"), section.getDouble("z"))
                require(coordinates.all { it.isFinite() } && kotlin.math.abs(coordinates[0]) <= 29_999_980 && kotlin.math.abs(coordinates[2]) <= 29_999_980) { "坐标无效" }
                TableConfig(id, Location(world, coordinates[0], coordinates[1], coordinates[2], section.getDouble("yaw").toFloat(), 0f),
                    bet, skin, seconds, section.getString("game", "doudizhu")!!, options)
            } catch (error: IllegalArgumentException) {
                plugin.logger.warning("牌桌 $id 未加载：${error.message}，原配置已保留。")
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
        yaml.save(staging)
        Files.move(staging.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }

    companion object {
        fun validateId(id: String) = require(id.matches(Regex("[a-z0-9_-]{1,32}"))) { "牌桌名只能包含小写字母、数字、下划线和连字符，最多32字。" }
    }
}
