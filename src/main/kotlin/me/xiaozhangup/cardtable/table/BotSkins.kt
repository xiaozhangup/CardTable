package me.xiaozhangup.cardtable.table

import com.destroystokyo.paper.profile.ProfileProperty
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import io.papermc.paper.datacomponent.item.ResolvableProfile
import me.xiaozhangup.cardtable.api.Participant
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
import java.util.Base64
import java.util.Locale
import java.util.concurrent.TimeUnit

/** NameMC selects the profiles; Mojang supplies their complete signed texture properties. */
internal class BotSkins(private val cache: NameMCCache) : AutoCloseable {
    private data class Skin(
        val sourceSkin: String,
        val sourcePlayer: String,
        val profileId: String,
        val value: String,
        val signature: String,
    )

    private val lifecycle = Any()
    private var task: Task? = null
    private var client: HttpClient? = null
    @Volatile private var skins: List<Skin> = emptyList()
    @Volatile private var closed = false

    fun start() {
        task = submitTask(async = true) {
            loadCache()
            refresh()
        }
    }

    /** Never resolves a name or performs network I/O on the entity-rendering path. */
    fun profile(participant: Participant): ResolvableProfile? {
        val pool = skins
        if (pool.isEmpty()) return null
        val skin = pool[Math.floorMod(participant.id.hashCode(), pool.size)]
        return ResolvableProfile.resolvableProfile()
            .uuid(participant.id)
            .name("CardBot_${participant.seat + 1}")
            .addProperty(ProfileProperty("textures", skin.value, skin.signature))
            .build()
    }

    private fun loadCache() {
        try {
            synchronized(lifecycle) {
                if (closed) return
                val cached = cache.skins().map { entry ->
                    val skin = entry.asJsonObject
                    validate(Skin(skin.string("sourceSkin"), skin.string("sourcePlayer"),
                        skin.string("profileId"), skin.string("value"), skin.string("signature")))
                }.distinctBy { it.profileId.lowercase(Locale.ROOT) }.take(MAX_SKINS)
                skins = cached
                if (cached.isNotEmpty()) info("Loaded ${cached.size} cached NameMC bot skins.")
            }
        } catch (error: Exception) {
            if (!closed) warning("Failed to read the NameMC bot skin cache: ${reason(error)}")
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
            val links = SKIN_LINK.findAll(get(http, SOURCE)).map { it.groupValues[1].lowercase(Locale.ROOT) }
                .distinct().take(MAX_SKINS).toList()
            if (links.isEmpty()) throw IOException("The NameMC page contains no skin links")
            val fetched = mutableListOf<Skin>()
            val players = hashSetOf<String>()
            for (id in links) {
                val source = "https://namemc.com/skin/$id"
                val player = PLAYER_LINK.find(get(http, source))?.groupValues?.get(1)
                    ?: throw IOException("The NameMC skin page has no source player: $source")
                if (!players.add(player.lowercase(Locale.ROOT))) continue
                val account = JsonParser.parseString(get(http,
                    "https://api.mojang.com/users/profiles/minecraft/$player")).asJsonObject
                val uuid = account.string("id")
                require(PROFILE_ID.matches(uuid)) { "Mojang returned an invalid profile ID" }
                val session = JsonParser.parseString(get(http,
                    "https://sessionserver.mojang.com/session/minecraft/profile/$uuid?unsigned=false")).asJsonObject
                require(session.string("id").equals(uuid, ignoreCase = true)) { "Mojang returned a different profile ID" }
                val properties = session.getAsJsonArray("properties") ?: throw IOException("Mojang returned no profile properties")
                val textures = properties.map { it.asJsonObject }
                    .firstOrNull { it.string("name") == "textures" }
                    ?: throw IOException("Mojang returned no textures for $player")
                fetched += validate(Skin(source, player, uuid, textures.string("value"), textures.string("signature")))
            }
            synchronized(lifecycle) {
                if (closed) return
                try {
                    cache.saveSkins(Gson().toJsonTree(fetched).asJsonArray, SOURCE)
                } catch (error: Exception) {
                    warning("Failed to save the NameMC bot skin cache; using fetched skins for this session: ${reason(error)}")
                }
                skins = fetched
                info("Fetched ${fetched.size} bot skins from NameMC source profiles.")
            }
        } catch (error: Exception) {
            if (!closed) {
                val fallback = if (skins.isEmpty()) "No cached skins are available; using vanilla defaults."
                    else "Using ${skins.size} cached skins."
                warning("Failed to fetch NameMC bot skins: ${reason(error)}. $fallback")
            }
            if (error is InterruptedException) Thread.currentThread().interrupt()
        } finally {
            http.shutdownNow()
            synchronized(lifecycle) { client = null }
        }
    }

