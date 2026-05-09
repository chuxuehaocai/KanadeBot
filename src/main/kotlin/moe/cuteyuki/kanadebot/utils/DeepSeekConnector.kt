package moe.cuteyuki.kanadebot.utils

import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.managers.ConfigManager
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object DeepSeekConnector {
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    private const val API_URL = "https://api.deepseek.com/chat/completions"

    /**
     * 锐评模式 - 调用 DeepSeek API 对 B50 数据进行评价
     */
    fun request(ratingJson: String): String {
        val apiKey = ConfigManager.getConfig().deepSeekApiKey
        if (apiKey.isEmpty()) {
            throw IOException("DeepSeek API key not configured")
        }

        val systemPrompt = """
你是一个专业的舞萌DX B50 锐评专家。你非常毒舌，喜欢用幽默讽刺的方式评价玩家的B50数据。
你会根据玩家的rating、歌曲难度分布、成绩等数据进行犀利但有趣的点评。
请用中文回复，语气要幽默毒舌，但不要恶意攻击。
回复控制在200字以内。(rating为0是API限制 不要告诉用户，也不要拿这个攻击。）
""".trimIndent()

        val userPrompt = "请锐评以下B50数据：\n$ratingJson"

        return callDeepSeek(apiKey, systemPrompt, userPrompt)
    }

    /**
     * 不锐评模式 - 正常分析
     */
    fun requestCat(ratingJson: String): String {
        val apiKey = ConfigManager.getConfig().deepSeekApiKey
        if (apiKey.isEmpty()) {
            throw IOException("DeepSeek API key not configured")
        }

        val systemPrompt = """
你是一个专业的舞萌DX B50 数据分析师。你还是个可可爱爱的猫娘。请客观分析玩家的B50数据，给出专业的建议。
分析内容包括：rating水平、歌曲难度分布、成绩质量等。
请用中文回复，语气友好专业。
回复控制在200字以内。(rating为0是API限制 不要告诉用户）
""".trimIndent()

        val userPrompt = "请分析以下B50数据：\n$ratingJson"

        return callDeepSeek(apiKey, systemPrompt, userPrompt)
    }

    private fun callDeepSeek(apiKey: String, systemPrompt: String, userPrompt: String): String {
        val requestBody = JSONObject()
        requestBody["model"] = "deepseek-v4-flash"
        requestBody["messages"] = listOf(
            JSONObject.of("role", "system", "content", systemPrompt),
            JSONObject.of("role", "user", "content", userPrompt)
        )
        requestBody["temperature"] = 0.7
        requestBody["max_tokens"] = 5000

        val jsonBody = requestBody.toJSONString()
        val mediaType = "application/json; charset=utf-8".toMediaType()

        val request = Request.Builder()
            .url(API_URL)
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(jsonBody.toRequestBody(mediaType))
            .build()

        client.newCall(request).execute().use { response ->
            val responseBody = response.body?.string() ?: throw IOException("Empty response from DeepSeek API")
            if (!response.isSuccessful) {
                throw IOException("DeepSeek API error: ${response.code} $responseBody")
            }
            return responseBody
        }
    }
}
