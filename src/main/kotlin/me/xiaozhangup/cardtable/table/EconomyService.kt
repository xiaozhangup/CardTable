package me.xiaozhangup.cardtable.table

import me.xiaozhangup.cardtable.api.GameEconomy
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.plugin.java.JavaPlugin
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Shared economy escrow; its records contain no game-specific state. */
class EconomyService(private val plugin: JavaPlugin) : GameEconomy {
    private data class Entry(
        val id: String,
        val roundId: String,
        val tableId: String,
        val playerId: UUID,
        val playerName: String,
        var amount: Double,
        var state: String,
    )

    private val file = plugin.dataFolder.resolve("economy.yml")
    private val entries = linkedMapOf<String, Entry>()
    private var lastRetry = 0L

    init {
        if (file.exists()) {
            val yaml = YamlConfiguration.loadConfiguration(file)
            val records = yaml.getConfigurationSection("records")
            records?.getKeys(false)?.forEach { id ->
                val row = records.getConfigurationSection(id)!!
                entries[id] = Entry(
                    id, row.getString("round")!!, row.getString("table")!!,
                    UUID.fromString(row.getString("player")!!), row.getString("name")!!,
                    row.getDouble("amount"), row.getString("state")!!,
                )
            }
        }
        // A running game is never restored after a server restart, so return its escrow.
        entries.values.filter { it.state == "held" }.forEach { it.state = "pending" }
        if (entries.isNotEmpty()) save()
        entries.values.filter { it.state == "withdrawing" || it.state == "depositing" }.forEach {
            plugin.logger.severe("牌桌资金操作未确认：${it.id}，${it.playerName}，${it.amount}，${it.state}。请核对经济插件余额后处理 economy.yml，系统不会重复执行这笔操作。")
        }
        retry()
    }

    private fun provider(): Economy? {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null
        return Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider?.takeIf { it.isEnabled }
    }

    override fun ensureAvailable() {
        require(provider() != null) { "这张牌桌需要 Vault 和经济插件，当前无法使用。" }
    }

    fun validateAmount(amount: Double) {
        val economy = provider() ?: throw IllegalArgumentException("未找到可用的 Vault 经济服务。")
        require(amount.isFinite() && amount > 0) { "下注金额必须是有效的正数。" }
        val digits = economy.fractionalDigits()
        if (digits >= 0) {
            try {
                BigDecimal.valueOf(amount).setScale(digits, RoundingMode.UNNECESSARY)
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("经济插件最多支持 $digits 位小数，请调整本桌底注。")
            }
        }
    }

