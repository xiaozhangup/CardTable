package me.xiaozhangup.cardtable.game.doudizhu

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.game.doudizhu.Card
import me.xiaozhangup.cardtable.game.doudizhu.Play
import me.xiaozhangup.cardtable.game.doudizhu.Rules
import me.xiaozhangup.cardtable.table.TableConfig
import me.xiaozhangup.cardtable.table.EconomyService
import me.xiaozhangup.cardtable.api.BotDecision
import me.xiaozhangup.cardtable.api.Participant
import me.xiaozhangup.cardtable.api.TableRoundStartEvent
import me.xiaozhangup.cardtable.api.TableRoundEndEvent
import me.xiaozhangup.cardtable.api.TableRoundEndReason
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.cardtable.util.ext.warning
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.math.BigDecimal
import java.util.UUID
import java.util.concurrent.CompletableFuture

enum class Phase { WAITING, BIDDING, PLAYING }

data class Seat(
    val playerId: UUID,
    val playerName: String,
    val index: Int,
    val bot: Boolean = false,
    val hand: MutableList<Card> = mutableListOf(),
    val selected: MutableSet<Int> = linkedSetOf(),
    var ready: Boolean = false,
    var playedTurns: Int = 0,
)

class TableRuntime(val config: TableConfig) {
    val seats: Array<Seat?> = arrayOfNulls(3)
    var phase: Phase = Phase.WAITING
    var bottom: List<Card> = emptyList()
    var wildRank: Int? = null
    var currentSeat: Int = 0
    var landlord: Int = -1
    var highestBid: Int = 0
    var multiplier: Int = 1
    var previous: Play? = null
    var previousSeat: Int = -1
    var remainingSeconds: Int = 0
    var roundId: String? = null
    internal var openingSeat: Int = 0
    internal var bidsRemaining: Int = 3
    internal var consecutivePasses: Int = 0
    internal val trace = mutableListOf<Pair<Int, String>>()
    internal val playedPlays = mutableListOf<Play>()
    internal var playSequence: Long = 0
    internal var lastPlaySeat: Int = -1
    internal var lastPlayWildRank: Int? = null
    internal var decision: CompletableFuture<BotDecision>? = null
    internal var decisionKey: String? = null
}

/** Dou Dizhu controller. Rendering, player transport, and other card games live outside it. */
class DoudizhuController(private val plugin: CardTablePlugin) {
    val economy: EconomyService get() = plugin.economy
    val tables: MutableMap<String, TableRuntime> = linkedMapOf()
    var onChange: (TableRuntime) -> Unit = {}
    var onMessage: (TableRuntime, String) -> Unit = { _, _ -> }
    var onVoice: (TableRuntime, String) -> Unit = { _, _ -> }
    var onJoin: (TableRuntime, Seat) -> Unit = { _, _ -> }
    var onLeave: (TableRuntime, Seat) -> Unit = { _, _ -> }
    var onRemove: (TableRuntime) -> Unit = {}

    fun add(config: TableConfig) {
        requireInput(config.id !in tables, "Table '${config.id}' already exists") { "牌桌 ${config.id} 已存在" }
        tables[config.id] = TableRuntime(config)
        onChange(tables.getValue(config.id))
    }

    fun tableOf(playerId: UUID): TableRuntime? = tables.values.firstOrNull { table -> table.seats.any { it?.playerId == playerId } }

    fun join(player: Player, id: String, prepareSeat: (Seat) -> Unit = {}) {
        require(tableOf(player.uniqueId) == null) { "你已经在一张牌桌上, 请先离开" }
        joinActor(player.uniqueId, player.name, id, false, prepareSeat)
    }

    fun joinBot(botId: UUID, name: String, id: String): Seat {
        val table = tables.getValue(id)
        require(table.config.bet == 0.0) { "机器人只支持免费桌" }
        require(table.seats.filterNotNull().any { !it.bot }) { "请先由真人入座" }
        return joinActor(botId, name, id, true)
    }

    private fun joinActor(actorId: UUID, name: String, id: String, bot: Boolean, prepareSeat: (Seat) -> Unit = {}): Seat {
        val table = tables[id] ?: throw IllegalArgumentException("找不到牌桌 $id")
        require(table.phase == Phase.WAITING) { "这张牌桌正在游戏中" }
        val botIndex = if (bot) -1 else table.seats.indexOfFirst { it?.bot == true }
        val index = if (botIndex >= 0) botIndex else table.seats.indexOfFirst { it == null }
        require(index >= 0) { "这张牌桌已经坐满了" }
        if (table.config.bet > 0) economy.ensureAvailable()
        val seat = Seat(actorId, name, index, bot = bot, ready = bot)
        prepareSeat(seat)
        val replaced = table.seats[index]
        table.seats[index] = seat
        replaced?.let { onLeave(table, it) }
        onJoin(table, seat)
        message(table, "$name${if (bot) "(机器人)" else ""} 入座(${table.seats.count { it != null }}/3)")
        onChange(table)
        return seat
    }

