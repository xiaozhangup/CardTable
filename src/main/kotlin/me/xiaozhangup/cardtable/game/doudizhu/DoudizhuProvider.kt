package me.xiaozhangup.cardtable.game.doudizhu

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.CardFace
import me.xiaozhangup.cardtable.api.GameContext
import me.xiaozhangup.cardtable.api.GameControl
import me.xiaozhangup.cardtable.api.GameProvider
import me.xiaozhangup.cardtable.api.GameSession
import me.xiaozhangup.cardtable.api.GameView
import me.xiaozhangup.cardtable.api.Participant
import me.xiaozhangup.cardtable.api.PlayerView
import me.xiaozhangup.cardtable.api.TableCardPile
import me.xiaozhangup.cardtable.api.TablePileLayout
import me.xiaozhangup.cardtable.api.TableHeader
import me.xiaozhangup.cardtable.api.TableHeaderView
import me.xiaozhangup.cardtable.api.TablePlay
import me.xiaozhangup.cardtable.api.TablePlayEvents
import me.xiaozhangup.cardtable.api.TablePlayerJoin
import me.xiaozhangup.cardtable.api.TableTurnOrder
import me.xiaozhangup.cardtable.api.TurnDirection
import me.xiaozhangup.cardtable.api.TurnDirectionPlacement
import me.xiaozhangup.cardtable.table.TableConfig
import me.xiaozhangup.cardtable.util.requireInput
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.entity.Player
import java.util.Locale
import java.util.UUID

/** 内置玩法的全部规则状态都留在 session 内，共用牌桌层只读取 GameView。 */
class DoudizhuProvider : GameProvider {
    override val id: String = "doudizhu"
    override val displayName: String = "斗地主"

    override fun validate(table: TableConfig) {
        requireInput(table.options.keys.all { it == "laizi" }, "Dou Dizhu only supports the 'laizi' option") {
            "斗地主只支持 laizi 选项"
        }
        requireInput(table.options["laizi"] == null || table.options["laizi"] in setOf("true", "false"), "The 'laizi' option must be true or false") {
            "laizi 只能为 true 或 false"
        }
    }

    override fun create(context: GameContext, table: TableConfig): GameSession = DoudizhuSession(context, table)
}

private class DoudizhuSession(context: GameContext, override val table: TableConfig) : GameSession, TablePlayerJoin, TableCardPile, TableTurnOrder, TablePlayEvents, TableHeader {
    private val manager = DoudizhuController(context.plugin as CardTablePlugin).apply {
        onChange = { context.changed() }
        onMessage = { _, message -> context.broadcast(message) }
        onVoice = { state, event ->
            val voice = when (event) {
                "bid_0" -> "bid_0"
                // 上游只有“叫地主”，具体叫分继续由文字显示。
                "bid_1", "bid_2", "bid_3" -> "bid_call"
                "pass" -> "pass"
                // Controller 已写入 previous；mainRank 包含癞子的实际解释。
                else -> if (event.startsWith("play_")) state.previous!!.voiceKey() else null
            }
            // 无对应录音的开局/结算不播报，最后一手牌也不会被结算语音重叠。
            if (voice != null) {
                val bank = context.plugin.settings.doudizhuVoice
                context.voice("doudizhu:voice.$bank.$voice")
            }
        }
        add(table)
    }
    private val runtime get() = manager.tables.getValue(table.id)

    override val participants: List<Participant>
        get() = runtime.seats.filterNotNull().map(::participant)
    override val active: Boolean get() = runtime.phase != Phase.WAITING
    // Increasing seat angles (x = sin, z = cos) run counterclockwise from above.
    override val turnDirection: TurnDirection? get() = if (active) TurnDirection.COUNTERCLOCKWISE else null
    override val directionPlacement: TurnDirectionPlacement = TurnDirectionPlacement.HEADER
    override val lastTablePlay: TablePlay? get() = runtime.playedPlays.lastOrNull()?.let { play ->
        TablePlay(runtime.playSequence, runtime.lastPlaySeat, publicFaces(play, runtime.lastPlayWildRank))
    }
    override val seatCount: Int = 3
    override val tablePileLayout: TablePileLayout = TablePileLayout.SCATTERED
    override val supportsBots: Boolean = true
    override fun addBot(id: UUID, name: String): Participant = participant(manager.joinBot(id, name, table.id))
    override fun removeBot(id: UUID) = manager.removeBot(id)

