package me.xiaozhangup.cardtable.util

/** Shared validation can reach both player feedback and English server diagnostics. */
internal class InputException(message: String, val playerMessage: String, cause: Throwable? = null) :
    IllegalArgumentException(message, cause)

internal inline fun requireInput(value: Boolean, message: String, playerMessage: () -> String) {
    if (!value) throw InputException(message, playerMessage())
}