    fun removeBot(id: UUID) {
        val (table, seat) = requireSeat(id)
        require(seat.bot) { "这个席位不是机器人" }
        require(table.phase == Phase.WAITING) { "请等本局结束后移除机器人" }
        table.seats[seat.index] = null
        onLeave(table, seat)
        onChange(table)
    }

    fun ready(player: Player) {
        val (table, seat) = requireSeat(player)
        require(table.phase == Phase.WAITING) { "本局已开始" }
        seat.ready = !seat.ready
        message(table, "${seat.playerName} ${if (seat.ready) "准备就绪" else "取消了准备"}")
        onChange(table)
        if (table.seats.all { it?.ready == true }) start(table)
    }

    private fun start(table: TableRuntime) {
        require(table.seats.filterNotNull().any { !it.bot }) { "至少需要一名真人" }
        require(table.config.bet == 0.0 || table.seats.filterNotNull().none { it.bot }) { "机器人只支持免费桌" }
        val roundId = UUID.randomUUID().toString()
        val reserve = money(table.config.bet, 6L * plugin.settings.maxMultiplier)
        try {
            if (table.config.bet > 0) economy.validateAmount(table.config.bet)
            economy.reserve(roundId, table.config.id, table.seats.map { it!!.let { seat -> seat.playerId to seat.playerName } }, reserve)
        } catch (error: IllegalArgumentException) {
            table.seats.forEach { it!!.ready = it.bot }
            onChange(table)
            throw error
        }
        table.roundId = roundId
        deal(table, (0..2).random())
        Bukkit.getPluginManager().callEvent(TableRoundStartEvent(table.config, roundId, participants(table)))
    }

    private fun deal(table: TableRuntime, openingSeat: Int) {
        clearDecision(table)
        table.trace.clear()
        table.playedPlays.clear()
        val deck = Card.deck().shuffled()
        table.seats.forEachIndexed { index, seat ->
            seat!!
            seat.hand.clear()
            seat.hand.addAll(deck.subList(index * 17, index * 17 + 17))
            sortHand(seat)
            seat.selected.clear()
            seat.playedTurns = 0
        }
        table.bottom = deck.takeLast(3)
        table.wildRank = if (table.config.options["laizi"] == "true") (3..15).random() else null
        table.phase = Phase.BIDDING
        table.openingSeat = openingSeat
        table.currentSeat = openingSeat
        table.landlord = -1
        table.highestBid = 0
        table.multiplier = 1
        table.previous = null
        table.previousSeat = -1
        table.bidsRemaining = 3
        table.consecutivePasses = 0
        table.remainingSeconds = table.config.turnSeconds
        message(table, "发牌完成, 请 ${current(table).playerName} 叫分${table.wildRank?.let { ", 本局癞子: ${rankLabel(it)}" } ?: ""}")
        onVoice(table, "deal")
        onChange(table)
    }

    fun bid(player: Player, score: Int) {
        val (table, seat) = requireTurn(player, Phase.BIDDING)
        bid(table, seat, score)
    }

    private fun bid(table: TableRuntime, seat: Seat, score: Int) {
        requireInput(score in 0..3, "Dou Dizhu bid must be between 0 and 3") { "叫分只能是 0, 1, 2, 3" }
        requireInput(score == 0 || score > table.highestBid, "Dou Dizhu bid must exceed ${table.highestBid}, or be 0 to pass") {
            "叫分必须高于当前的 ${table.highestBid} 分, 或选择不叫"
        }
        message(table, "${seat.playerName}: ${if (score == 0) "不叫" else "${score} 分"}")
        onVoice(table, "bid_$score")
        if (score > table.highestBid) {
            table.highestBid = score
            table.landlord = seat.index
        }
        table.bidsRemaining--
        if (score == 3 || table.bidsRemaining == 0) {
            if (table.landlord == -1) {
                message(table, "无人叫地主, 重新发牌")
                deal(table, (table.openingSeat + 1) % 3)
                return
            }
            val landlord = table.seats[table.landlord]!!
            landlord.hand.addAll(table.bottom)
            sortHand(landlord)
            table.phase = Phase.PLAYING
            table.currentSeat = table.landlord
            table.remainingSeconds = table.config.turnSeconds
            message(table, "${landlord.playerName} 成为地主(${table.highestBid} 分), 由地主先出牌")
            onVoice(table, "start")
        } else {
            next(table)
        }
        onChange(table)
    }

