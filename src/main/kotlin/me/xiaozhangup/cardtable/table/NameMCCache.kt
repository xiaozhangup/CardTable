package me.xiaozhangup.cardtable.table

import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import me.xiaozhangup.cardtable.util.ext.getDataFolder
import me.xiaozhangup.cardtable.util.ext.releaseResourceFile
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.Instant

/** One store is shared by both asynchronous refreshers so neither overwrites the other section. */
internal class NameMCCache {
    private val directory = getDataFolder().toPath()
    private val file = directory.resolve("namemc-cache.json")
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
        if (!Files.exists(file)) {
            releaseResourceFile("namemc-cache.json")
        }
        val data = JsonParser.parseString(Files.readString(file)).asJsonObject
        require(data.get("names")?.isJsonArray == true && data.get("skins")?.isJsonArray == true &&
            data.get("source")?.isJsonObject == true) {
            "Invalid sections in the NameMC cache"
        }
        document = data
        return data
    }

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
    }
}
