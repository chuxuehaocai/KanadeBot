package moe.cuteyuki.kanadebot.mainetwork

import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.utils.CipherAES
import moe.cuteyuki.kanadebot.utils.HttpClient
import moe.cuteyuki.kanadebot.utils.Logger
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.Deflater
import java.util.zip.Inflater

object NetworkManager {
    private val titleServerUri: String
        get() = ConfigManager.getConfig().titleServerUrl

    private val OBFUSCATE_PARAM: String
        get() = ConfigManager.getConfig().obfuscateParam

    private val API_VERSION: String
        get() = ConfigManager.getConfig().apiVersion

    @Throws(Exception::class)
    fun sendToTitle(data: String, useApi: String?, userId: Long): String {
        // APIObfuscator 内部会拼接 "MaimaiChn"，勿在外面重复拼接 (参考 reverseMai/config.py get_api_hash)
        val api: String = useApi!!

        val hashApi = APIObfuscator(api)

        val plainBytes = data.toByteArray(StandardCharsets.UTF_8)
        val compressed = zlibCompress(plainBytes)
        val encrypted: ByteArray? = CipherAES.encrypt(compressed)

        val headers: MutableMap<String?, String?> = HashMap<String?, String?>()
        headers.put("User-Agent", hashApi + "#" + userId)
        headers.put("Content-Type", "application/json")
        headers.put("Mai-Encoding", API_VERSION)
        headers.put("Accept-Encoding", "")
        headers.put("Charset", "UTF-8")
        headers.put("Content-Encoding", "deflate")
        headers.put("Host", "maimai-gm.wahlap.com:42081")

        val url = titleServerUri + hashApi
        Logger.log("URL:" + url + " Data:" + data, Logger.LogType.DEBUG)

        // 重试机制 (from reverseMai/api.py - 2次重试)
        val maxRetries = 2
        var lastException: Exception? = null

        for (attempt in 0..<maxRetries) {
            try {
                val httpResult: HttpClient.HttpResult = HttpClient.post(url, headers, encrypted, 15.0)

                if (httpResult.statusCode != 200) {
                    val bodyText =
                        if (httpResult.body != null) String(httpResult.body, StandardCharsets.UTF_8) else ""
                    lastException = Exception("Response error: " + httpResult.statusCode + "\n" + bodyText)
                    if (attempt < maxRetries - 1) {
                        Logger.log(
                            "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi,
                            Logger.LogType.DEBUG
                        )
                        Thread.sleep(1000)
                        continue
                    }
                    throw lastException
                }

                val respBytes: ByteArray? = httpResult.body
                if (respBytes == null || respBytes.isEmpty()) {
                    lastException = Exception("Empty response body")
                    if (attempt < maxRetries - 1) {
                        Logger.log(
                            "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (empty body)",
                            Logger.LogType.DEBUG
                        )
                        Thread.sleep(1000)
                        continue
                    }
                    throw lastException
                }

                val decrypted: ByteArray?
                try {
                    decrypted = CipherAES.decrypt(respBytes)
                } catch (e: Exception) {
                    throw Exception("AES decrypt failed: " + e.message, e)
                }

                val decompressed: ByteArray?
                try {
                    decompressed = zlibDecompress(decrypted)
                } catch (e: Exception) {
                    throw Exception("Zlib decompression failed: " + e.message, e)
                }

                return String(decompressed, StandardCharsets.UTF_8)
            } catch (e: SocketTimeoutException) {
                // 网络超时/连接失败 - 重试 (from reverseMai/api.py httpx.TimeoutException/ConnectError)
                lastException = e
                if (attempt < maxRetries - 1) {
                    Logger.log(
                        "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (network error: " + e.javaClass.getSimpleName() + ")",
                        Logger.LogType.DEBUG
                    )
                    Thread.sleep(2000)
                    continue
                }
            } catch (e: UnknownHostException) {
                lastException = e
                if (attempt < maxRetries - 1) {
                    Logger .log(
                        "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (network error: " + e.javaClass.getSimpleName() + ")",
                        Logger.LogType.DEBUG
                    )
                    Thread.sleep(2000)
                    continue
                }
            } catch (e: IOException) {
                // 其他 IO 异常 - 重试一次
                lastException = e
                if (attempt < maxRetries - 1) {
                    Logger.log(
                        "API retry " + (attempt + 1) + "/" + maxRetries + " for " + useApi + " (IO error: " + e.message + ")",
                        Logger.LogType.DEBUG
                    )
                    Thread.sleep(1000)
                    continue
                }
            }
        }

        throw if (lastException != null) lastException else Exception("API call failed after retries: " + useApi)
    }

    private fun APIObfuscator(api: String?): String {
        try {
            val combined = api + "MaimaiChn" + OBFUSCATE_PARAM
            val md = MessageDigest.getInstance("MD5")
            val digest = md.digest(combined.toByteArray(StandardCharsets.UTF_8))

            val sb = StringBuilder()
            for (b in digest) {
                sb.append(String.format("%02x", b.toInt() and 0xFF))
            }
            return sb.toString()
        } catch (e: NoSuchAlgorithmException) {
            throw RuntimeException("MD5 algorithm not available", e)
        }
    }

    @Throws(IOException::class)
    private fun zlibCompress(input: ByteArray?): ByteArray {
        val deflater = Deflater()
        deflater.setInput(input)
        deflater.finish()

        val buffer = ByteArray(1024)
        val chunks: MutableList<ByteArray> = ArrayList<ByteArray>()
        var totalLen = 0

        while (!deflater.finished()) {
            val count = deflater.deflate(buffer)
            chunks.add(buffer.copyOf(count))
            totalLen += count
        }

        val output = ByteArray(totalLen)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, output, offset, chunk.size)
            offset += chunk.size
        }
        return output
    }

    @Throws(IOException::class)
    private fun zlibDecompress(input: ByteArray?): ByteArray {
        val inflater = Inflater()
        inflater.setInput(input)

        val buffer = ByteArray(1024)
        val chunks: MutableList<ByteArray> = ArrayList<ByteArray>()
        var totalLen = 0

        try {
            while (!inflater.finished()) {
                val count = inflater.inflate(buffer)
                if (count == 0 && inflater.needsInput()) break
                chunks.add(buffer.copyOf(count))
                totalLen += count
            }
        } catch (e: Exception) {
            throw IOException("ZLIB 解压失败", e)
        }

        val output = ByteArray(totalLen)
        var offset = 0
        for (chunk in chunks) {
            System.arraycopy(chunk, 0, output, offset, chunk.size)
            offset += chunk.size
        }
        return output
    }
}
