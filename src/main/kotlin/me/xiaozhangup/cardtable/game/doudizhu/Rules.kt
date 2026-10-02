package me.xiaozhangup.cardtable.game.doudizhu

/**
 * 每次只匹配有限牌型模板，不枚举癞子的全部赋值。
 * 单张翅膀允许对子；对子翅膀必须为不同点数，所有翅膀都不能占用主体点数。
 */
object Rules {
    private enum class Wings { NONE, SINGLE, PAIR }
    private data class Pattern(
        val type: PlayType,
        val mainRank: Int,
        val chainLength: Int,
        val core: IntArray,
        val wings: Wings = Wings.NONE,
        val wingCount: Int = 0
    )

    private val weakestFirst = compareBy<Play> { it.bombTier }
        .thenBy { it.type.ordinal }.thenBy { it.mainRank }
        .thenBy { it.effectiveRanks.joinToString(",") }

    /** 解释整组选牌；有上一手时，返回最小的合法压牌解释。 */
    fun resolve(cards: List<Card>, wildRank: Int?, previous: Play? = null): Play? {
        if (cards.isEmpty() || cards.size > 20 || cards.map { it.id }.toSet().size != cards.size) return null
        val source = Source(cards, wildRank)
        var best: Play? = null
        patterns(cards.size) { pattern ->
            val play = match(source, pattern, exact = true) ?: return@patterns
            if (previous != null && !play.beats(previous)) return@patterns
            if (best == null || weakestFirst.compare(play, best!!) < 0) best = play
        }
        return best
    }

    /** 有上一手时优先同型小牌，其次最小炸弹；领出时优先出较长的普通牌型。 */
    fun hint(hand: List<Card>, wildRank: Int?, previous: Play? = null): Play? {
        if (hand.isEmpty()) return null
        val source = Source(hand, wildRank)
        if (previous == null) {
            // 能一次出完时，包括炸弹、王炸，直接收下本局。
            resolve(hand, wildRank)?.let { return it }
            for (size in minOf(hand.size, 20) downTo 1) {
                var best: Play? = null
                patterns(size) { pattern ->
                    if (pattern.type == PlayType.BOMB || pattern.type == PlayType.ROCKET) return@patterns
                    val play = match(source, pattern, exact = false) ?: return@patterns
                    if (best == null || weakestFirst.compare(play, best!!) < 0) best = play
                }
                if (best != null) return best
            }
            return null
        }
        var best: Play? = null
        val sizes = linkedSetOf(previous.cards.size, 4, 2)
        for (size in sizes) {
            if (size > hand.size) continue
            patterns(size) { pattern ->
                if (pattern.type != previous.type && pattern.type != PlayType.BOMB && pattern.type != PlayType.ROCKET) return@patterns
                val play = match(source, pattern, exact = false) ?: return@patterns
                if (!play.beats(previous)) return@patterns
                if (best == null || weakestFirst.compare(play, best!!) < 0) best = play
            }
        }
        return best
    }

    /** Finite legal templates offered to an external agent; the agent cannot invent cards. */
    internal fun legalPlays(hand: List<Card>, wildRank: Int?, previous: Play?): List<Play> {
        val source = Source(hand, wildRank)
        val plays = mutableListOf<Play>()
        val sizes = if (previous == null) (1..minOf(20, hand.size)).toList() else listOf(previous.cards.size, 4, 2).distinct()
        for (size in sizes) {
            if (size > hand.size) continue
            patterns(size) { pattern ->
                if (previous != null && pattern.type != previous.type && pattern.type != PlayType.BOMB && pattern.type != PlayType.ROCKET) return@patterns
                val play = match(source, pattern, exact = false) ?: return@patterns
                if (previous == null || play.beats(previous)) plays += play
            }
        }
        return plays.distinctBy { it.cards.map(Card::id) to it.effectiveRanks }.sortedWith(weakestFirst)
    }

    private class Source(val cards: List<Card>, val wildRank: Int?) {
        val wild = if (wildRank == null) emptyList() else cards.filter { it.rank == wildRank }.sortedBy { it.id }
        val natural = cards.filter { it.rank != wildRank }.groupBy { it.rank }
        val counts = IntArray(18).also { array -> natural.forEach { (rank, list) -> array[rank] = list.size } }
    }

