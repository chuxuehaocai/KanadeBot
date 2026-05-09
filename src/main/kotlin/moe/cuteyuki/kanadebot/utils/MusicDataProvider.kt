package moe.cuteyuki.kanadebot.utils

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONArray
import com.alibaba.fastjson2.JSONObject
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

/**
 * 从水鱼查分器获取歌曲定数数据
 *
 * 使用 API: GET https://www.diving-fish.com/api/maimaidxprober/music_data
 * 返回所有歌曲的定数信息，缓存在内存中
 */
object MusicDataProvider {

    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private const val MUSIC_DATA_URL = "https://www.diving-fish.com/api/maimaidxprober/music_data"

    // 缓存: musicId -> (levelIndex -> ds)
    private val dsCache = ConcurrentHashMap<Int, Map<Int, Double>>()

    // 缓存: musicId -> title
    private val titleCache = ConcurrentHashMap<Int, String>()

    // 缓存是否已加载
    @Volatile
    private var loaded = false

    // 缓存过期时间（5分钟）
    private var lastLoadTime = 0L
    private const val CACHE_TTL_MS = 5 * 60 * 1000L

    /**
     * 获取指定歌曲指定难度的定数
     * @param musicId 歌曲 ID
     * @param level 难度等级（0=Basic, 1=Advanced, 2=Expert, 3=Master, 4=Re:Master）
     * @return 定数值，如果找不到则返回 0.0
     */
    fun getDs(musicId: Int, level: Int): Double {
        ensureLoaded()

        val levelMap = dsCache[musicId] ?: return 0.0
        return levelMap[level] ?: 0.0
    }

    /**
     * 获取指定歌曲的标题
     * @param musicId 歌曲 ID
     * @return 歌曲标题，如果找不到则返回 "Unknown"
     */
    fun getTitle(musicId: Int): String {
        ensureLoaded()
        return titleCache[musicId] ?: "Unknown"
    }

    /**
     * 确保定数缓存已加载
     */
    private fun ensureLoaded() {
        val now = System.currentTimeMillis()
        if (loaded && (now - lastLoadTime) < CACHE_TTL_MS) return

        synchronized(this) {
            if (loaded && (System.currentTimeMillis() - lastLoadTime) < CACHE_TTL_MS) return

            try {
                loadMusicData()
                loaded = true
                lastLoadTime = System.currentTimeMillis()
                println("[MusicDataProvider] 定数数据加载成功，共 ${dsCache.size} 首歌曲")
            } catch (e: Exception) {
                System.err.println("[MusicDataProvider] 加载定数数据失败: ${e.message}")
                // 如果之前有缓存数据，继续使用旧缓存
                if (dsCache.isNotEmpty()) {
                    loaded = true
                    lastLoadTime = now
                }
            }
        }
    }

    /**
     * 从水鱼查分器加载所有歌曲定数数据
     */
    private fun loadMusicData() {
        val request = Request.Builder()
            .url(MUSIC_DATA_URL)
            .header("User-Agent", "KanadeBot/1.0")
            .get()
            .build()

        val responseStr: String
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}")
            }
            responseStr = response.body?.string() ?: throw IOException("Empty response")
        }

        // DEBUG: 打印前500字符看看结构
        println("[MusicDataProvider] DEBUG: 响应前500字符: ${responseStr.take(500)}")

        val musicArray = JSON.parseArray(responseStr)
        println("[MusicDataProvider] DEBUG: 解析为 JSONArray, size=${musicArray.size}")

        // 看看第一个元素的结构
        if (musicArray.size > 0) {
            val first = musicArray.getJSONObject(0)
            println("[MusicDataProvider] DEBUG: 第一个元素 keys=${first.keys}")
            println("[MusicDataProvider] DEBUG: 第一个元素内容: ${first.toJSONString().take(300)}")
        }

        // 清空旧缓存
        dsCache.clear()
        titleCache.clear()

        var parsedCount = 0
        var skippedNoDs = 0

        for (i in 0 until musicArray.size) {
            val music = musicArray.getJSONObject(i)
            val musicId = music.getIntValue("id")
            val title = music.getString("title") ?: "Unknown"
            val dsArray = music.getJSONArray("ds")
            if (dsArray == null || dsArray.isEmpty()) {
                skippedNoDs++
                continue
            }

            // 缓存标题
            titleCache[musicId] = title

            val levelMap = mutableMapOf<Int, Double>()
            for (j in 0 until dsArray.size) {
                val ds = dsArray.getDoubleValue(j)
                if (ds > 0) {
                    levelMap[j] = ds
                }
            }

            if (levelMap.isNotEmpty()) {
                dsCache[musicId] = levelMap
                parsedCount++
            }
        }

        println("[MusicDataProvider] DEBUG: parsedCount=$parsedCount, skippedNoDs=$skippedNoDs")
    }

    /**
     * 强制刷新缓存
     */
    fun refresh() {
        synchronized(this) {
            loaded = false
            dsCache.clear()
            ensureLoaded()
        }
    }
}
