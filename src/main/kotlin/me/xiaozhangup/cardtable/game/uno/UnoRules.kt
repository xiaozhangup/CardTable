package me.xiaozhangup.cardtable.game.uno

import kotlin.random.Random

/** 经典 108 张 UNO：不叠加罚抽、不抢出、不使用 7-0 换手。 */
object UnoRules {
    fun deck(): List<UnoCard> = (0..107).map(::UnoCard)

    /** 万能 +4 的颜色限制单独检查，以便对局保留挑战机制。 */
    fun matches(card: UnoCard, top: UnoCard, currentColor: UnoColor): Boolean =
        card.value.isWild || card.color == currentColor || card.value == top.value

    /** 同数字或同行动符号不影响 +4 合法性，只检查出牌前手中是否有当前颜色。 */
    fun canPlayDrawFour(hand: List<UnoCard>, currentColor: UnoColor): Boolean =
        hand.none { it.color == currentColor }

    /** 牌堆见底时保留弃牌堆顶牌，把其余弃牌洗回抽牌堆。牌堆末尾为堆顶。 */
    fun refill(
        draw: MutableList<UnoCard>,
        discard: MutableList<UnoCard>,
        random: Random = Random.Default
    ): Boolean {
        if (draw.isNotEmpty() || discard.size < 2) return false
        val top = discard.removeAt(discard.lastIndex)
        draw.addAll(discard.shuffled(random))
        discard.clear()
        discard += top
        return true
    }

    /** 极端情况下所有其他牌都在手中，返回实际能抽到的牌，不复制牌。 */
    fun draw(
        draw: MutableList<UnoCard>,
        discard: MutableList<UnoCard>,
        count: Int,
        random: Random = Random.Default
    ): List<UnoCard> {
        require(count >= 0) { "抽牌数量不能为负数" }
        val result = ArrayList<UnoCard>(count)
        repeat(count) {
            if (draw.isEmpty() && !refill(draw, discard, random)) return result
            result += draw.removeAt(draw.lastIndex)
        }
        return result
    }

    fun score(hand: List<UnoCard>): Int = hand.sumOf { it.points }
}