    /** Reserve an equal upper bound from every participant before a game starts. */
    override fun reserve(roundId: String, tableId: String, players: List<Pair<UUID, String>>, amount: Double) {
        if (amount == 0.0) return
        val economy = provider() ?: throw IllegalArgumentException("未找到可用的 Vault 经济服务。")
        require(entries.values.none { it.roundId == roundId }) { "该局已有预扣记录，请先处理资金记录。" }
        validateAmount(amount)
        val insufficient = players.filter { !economy.has(Bukkit.getOfflinePlayer(it.first), amount) }
        require(insufficient.isEmpty()) {
            "${insufficient.joinToString("、") { it.second }} 的余额不足，本桌每人需要预扣 ${format(amount)}。"
        }
        for ((playerId, playerName) in players) {
            val entry = Entry(UUID.randomUUID().toString(), roundId, tableId, playerId, playerName, amount, "withdrawing")
            entries[entry.id] = entry
            save()
            val result = try {
                economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), amount)
            } catch (error: RuntimeException) {
                plugin.logger.severe("经济插件在预扣时抛出异常，操作结果未确认：${entry.id}，${entry.playerName}，${entry.amount}。请核对余额；${error.message}")
                refund(roundId)
                throw IllegalArgumentException("经济插件预扣异常，未确认的金额已记入资金记录，请联系管理员核对。", error)
            }
            if (!result.transactionSuccess()) {
                entries.remove(entry.id)
                save()
                refund(roundId)
                throw IllegalArgumentException("${playerName} 的预扣失败：${result.errorMessage}。已成功预扣的玩家将退回金额。")
            }
            entry.state = "held"
            save()
        }
    }

    /** netResults is the game result: positive for a winner and negative for a loser. */
    override fun settle(roundId: String, netResults: Map<UUID, Double>) {
        val held = entries.values.filter { it.roundId == roundId && it.state == "held" }
        require(netResults.values.all { it.isFinite() } && netResults.values.fold(BigDecimal.ZERO) { total, amount -> total + BigDecimal.valueOf(amount) }.signum() == 0) { "结算净输赢必须为有限金额且合计为零。" }
        if (held.isNotEmpty()) require(netResults.keys == held.map { it.playerId }.toSet()) { "结算玩家必须与本局托管玩家一致。" }
        for (entry in held) {
            val returned = sum(entry.amount, netResults.getValue(entry.playerId))
            require(returned >= 0 && returned.isFinite()) { "结算金额超出已预扣额度。" }
        }
        for (entry in held) {
            entry.amount = sum(entry.amount, netResults.getValue(entry.playerId))
            entry.state = "pending"
        }
        if (held.isNotEmpty()) save()
        retry()
    }

    override fun refund(roundId: String) {
        val held = entries.values.filter { it.roundId == roundId && it.state == "held" }
        held.forEach { it.state = "pending" }
        if (held.isNotEmpty()) save()
        retry()
    }

    fun tick() {
        val now = System.currentTimeMillis()
        if (now - lastRetry < 30_000L) return
        lastRetry = now
        retry()
    }

    /** Called on player login as well as periodic provider failure recovery. */
    override fun retry(playerId: UUID?) {
        val pending = entries.values.filter { it.state == "pending" && (playerId == null || it.playerId == playerId) }
        if (pending.isEmpty()) return
        val economy = provider() ?: return
        for (entry in pending) {
            if (entry.amount == 0.0) {
                entries.remove(entry.id)
                save()
                continue
            }
            entry.state = "depositing"
            save()
            val result = try {
                economy.depositPlayer(Bukkit.getOfflinePlayer(entry.playerId), entry.amount)
            } catch (error: RuntimeException) {
                plugin.logger.severe("经济插件在退款时抛出异常，操作结果未确认：${entry.id}，${entry.playerName}，${entry.amount}。请核对余额；${error.message}")
                continue
            }
            if (result.transactionSuccess()) {
                entries.remove(entry.id)
            } else {
                entry.state = "pending"
                plugin.logger.warning("牌桌退款暂未成功：${entry.playerName} ${entry.amount}，${result.errorMessage}。已保存待退款记录。")
            }
            save()
        }
    }

    fun shutdown() {
        entries.values.filter { it.state == "held" }.forEach { it.state = "pending" }
        if (entries.isNotEmpty()) save()
        retry()
    }

    private fun save() {
        plugin.dataFolder.mkdirs()
        val yaml = YamlConfiguration()
        for (entry in entries.values) {
            val key = "records.${entry.id}"
            yaml.set("$key.round", entry.roundId)
            yaml.set("$key.table", entry.tableId)
            yaml.set("$key.player", entry.playerId.toString())
            yaml.set("$key.name", entry.playerName)
            yaml.set("$key.amount", entry.amount)
            yaml.set("$key.state", entry.state)
        }
        val temporary = file.toPath().resolveSibling("${file.name}.tmp")
        Files.writeString(temporary, yaml.saveToString())
        Files.move(temporary, file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }

    private fun format(amount: Double): String = String.format(java.util.Locale.ROOT, "%.2f", amount)
    private fun sum(left: Double, right: Double): Double = BigDecimal.valueOf(left).add(BigDecimal.valueOf(right)).toDouble()
}
