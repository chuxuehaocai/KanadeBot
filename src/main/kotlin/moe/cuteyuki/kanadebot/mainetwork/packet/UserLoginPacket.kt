package moe.cuteyuki.kanadebot.mainetwork.packet

import com.alibaba.fastjson2.annotation.JSONField
import moe.cuteyuki.kanadebot.mainetwork.IPacket

/**
 * 用户登录请求包 (from api.py login)
 *
 * Python 原始代码:
 * ```
 * data = {
 *     "userId": user_id,
 *     "accessCode": "",
 *     "regionId": self.cfg["regionId"],
 *     "placeId": self.cfg["placeId"],
 *     "clientId": self.cfg["clientId"],
 *     "dateTime": ts - 600,
 *     "loginDateTime": ts,
 *     "isContinue": False,
 *     "genericFlag": 0,
 *     "token": token
 * }
 * return await self.call_api("UserLoginApi", client, data, user_id)
 * ```
 */
data class UserLoginPacket(
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

    @JSONField(name = "dateTime")
    val dateTime: Long,

    @JSONField(name = "loginDateTime")
    val loginDateTime: Long,

    @JSONField(name = "isContinue")
    val isContinue: Boolean = false,

    @JSONField(name = "genericFlag")
    val genericFlag: Int = 0,

    @JSONField(name = "token")
    val token: String
) : IPacket
