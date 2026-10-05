package me.xiaozhangup.cardtable.table

import me.xiaozhangup.cardtable.api.Participant
import me.xiaozhangup.cardtable.util.requireInput
import me.xiaozhangup.cardtable.util.ext.info
import me.xiaozhangup.cardtable.util.ext.submitTask
import me.xiaozhangup.cardtable.util.ext.warning
import me.xiaozhangup.crab.task.Task
import java.io.IOException
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Names are loaded once per plugin startup; seating only reads the published pool. */
internal class BotNames(private val cache: NameMCCache) : AutoCloseable {
    private val lifecycle = Any()
    private var task: Task? = null
    private var client: HttpClient? = null
    @Volatile private var names: List<String> = emptyList()
    @Volatile private var loading = true
    @Volatile private var closed = false

    fun start() {
        task = submitTask(async = true) {
            loadCache()
            refresh()
        }
    }

    fun pick(participants: List<Participant>, count: Int): List<String> {
        val pool = names
        requireInput(pool.isNotEmpty(), "No NameMC bot names are available.") {
            if (loading) "机器人名称正在加载, 请稍后再试" else "机器人名称暂不可用, 请稍后重试"
        }
        val occupied = participants.mapTo(hashSetOf()) { it.name.lowercase(Locale.ROOT) }
        val available = pool.filter { it.lowercase(Locale.ROOT) !in occupied }
        requireInput(available.size >= count, "Not enough distinct NameMC bot names are available for this table.") {
            "机器人名称不足, 无法添加 $count 个机器人"
        }
        return available.shuffled().take(count)
    }

    fun canPick(participants: List<Participant>, count: Int): Boolean {
        val occupied = participants.mapTo(hashSetOf()) { it.name.lowercase(Locale.ROOT) }
        return names.count { it.lowercase(Locale.ROOT) !in occupied } >= count
    }

    private fun loadCache() {
        try {
            synchronized(lifecycle) {
                if (closed) return
                val cached = validNames(cache.names())
                names = cached
                if (cached.isNotEmpty()) info("Loaded ${cached.size} cached NameMC bot names.")
            }
        } catch (error: Exception) {
            if (!closed) warning("Failed to read the NameMC bot name cache: ${error.message}")
        }
    }

    private fun refresh() {
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL).build()
        synchronized(lifecycle) {
            if (closed) {
                http.shutdownNow()
                return
            }
            client = http
        }
        try {
            val request = HttpRequest.newBuilder(URI.create(SOURCE))
                .timeout(Duration.ofSeconds(20))
                .header("User-Agent", "CardTable")
                .GET().build()
            val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
                .get(25, TimeUnit.SECONDS)
            if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()}")
            val fetched = validNames(NAME_LINK.findAll(response.body()).map { it.groupValues[1] }.toList())
            if (fetched.isEmpty()) throw IOException("The NameMC page contains no valid name links")
            synchronized(lifecycle) {
                if (closed) return
                // The lock also keeps a completed request from replacing the cache after disable.
                try {
                    cache.saveNames(fetched, SOURCE)
                } catch (error: Exception) {
                    warning("Failed to save the NameMC bot name cache; using fetched names for this session: ${error.message}")
                }
                names = fetched
                info("Fetched ${fetched.size} bot names from NameMC.")
            }
        } catch (error: Exception) {
            if (!closed) {
                val fallback = if (names.isEmpty()) "No cached names are available; adding bots is unavailable."
                    else "Using ${names.size} cached names."
                val reason = error.cause?.message ?: error.message ?: error.javaClass.simpleName
                warning("Failed to fetch NameMC bot names: $reason. $fallback")
            }
            if (error is InterruptedException) Thread.currentThread().interrupt()
        } finally {
            http.shutdownNow()
            synchronized(lifecycle) {
                client = null
                loading = false
            }
        }
    }

    override fun close() {
        val http = synchronized(lifecycle) {
            closed = true
            client
        }
        task?.cancel()
        http?.shutdownNow()
    }

    private fun validNames(values: List<String>): List<String> = values.asSequence()
        .map { it.trim() }
        .filter(NAME::matches)
        .distinctBy { it.lowercase(Locale.ROOT) }
        .toList()

    private companion object {
        const val SOURCE = "https://namemc.com/minecraft-names"
        val NAME = Regex("[A-Za-z0-9_]{3,16}")
        val NAME_LINK = Regex(
            """<a\b[^>]*\bhref\s*=\s*["'](?:https://namemc\.com)?/search\?q=([A-Za-z0-9_]{3,16})["']""",
            RegexOption.IGNORE_CASE
        )
    }
}
