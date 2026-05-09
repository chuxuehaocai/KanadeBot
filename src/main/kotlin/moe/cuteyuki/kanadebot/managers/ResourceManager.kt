package moe.cuteyuki.kanadebot.managers

import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.Base64
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import okhttp3.OkHttpClient
import okhttp3.Request

object ResourceManager {
    private lateinit var resourceFolder: File
    private lateinit var iconsCacheFolder: File

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    fun initialize() {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        resourceFolder = File(workingDir, "resource")

        if (resourceFolder.exists()) {
            if(!resourceFolder.isDirectory)
                throw RuntimeException("Resource folder:${resourceFolder.path} must be a directory.")

        } else {
            resourceFolder.mkdir()
        }

        iconsCacheFolder = File(resourceFolder, "icons")

        if (iconsCacheFolder.exists()) {
            if(!iconsCacheFolder.isDirectory)
                throw RuntimeException("Resource folder:${iconsCacheFolder.path} must be a directory.")

        } else {
            iconsCacheFolder.mkdir()
        }
    }

//    val whoamiBackground: File = File(resourceFolder, "whoami.png")

    fun iconImagePath(iconId: String): String?{
        val cacheFile = File(iconsCacheFolder, "$iconId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                if (cacheFile.exists()) return cacheFile.path
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        try {
            val urlStr = "https://assets2.lxns.net/maimai/icon/$iconId.png"
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/26.0 Safari/605.1.15")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@use
                    val bytes = body.bytes()
                    if (bytes.isNotEmpty()) {
                        Files.write(cacheFile.toPath(), bytes)
                        return cacheFile.path
                    }
                }
            }
        } catch (_: IOException) {
            // download failed
        }

        return null
    }


    fun iconImage(iconId: String): BufferedImage? {
        val cacheFile = File(iconsCacheFolder, "$iconId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                val cached = ImageIO.read(cacheFile)
                if (cached != null) return cached
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        try {
            val urlStr = "https://assets2.lxns.net/maimai/icon/$iconId.png"
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "KanadeBot/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@use
                    val bytes = body.bytes()
                    if (bytes.isNotEmpty()) {
                        Files.write(cacheFile.toPath(), bytes)
                        val downloaded = ImageIO.read(cacheFile)
                        if (downloaded != null) {
                            return downloaded
                        }
                    }
                }
            }
        } catch (_: IOException) {
            // download failed
        }

        return null
    }

    fun iconImageBase64(iconId: String): String? {
        val cacheFile = File(iconsCacheFolder, "$iconId.png")

        // 1. try to get image from local cache
        if (cacheFile.exists()) {
            try {
                val bytes = Files.readAllBytes(cacheFile.toPath())
                return Base64.getEncoder().encodeToString(bytes)
            } catch (_: IOException) {
                // corrupted cache file, fall through to re-download
            }
        }

        // 2. download from lxns server and save to cache
        try {
            val urlStr = "https://assets2.lxns.net/maimai/icon/$iconId.png"
            val request = Request.Builder()
                .url(urlStr)
                .header("User-Agent", "KanadeBot/1.0")
                .build()

            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body ?: return@use
                    val bytes = body.bytes()
                    if (bytes.isNotEmpty()) {
                        Files.write(cacheFile.toPath(), bytes)
                        return Base64.getEncoder().encodeToString(bytes)
                    }
                }
            }
        } catch (_: IOException) {
            // download failed
        }

        return null
    }
}
