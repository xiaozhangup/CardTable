package me.xiaozhangup.cardtable.table

import me.xiaozhangup.cardtable.api.GameEconomy
import me.xiaozhangup.cardtable.util.InputException
import me.xiaozhangup.cardtable.util.ext.severe
import me.xiaozhangup.cardtable.util.ext.getDataFolder
import me.xiaozhangup.cardtable.util.ext.warning
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.crab.configuration.Configuration
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID

/** Shared economy escrow; its records contain no game-specific state. */
class EconomyService : GameEconomy {
    private data class Entry(
        val id: String,
        val roundId: String,
        val tableId: String,
        val playerId: UUID,
        val playerName: String,
        var amount: Double,
        var state: String,
    )

    private val file = getDataFolder().resolve("economy.yml")
    private val entries = linkedMapOf<String, Entry>()
    private var lastRetry = 0L

    init {
        if (file.exists()) {
            val yaml = Configuration.loadFromFile(file, concurrent = false)
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
            severe("Unconfirmed economy transaction: id=${it.id}, player=${it.playerName}, amount=${it.amount}, state=${it.state}. Check the economy provider's balance before resolving economy.yml; this transaction will not be repeated automatically.")
        }
        retry()
    }

    private fun provider(): Economy? {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) return null
        return Bukkit.getServicesManager().getRegistration(Economy::class.java)?.provider?.takeIf { it.isEnabled }
    }

    override fun ensureAvailable() {
        requireInput(provider() != null, "This table requires Vault and an available economy provider.") { "这张牌桌需要 Vault 和经济插件, 当前无法使用" }
    }

    fun validateAmount(amount: Double) {
        val economy = provider() ?: throw InputException("No available Vault economy provider was found.", "未找到可用的 Vault 经济服务")
        requireInput(amount.isFinite() && amount > 0, "The bet amount must be finite and positive.") { "下注金额必须是有效的正数" }
        val digits = economy.fractionalDigits()
        if (digits >= 0) {
            try {
                BigDecimal.valueOf(amount).setScale(digits, RoundingMode.UNNECESSARY)
            } catch (_: ArithmeticException) {
                throw InputException("The economy provider supports at most $digits decimal places.", "经济插件最多支持 $digits 位小数, 请调整本桌底注")
            }
        }
    }

    /** Reserve an equal upper bound from every participant before a game starts. */
    override fun reserve(roundId: String, tableId: String, players: List<Pair<UUID, String>>, amount: Double) {
        if (amount == 0.0) return
        val economy = provider() ?: throw InputException("No available Vault economy provider was found.", "未找到可用的 Vault 经济服务")
        requireInput(entries.values.none { it.roundId == roundId }, "This round already has escrow records; resolve them before reserving funds again.") { "该局已有预扣记录, 请先处理资金记录" }
        validateAmount(amount)
        val insufficient = players.filter { !economy.has(Bukkit.getOfflinePlayer(it.first), amount) }
        requireInput(insufficient.isEmpty(), "Insufficient balance for ${insufficient.joinToString(", ") { it.second }}; each player must reserve ${format(amount)}.") {
            "${insufficient.joinToString(", ") { it.second }} 的余额不足, 本桌每人需要预扣 ${format(amount)}"
        }
        for ((playerId, playerName) in players) {
            val entry = Entry(UUID.randomUUID().toString(), roundId, tableId, playerId, playerName, amount, "withdrawing")
            entries[entry.id] = entry
            save()
            val result = try {
                economy.withdrawPlayer(Bukkit.getOfflinePlayer(playerId), amount)
            } catch (error: RuntimeException) {
                severe("Economy provider threw during withdrawal: id=${entry.id}, player=${entry.playerName}, amount=${entry.amount}. The result is unconfirmed; verify the player's balance.", error.stackTraceToString())
                refund(roundId)
                throw InputException("The economy provider threw during withdrawal; the unconfirmed transaction has been recorded for administrator review.", "经济插件预扣异常, 未确认的金额已记入资金记录, 请联系管理员核对", error)
            }
            if (!result.transactionSuccess()) {
                entries.remove(entry.id)
                save()
                refund(roundId)
                throw InputException("Withdrawal failed for $playerName: ${result.errorMessage}. Successfully reserved funds will be refunded.", "${playerName} 的预扣失败: ${result.errorMessage}. 已成功预扣的玩家将退回金额")
            }
            entry.state = "held"
            save()
        }
    }

    /** netResults is the game result: positive for a winner and negative for a loser. */
    override fun settle(roundId: String, netResults: Map<UUID, Double>) {
        val held = entries.values.filter { it.roundId == roundId && it.state == "held" }
        require(netResults.values.all { it.isFinite() } && netResults.values.fold(BigDecimal.ZERO) { total, amount -> total + BigDecimal.valueOf(amount) }.signum() == 0) { "Settlement results must be finite and sum to zero." }
        if (held.isNotEmpty()) require(netResults.keys == held.map { it.playerId }.toSet()) { "Settlement players must match this round's escrow participants." }
        for (entry in held) {
            val returned = sum(entry.amount, netResults.getValue(entry.playerId))
            require(returned >= 0 && returned.isFinite()) { "Settlement amount exceeds the escrow reserve." }
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
                severe("Economy provider threw during refund: id=${entry.id}, player=${entry.playerName}, amount=${entry.amount}. The result is unconfirmed; verify the player's balance.", error.stackTraceToString())
                continue
            }
            if (result.transactionSuccess()) {
                entries.remove(entry.id)
            } else {
                entry.state = "pending"
                warning("Refund failed for ${entry.playerName}, amount=${entry.amount}: ${result.errorMessage}. The pending refund record has been saved.")
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
        val yaml = Configuration.empty(concurrent = false)
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
