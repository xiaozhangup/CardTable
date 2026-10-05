package me.xiaozhangup.cardtable.game.doudizhu

data class Card(val id: Int) {
    init {
        require(id in 0..53) { "Dou Dizhu card ID must be between 0 and 53" }
    }

    val rank: Int get() = if (id < 52) id % 13 + 3 else id - 36
    val label: String
        get() = if (id >= 52) rankLabel(rank) else SUIT_LABELS[id / 13] + rankLabel(rank)
    val assetId: String
        get() = when (id) {
            52 -> "joker_small"
            53 -> "joker_big"
            else -> SUITS[id / 13] + "_" + when (rank) {
                11 -> "j"
                12 -> "q"
                13 -> "k"
                14 -> "a"
                15 -> "2"
                else -> rank.toString()
            }
        }

    companion object {
        private val SUITS = listOf("spade", "heart", "club", "diamond")
        private val SUIT_LABELS = listOf("♠", "♥", "♣", "♦")
        fun deck(): List<Card> = (0..53).map(::Card)
        fun rankLabel(rank: Int): String = when (rank) {
            11 -> "J"
            12 -> "Q"
            13 -> "K"
            14 -> "A"
            15 -> "2"
            16 -> "小王"
            17 -> "大王"
            else -> rank.toString()
        }
    }
}

enum class PlayType(val chineseName: String) {
    SINGLE("单张"),
    PAIR("对子"),
    TRIPLE("三张"),
    TRIPLE_SINGLE("三带一"),
    TRIPLE_PAIR("三带二"),
    STRAIGHT("顺子"),
    PAIR_STRAIGHT("连对"),
    AIRPLANE("飞机"),
    AIRPLANE_SINGLE("飞机带单"),
    AIRPLANE_PAIR("飞机带对"),
    FOUR_TWO_SINGLE("四带二"),
    FOUR_TWO_PAIR("四带两对"),
    BOMB("炸弹"),
    ROCKET("王炸")
}

data class Play(
    val cards: List<Card>,
    /** 与 cards 顺序对应；癞子出牌后在此记录实际点数。 */
    val effectiveRanks: List<Int>,
    val type: PlayType,
    val mainRank: Int,
    val chainLength: Int = 1,
    /** 非炸弹=0，软炸=1，硬炸=2，四癞子炸=3，王炸=4。 */
    val bombTier: Int = 0
) {
    val isBomb: Boolean get() = bombTier > 0
    val label: String
        get() = when (bombTier) {
            1 -> "软炸  ${Card.rankLabel(mainRank)}"
            2 -> "炸弹  ${Card.rankLabel(mainRank)}"
            3 -> "四癞子炸"
            4 -> "王炸"
            else -> "${type.chineseName}  ${Card.rankLabel(mainRank)}"
        }

    fun beats(previous: Play): Boolean {
        if (isBomb || previous.isBomb) {
            if (!isBomb) return false
            if (!previous.isBomb) return true
            if (bombTier != previous.bombTier) return bombTier > previous.bombTier
            return bombTier < 3 && mainRank > previous.mainRank
        }
        return type == previous.type && cards.size == previous.cards.size &&
            chainLength == previous.chainLength && mainRank > previous.mainRank
    }
}
