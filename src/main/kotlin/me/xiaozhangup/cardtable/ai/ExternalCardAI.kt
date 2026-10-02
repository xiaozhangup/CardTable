package me.xiaozhangup.cardtable.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.xiaozhangup.cardtable.api.BotDecision
import me.xiaozhangup.cardtable.api.CardAI
import org.bukkit.plugin.java.JavaPlugin
import java.io.BufferedReader
import java.io.BufferedWriter
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** A JSON-lines child process. Neither startup nor inference blocks the Paper thread. */
class ExternalCardAI(private val plugin: JavaPlugin) : CardAI {
    private val gson = Gson()
    private val executor = Executors.newSingleThreadExecutor { runnable -> Thread(runnable, "CardTable-AI").apply { isDaemon = true } }
    @Volatile private var process: Process? = null
    @Volatile private var ready = false
    private lateinit var input: BufferedWriter
    private lateinit var output: BufferedReader
    private var sequence = 0L
    @Volatile private var closed = false
    override val available: Boolean get() = ready && process?.isAlive == true

    fun start() {
        listOf("ai/bridge.py", "ai/requirements.txt").forEach { path ->
            if (!plugin.dataFolder.resolve(path).exists()) plugin.saveResource(path, false)
        }
        if (!plugin.config.getBoolean("ai.enabled", false)) return
        val command = plugin.config.getStringList("ai.command").map { it.replace("{data}", plugin.dataFolder.absolutePath) }
        require(command.isNotEmpty()) { "ai.command 不能为空。" }
        val startup = CompletableFuture<Unit>()
        executor.execute {
            try {
                val child = ProcessBuilder(command).directory(plugin.dataFolder)
                    .redirectError(ProcessBuilder.Redirect.appendTo(plugin.dataFolder.resolve("ai/process.log"))).start()
                process = child
                if (closed) { child.destroyForcibly(); error("插件已关闭") }
                input = child.outputStream.bufferedWriter(Charsets.UTF_8)
                output = child.inputStream.bufferedReader(Charsets.UTF_8)
                val line = output.readLine() ?: error("AI 程序未发送就绪消息便退出")
                val message = JsonParser.parseString(line).asJsonObject
                check(message.get("ready")?.asBoolean == true) { "AI 程序未发送 ready=true" }
                ready = true
                plugin.logger.info("外部 AI 已就绪：${message.get("engine")?.asString ?: "自定义程序"}。")
                startup.complete(Unit)
            } catch (error: Exception) { startup.completeExceptionally(error) }
        }
        startup.orTimeout(30, TimeUnit.SECONDS).whenComplete { _, error ->
            if (error != null) { stopProcess(); plugin.logger.warning("外部 AI 启动失败：${error.message}；详见 ai/process.log。") }
        }
    }

    override fun decide(game: String, state: Map<String, Any?>): CompletableFuture<BotDecision> {
        val child = process
        if (!available || child == null) return CompletableFuture.failedFuture(IllegalStateException("外部 AI 未就绪"))
        val result = CompletableFuture<BotDecision>()
        executor.execute {
            if (result.isDone) return@execute
            try {
                val id = ++sequence
                input.write(gson.toJson(mapOf("id" to id, "game" to game, "state" to state)))
                input.newLine()
                input.flush()
                val line = output.readLine() ?: error("AI 程序退出")
                val response = JsonParser.parseString(line).asJsonObject
                check(response.get("id")?.asLong == id) { "AI 响应编号不匹配" }
                response.get("error")?.let { error(it.asString) }
                val action = response.get("action")?.asString ?: error("AI 响应缺少 action")
                val argument = response.get("argument")?.takeUnless { it.isJsonNull }?.asString
                result.complete(BotDecision(action, argument))
            } catch (error: Exception) { result.completeExceptionally(error) }
        }
        return result.orTimeout(10, TimeUnit.SECONDS).whenComplete { _, error ->
            if (error != null) {
                if (error is TimeoutException && process === child) stopProcess()
                plugin.logger.warning("外部 AI 决策失败：${error.message}；本局将取消。")
            }
        }
    }

    private fun stopProcess() { ready = false; process?.destroyForcibly(); process = null }
    fun close() { closed = true; stopProcess(); executor.shutdownNow() }
}
