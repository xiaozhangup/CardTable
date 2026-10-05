package me.xiaozhangup.cardtable.ai

import com.google.gson.Gson
import com.google.gson.JsonParser
import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.api.BotDecision
import me.xiaozhangup.cardtable.api.CardAI
import me.xiaozhangup.cardtable.util.ext.getDataFolder
import me.xiaozhangup.cardtable.util.ext.info
import me.xiaozhangup.cardtable.util.ext.releaseResourceFile
import me.xiaozhangup.cardtable.util.ext.submitSerialTask
import me.xiaozhangup.cardtable.util.ext.warning
import me.xiaozhangup.crab.task.Task
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** Environment preparation and JSON-lines inference share one worker, never the Paper thread. */
class ExternalCardAI(private val plugin: CardTablePlugin) : CardAI {
    private val gson = Gson()
    private val executor = Executors.newSingleThreadScheduledExecutor { runnable -> Thread(runnable, "CardTable-AI").apply { isDaemon = true } }
    private val processLock = Any()
    private val tasks = mutableSetOf<Task>()
    private val decisions = mutableMapOf<CompletableFuture<BotDecision>, CompletableFuture<BotDecision>>()
    private var termination: CompletableFuture<Void>? = null
    @Volatile private var process: Process? = null
    @Volatile private var ready = false
    @Volatile private var closed = false
    @Volatile var unavailableReason = "机器人运行环境正在准备, 请稍后再添加机器人"
        private set
    private lateinit var input: BufferedWriter
    private lateinit var output: BufferedReader
    private var sequence = 0L
    override val available: Boolean get() = ready && process?.isAlive == true

    fun start() {
        if (!plugin.configuration.getBoolean("ai.enabled")) {
            unavailableReason = "机器人已关闭, 请在配置中启用 ai.enabled"
            return
        }
        val mode = plugin.configuration.getString("ai.mode")
        val externalCommand = plugin.configuration.getStringList("ai.command").map { it.replace("{data}", getDataFolder().absolutePath) }
        submitWork {
            try {
                val root = getDataFolder().resolve("env").absoluteFile
                root.mkdirs()
                val command = when (mode) {
                    "managed" -> ManagedAIRuntime(plugin, ::runSetup).prepare()
                    "external" -> {
                        require(externalCommand.isNotEmpty()) { "ai.command must not be empty in external mode" }
                        // Keep existing custom bridges untouched in the optional external mode.
                        listOf("ai/bridge.py", "ai/requirements.txt").forEach { path ->
                            releaseResourceFile(path)
                        }
                        externalCommand
                    }
                    else -> error("Unknown ai.mode: $mode; expected managed or external")
                }
                unavailableReason = "机器人正在启动, 请稍后再添加机器人"
                startBridge(command, root, mode == "managed")
            } catch (error: Exception) {
                stopProcess()
                reportFailure(error)
            }
        }
    }

    private fun runSetup(builder: ProcessBuilder) {
        val child = launch(builder)
        try {
            check(child.waitFor(10, TimeUnit.MINUTES)) { "AI environment installation timed out after 10 minutes" }
            check(child.exitValue() == 0) { "AI environment installer exited with code ${child.exitValue()}" }
        } finally {
            // Only the worker waits; reload must not reuse env before installers have exited.
            stopProcess(child).join()
        }
    }

    private fun startBridge(command: List<String>, root: File, managed: Boolean) {
        val builder = ProcessBuilder(command).directory(getDataFolder())
            .redirectError(ProcessBuilder.Redirect.appendTo(root.resolve("process.log")))
        if (managed) {
            root.resolve("tmp").mkdirs()
            builder.environment().apply {
                // Do not let the server's Python settings redirect the managed interpreter/imports.
                remove("PYTHONHOME")
                remove("PYTHONPATH")
                put("PYTHONNOUSERSITE", "1")
                put("PIP_CACHE_DIR", root.resolve("cache/pip").path)
                listOf("TMPDIR", "TMP", "TEMP").forEach { put(it, root.resolve("tmp").path) }
            }
        }
        val child = launch(builder)
        val startup = CompletableFuture<Unit>()
        // Downloads and installation have their own deadlines; this only times the ready handshake.
        startup.orTimeout(30, TimeUnit.SECONDS).whenComplete { _, error ->
            if (error != null) {
                stopProcess(child)
                reportFailure(if (error is TimeoutException) IllegalStateException("AI process did not become ready within 30 seconds", error) else error)
            }
        }
        try {
            input = child.outputStream.bufferedWriter(Charsets.UTF_8)
            output = child.inputStream.bufferedReader(Charsets.UTF_8)
            val line = output.readLine() ?: error("AI process exited before sending its ready message")
            val message = JsonParser.parseString(line).asJsonObject
            check(message.get("ready")?.asBoolean == true) { "AI process did not send ready=true" }
            synchronized(processLock) {
                // A shutdown or ready timeout may have killed this process while readLine completed.
                if (closed || process !== child || !startup.complete(Unit)) return
                ready = true
                unavailableReason = "机器人进程已退出, 请检查服务端日志后重载"
                info("External AI is ready: ${message.get("engine")?.asString ?: "custom engine"}.")
            }
        } catch (error: Exception) {
            startup.completeExceptionally(error)
        }
    }

