package me.xiaozhangup.cardtable.api

import net.kyori.adventure.text.Component

/** Optional public table summary. Both lines must be short and contain no private hand data. */
interface TableHeader {
    fun tableHeader(): TableHeaderView
}

/** One line for game and phase, one for the public information needed in that phase. */
data class TableHeaderView(val title: Component, val detail: Component)