    override fun join(player: Player) = manager.join(player, table.id)
    override fun join(player: Player, prepareSeat: (Participant) -> Unit) =
        manager.join(player, table.id) { prepareSeat(participant(it)) }
    override fun leave(player: Player) = manager.leave(player)
    override fun tick() = manager.tick()
    override fun close() = manager.shutdown()

    override fun tableCards(): List<CardFace> = runtime.playedPlays.flatMap { play ->
        publicFaces(play, runtime.lastPlayWildRank)
    }

    override fun tableHeader(): TableHeaderView {
        val joined = runtime.seats.filterNotNull()
        val stage = when (runtime.phase) {
            Phase.WAITING -> if (joined.size < seatCount) "待入座" else "待准备"
            Phase.BIDDING -> "叫分"
            Phase.PLAYING -> "出牌"
        }
        val title = Component.text(if (table.options["laizi"] == "true") "癞子斗地主" else "斗地主", TextColor.color(0x9BC7B6))
            .append(Component.text("  ", NamedTextColor.DARK_GRAY))
            .append(Component.text(stage, NamedTextColor.GRAY))
            .decoration(TextDecoration.ITALIC, false)
        var detail = when (runtime.phase) {
            Phase.WAITING -> {
                val population = Component.text("人数 ", NamedTextColor.GRAY)
                    .append(Component.text("${joined.size}/$seatCount", NamedTextColor.WHITE))
                if (joined.size < seatCount) population else population
                    .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                    .append(Component.text("准备 ", NamedTextColor.GRAY))
                    .append(Component.text("${joined.count { it.ready }}/${joined.size}", NamedTextColor.WHITE))
            }
            Phase.BIDDING -> Component.text("最高分 ", NamedTextColor.GRAY)
                .append(Component.text(runtime.highestBid.toString(), NamedTextColor.WHITE))
            Phase.PLAYING -> Component.text("倍率 ", NamedTextColor.GRAY)
                .append(Component.text("×${runtime.highestBid.toLong() * runtime.multiplier}", NamedTextColor.WHITE))
        }
        if (active) runtime.wildRank?.let { rank ->
            detail = detail
                .append(Component.text("  ", NamedTextColor.DARK_GRAY))
                .append(Component.text("癞子 ", NamedTextColor.GRAY))
                .append(Component.text(Card.rankLabel(rank), NamedTextColor.WHITE))
        }
        return TableHeaderView(title, detail.decoration(TextDecoration.ITALIC, false))
    }

    override fun act(player: Player, action: String, argument: String?) {
        when (action) {
            "ready" -> manager.ready(player)
            "bid" -> manager.bid(player, argument?.toIntOrNull() ?: throw IllegalArgumentException("请选择 0, 1, 2, 3 分"))
            "play" -> manager.play(player)
            "pass" -> manager.pass(player)
            "hint" -> manager.hint(player)
            "select" -> {
                val seat = runtime.seats.firstOrNull { it?.playerId == player.uniqueId }
                    ?: throw IllegalArgumentException("请先加入这张牌桌")
                require(runtime.phase == Phase.BIDDING || runtime.phase == Phase.PLAYING) { "本局尚未发牌" }
                val id = argument?.toIntOrNull() ?: throw IllegalArgumentException("请选择手中的一张牌")
                require(seat.hand.any { it.id == id }) { "这张牌不在你的手牌中" }
                if (!seat.selected.add(id)) seat.selected.remove(id)
                manager.onChange(runtime)
            }
            else -> throw IllegalArgumentException("未知操作: $action")
        }
    }

