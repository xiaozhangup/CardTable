package me.xiaozhangup.cardtable.game.uno

enum class UnoColor(val assetName: String, val label: String, private val colorCode: Char) {
    RED("red", "红色", 'c'),
    YELLOW("yellow", "黄色", 'e'),
    GREEN("green", "绿色", 'a'),
    BLUE("blue", "蓝色", '9');

    val coloredLabel: String get() = "§$colorCode$label§r"
}

enum class UnoValue(val assetName: String, val label: String, val number: Int? = null) {
    ZERO("0", "0", 0),
    ONE("1", "1", 1),
    TWO("2", "2", 2),
    THREE("3", "3", 3),
    FOUR("4", "4", 4),
    FIVE("5", "5", 5),
    SIX("6", "6", 6),
    SEVEN("7", "7", 7),
    EIGHT("8", "8", 8),
    NINE("9", "9", 9),
    SKIP("skip", "跳过"),
    REVERSE("reverse", "反转"),
    DRAW_TWO("draw_two", "+2"),
    WILD("wild", "万能变色"),
    WILD_DRAW_FOUR("wild_draw_four", "万能 +4");

    val isWild: Boolean get() = this == WILD || this == WILD_DRAW_FOUR
    val points: Int get() = number ?: if (isWild) 50 else 20
}

/** 每张实体牌拥有唯一编号；相同牌面也能分别选择、抽取和弃置。 */
data class UnoCard(val id: Int) {
    init {
        require(id in 0..107) { "UNO card ID must be between 0 and 107" }
    }

    val color: UnoColor? get() = if (id < 100) UnoColor.entries[id / 25] else null
    val value: UnoValue
        get() = when {
            id >= 104 -> UnoValue.WILD_DRAW_FOUR
            id >= 100 -> UnoValue.WILD
            else -> when (val offset = id % 25) {
                0 -> UnoValue.ZERO
                in 1..18 -> UnoValue.entries[(offset + 1) / 2]
                19, 20 -> UnoValue.SKIP
                21, 22 -> UnoValue.REVERSE
                else -> UnoValue.DRAW_TWO
            }
        }

    val token: String get() = id.toString()
    val points: Int get() = value.points
    val label: String get() = color?.let { "${it.label} ${value.label}" } ?: value.label
    val coloredLabel: String get() = color?.let { "${it.coloredLabel} ${value.label}" } ?: value.label
    val assetId: String
        get() = color?.let { "uno_${it.assetName}_${value.assetName}" } ?: "uno_${value.assetName}"
}
