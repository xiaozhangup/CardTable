package me.xiaozhangup.cardtable.ai

import com.google.gson.JsonParser
import me.xiaozhangup.cardtable.CardTablePlugin
import me.xiaozhangup.cardtable.util.ext.getDataFolder
import me.xiaozhangup.cardtable.util.ext.info
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.time.Duration
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.locks.ReentrantLock
import java.util.zip.ZipFile

/** Prepares the bundled rule agents without relying on a system Python installation. */
internal class ManagedAIRuntime(private val plugin: CardTablePlugin, private val run: (ProcessBuilder) -> Unit) {
    val root: File = getDataFolder().resolve("env").absoluteFile

    fun prepare(): List<String> {
        // Reload creates the next AI instance immediately after interrupting the old one.
        installationLock.lockInterruptibly()
        try {
            Files.createDirectories(root.toPath())
            val requirements = resource("requirements.txt")
            Files.write(root.resolve("requirements.txt").toPath(), requirements)
            Files.write(root.resolve("bridge.py").toPath(), resource("bridge.py"))
            Files.write(root.resolve("README.md").toPath(), resource("env-README.md"))

            val platform = platform()
            val manifestBytes = resource("uv-runtime.json")
            val manifest = JsonParser.parseString(manifestBytes.toString(Charsets.UTF_8)).asJsonObject
            val distribution = manifest.getAsJsonObject("platforms").getAsJsonObject(platform)
            val windows = platform.startsWith("windows-")
            val python = root.resolve(if (windows) "venv/Scripts/python.exe" else "venv/bin/python")
            val marker = root.resolve(".ready")
            val fingerprint = digest(listOf(
                PYTHON_VERSION.toByteArray(), PYTHON_BUILD.toByteArray(), platform.toByteArray(),
                root.path.toByteArray(), manifestBytes, requirements
            ))
            val command = listOf(python.path, "-u", root.resolve("bridge.py").path)
            if (marker.isFile && marker.readText() == fingerprint && python.isFile) {
                info("Reusing the prepared AI runtime at ${root.path}.")
                return command
            }
            Files.deleteIfExists(marker.toPath())
            checkInterrupted()

            val version = manifest.get("version").asString
            val uv = root.resolve("bin/uv-$version-$platform${if (windows) ".exe" else ""}")
            if (!uv.isFile) {
                info("Downloading uv $version for the AI runtime.")
                installUv(distribution.get("url").asString, distribution.get("sha256").asString,
                    distribution.get("entry").asString, uv, windows)
            }
            Files.createDirectories(root.resolve("tmp").toPath())
            info("Preparing Python $PYTHON_VERSION for AI; the first startup requires a runtime download.")
            invoke(uv, "python", "install", PYTHON_VERSION, "--no-bin", "--no-registry")
            invoke(uv, "venv", "--no-project", "--managed-python", "--python", PYTHON_VERSION,
                "--clear", root.resolve("venv").path)
            info("Installing AI rule agents and pinned dependencies.")
            invoke(uv, "pip", "install", "--python", python.path, "--requirements", root.resolve("requirements.txt").path)
            checkInterrupted()
            Files.writeString(marker.toPath(), fingerprint)
            info("AI runtime is ready at ${root.path}.")
            return command
        } finally {
            installationLock.unlock()
        }
    }

