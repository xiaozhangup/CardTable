package me.xiaozhangup.cardtable.game.uno

import me.xiaozhangup.cardtable.api.CardFace
import me.xiaozhangup.cardtable.api.BotDecision
import me.xiaozhangup.cardtable.api.GameContext
import me.xiaozhangup.cardtable.api.GameControl
import me.xiaozhangup.cardtable.api.GameSession
import me.xiaozhangup.cardtable.api.GameView
import me.xiaozhangup.cardtable.api.Participant
import me.xiaozhangup.cardtable.api.PlayerView
import me.xiaozhangup.cardtable.api.TableCardPile
import me.xiaozhangup.cardtable.api.TableDraw
import me.xiaozhangup.cardtable.api.TableDrawEvents
import me.xiaozhangup.cardtable.api.TableHeader
import me.xiaozhangup.cardtable.api.TableHeaderView
import me.xiaozhangup.cardtable.api.TablePlay
import me.xiaozhangup.cardtable.api.TablePlayEvents
import me.xiaozhangup.cardtable.api.TablePlayerJoin
import me.xiaozhangup.cardtable.api.TableRoundStartEvent
import me.xiaozhangup.cardtable.api.TableRoundEndEvent
import me.xiaozhangup.cardtable.api.TableRoundEndReason
import me.xiaozhangup.cardtable.api.TableSkip
import me.xiaozhangup.cardtable.api.TableSkipEvents
import me.xiaozhangup.cardtable.api.TableTurnOrder
import me.xiaozhangup.cardtable.api.TurnDirection
import me.xiaozhangup.cardtable.table.TableConfig
import me.xiaozhangup.cardtable.util.InputException
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.cardtable.util.sendTableMessage
import me.xiaozhangup.cardtable.util.ext.warning
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.math.BigDecimal
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CompletableFuture

/** One classic 108-card round. This implementation only depends on the shared game API. */
class UnoSession(private val context: GameContext, override val table: TableConfig) : GameSession, TableCardPile, TableTurnOrder, TablePlayEvents, TableSkipEvents, TableDrawEvents, TableHeader, TablePlayerJoin {
    private enum class Phase { WAITING, TURN, COLOR, DRAW_FOUR }

    private data class Seat(
        val participant: Participant,
        val hand: MutableList<UnoCard> = mutableListOf(),
        var ready: Boolean = false,
        var selected: Int? = null,
        var announced: Boolean = false,
        var preannounced: Boolean = false
    )

    private data class ColorChoice(val card: UnoCard, val opening: Boolean)
    private data class DrawFour(
        val offender: Int,
        val oldColor: UnoColor,
        val legal: Boolean,
        val proof: List<UnoCard>
    )

    private data class BotTurnKey(
        val round: String?,
        val phase: Phase,
        val player: UUID,
        val hand: List<Int>,
        val top: Int,
        val color: UnoColor,
        val hasDrawn: Boolean,
        val drawnCard: Int?
    )
    private data class PendingBot(val key: BotTurnKey, val future: CompletableFuture<BotDecision>)

    override val seatCount: Int = table.options["seats"]?.toInt() ?: 4
    private val seats: Array<Seat?> = arrayOfNulls(seatCount)
    private var phase = Phase.WAITING
    private var order: List<Seat> = emptyList()
    private val drawPile = mutableListOf<UnoCard>()
    private val discardPile = mutableListOf<UnoCard>()
    private var lastRoundPile: List<CardFace> = emptyList()
    private var playSequence: Long = 0
    private var skipSequence: Long = 0
    private var drawSequence: Long = 0
    private var direction = 1
    private var currentPosition = 0
    private var currentColor: UnoColor? = null
    private var colorChoice: ColorChoice? = null
    private var drawFour: DrawFour? = null
    private var hasDrawn = false
    private var drawnCard: Int? = null
    private var catchable: UUID? = null
    private var emptyDrawTurns = 0
    private var remainingSeconds = 0
    private var roundId: String? = null
    private var lastResult = ""
    private var pendingBot: PendingBot? = null

    override val participants: List<Participant> get() = seats.filterNotNull().map { it.participant }
    override val active: Boolean get() = phase != Phase.WAITING
    // order follows increasing physical seat angles (x = sin, z = cos).
    override val turnDirection: TurnDirection? get() = when {
        !active -> null
        direction == 1 -> TurnDirection.COUNTERCLOCKWISE
        else -> TurnDirection.CLOCKWISE
    }
    override val supportsBots: Boolean = true
    override var lastTablePlay: TablePlay? = null
        private set
    override var lastTableSkip: TableSkip? = null
        private set
    override var lastTableDraw: TableDraw? = null
        private set

