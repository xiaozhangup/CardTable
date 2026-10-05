package me.xiaozhangup.cardtable.util.ext

import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.crab.command.Notify
import me.xiaozhangup.crab.command.PermissionDefault
import me.xiaozhangup.crab.command.component.CommandBase
import me.xiaozhangup.crab.task.Task
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

// Keep every shared service bound to CardTable's own Crab instance.
internal fun command(
    name: String,
    aliases: List<String> = emptyList(),
    description: String = "",
    usage: String = "",
    permission: String = "",
    permissionMessage: String = "",
    permissionDefault: PermissionDefault = PermissionDefault.OP,
    permissionChildren: Map<String, PermissionDefault> = emptyMap(),
    newParser: Boolean = false,
    notify: Notify? = null,
    commandBuilder: CommandBase.() -> Unit,
) = CardTablePlugin.instance.crab.command(
    name, aliases, description, usage, permission, permissionMessage,
    permissionDefault, permissionChildren, newParser, notify, commandBuilder
)

internal fun submitTask(
    now: Boolean = false, async: Boolean = false, delay: Long = 0, period: Long = 0,
    executor: Task.() -> Unit
): Task = CardTablePlugin.instance.crab.submitTask(now, async, delay, period, executor)

internal fun submitSerialTask(
    executor: ScheduledExecutorService, delay: Long = 0,
    unit: TimeUnit = TimeUnit.MILLISECONDS, runnable: Runnable
): Task = CardTablePlugin.instance.crab.submitTask(executor, delay, unit, runnable)

internal fun info(vararg messages: Any?) = CardTablePlugin.instance.crab.info(*messages)
internal fun warning(vararg messages: Any?) = CardTablePlugin.instance.crab.warning(*messages)
internal fun severe(vararg messages: Any?) = CardTablePlugin.instance.crab.severe(*messages)
internal fun getDataFolder() = CardTablePlugin.instance.crab.getDataFolder()
internal fun releaseResourceFile(path: String, replace: Boolean = false) =
    CardTablePlugin.instance.crab.releaseResourceFile(path, replace)
