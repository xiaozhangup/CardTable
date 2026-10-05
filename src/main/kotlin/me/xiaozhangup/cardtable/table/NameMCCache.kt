package me.xiaozhangup.cardtable.table

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.xiaozhangup.cardtable.util.ext.getDataFolder
import me.xiaozhangup.cardtable.util.ext.info
import me.xiaozhangup.cardtable.util.ext.releaseResourceFile
import me.xiaozhangup.cardtable.util.ext.warning
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.time.Instant

/** One store is shared by both asynchronous refreshers so neither overwrites the other section. */
internal class NameMCCache {
    private val directory = getDataFolder().toPath()
    private val file = directory.resolve("namemc-cache.json")
    private val oldNames = directory.resolve("bot-names.txt")
    private val oldSkins = directory.resolve("bot-skins.json")
    private val oldSource = directory.resolve("namemc-cache-source.json")
    private val pendingCleanup = mutableListOf<Path>()
    private var document: JsonObject? = null

    @Synchronized
    fun names(): List<String> = load().getAsJsonArray("names").map { entry ->
        require(entry.isJsonPrimitive && entry.asJsonPrimitive.isString) { "Invalid name in the NameMC cache" }
        entry.asString
    }

    @Synchronized
    fun skins(): JsonArray = load().getAsJsonArray("skins")

    @Synchronized
    fun saveNames(names: List<String>, source: String) {
        val data = load()
        data.add("names", JsonArray().apply { names.forEach(::add) })
        data.getAsJsonObject("source").apply {
            addProperty("namesSource", source)
            addProperty("lastNamesRefresh", Instant.now().toString())
        }
        persist(data)
    }

    @Synchronized
    fun saveSkins(skins: JsonArray, source: String) {
        val data = load()
        data.add("skins", skins)
        data.getAsJsonObject("source").apply {
            addProperty("skinsSource", source)
            addProperty("lastSkinsRefresh", Instant.now().toString())
        }
        persist(data)
    }

    private fun load(): JsonObject {
        document?.let { return it }
        if (!Files.exists(file) && !Files.exists(oldNames) && !Files.exists(oldSkins) && !Files.exists(oldSource)) {
            releaseResourceFile("namemc-cache.json")
        }
        val data = if (Files.exists(file)) readObject(file) else JsonObject()
        if (!data.has("names")) {
            data.add("names", JsonArray().apply {
                if (Files.exists(oldNames)) Files.readAllLines(oldNames).forEach(::add)
            })
        }
        if (!data.has("skins")) {
            data.add("skins", if (Files.exists(oldSkins)) readObject(oldSkins).getAsJsonArray("skins")
                ?: throw IOException("Missing skin entries in the legacy NameMC cache") else JsonArray())
        }
        if (!data.has("source")) {
            data.add("source", if (Files.exists(oldSource)) readObject(oldSource) else JsonObject())
        }
        require(data.get("names").isJsonArray && data.get("skins").isJsonArray && data.get("source").isJsonObject) {
            "Invalid sections in the NameMC cache"
        }
        document = data
        pendingCleanup += listOf(oldNames, oldSkins, oldSource).filter { Files.exists(it) }
        if (pendingCleanup.isNotEmpty()) {
            try {
                persist(data)
                info("Migrated NameMC names, skins and source metadata to namemc-cache.json.")
            } catch (error: IOException) {
                warning("Failed to migrate the NameMC cache; legacy files were retained: ${error.message}")
            }
        }
        return data
    }

    private fun readObject(path: Path): JsonObject = JsonParser.parseString(Files.readString(path)).asJsonObject

    private fun persist(data: JsonObject) {
        Files.createDirectories(directory)
        val temporary = Files.createTempFile(directory, "namemc-cache-", ".tmp")
        try {
            val json = GsonBuilder().setPrettyPrinting().create().toJson(data)
            Files.writeString(temporary, "$json\n")
            Files.move(temporary, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } finally {
            Files.deleteIfExists(temporary)
        }
        // A failed replacement leaves all legacy files intact for the next startup.
        val remaining = pendingCleanup.iterator()
        while (remaining.hasNext()) {
            val old = remaining.next()
            try {
                Files.deleteIfExists(old)
                remaining.remove()
            } catch (error: IOException) {
                warning("Failed to remove migrated NameMC cache file ${old.fileName}: ${error.message}")
            }
        }
    }
}
