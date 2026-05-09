package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

/**
 * 用户登出请求包
 */
data class UserLogoutPacket(
    @JSONField(name = "userId")
    val userId: Long,

    @JSONField(name = "accessCode")
    val accessCode: String = "",

    @JSONField(name = "regionId")
    val regionId: Int,

    @JSONField(name = "placeId")
    val placeId: Int,

    @JSONField(name = "clientId")
    val clientId: String,

    @JSONField(name = "loginDateTime")
    val loginDateTime: Any,

    @JSONField(name = "type")
    val type: Int = 1
) : IPacket