    private fun reportFailure(error: Throwable) {
        if (closed) return
        unavailableReason = "机器人启动失败, 请检查服务端日志后重载"
        warning("External AI failed to start; see env/setup.log and env/process.log.", error.stackTraceToString())
    }

    private fun launch(builder: ProcessBuilder): Process = synchronized(processLock) {
        check(!closed) { "Plugin is closed" }
        builder.start().also { process = it; termination = null }
    }

    override fun decide(game: String, state: Map<String, Any?>): CompletableFuture<BotDecision> = synchronized(processLock) {
        val child = process
        if (!available || child == null) return@synchronized CompletableFuture.failedFuture(IllegalStateException("External AI is not ready"))
        val result = CompletableFuture<BotDecision>()
        // Keep the protocol deadline even when a caller cancels a request already sent to the process.
        val completion = CompletableFuture<BotDecision>()
        var started = false
        decisions[completion] = result
        completion.whenComplete { decision, error ->
            synchronized(processLock) {
                decisions.remove(completion)
                if (error == null) result.complete(decision)
                else {
                    if (error is TimeoutException && !closed) stopProcess(child)
                    result.completeExceptionally(error)
                    if (!closed && !result.isCancelled) {
                        warning("External AI decision failed; the current game will be cancelled.", error.stackTraceToString())
                    }
                }
            }
        }
        result.whenComplete { _, _ ->
            synchronized(processLock) {
                if (result.isCancelled && !started) completion.cancel(false)
            }
        }
        try {
            submitWork {
                synchronized(processLock) {
                    if (result.isDone || completion.isDone) return@submitWork
                    started = true
                }
                try {
                    val id = ++sequence
                    input.write(gson.toJson(mapOf("id" to id, "game" to game, "state" to state)))
                    input.newLine()
                    input.flush()
                    // Once sent, consume the response even if the caller cancels this decision.
                    val line = output.readLine() ?: error("AI process exited")
                    val response = JsonParser.parseString(line).asJsonObject
                    check(response.get("id")?.asLong == id) { "AI response ID does not match the request" }
                    response.get("error")?.let { error(it.asString) }
                    val action = response.get("action")?.asString ?: error("AI response is missing action")
                    val argument = response.get("argument")?.takeUnless { it.isJsonNull }?.asString
                    completion.complete(BotDecision(action, argument))
                } catch (error: Exception) { completion.completeExceptionally(error) }
            }
        } catch (error: Exception) { completion.completeExceptionally(error) }
        completion.orTimeout(10, TimeUnit.SECONDS)
        result
    }

    private fun submitWork(action: () -> Unit) = synchronized(processLock) {
        check(!closed) { "Plugin is closed" }
        lateinit var task: Task
        task = submitSerialTask(executor) {
            try { action() }
            finally {
                // Registration holds this lock until the returned Task has been recorded.
                synchronized(processLock) { tasks.remove(task) }
            }
        }
        tasks.add(task)
        Unit
    }

    private fun stopProcess(expected: Process? = null): CompletableFuture<Void> = synchronized(processLock) {
        if (expected != null && process !== expected) return@synchronized CompletableFuture.completedFuture(null)
        ready = false
        termination ?: run {
            val child = process ?: return@synchronized CompletableFuture.completedFuture(null)
            val handles = child.descendants().use { it.toList() } + child.toHandle()
            handles.forEach { it.destroyForcibly() }
            CompletableFuture.allOf(*handles.map { it.onExit() }.toTypedArray()).also { termination = it }
        }
    }

    fun close() = synchronized(processLock) {
        closed = true
        stopProcess()
        decisions.toList().forEach { (completion, result) ->
            result.cancel(false)
            completion.cancel(false)
        }
        tasks.toList().forEach(Task::cancel)
        tasks.clear()
        executor.shutdownNow()
        Unit
    }
}
