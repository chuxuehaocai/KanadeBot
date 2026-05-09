package moe.cuteyuki.kanadebot.config

data class Config(
    val keychipId: String = "",
    val aimeSalt: String = "",
    val aesIv: String = "",
    val aesKey: String = "",
    val titleServerUrl: String = "",
    val aimeUrl: String = "",
    val packetSalt: String = "",
    val obfuscateParam: String = "LatuAa81",
    val apiVersion: String = "1.53",
    val clientId: String = "",
    val regionId: Int = 0,
    val regionName: String = "",
    val placeId: Int = 0,
    val placeName: String = "",
    val qqid: Long = 0,
    val deepSeekApiKey: String = ""
)
