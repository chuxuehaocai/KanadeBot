package moe.cuteyuki.kanadebot.managers

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import moe.cuteyuki.kanadebot.config.Config
import java.io.File
import java.nio.file.Paths

object ConfigManager {
    private const val CONFIG_FILE_NAME = "kanade.json"

    private lateinit var config: Config
    private lateinit var configFile: File

    /**
     * 初始化配置管理器，从文件中加载配置
     * 如果文件不存在则创建默认配置
     */
    fun initialize() {
        val workingDir = Paths.get("").toAbsolutePath().toString()
        configFile = File(workingDir, CONFIG_FILE_NAME)

        if (configFile.exists()) {
            loadConfig()
        } else {
            createDefaultConfig()
        }
    }

    /**
     * 从 JSON 文件中加载配置
     */
    private fun loadConfig() {
        val content = configFile.readText()
        val json = JSON.parseObject(content)

        // 去除 titleServerUrl 末尾的斜杠，防止拼接 API hash 时出现双斜杠
        var rawUrl = json.getString("titleServerUrl") ?: ""
        if (rawUrl.endsWith("/")) {
            rawUrl = rawUrl.removeSuffix("/")
        }

        config = Config(
            keychipId = json.getString("keychipId") ?: "",
            aimeSalt = json.getString("aimeSalt") ?: "",
            aesIv = json.getString("aesIv") ?: "",
            aesKey = json.getString("aesKey") ?: "",
            titleServerUrl = rawUrl,
            aimeUrl = json.getString("aimeUrl") ?: "",
            packetSalt = json.getString("packetSalt") ?: "",
            obfuscateParam = json.getString("obfuscateParam") ?: "LatuAa81",
            apiVersion = json.getString("apiVersion") ?: "1.53",
            clientId = json.getString("clientId") ?: "",
            regionId = json.getIntValue("regionId"),
            regionName = json.getString("regionName") ?: "",
            placeId = json.getIntValue("placeId"),
            placeName = json.getString("placeName") ?: "",
            deepSeekApiKey = json.getString("deepSeekApiKey") ?: "",
        )
    }


    /**
     * 创建默认配置文件并保存
     */
    private fun createDefaultConfig() {
        config = Config()
        saveConfig()
        println("Created default config file: ${configFile.absolutePath}")
    }

    /**
     * 将当前配置保存到 JSON 文件
     */
    fun saveConfig() {
        val json = JSONObject()
        json["keychipId"] = config.keychipId
        json["aimeSalt"] = config.aimeSalt
        json["aesIv"] = config.aesIv
        json["aesKey"] = config.aesKey
        json["titleServerUrl"] = config.titleServerUrl
        json["aimeUrl"] = config.aimeUrl
        json["packetSalt"] = config.packetSalt
        json["obfuscateParam"] = config.obfuscateParam
        json["apiVersion"] = config.apiVersion
        json["clientId"] = config.clientId
        json["regionId"] = config.regionId
        json["regionName"] = config.regionName
        json["placeId"] = config.placeId
        json["placeName"] = config.placeName
        json["deepSeekApiKey"] = config.deepSeekApiKey

        configFile.writeText(json.toJSONString(com.alibaba.fastjson2.JSONWriter.Feature.PrettyFormat))
    }

    /**
     * 获取当前配置
     */
    fun getConfig(): Config {
        if (!::config.isInitialized) {
            initialize()
        }
        return config
    }

    /**
     * 更新配置并保存到文件
     */
    fun updateConfig(newConfig: Config) {
        config = newConfig
        saveConfig()
    }
}