    private fun match(source: Source, pattern: Pattern, exact: Boolean): Play? {
        val target = pattern.core.copyOf()
        var wildNeeded = 0
        for (rank in 3..17) {
            if (exact && target[rank] > 0 && source.counts[rank] > target[rank]) return null
            val missing = (target[rank] - source.counts[rank]).coerceAtLeast(0)
            if (rank >= 16 && missing > 0) return null
            wildNeeded += missing
        }
        if (wildNeeded > source.wild.size) return null

        when (pattern.wings) {
            Wings.NONE -> {
                if (exact && (3..17).any { target[it] == 0 && source.counts[it] > 0 }) return null
            }
            Wings.SINGLE -> {
                var remaining = pattern.wingCount
                for (rank in 3..17) {
                    if (pattern.core[rank] != 0) continue
                    val count = if (exact) source.counts[rank] else minOf(source.counts[rank], remaining)
                    target[rank] = count
                    remaining -= count
                }
                if (remaining < 0 || wildNeeded + remaining > source.wild.size) return null
                // 癞子优先保留原点数；点数在主体内时，改作最小的可用翅膀。
                val wingRanks = buildList {
                    source.wildRank?.let { if (pattern.core[it] == 0) add(it) }
                    addAll((3..15).filter { pattern.core[it] == 0 && it != source.wildRank })
                }
                for (rank in wingRanks) {
                    val add = minOf(remaining, 4 - target[rank])
                    target[rank] += add
                    remaining -= add
                    if (remaining == 0) break
                }
                if (remaining != 0) return null
            }
            Wings.PAIR -> {
                val ranks = (3..15).filter { pattern.core[it] == 0 }
                val chosen: List<Int>
                if (exact) {
                    if ((16..17).any { source.counts[it] > 0 }) return null
                    if (ranks.any { source.counts[it] > 2 }) return null
                    val required = ranks.filter { source.counts[it] > 0 }
                    if (required.size > pattern.wingCount) return null
                    chosen = required + ranks.filter { source.counts[it] == 0 }.take(pattern.wingCount - required.size)
                } else {
                    chosen = ranks.sortedWith(compareBy<Int> { (2 - source.counts[it]).coerceAtLeast(0) }.thenBy { it })
                        .take(pattern.wingCount)
                }
                for (rank in chosen) {
                    target[rank] = 2
                    wildNeeded += (2 - source.counts[rank]).coerceAtLeast(0)
                }
                if (chosen.size != pattern.wingCount || wildNeeded > source.wild.size) return null
            }
        }

        if (exact && target.sum() != source.cards.size) return null
        val selected = ArrayList<Card>(target.sum())
        val effectiveById = HashMap<Int, Int>()
        val replacements = ArrayList<Int>(4)
        for (rank in 3..17) {
            val fixed = source.natural[rank].orEmpty().take(target[rank])
            for (card in fixed) {
                selected += card
                effectiveById[card.id] = rank
            }
            repeat(target[rank] - fixed.size) { replacements += rank }
        }
        if (replacements.size > source.wild.size) return null
        // 同点数优先，保证 UI 中不必要的癞子变化尽量少。
        replacements.sortWith(compareBy<Int> { if (it == source.wildRank) 0 else 1 }.thenBy { it })
        for ((index, rank) in replacements.withIndex()) {
            val card = source.wild[index]
            selected += card
            effectiveById[card.id] = rank
        }
        if (exact && selected.size != source.cards.size) return null
        selected.sortWith(compareBy<Card> { it.rank }.thenBy { it.id })
        val effective = selected.map { effectiveById.getValue(it.id) }
        val tier = when (pattern.type) {
            PlayType.ROCKET -> 4
            PlayType.BOMB -> when {
                replacements.size == 4 -> 3
                selected.indices.any { selected[it].rank != effective[it] } -> 1
                else -> 2
            }
            else -> 0
        }
        if (tier == 3 && pattern.mainRank != source.wildRank) return null
        return Play(selected, effective, pattern.type, pattern.mainRank, pattern.chainLength, tier)
    }

    private fun patterns(size: Int, emit: (Pattern) -> Unit) {
        fun core(rank: Int, count: Int): IntArray = IntArray(18).also { it[rank] = count }
        when (size) {
            1 -> for (rank in 3..17) emit(Pattern(PlayType.SINGLE, rank, 1, core(rank, 1)))
            2 -> {
                for (rank in 3..15) emit(Pattern(PlayType.PAIR, rank, 1, core(rank, 2)))
                emit(Pattern(PlayType.ROCKET, 17, 1, IntArray(18).also { it[16] = 1; it[17] = 1 }))
            }
            3 -> for (rank in 3..15) emit(Pattern(PlayType.TRIPLE, rank, 1, core(rank, 3)))
            4 -> for (rank in 3..15) {
                emit(Pattern(PlayType.TRIPLE_SINGLE, rank, 1, core(rank, 3), Wings.SINGLE, 1))
                emit(Pattern(PlayType.BOMB, rank, 1, core(rank, 4)))
            }
            5 -> for (rank in 3..15) emit(Pattern(PlayType.TRIPLE_PAIR, rank, 1, core(rank, 3), Wings.PAIR, 1))
            6 -> for (rank in 3..15) emit(Pattern(PlayType.FOUR_TWO_SINGLE, rank, 1, core(rank, 4), Wings.SINGLE, 2))
            8 -> for (rank in 3..15) emit(Pattern(PlayType.FOUR_TWO_PAIR, rank, 1, core(rank, 4), Wings.PAIR, 2))
        }
        if (size in 5..12) {
            for (start in 3..(15 - size)) {
                val target = IntArray(18)
                for (rank in start until start + size) target[rank] = 1
                emit(Pattern(PlayType.STRAIGHT, start + size - 1, size, target))
            }
        }
        if (size >= 6 && size % 2 == 0) {
            val length = size / 2
            for (start in 3..(15 - length)) {
                val target = IntArray(18)
                for (rank in start until start + length) target[rank] = 2
                emit(Pattern(PlayType.PAIR_STRAIGHT, start + length - 1, length, target))
            }
        }
        for ((perGroup, type, wings) in listOf(
            Triple(3, PlayType.AIRPLANE, Wings.NONE),
            Triple(4, PlayType.AIRPLANE_SINGLE, Wings.SINGLE),
            Triple(5, PlayType.AIRPLANE_PAIR, Wings.PAIR)
        )) {
            if (size < perGroup * 2 || size % perGroup != 0) continue
            val length = size / perGroup
            for (start in 3..(15 - length)) {
                val target = IntArray(18)
                for (rank in start until start + length) target[rank] = 3
                emit(Pattern(type, start + length - 1, length, target, wings, if (wings == Wings.NONE) 0 else length))
            }
        }
    }
}