    fun play(player: Player) {
        val (table, seat) = requireTurn(player, Phase.PLAYING)
        val cards = seat.hand.filter { it.id in seat.selected }
        require(cards.isNotEmpty()) { "请先选中要出的牌" }
        val play = Rules.resolve(cards, table.wildRank, table.previous)
            ?: throw IllegalArgumentException("选中的牌型不合法, 或无法压过上一手牌")
        play(table, seat, play)
    }

    private fun play(table: TableRuntime, seat: Seat, play: Play) {
        table.trace += seat.index to actionString(play.effectiveRanks)
        table.playedPlays += play
        table.playSequence++
        table.lastPlaySeat = seat.index
        table.lastPlayWildRank = table.wildRank
        val playedIds = play.cards.map { it.id }.toSet()
        seat.hand.removeAll { it.id in playedIds }
        seat.selected.clear()
        seat.playedTurns++
        table.previous = play
        table.previousSeat = seat.index
        table.consecutivePasses = 0
        if (play.isBomb) doubleMultiplier(table)
        message(table, "${seat.playerName} 出牌: ${play.label}(${play.cards.joinToString(" ") { it.label }})${if (play.isBomb) ", 倍数 ${table.multiplier}" else ""}")
        onVoice(table, "play_${play.type.name.lowercase()}")
        if (seat.hand.isEmpty()) {
            finish(table, seat.index == table.landlord)
            return
        }
        next(table)
        onChange(table)
    }

    fun pass(player: Player) {
        val (table, seat) = requireTurn(player, Phase.PLAYING)
        require(table.previous != null) { "你是首出, 必须出牌" }
        pass(table, seat)
    }

    private fun pass(table: TableRuntime, seat: Seat) {
        table.trace += seat.index to "pass"
        seat.selected.clear()
        table.consecutivePasses++
        message(table, "${seat.playerName}: 不要")
        onVoice(table, "pass")
        if (table.consecutivePasses == 2) {
            table.previous = null
            table.previousSeat = -1
            table.consecutivePasses = 0
        }
        next(table)
        onChange(table)
    }

    fun hint(player: Player) {
        val (table, seat) = requireTurn(player, Phase.PLAYING)
        val suggested = Rules.hint(seat.hand, table.wildRank, table.previous)
        seat.selected.clear()
        if (suggested == null) {
            plugin.tell(player, "§e没有可以压过上一手的牌, 可以选择不要")
        } else {
            seat.selected.addAll(suggested.cards.map { it.id })
        }
        onChange(table)
    }

    fun leave(player: Player) {
        val table = tableOf(player.uniqueId) ?: return
        val seat = table.seats.first { it?.playerId == player.uniqueId }!!
        when (table.phase) {
            Phase.PLAYING -> {
                message(table, "${seat.playerName} 离桌, 所属阵营判负")
                finish(table, seat.index != table.landlord, reason = TableRoundEndReason.FORFEIT, preservePlayDisplay = false)
            }
            Phase.BIDDING -> {
                message(table, "${seat.playerName} 离桌, 尚未确定地主, 本局取消并退回预扣金额")
                val ended = refund(table)
                reset(table)
                ended?.let { Bukkit.getPluginManager().callEvent(it) }
            }
            Phase.WAITING -> Unit
        }
        table.seats[seat.index] = null
        onLeave(table, seat)
        message(table, "${seat.playerName} 离开牌桌")
        if (table.seats.filterNotNull().none { !it.bot }) {
            table.seats.filterNotNull().forEach { bot -> table.seats[bot.index] = null; onLeave(table, bot) }
        }
        onChange(table)
    }

    fun tick() {
        for (table in tables.values) {
            if (table.phase == Phase.WAITING) {
                if (table.seats.any { it?.bot == true } && table.seats.all { it?.ready == true }) start(table)
                continue
            }
            if (current(table).bot) { botTurn(table); continue }
            table.remainingSeconds--
            if (table.remainingSeconds > 0) {
                onChange(table)
                continue
            }
            val seat = current(table)
            message(table, "${seat.playerName} 操作超时, 已自动${if (table.phase == Phase.BIDDING) "不叫" else if (table.previous != null) "不要" else "出最小单牌"}")
            if (table.phase == Phase.BIDDING) {
                bid(table, seat, 0)
            } else if (table.previous != null) {
                pass(table, seat)
            } else {
                val card = seat.hand.minWith(compareBy<Card> { it.rank }.thenBy { it.id })
                play(table, seat, Rules.resolve(listOf(card), table.wildRank)!!)
            }
        }
    }