    override fun tableCards(): List<CardFace> = if (active) discardPile.map { face(it) } else lastRoundPile

    override fun tableHeader(): TableHeaderView {
        val joined = seats.filterNotNull()
        val stage = when (phase) {
            Phase.WAITING -> if (joined.size < 2) "待入座" else "待准备"
            Phase.TURN -> if (hasDrawn) "摸后出牌" else "出牌"
            Phase.COLOR -> "选色"
            Phase.DRAW_FOUR -> "处理 +4"
        }
        val title = Component.text("UNO", TextColor.color(0x9BC7B6))
            .append(Component.text("  ", NamedTextColor.DARK_GRAY))
            .append(Component.text(stage, NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false)
        val detail = when (phase) {
            Phase.WAITING -> {
                val population = Component.text("人数 ", NamedTextColor.GRAY)
                    .append(Component.text("${joined.size}/$seatCount", NamedTextColor.WHITE))
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                if (joined.size < 2) population
                    .append(Component.text("2 ", NamedTextColor.WHITE))
                    .append(Component.text("人起", NamedTextColor.GRAY))
                else population
                    .append(Component.text("准备 ", NamedTextColor.GRAY))
                    .append(Component.text("${joined.count { it.ready }}/${joined.size}", NamedTextColor.WHITE))
            }
            Phase.COLOR -> Component.text(colorChoice!!.card.value.label, NamedTextColor.WHITE)
            Phase.TURN, Phase.DRAW_FOUR -> {
                val color = currentColor!!
                Component.text("当前牌面: ", NamedTextColor.GRAY)
                    .append(Component.text(color.label, when (color) {
                        UnoColor.RED -> NamedTextColor.RED
                        UnoColor.YELLOW -> NamedTextColor.YELLOW
                        UnoColor.GREEN -> NamedTextColor.GREEN
                        UnoColor.BLUE -> NamedTextColor.BLUE
                    }))
            }
        }.decoration(TextDecoration.ITALIC, false)
        return TableHeaderView(title, detail)
    }

    override fun join(player: Player) {
        join(player) {}
    }

    override fun join(player: Player, prepareSeat: (Participant) -> Unit) {
        joinParticipant(player.uniqueId, player.name, bot = false, prepareSeat = prepareSeat)
    }

    override fun addBot(id: UUID, name: String): Participant {
        require(table.bet == 0.0) { "机器人只能加入免费牌桌, 请先把本桌底注设为 0" }
        require(participants.any { !it.bot }) { "请先让一名玩家加入牌桌" }
        require(context.botAI.available) { "机器人策略服务尚未就绪" }
        return joinParticipant(id, name, bot = true)
    }

    private fun joinParticipant(id: UUID, name: String, bot: Boolean, prepareSeat: (Participant) -> Unit = {}): Participant {
        require(!active) { "这张 UNO 牌桌正在游戏中" }
        require(seats.none { it?.participant?.id == id }) { "你已经入座" }
        val botSeat = if (bot) -1 else seats.indexOfFirst { it?.participant?.bot == true }
        val index = if (botSeat >= 0) botSeat else seats.indexOfFirst { it == null }
        require(index >= 0) { "这张 UNO 牌桌已经坐满了" }
        if (table.bet > 0) context.economy.ensureAvailable()
        val participant = Participant(id, name, index, bot)
        prepareSeat(participant)
        seats[index] = Seat(participant, ready = bot)
        context.broadcast("$name 入座 UNO(${participants.size}/$seatCount)${if (bot) ", 机器人准备就绪" else ""}")
        context.changed()
        return participant
    }

    override fun leave(player: Player) {
        val seat = seat(player.uniqueId)
        if (active) cancel("${seat.participant.name} 离开牌桌, 本局取消, 所有预扣退回")
        seats[seat.participant.seat] = null
        if (participants.none { !it.bot }) seats.fill(null)
        context.broadcast("${seat.participant.name} 离开 UNO 牌桌")
        context.changed()
    }

    override fun removeBot(id: UUID) {
        require(!active) { "对局中不能移除机器人, 请等待本局结束" }
        val own = seat(id)
        require(own.participant.bot) { "只能用此操作移除机器人" }
        seats[own.participant.seat] = null
        context.broadcast("${own.participant.name} 离开 UNO 牌桌")
        context.changed()
    }

    override fun close() {
        val ended = refundRound()
        reset()
        seats.fill(null)
        ended?.let { Bukkit.getPluginManager().callEvent(it) }
    }

    override fun act(player: Player, action: String, argument: String?) {
        act(player.uniqueId, action, argument)
    }

    private fun act(id: UUID, action: String, argument: String?) {
        val own = seat(id)
        when (action) {
            "ready" -> ready(own)
            "select" -> select(own, argument)
            "play" -> play(own)
            "draw" -> draw(own)
            "pass" -> pass(own)
            "color" -> chooseColor(own, argument)
            "uno" -> announce(own)
            "catch" -> catch(own)
            "challenge" -> challenge(own)
            "accept" -> accept(own)
            "hint" -> hint(own)
            else -> throw IllegalArgumentException("UNO 不支持操作: $action")
        }
    }

    private fun ready(own: Seat) {
        require(!active) { "本局 UNO 已开始" }
        own.ready = !own.ready
        context.broadcast("${own.participant.name} ${if (own.ready) "准备就绪" else "取消准备"}")
        context.changed()
        val joined = seats.filterNotNull()
        if (joined.size >= 2 && joined.any { !it.participant.bot } && joined.all { it.ready }) start(joined)
    }

    private fun start(joined: List<Seat>) {
        requireInput(table.bet == 0.0 || joined.none { it.participant.bot }, "UNO bots cannot play at a paid table") {
            "收费牌桌不能进行机器人对局"
        }
        requireInput(joined.none { it.participant.bot } || context.botAI.available, "The UNO AI service is not ready") {
            "机器人策略服务尚未就绪"
        }
        val id = UUID.randomUUID().toString()
        try {
            context.economy.reserve(id, table.id, joined.map { it.participant.id to it.participant.name }, table.bet)
        } catch (error: IllegalArgumentException) {
            joined.forEach { it.ready = false }
            context.changed()
            throw error
        }
        roundId = id
        lastRoundPile = emptyList()
        lastTablePlay = null
        lastTableSkip = null
        lastTableDraw = null
        order = joined
        drawPile += UnoRules.deck().shuffled()
        repeat(7) { order.forEach { it.hand += drawPile.removeAt(drawPile.lastIndex) } }
        order.forEach(::sortHand)
        direction = 1
        val dealer = order.indices.random()
        currentPosition = (dealer + 1) % order.size
        phase = Phase.TURN
        var opening = drawPile.removeAt(drawPile.lastIndex)
        while (opening.value == UnoValue.WILD_DRAW_FOUR) {
            drawPile += opening
            drawPile.shuffle()
            opening = drawPile.removeAt(drawPile.lastIndex)
        }
        discardPile += opening
        currentColor = opening.color
        remainingSeconds = table.turnSeconds
        context.broadcast("UNO 开局, 每人 7 张, 翻出 ${opening.coloredLabel}")
        when (opening.value) {
            UnoValue.WILD -> {
                colorChoice = ColorChoice(opening, opening = true)
                phase = Phase.COLOR
            }
            UnoValue.REVERSE -> {
                direction = -1
                currentPosition = dealer
                context.broadcast("开局反转, 由发牌者 ${current().participant.name} 先出")
            }
            UnoValue.SKIP -> {
                context.broadcast("${current().participant.name} 被开局跳过")
                recordSkip(current())
                beginTurn(nextPosition())
            }
            UnoValue.DRAW_TWO -> {
                penalty(current(), 2, "开局 +2")
                beginTurn(nextPosition())
            }
            else -> Unit
        }
        context.changed()
        Bukkit.getPluginManager().callEvent(TableRoundStartEvent(table, id, participants))
    }

    private fun select(own: Seat, argument: String?) {
        require(active) { "本局尚未发牌" }
        val token = argument?.toIntOrNull() ?: throw IllegalArgumentException("请选择手中的一张 UNO 牌")
        require(own.hand.any { it.id == token }) { "这张牌不在你的手牌中" }
        own.selected = if (own.selected == token) null else token
        if (own.selected == null) own.preannounced = false
        context.changed()
    }

    private fun playable(own: Seat, card: UnoCard): Boolean =
        (!hasDrawn || drawnCard == card.id) && UnoRules.matches(card, discardPile.last(), currentColor!!)

    private fun play(own: Seat) {
        requireTurn(own, Phase.TURN)
        val card = own.hand.firstOrNull { it.id == own.selected }
            ?: throw InputException("No UNO card is selected", "请先选中一张 UNO 牌")
        requireInput(!hasDrawn || drawnCard == card.id, "Only the newly drawn UNO card can be played after drawing") {
            "摸牌后只能出刚摸到的那一张, 或选择不要"
        }
        requireInput(UnoRules.matches(card, discardPile.last(), currentColor!!), "The UNO card does not match the current color or symbol") {
            "这张牌与当前颜色或符号不匹配"
        }
        closeCatchWindow()
        emptyDrawTurns = 0
        val proof = own.hand.toList()
        val oldColor = currentColor!!
        val legalFour = UnoRules.canPlayDrawFour(proof, oldColor)
        own.hand.remove(card)
        own.selected = null
        own.announced = own.hand.size == 1 && own.preannounced
        own.preannounced = false
        if (own.hand.size == 1) {
            if (own.announced) context.broadcast("${own.participant.name}: UNO!")
            else catchable = own.participant.id
        }
        discardPile += card
        playSequence++
        lastTablePlay = TablePlay(playSequence, own.participant.seat, listOf(face(card)))
        context.broadcast("${own.participant.name} 出了 ${card.coloredLabel}")
        if (card.value.isWild) {
            colorChoice = ColorChoice(card, opening = false)
            if (card.value == UnoValue.WILD_DRAW_FOUR) drawFour = DrawFour(currentPosition, oldColor, legalFour, proof)
            phase = Phase.COLOR
            remainingSeconds = table.turnSeconds
        } else {
            currentColor = card.color
            when (card.value) {
                UnoValue.DRAW_TWO -> {
                    val target = nextPosition()
                    // A forced draw also starts the next player's turn and ends the catch window.
                    closeCatchWindow()
                    val drew = penalty(order[target], 2, "+2")
                    if (own.hand.isEmpty()) finish(own, preserveDrawEvent = drew) else beginTurn(nextPosition(2))
                }
                UnoValue.SKIP -> {
                    val skipped = order[nextPosition()]
                    context.broadcast("${skipped.participant.name} 被跳过")
                    if (own.hand.isEmpty()) finish(own) else {
                        recordSkip(skipped)
                        beginTurn(nextPosition(2))
                    }
                }
                UnoValue.REVERSE -> {
                    direction = -direction
                    if (own.hand.isEmpty()) finish(own)
                    else beginTurn(if (order.size == 2) currentPosition else nextPosition())
                }
                else -> if (own.hand.isEmpty()) finish(own) else beginTurn(nextPosition())
            }
        }
        context.changed()
    }

    private fun draw(own: Seat) {
        requireTurn(own, Phase.TURN)
        requireInput(!hasDrawn, "The UNO player has already drawn this turn") {
            "本回合已经摸过牌, 请出刚摸到的牌或选择不要"
        }
        closeCatchWindow()
        own.preannounced = false
        own.announced = false
        val cards = UnoRules.draw(drawPile, discardPile, 1)
        own.hand += cards
        sortHand(own)
        hasDrawn = true
        drawnCard = cards.singleOrNull()?.id
        own.selected = drawnCard
        if (cards.isEmpty()) {
            emptyDrawTurns++
            context.broadcast("${own.participant.name} 无牌可摸, 本回合可选择不要")
        } else {
            emptyDrawTurns = 0
            recordDraw(own, cards.size)
            context.broadcast("${own.participant.name} 摸了 1 张")
        }
        remainingSeconds = table.turnSeconds
        context.changed()
    }

    private fun pass(own: Seat) {
        requireTurn(own, Phase.TURN)
        requireInput(hasDrawn, "The UNO player must draw before passing") { "UNO 不能直接跳过, 请先摸 1 张" }
        own.selected = null
        own.preannounced = false
        context.broadcast("${own.participant.name} 选择不要")
        if (emptyDrawTurns >= order.size && order.all { candidate ->
                candidate.hand.none { UnoRules.matches(it, discardPile.last(), currentColor!!) }
            }) {
            cancel("牌堆已空, 所有玩家均无法接牌, 本局流局, 所有预扣退回")
        } else beginTurn(nextPosition())
        context.changed()
    }

    private fun chooseColor(own: Seat, argument: String?) {
        requireTurn(own, Phase.COLOR)
        val color = UnoColor.entries.firstOrNull { it.assetName == argument?.lowercase(Locale.ROOT) }
            ?: throw InputException("Invalid UNO color: $argument", "请选择 red, yellow, green 或 blue")
        val choice = colorChoice!!
        currentColor = color
        colorChoice = null
        context.broadcast("${own.participant.name} 选择了 ${color.coloredLabel}")
        when {
            choice.opening -> beginTurn(currentPosition)
            choice.card.value == UnoValue.WILD_DRAW_FOUR -> {
                phase = Phase.DRAW_FOUR
                currentPosition = nextPosition()
                hasDrawn = false
                drawnCard = null
                remainingSeconds = table.turnSeconds
                context.broadcast("${current().participant.name} 请选择接受 +4, 或挑战是否合法")
            }
            own.hand.isEmpty() -> finish(own)
            else -> beginTurn(nextPosition())
        }
        context.changed()
    }

    private fun accept(own: Seat) {
        requireTurn(own, Phase.DRAW_FOUR)
        closeCatchWindow()
        val pending = drawFour!!
        val drew = penalty(own, 4, "接受 +4")
        drawFour = null
        val offender = order[pending.offender]
        if (offender.hand.isEmpty()) finish(offender, preserveDrawEvent = drew) else beginTurn(nextPosition())
        context.changed()
    }

    private fun challenge(own: Seat) {
        requireTurn(own, Phase.DRAW_FOUR)
        closeCatchWindow()
        val pending = drawFour!!
        val offender = order[pending.offender]
        // Only the challenger may see this evidence; public views never contain it.
        Bukkit.getPlayer(own.participant.id)?.let { player ->
            sendTableMessage(player,
                "UNO 挑战证据: 出牌前颜色为 ${pending.oldColor.coloredLabel}; ${offender.participant.name} 当时手牌: ${pending.proof.joinToString(", ") { it.coloredLabel }}")
        }
        drawFour = null
        if (pending.legal) {
            context.broadcast("${own.participant.name} 挑战失败, +4 合法, 罚摸 6 张并跳过")
            val drew = penalty(own, 6, "挑战失败")
            if (offender.hand.isEmpty()) finish(offender, preserveDrawEvent = drew) else beginTurn(nextPosition())
        } else {
            context.broadcast("${own.participant.name} 挑战成功, ${offender.participant.name} 当时持有原颜色, 罚摸 4 张")
            penalty(offender, 4, "违规 +4")
            beginTurn(currentPosition)
        }
        context.changed()
    }

    private fun announce(own: Seat) {
        requireInput(active, "The UNO round has not started") { "本局尚未开始" }
        when {
            own.hand.size == 1 -> {
                own.announced = true
                if (catchable == own.participant.id) catchable = null
                context.broadcast("${own.participant.name}: UNO!")
            }
            own.hand.size == 2 && phase == Phase.TURN && own === current() -> {
                own.preannounced = true
                context.broadcast("${own.participant.name} 预先宣告 UNO, 本回合出至 1 张时生效")
            }
            else -> throw InputException("UNO can only be announced with one card, or before playing from two cards on the player's turn",
                "剩 1 张时可宣告 UNO; 轮到你且有 2 张时也可在出牌前宣告")
        }
        context.changed()
    }

    private fun catch(own: Seat) {
        requireInput(active, "The UNO round has not started") { "本局尚未开始" }
        val target = seats.filterNotNull().firstOrNull { it.participant.id == catchable }
            ?: throw InputException("No UNO player can be caught for a missed call", "目前没有可以抓漏叫 UNO 的玩家")
        requireInput(target !== own, "The UNO player cannot catch their own missed call") { "请用 UNO 操作宣告自己的最后一张牌" }
        requireInput(target.hand.size == 1 && !target.announced, "The UNO target has already announced or does not have exactly one card") {
            "该玩家已经叫过 UNO, 或已不剩 1 张"
        }
        catchable = null
        context.broadcast("${own.participant.name} 抓到 ${target.participant.name} 漏叫 UNO, 罚摸 2 张")
        penalty(target, 2, "漏叫 UNO")
        context.changed()
    }

    private fun hint(own: Seat) {
        requireTurn(own, Phase.TURN)
        val candidate = own.hand.filter { playable(own, it) }.minWithOrNull(
            compareBy<UnoCard> {
                when {
                    it.value == UnoValue.WILD_DRAW_FOUR && !UnoRules.canPlayDrawFour(own.hand, currentColor!!) -> 3
                    it.value == UnoValue.WILD_DRAW_FOUR -> 2
                    it.value == UnoValue.WILD -> 1
                    else -> 0
                }
            }.thenByDescending { it.points }.thenBy { it.id }
        )
        own.selected = candidate?.id
        if (candidate == null) {
            own.preannounced = false
            context.changed()
            throw IllegalArgumentException(if (hasDrawn) "刚摸到的牌不能出, 请选择不要" else "没有可接的牌, 请摸 1 张")
        }
        context.changed()
    }

    private fun penalty(own: Seat, count: Int, reason: String): Boolean {
        val cards = UnoRules.draw(drawPile, discardPile, count)
        own.hand += cards
        own.selected = null
        own.announced = false
        own.preannounced = false
        if (catchable == own.participant.id) catchable = null
        if (cards.isNotEmpty()) {
            emptyDrawTurns = 0
            recordDraw(own, cards.size)
        }
        sortHand(own)
        context.broadcast("${own.participant.name} 因 $reason 摸了 ${cards.size} 张${if (cards.size < count) "(牌堆不足, 原应摸 $count 张)" else ""}")
        return cards.isNotEmpty()
    }

    private fun closeCatchWindow() { catchable = null }

    private fun recordSkip(skipped: Seat) {
        skipSequence++
        lastTableSkip = TableSkip(skipSequence, skipped.participant.seat)
    }

    private fun recordDraw(own: Seat, count: Int) {
        drawSequence++
        lastTableDraw = TableDraw(drawSequence, own.participant.seat, count)
    }

    private fun beginTurn(position: Int) {
        phase = Phase.TURN
        currentPosition = position
        hasDrawn = false
        drawnCard = null
        remainingSeconds = table.turnSeconds
        current().selected = null
        current().preannounced = false
    }

    private fun finish(winner: Seat, preserveDrawEvent: Boolean = false) {
        val score = order.filter { it !== winner }.sumOf { UnoRules.score(it.hand) }
        val results = order.associate { it.participant.id to if (it === winner) money(order.size - 1) else money(-1) }
        context.economy.settle(roundId!!, results)
        val ended = TableRoundEndEvent(table, roundId!!, participants,
            listOf(winner.participant), TableRoundEndReason.WIN)
        roundId = null
        lastResult = "${winner.participant.name} 获胜, 本局得分 $score"
        context.broadcast("$lastResult${if (table.bet > 0) ", 净赢 ${format(money(order.size - 1))}, 每名输者输 ${format(table.bet)}" else ""}")
        val completedPile = discardPile.map { face(it) }
        val completedPlay = lastTablePlay
        val completedDraw = if (preserveDrawEvent) lastTableDraw else null
        reset()
        lastRoundPile = completedPile
        lastTablePlay = completedPlay
        lastTableDraw = completedDraw
        Bukkit.getPluginManager().callEvent(ended)
    }

    private fun cancel(message: String) {
        val ended = refundRound()
        lastResult = message
        context.broadcast(message)
        reset()
        ended?.let { Bukkit.getPluginManager().callEvent(it) }
    }

    private fun refundRound(): TableRoundEndEvent? {
        val id = roundId ?: return null // A failed reserve never committed a round.
        context.economy.refund(id)
        roundId = null
        return TableRoundEndEvent(table, id, participants, emptyList(), TableRoundEndReason.CANCELLED)
    }

    private fun reset() {
        pendingBot?.future?.cancel(false)
        pendingBot = null
        phase = Phase.WAITING
        order = emptyList()
        drawPile.clear()
        discardPile.clear()
        lastRoundPile = emptyList()
        lastTablePlay = null
        lastTableSkip = null
        lastTableDraw = null
        direction = 1
        currentPosition = 0
        currentColor = null
        colorChoice = null
        drawFour = null
        hasDrawn = false
        drawnCard = null
        catchable = null
        emptyDrawTurns = 0
        remainingSeconds = 0
        seats.filterNotNull().forEach {
            it.hand.clear()
            it.ready = it.participant.bot
            it.selected = null
            it.announced = false
            it.preannounced = false
        }
    }

    override fun tick() {
        if (!active) {
            val joined = seats.filterNotNull()
            if (joined.size >= 2 && joined.any { it.participant.bot } && joined.any { !it.participant.bot } && joined.all { it.ready }) {
                try {
                    start(joined)
                } catch (error: IllegalArgumentException) {
                    cancel("UNO 开局失败: ${(error as? InputException)?.playerMessage ?: error.message}; 请检查机器人策略服务后重新准备")
                    context.changed()
                }
            }
            return
        }
        // Catching a missed call is a public legal action, including outside one's turn.
        val catcher = if (catchable != null) seats.filterNotNull().firstOrNull {
            it.participant.bot && it.participant.id != catchable
        } else null
        if (catcher != null) {
            catch(catcher)
            return
        }
        val own = current()
        if (own.participant.bot) {
            actBot(own)
            return
        }
        remainingSeconds--
        if (remainingSeconds > 0) {
            context.changed()
            return
        }
        context.broadcast("${own.participant.name} 操作超时")
        when (phase) {
            Phase.TURN -> {
                if (!hasDrawn) draw(own)
                pass(own)
            }
            Phase.COLOR -> {
                val color = UnoColor.entries.maxBy { candidate -> own.hand.count { it.color == candidate } }
                chooseColor(own, color.assetName)
            }
            Phase.DRAW_FOUR -> accept(own)
            Phase.WAITING -> Unit
        }
    }

    private fun actBot(own: Seat) {
        when (phase) {
            Phase.COLOR -> {
                val color = UnoColor.entries.maxBy { candidate -> own.hand.count { it.color == candidate } }
                act(own.participant.id, "color", color.assetName)
            }
            // This rule action never consults the opponent's hand or challenge evidence.
            Phase.DRAW_FOUR -> act(own.participant.id, "accept", null)
            Phase.TURN -> requestBotTurn(own)
            Phase.WAITING -> Unit
        }
    }

    private fun requestBotTurn(own: Seat) {
        if (own.hand.size == 1 && !own.announced) {
            act(own.participant.id, "uno", null)
            return
        }
        val key = BotTurnKey(roundId, phase, own.participant.id, own.hand.map { it.id },
            discardPile.last().id, currentColor!!, hasDrawn, drawnCard)
        val pending = pendingBot
        if (pending != null && pending.key != key) {
            pending.future.cancel(false)
            pendingBot = null
            return
        }
        if (pending == null) {
            try {
                pendingBot = PendingBot(key, context.botAI.decide("uno", botState(own)))
            } catch (error: RuntimeException) {
                failBot(error)
            }
            return
        }
        if (!pending.future.isDone) return
        try {
            val decision = pending.future.join()
            require(decision.action in setOf("play", "draw", "pass")) { "UNO AI returned an unsupported action: ${decision.action}" }
            val card = if (decision.action == "play") {
                val token = decision.argument?.toIntOrNull() ?: throw IllegalArgumentException("UNO AI did not return a card ID")
                own.hand.firstOrNull { it.id == token } ?: throw IllegalArgumentException("UNO AI returned a card not in its hand")
            } else null
            require(card?.value != UnoValue.WILD_DRAW_FOUR || UnoRules.canPlayDrawFour(own.hand, currentColor!!)) { "UNO AI returned an illegal +4" }
            if (decision.action == "play" && own.hand.size == 2 && !own.preannounced) {
                act(own.participant.id, "uno", null)
                return // The completed decision is consumed on the next tick.
            }
            pendingBot = null
            if (card != null) own.selected = card.id
            act(own.participant.id, decision.action, decision.argument)
        } catch (error: RuntimeException) {
            failBot(error)
        }
    }

    /** RLCard receives this bot's hand and public facts; no draw order or opponent hand. */
    private fun botState(own: Seat): Map<String, Any?> {
        val actions = linkedMapOf<String, Map<String, String>>()
        own.hand.filter { card ->
            playable(own, card) && (card.value != UnoValue.WILD_DRAW_FOUR || UnoRules.canPlayDrawFour(own.hand, currentColor!!))
        }.forEach { card ->
            val names = if (card.value.isWild) UnoColor.entries.map { wireColor(it) + "-" + wireValue(card.value) }
                else listOf(wireCard(card))
            names.forEach { action -> actions.putIfAbsent(action, mapOf("action" to "play", "argument" to card.token)) }
        }
        // RLCard's rule agent always draws if draw is listed; expose it only with no playable card.
        if (actions.isEmpty()) actions["draw"] = mapOf("action" to if (hasDrawn) "pass" else "draw")
        return mapOf(
            "hand" to own.hand.map(::wireCard),
            "target" to wireCard(discardPile.last()),
            "current_color" to wireColor(currentColor!!),
            "direction" to direction,
            "players" to order.map { mapOf("seat" to it.participant.seat, "count" to it.hand.size) },
            "legal_actions" to actions.keys.toList(),
            "action_map" to actions
        )
    }

    private fun wireCard(card: UnoCard): String = wireColor(card.color ?: UnoColor.RED) + "-" + wireValue(card.value)
    private fun wireColor(color: UnoColor): String = when (color) {
        UnoColor.RED -> "r"
        UnoColor.YELLOW -> "y"
        UnoColor.GREEN -> "g"
        UnoColor.BLUE -> "b"
    }
    private fun wireValue(value: UnoValue): String = if (value == UnoValue.DRAW_TWO) "draw_2" else value.assetName

    private fun failBot(error: RuntimeException) {
        warning("UNO AI decision failed at table '${table.id}':\n${error.stackTraceToString()}")
        cancel("机器人策略服务请求失败或返回非法操作, 本局 UNO 取消, 所有预扣退回")
        context.changed()
    }

    override fun view(viewer: UUID?): GameView {
        val own = seats.filterNotNull().firstOrNull { it.participant.id == viewer }
        val name = if (active) current().participant.name else ""
        val status = when (phase) {
            Phase.WAITING -> "UNO 等待准备  ${participants.size}/$seatCount 人(至少 2 人)"
            Phase.TURN -> "$name ${if (hasDrawn) "选择出刚摸到的牌或不要" else "出牌或摸牌"}"
            Phase.COLOR -> "$name 选择颜色"
            Phase.DRAW_FOUR -> "$name 接受 +4 或挑战"
        }
        val controls = buildList {
            if (own == null) return@buildList
            when {
                !active -> add(GameControl("ready", if (own.ready) "取消准备" else "准备", "doudizhu:ready"))
                own === current() && phase == Phase.COLOR -> UnoColor.entries.forEach {
                    add(GameControl("color", it.label, button("color_${it.assetName}"), it.assetName))
                }
                own === current() && phase == Phase.DRAW_FOUR -> {
                    add(GameControl("accept", "接受 +4", button("accept"), description = listOf("摸 4 张并跳过本回合")))
                    add(GameControl("challenge", "挑战 +4", button("challenge"), description = listOf("成功: 上家摸 4 张, 你继续出", "失败: 你摸 6 张并跳过")))
                }
                own === current() && phase == Phase.TURN -> {
                    add(GameControl("play", "出牌", "doudizhu:play", description = listOf("每次选中并出一张牌")))
                    if (hasDrawn) add(GameControl("pass", "不要", "doudizhu:pass"))
                    else add(GameControl("draw", "摸 1 张", button("draw"), description = listOf("摸后只能出新摸的牌, 或选择不要")))
                    add(GameControl("hint", "提示", "doudizhu:hint"))
                }
            }
            if (active && ((own.hand.size == 1 && !own.announced) || (own.hand.size == 2 && own === current() && phase == Phase.TURN && !own.preannounced))) {
                add(GameControl("uno", "叫 UNO", button("uno"), description = listOf("漏叫被抓后罚摸 2 张")))
            }
            if (active && catchable != null && catchable != own.participant.id) {
                add(GameControl("catch", "抓漏叫 UNO", button("catch"), description = listOf("须在下一位玩家开始摸牌或出牌前")))
            }
        }
        return GameView(
            status = status,
            players = seats.filterNotNull().map {
                PlayerView(it.participant, when {
                    it.hand.size == 1 && it.announced -> "UNO"
                    it.participant.bot -> "机器人"
                    else -> "玩家"
                }, it.hand.size, it.ready, active && it === current())
            },
            hand = own?.hand?.map { face(it, it.id == own.selected, if (hasDrawn && own === current() && it.id == drawnCard) "(本回合摸到)" else "") }.orEmpty(),
            publicCards = discardPile.lastOrNull()?.let { listOf(face(it)) }.orEmpty(),
            bottomCards = if (active) listOf(CardFace("uno_back", "抽牌堆: ${drawPile.size} 张", "")) else emptyList(),
            controls = controls,
            remainingSeconds = remainingSeconds,
            multiplier = "每名输者 ${format(table.bet)}  单局计分",
            note = if (active) "当前 ${currentColor?.label ?: "待选颜色"}  不叠罚, 不抢出" else lastResult,
            backAsset = "uno_back",
            leaveDescription = listOf("对局中离桌, 断线或超出距离会取消整局", "收费局退回所有玩家本局预扣")
        )
    }

    private fun seat(id: UUID): Seat = seats.filterNotNull().firstOrNull { it.participant.id == id }
        ?: throw InputException("Player '$id' is not seated at UNO table '${table.id}'", "请先加入这张 UNO 牌桌")

    private fun current(): Seat = order[currentPosition]
    private fun nextPosition(steps: Int = 1): Int = Math.floorMod(currentPosition + direction * steps, order.size)

    private fun requireTurn(own: Seat, expected: Phase) {
        requireInput(phase == expected, "UNO table '${table.id}' is in phase $phase; expected $expected") {
            when (phase) {
                Phase.WAITING -> "本局尚未开始"
                Phase.COLOR -> "请先选择颜色"
                Phase.DRAW_FOUR -> "请先接受 +4 或挑战, 不能叠加罚牌"
                Phase.TURN -> "当前为出牌阶段"
            }
        }
        requireInput(own === current(), "It is not player '${own.participant.id}'s turn at UNO table '${table.id}'") {
            "还没轮到你, 请等待 ${current().participant.name}"
        }
    }

    private fun sortHand(own: Seat) { own.hand.sortWith(compareBy<UnoCard> { it.color?.ordinal ?: 4 }.thenBy { it.value.ordinal }.thenBy { it.id }) }
    private fun face(card: UnoCard, selected: Boolean = false, suffix: String = ""): CardFace = CardFace(card.assetId, card.label + suffix, card.token, selected)
    private fun button(key: String): String = "doudizhu:${if (table.skin == "jade") "jade" else "classic"}_uno_button_$key"
    private fun money(factor: Int): Double = BigDecimal.valueOf(table.bet).multiply(BigDecimal.valueOf(factor.toLong())).toDouble()
    private fun format(amount: Double): String = String.format(Locale.ROOT, "%.2f", amount)
}
