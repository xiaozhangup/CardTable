package me.xiaozhangup.cardtable.game.uno

import me.xiaozhangup.cardtable.api.GameContext
import me.xiaozhangup.cardtable.api.GameProvider
import me.xiaozhangup.cardtable.api.GameSession
import me.xiaozhangup.cardtable.table.TableConfig

class UnoProvider : GameProvider {
    override val id: String = "uno"
    override val displayName: String = "UNO"

    override fun validate(table: TableConfig) {
        require(table.options.keys.all { it == "seats" }) { "UNO 只支持 seats 选项。" }
        require(table.options["seats"] == null || table.options.getValue("seats").toIntOrNull() in 2..10) {
            "UNO 的 seats 必须为 2..10，默认 4。"
        }
    }

    override fun create(context: GameContext, table: TableConfig): GameSession = UnoSession(context, table)
}