    private fun finish(table: TableRuntime, landlordWon: Boolean, reason: TableRoundEndReason = TableRoundEndReason.WIN, preservePlayDisplay: Boolean = true) {
        if (reason == TableRoundEndReason.WIN) {
            val spring = landlordWon && table.seats.filterIndexed { index, _ -> index != table.landlord }.all { it!!.playedTurns == 0 }
            val antiSpring = !landlordWon && table.seats[table.landlord]!!.playedTurns == 1
            if (spring || antiSpring) {
                doubleMultiplier(table)
                message(table, "${if (spring) "春天" else "反春天"}! 倍数升至 ${table.multiplier}")
                onVoice(table, if (spring) "spring" else "anti_spring")
            }
        }
        val unit = money(table.config.bet, table.highestBid.toLong() * table.multiplier)
        val results = table.seats.associate { seat ->
            seat!!
            val won = (seat.index == table.landlord) == landlordWon
            val amount = money(unit, (if (seat.index == table.landlord) 2L else 1L) * (if (won) 1L else -1L))
            seat.playerId to amount
        }
        economy.settle(table.roundId!!, results)
        val players = participants(table)
        val ended = TableRoundEndEvent(table.config, table.roundId!!, players,
            players.filter { (it.seat == table.landlord) == landlordWon }, reason)
        table.roundId = null
        message(table, "${if (landlordWon) "地主" else "农民"}获胜! 叫分 ${table.highestBid} × 倍数 ${table.multiplier}${if (table.config.bet > 0) ", 每份 ${String.format(java.util.Locale.ROOT, "%.2f", unit)}" else ""}")
        onVoice(table, if (landlordWon) "landlord_win" else "farmer_win")
        reset(table, preservePlayDisplay)
        onChange(table)
        Bukkit.getPluginManager().callEvent(ended)
    }

    private fun reset(table: TableRuntime, preservePlayDisplay: Boolean = false) {
        clearDecision(table)
        table.trace.clear()
        if (!preservePlayDisplay) table.playedPlays.clear()
        table.phase = Phase.WAITING
        table.bottom = emptyList()
        table.wildRank = null
        table.currentSeat = 0
        table.landlord = -1
        table.highestBid = 0
        table.multiplier = 1
        table.previous = null
        table.previousSeat = -1
        table.remainingSeconds = 0
        table.seats.filterNotNull().forEach {
            it.ready = it.bot
            it.hand.clear()
            it.selected.clear()
            it.playedTurns = 0
        }
    }

    private fun refund(table: TableRuntime): TableRoundEndEvent? {
        val roundId = table.roundId ?: return null
        economy.refund(roundId)
        table.roundId = null
        return TableRoundEndEvent(table.config, roundId, participants(table), emptyList(), TableRoundEndReason.CANCELLED)
    }

    private fun participants(table: TableRuntime): List<Participant> = table.seats.filterNotNull().map {
        Participant(it.playerId, it.playerName, it.index, it.bot)
    }

    fun remove(id: String) {
        val table = tables[id] ?: throw IllegalArgumentException("找不到牌桌 $id")
        val ended = refund(table)
        reset(table)
        table.seats.filterNotNull().forEach { onLeave(table, it) }
        ended?.let { Bukkit.getPluginManager().callEvent(it) }
        tables.remove(id)
        onRemove(table)
    }

    fun shutdown() {
        tables.values.toList().forEach { remove(it.config.id) }
    }

    private fun requireSeat(player: Player): Pair<TableRuntime, Seat> {
        return requireSeat(player.uniqueId)
    }

    private fun requireSeat(id: UUID): Pair<TableRuntime, Seat> {
        val table = tableOf(id) ?: throw IllegalArgumentException("请先加入一张牌桌")
        return table to table.seats.first { it?.playerId == id }!!
    }

