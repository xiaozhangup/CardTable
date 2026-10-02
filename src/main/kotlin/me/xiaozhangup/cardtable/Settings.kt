package me.xiaozhangup.cardtable

import org.bukkit.configuration.file.FileConfiguration

data class Settings(
    val turnSeconds: Int,
    val maxDistance: Double,
    val maxMultiplier: Int,
    val voice: Boolean,
    val music: Boolean,
    val defaultSkin: String,
    val skins: Map<String, String>
) {
    companion object {
        fun read(config: FileConfiguration): Settings {
            val skins = config.getConfigurationSection("skin-prefixes")!!.getKeys(false)
                .associateWith { config.getString("skin-prefixes.$it")!! }
            val value = Settings(config.getInt("turn-seconds"), config.getDouble("max-distance"),
                config.getInt("max-multiplier"), config.getBoolean("voice"), config.getBoolean("music"),
                config.getString("default-skin")!!, skins)
            require(value.turnSeconds in 5..300) { "turn-seconds 必须在 5..300 之间。" }
            require(value.maxDistance.isFinite() && value.maxDistance in 3.0..128.0) { "max-distance 必须在 3..128 之间。" }
            require(value.maxMultiplier in 1..1024) { "max-multiplier 必须在 1..1024 之间。" }
            require(value.defaultSkin in skins) { "default-skin 必须出现在 skin-prefixes 中。" }
            require(skins.keys.all { it.matches(Regex("[a-z0-9_-]+")) } && skins.values.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) }) { "牌面名称或资源前缀格式无效。" }
            return value
        }
    }
}
