package me.xiaozhangup.cardtable.api

import java.util.concurrent.CompletableFuture

data class BotDecision(val action: String, val argument: String? = null)

/** Only submit the actor's own hand and public state. Apply the result on the server thread. */
interface CardAI {
    val available: Boolean
    fun decide(game: String, state: Map<String, Any?>): CompletableFuture<BotDecision>
}