    override fun view(viewer: UUID?): GameView {
        val seats = runtime.seats.filterNotNull()
        val ownSeat = seats.firstOrNull { it.playerId == viewer }
        val previous = runtime.previous
        val currentName = if (active) runtime.seats[runtime.currentSeat]!!.playerName else ""
        val status = when (runtime.phase) {
            Phase.WAITING -> "等待准备  ${seats.size}/3 人"
            Phase.BIDDING -> "$currentName 叫地主  当前 ${runtime.highestBid} 分"
            Phase.PLAYING -> "$currentName 出牌"
        }
        val players = seats.map { seat ->
            PlayerView(
                participant(seat),
                role = when (runtime.phase) {
                    Phase.WAITING -> if (seat.bot) "机器人" else "玩家"
                    Phase.BIDDING -> "待定"
                    Phase.PLAYING -> if (seat.index == runtime.landlord) "地主" else "农民"
                },
                count = seat.hand.size,
                ready = seat.ready,
                current = active && runtime.currentSeat == seat.index
            )
        }
        val hand = ownSeat?.let { seat -> seat.hand.map { card -> face(card, selected = card.id in seat.selected) } }.orEmpty()
        val publicCards = previous?.cards?.mapIndexed { index, card -> face(card, effectiveRank = previous.effectiveRanks[index]) }.orEmpty()
        val bottomCards = when (runtime.phase) {
            Phase.WAITING -> emptyList()
            Phase.BIDDING -> runtime.bottom.map { CardFace("back", "未公开的底牌", "") }
            Phase.PLAYING -> runtime.bottom.map { face(it) }
        }
        val controls = when {
            ownSeat == null -> emptyList()
            runtime.phase == Phase.WAITING -> listOf(GameControl("ready", if (ownSeat.ready) "取消准备" else "准备", "doudizhu:ready"))
            runtime.currentSeat != ownSeat.index -> emptyList()
            runtime.phase == Phase.BIDDING -> (0..3).filter { it == 0 || it > runtime.highestBid }.map { score ->
                GameControl("bid", if (score == 0) "不叫" else "$score 分", "doudizhu:bid_$score", score.toString())
            }
            else -> buildList {
                add(GameControl("play", "出牌", "doudizhu:play", description = listOf("先点击手牌选中, 再出牌")))
                if (previous != null) add(GameControl("pass", "不要", "doudizhu:pass"))
                add(GameControl("hint", "提示", "doudizhu:hint", description = listOf("选中一组可以出的牌")))
            }
        }
        val note = buildList {
            runtime.wildRank?.let { add("癞子: ${Card.rankLabel(it)}") }
            previous?.let {
                add("上一手: ${runtime.seats[runtime.previousSeat]!!.playerName}  ${it.label}")
            }
        }.joinToString("; ")
        return GameView(
            status = status,
            players = players,
            hand = hand,
            publicCards = publicCards,
            bottomCards = bottomCards,
            controls = controls,
            remainingSeconds = runtime.remainingSeconds,
            multiplier = "底注 ${String.format(Locale.ROOT, "%.2f", table.bet)} × 叫分 ${runtime.highestBid} × 倍数 ${runtime.multiplier}",
            note = note,
            leaveDescription = listOf("游戏中离桌按所属阵营判负", "叫分阶段离桌取消并退款")
        )
    }

    private fun participant(seat: Seat): Participant = Participant(seat.playerId, seat.playerName, seat.index, seat.bot)

    private fun publicFaces(play: Play, wildRank: Int?): List<CardFace> = play.cards.mapIndexed { index, card ->
        face(card, effectiveRank = play.effectiveRanks[index], wildRank = wildRank)
    }

    private fun face(card: Card, effectiveRank: Int = card.rank, selected: Boolean = false, wildRank: Int? = runtime.wildRank): CardFace {
        val name = buildString {
            append(card.label)
            if (card.rank == wildRank) append("(癞子)")
            if (effectiveRank != card.rank) append(" → ${Card.rankLabel(effectiveRank)}")
        }
        val shownAsset = if (effectiveRank == card.rank) card.assetId else Card(card.id / 13 * 13 + effectiveRank - 3).assetId
        return CardFace(shownAsset, name, card.id.toString(), selected)
    }
}

private fun Play.voiceKey(): String = when (type) {
    PlayType.SINGLE -> "single_$mainRank"
    PlayType.PAIR -> "pair_$mainRank"
    PlayType.TRIPLE -> "triple_$mainRank"
    PlayType.TRIPLE_SINGLE -> "triple_single"
    PlayType.TRIPLE_PAIR -> "triple_pair"
    PlayType.STRAIGHT -> "straight"
    PlayType.PAIR_STRAIGHT -> "pair_straight"
    PlayType.AIRPLANE, PlayType.AIRPLANE_SINGLE, PlayType.AIRPLANE_PAIR -> "airplane"
    PlayType.FOUR_TWO_SINGLE -> "four_single"
    PlayType.FOUR_TWO_PAIR -> "four_pair"
    PlayType.BOMB -> "bomb"
    PlayType.ROCKET -> "rocket"
}