    private fun invoke(uv: File, vararg arguments: String) {
        checkInterrupted()
        val builder = ProcessBuilder(listOf(uv.path, "--no-config") + arguments)
            .directory(root)
            .redirectOutput(ProcessBuilder.Redirect.appendTo(root.resolve("setup.log")))
            .redirectError(ProcessBuilder.Redirect.appendTo(root.resolve("setup.log")))
        builder.environment().putAll(mapOf(
            "UV_PYTHON_INSTALL_DIR" to root.resolve("python").path,
            "UV_PYTHON_CACHE_DIR" to root.resolve("cache/python").path,
            "UV_CACHE_DIR" to root.resolve("cache/uv").path,
            "UV_PYTHON_INSTALL_BIN" to "0",
            "UV_PYTHON_INSTALL_REGISTRY" to "0",
            "UV_PYTHON_CPYTHON_BUILD" to PYTHON_BUILD,
            "UV_NO_CONFIG" to "1",
            "UV_NO_PROGRESS" to "1",
            "TMPDIR" to root.resolve("tmp").path,
            "TMP" to root.resolve("tmp").path,
            "TEMP" to root.resolve("tmp").path,
            "PIP_CACHE_DIR" to root.resolve("cache/pip").path
        ))
        run(builder)
    }

    private fun installUv(url: String, checksum: String, entryName: String, target: File, windows: Boolean) {
        Files.createDirectories(target.parentFile.toPath())
        val archive = root.resolve("uv-download.whl.part").toPath()
        val executable = target.resolveSibling("${target.name}.part").toPath()
        val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30))
            .followRedirects(HttpClient.Redirect.NORMAL).build()
        val request = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofMinutes(3)).GET().build()
        val future = client.sendAsync(request, HttpResponse.BodyHandlers.ofFile(archive,
            StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE))
        try {
            val response = try { future.get(3, TimeUnit.MINUTES) }
            catch (error: TimeoutException) { throw IllegalStateException("uv download timed out after 3 minutes", error) }
            check(response.statusCode() == 200) { "uv download failed: HTTP ${response.statusCode()}" }
            checkInterrupted()
            val hash = MessageDigest.getInstance("SHA-256")
            Files.newInputStream(archive).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    checkInterrupted()
                    val count = input.read(buffer)
                    if (count < 0) break
                    hash.update(buffer, 0, count)
                }
            }
            check(hash.digest().toHexString() == checksum) { "SHA-256 verification failed for the uv download" }
            ZipFile(archive.toFile()).use { zip ->
                val entry = checkNotNull(zip.getEntry(entryName)) { "The uv download is missing executable $entryName" }
                zip.getInputStream(entry).use { input ->
                    Files.newOutputStream(executable).use { output -> input.copyTo(output) }
                }
            }
            checkInterrupted()
            if (!windows) check(executable.toFile().setExecutable(true, true)) { "Failed to set executable permissions for uv" }
            Files.move(executable, target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        } finally {
            future.cancel(true)
            client.shutdownNow()
            client.close() // Wait on the worker before reload reuses the download path.
            Files.deleteIfExists(archive)
            Files.deleteIfExists(executable)
        }
    }

    private fun resource(name: String): ByteArray = checkNotNull(plugin.getResource("ai/$name")) {
        "Missing bundled AI resource: ai/$name"
    }.use { it.readBytes() }

    private fun platform(): String {
        val system = System.getProperty("os.name").lowercase()
        val os = when {
            system.startsWith("linux") -> "linux"
            system.startsWith("windows") -> "windows"
            system.startsWith("mac") -> "macos"
            else -> error("Managed AI runtime does not support operating system $system")
        }
        val architecture = System.getProperty("os.arch").lowercase()
        val arch = when (architecture) {
            "amd64", "x86_64" -> "x86_64"
            "aarch64", "arm64" -> "aarch64"
            else -> error("Managed AI runtime does not support architecture $architecture")
        }
        return "$os-$arch"
    }

    private fun digest(parts: List<ByteArray>): String {
        val hash = MessageDigest.getInstance("SHA-256")
        parts.forEach { hash.update(it); hash.update(0.toByte()) }
        return hash.digest().toHexString()
    }

    private fun checkInterrupted() {
        if (Thread.currentThread().isInterrupted) throw InterruptedException("AI environment preparation was cancelled")
    }

    private companion object {
        const val PYTHON_VERSION = "3.14.7"
        const val PYTHON_BUILD = "20260901"
        val installationLock = ReentrantLock()
    }
}