    private fun get(http: HttpClient, url: String): String {
        if (closed) throw InterruptedException("Bot skin refresh was cancelled")
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "CardTable").GET().build()
        val response = http.sendAsync(request, HttpResponse.BodyHandlers.ofString(Charsets.UTF_8))
            .get(25, TimeUnit.SECONDS)
        if (response.statusCode() != 200) throw IOException("HTTP ${response.statusCode()} from ${URI.create(url).host}")
        return response.body()
    }

    private fun validate(skin: Skin): Skin {
        require(SKIN_SOURCE.matches(skin.sourceSkin)) { "Invalid NameMC skin source" }
        require(PLAYER_NAME.matches(skin.sourcePlayer)) { "Invalid NameMC source player" }
        require(PROFILE_ID.matches(skin.profileId)) { "Invalid skin profile ID" }
        require(skin.value.length in 1..Short.MAX_VALUE) { "Invalid textures property length" }
        require(skin.signature.length in 1..1024) { "Invalid textures signature length" }
        Base64.getDecoder().decode(skin.signature)
        val payload = JsonParser.parseString(Base64.getDecoder().decode(skin.value).toString(Charsets.UTF_8)).asJsonObject
        require(payload.string("profileId").equals(skin.profileId, ignoreCase = true)) { "Textures profile ID does not match its source" }
        val textures = payload.getAsJsonObject("textures")
            ?: throw IOException("Missing profile textures")
        require(textures.has("SKIN")) { "Profile has no skin texture" }
        textures.entrySet().forEach { (_, texture) ->
            val url = URI.create(texture.asJsonObject.string("url"))
            require(url.scheme?.lowercase(Locale.ROOT) in setOf("http", "https") &&
                url.host.equals("textures.minecraft.net", ignoreCase = true) &&
                url.path.startsWith("/texture/") && url.path.length > "/texture/".length) {
                "Profile texture URL is not an official Minecraft texture"
            }
        }
        return skin
    }

    override fun close() {
        val http = synchronized(lifecycle) {
            closed = true
            client
        }
        task?.cancel()
        http?.shutdownNow()
    }

    private fun JsonObject.string(key: String): String {
        val value = get(key)
        require(value != null && value.isJsonPrimitive && value.asJsonPrimitive.isString) { "Missing or invalid string: $key" }
        return value.asString
    }

    private fun reason(error: Exception): String = error.cause?.message ?: error.message ?: error.javaClass.simpleName

    private companion object {
        const val MAX_SKINS = 8
        const val SOURCE = "https://namemc.com/minecraft-skins"
        val PLAYER_NAME = Regex("[A-Za-z0-9_]{3,16}")
        val PROFILE_ID = Regex("[A-Fa-f0-9]{32}")
        val SKIN_SOURCE = Regex("https://namemc\\.com/skin/[a-f0-9]{16}")
        val SKIN_LINK = Regex(
            """<a\b[^>]*\bhref\s*=\s*["'](?:https://namemc\.com)?/skin/([A-Fa-f0-9]{16})["']""",
            RegexOption.IGNORE_CASE
        )
        val PLAYER_LINK = Regex(
            """<a\b[^>]*\bhref\s*=\s*["'](?:https://namemc\.com)?/profile/([A-Za-z0-9_]{3,16})\.\d+["']""",
            RegexOption.IGNORE_CASE
        )
    }
}
