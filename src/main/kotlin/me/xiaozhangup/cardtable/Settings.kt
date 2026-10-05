package me.xiaozhangup.cardtable

import me.xiaozhangup.crab.configuration.ConfigurationSection
import me.xiaozhangup.cardtable.util.requireInput

data class Settings(
    val turnSeconds: Int,
    val maxDistance: Double,
    val maxMultiplier: Int,
    val voice: Boolean,
    val doudizhuVoice: String,
    val music: Boolean,
    val defaultSkin: String,
    val skins: Map<String, String>
) {
    companion object {
        fun read(config: ConfigurationSection): Settings {
            val skins = config.getConfigurationSection("skin-prefixes")!!.getKeys(false)
                .associateWith { config.getString("skin-prefixes.$it")!! }
            val value = Settings(config.getInt("turn-seconds"), config.getDouble("max-distance"),
                config.getInt("max-multiplier"), config.getBoolean("voice"),
                config.getString("doudizhu-voice")!!, config.getBoolean("music"),
                config.getString("default-skin")!!, skins)
            requireInput(value.turnSeconds in 5..300, "turn-seconds must be between 5 and 300.") { "turn-seconds 必须在 5..300 之间" }
            requireInput(value.maxDistance.isFinite() && value.maxDistance in 3.0..128.0, "max-distance must be finite and between 3 and 128.") { "max-distance 必须在 3..128 之间" }
            requireInput(value.maxMultiplier in 1..1024, "max-multiplier must be between 1 and 1024.") { "max-multiplier 必须在 1..1024 之间" }
            requireInput(value.doudizhuVoice in setOf("female", "male"), "doudizhu-voice must be female or male.") { "doudizhu-voice 必须为 female 或 male" }
            requireInput(value.defaultSkin in skins, "default-skin must be defined in skin-prefixes.") { "default-skin 必须出现在 skin-prefixes 中" }
            requireInput(skins.keys.all { it.matches(Regex("[a-z0-9_-]+")) } && skins.values.all { it.matches(Regex("[a-z0-9_.-]+:[a-z0-9_./-]+")) }, "Invalid skin name or resource prefix.") { "牌面名称或资源前缀格式无效" }
            return value
        }
    }
}