    private fun botTurn(table: TableRuntime) {
        val seat = current(table)
        val key = "${table.roundId}:${table.phase}:${seat.playerId}:${table.previousSeat}:${table.trace.size}"
        if (table.decisionKey != key) clearDecision(table)
        val pending = table.decision
        if (pending == null) {
            val state = if (table.phase == Phase.BIDDING) mapOf<String, Any?>(
                "phase" to "bid", "current_hand" to actionString(seat.hand.map(Card::rank)), "highest_bid" to table.highestBid,
                "wild_rank" to table.wildRank
            ) else {
                val actions = Rules.legalPlays(seat.hand, table.wildRank, table.previous).associateBy { actionString(it.effectiveRanks) }
                val mapping = actions.mapValues { (_, play) -> mapOf("action" to "play", "argument" to play.cards.joinToString(",") { it.id.toString() }) }.toMutableMap()
                if (table.previous != null) mapping["pass"] = mapOf("action" to "pass", "argument" to "")
                mapOf("phase" to "play", "current_hand" to actionString(seat.hand.map(Card::rank)), "self" to seat.index,
                    "landlord" to table.landlord, "trace" to table.trace.map { listOf(it.first, it.second) },
                    "legal_actions" to mapping.keys.toList(), "action_map" to mapping, "wild_rank" to table.wildRank)
            }
            table.decisionKey = key
            table.decision = plugin.botAI.decide("doudizhu", state)
            return
        }
        if (!pending.isDone) return
        table.decision = null
        table.decisionKey = null
        try {
            val decision = pending.join()
            when (table.phase) {
                Phase.BIDDING -> {
                    require(decision.action == "bid") { "AI did not return a bid action" }
                    bid(table, seat, decision.argument?.toIntOrNull() ?: throw IllegalArgumentException("AI returned an invalid bid"))
                }
                Phase.PLAYING -> when (decision.action) {
                    "pass" -> { require(table.previous != null) { "AI cannot pass when leading" }; pass(table, seat) }
                    "play" -> {
                        val ids = decision.argument?.split(',')?.map { it.toInt() } ?: throw IllegalArgumentException("AI did not return card IDs")
                        require(ids.distinct().size == ids.size && ids.all { id -> seat.hand.any { it.id == id } }) {
                            "AI returned duplicate card IDs or cards not in its hand"
                        }
                        val cards = ids.map { id -> seat.hand.first { it.id == id } }
                        val play = Rules.resolve(cards, table.wildRank, table.previous) ?: throw IllegalArgumentException("AI returned an invalid card combination")
                        play(table, seat, play)
                    }
                    else -> throw IllegalArgumentException("AI returned an unsupported action: ${decision.action}")
                }
                Phase.WAITING -> Unit
            }
        } catch (error: Exception) {
            warning("Dou Dizhu AI decision failed at table '${table.config.id}':\n${error.stackTraceToString()}")
            message(table, "外部 AI 决策失败, 本局取消, 请检查 AI 程序")
            val ended = refund(table)
            reset(table)
            onChange(table)
            ended?.let { Bukkit.getPluginManager().callEvent(it) }
        }
    }

    private fun clearDecision(table: TableRuntime) {
        table.decision?.cancel(false)
        table.decision = null
        table.decisionKey = null
    }

    private fun actionString(ranks: List<Int>): String = ranks.sorted().joinToString("") { rank ->
        when (rank) { 10 -> "T"; 11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; 15 -> "2"; 16 -> "B"; 17 -> "R"; else -> rank.toString() }
    }

    private fun requireTurn(player: Player, phase: Phase): Pair<TableRuntime, Seat> {
        val (table, seat) = requireSeat(player)
        requireInput(table.phase == phase, "Dou Dizhu table '${table.config.id}' is in phase ${table.phase}; expected $phase") {
            if (phase == Phase.BIDDING) "现在不是叫分阶段" else "现在不是出牌阶段"
        }
        requireInput(table.currentSeat == seat.index, "It is not player '${seat.playerId}'s turn at table '${table.config.id}'") {
            "还没轮到你, 请等待 ${current(table).playerName}"
        }
        return table to seat
    }

    private fun next(table: TableRuntime) {
        table.currentSeat = (table.currentSeat + 1) % 3
        table.remainingSeconds = table.config.turnSeconds
    }

    private fun current(table: TableRuntime): Seat = table.seats[table.currentSeat]!!
    private fun sortHand(seat: Seat) = seat.hand.sortWith(compareBy<Card> { it.rank }.thenBy { it.id })
    private fun doubleMultiplier(table: TableRuntime) {
        table.multiplier = (table.multiplier.toLong() * 2).coerceAtMost(plugin.settings.maxMultiplier.toLong()).toInt()
    }
    private fun message(table: TableRuntime, text: String) = onMessage(table, text)
    private fun money(base: Double, factor: Long): Double = BigDecimal.valueOf(base).multiply(BigDecimal.valueOf(factor)).toDouble()
    private fun rankLabel(rank: Int): String = when (rank) { 11 -> "J"; 12 -> "Q"; 13 -> "K"; 14 -> "A"; 15 -> "2"; else -> rank.toString() }
}
