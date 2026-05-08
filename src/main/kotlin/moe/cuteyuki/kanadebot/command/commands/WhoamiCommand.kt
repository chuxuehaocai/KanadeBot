package moe.cuteyuki.kanadebot.command.commands

import com.alibaba.fastjson2.JSON
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.GroupContext
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.mainetwork.NetworkManager
import moe.cuteyuki.kanadebot.mainetwork.beans.*
import moe.cuteyuki.kanadebot.mainetwork.packet.*
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.PendingLoginManager

private val TICKET_MAP = mapOf(
    1 to "功能票",
    2 to "6倍功能票",
    3 to "3倍功能票",
    4 to "自由模式票",
    5 to "段位认定票"
)

class WhoamiCommand: ICommand {
    override val data: CommandData
        get() = CommandData(
            name = "whoami",
            description = "Show the data for your account.",
            usage = "whoami",
            aliases = listOf("wami")
        )

    override fun process(
        bot: Bot,
        event: MessageEvent,
        args: Array<String>
    ) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId

        // 注册二维码回调
        PendingLoginManager.register(userId, GroupContext(event.groupId, event.messageId)) { b, uid, qrResult, context ->
            val ctx = context as GroupContext
            handleQr(b, uid, ctx.groupId, ctx.messageId, qrResult)
        }

        // 回复用户提示
        val replyMsg = MsgUtils.builder()
            .reply(event.messageId)
            .at(userId)
            .text(" 请私聊发送你的登陆二维码给我，你有2分钟时间 ⏰")
            .build()

        bot.sendGroupMsg(event.groupId, replyMsg, false)
    }

    /**
     * 安全调用 API，如果出错则返回 null
     */
    private fun safeCallApi(apiName: String, jsonBody: String, userId: Long): String? {
        return try {
            NetworkManager.sendToTitle(jsonBody, apiName, userId)
        } catch (e: Exception) {
            System.err.println("[WhoamiCommand] $apiName 调用失败: ${e.message}")
            null
        }
    }

    /**
     * 处理 whoami 的二维码扫码结果
     * 流程: QR → login → GetUserPreviewApi → GetUserDataApi → GetUserKaleidxScopeApi
     *       → GetUserMissionDataApi → GetUserChargeApi → logout
     * (from api.py & main.py)
     */
    override fun handleQr(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String) {
        try {
            val packetResult = UserTokenAndIDPacket(qrToken).execute()

            if (packetResult.first < 10000000) {
                bot.sendPrivateMsg(qqUserId, "无效的QrCode Token. 错误代码：${packetResult.first}", false)
                return
            }

            val targetUserId = packetResult.first
            val token = packetResult.second
            val cfg = ConfigManager.getConfig()

            val previewPacket = UserPreviewPacket(
                targetUserId,
                "",
                token,
                cfg.clientId
            )
            val previewResult = safeCallApi("GetUserPreviewApi", previewPacket.toJson(), targetUserId)
            val userPreviewData = previewResult?.let { JSON.parseObject(it, UserPreviewDataBean::class.java) }

            val chargePacket = UserChargePacket(targetUserId)

            val chargeResult = safeCallApi("GetUserChargeApi", chargePacket.toJson(), targetUserId)
            val userChargeData = chargeResult?.let { JSON.parseObject(it, UserChargeData::class.java) }

            // --- 5. 构建消息 ---
            val msgBuilder = MsgUtils.builder()
                .reply(messageId)
                .at(qqUserId)
                .text(" ✅ 获取成功 下面是你当前的用户信息\n")

            // GetUserPreviewApi
            if (userPreviewData != null) {
                msgBuilder.text("昵称: ${userPreviewData.userName} (${userPreviewData.playerRating})\n")
                msgBuilder.text(" - 最后游玩版本: ${userPreviewData.lastRomVersion}\n")
                msgBuilder.text(" - 最后存档版本: ${userPreviewData.lastDataVersion}\n")
                msgBuilder.text(" - 旅行伙伴觉醒次数: ${userPreviewData.totalAwake}\n")
                msgBuilder.text(" - 当前账号封禁状态(banState): ${userPreviewData.banState}\n")
                msgBuilder.text(" - 当前帐号小黑屋状态(isLogin): ${userPreviewData.isLogin}\n")
                msgBuilder.text(" - 账号最后登录时间(lastLoginDate): ${userPreviewData.lastLoginDate}\n")
            } else {
                msgBuilder.text("❌ 获取用户基本信息失败\n")
            }


            // GetUserChargeApi
            if (userChargeData != null) {
                val chargeList = userChargeData.userChargeList
                if (!chargeList.isNullOrEmpty()) {
                    msgBuilder.text("\n🎫 功能票\n")
                    for ((index, item) in chargeList.withIndex()) {
                        val tname = TICKET_MAP.getOrDefault(item.chargeId, "票${item.chargeId}")
                        msgBuilder.text("  [${index + 1}] $tname (ID:${item.chargeId}) | 剩余:${item.stock} | 有效期:${item.validDate}\n")
                    }
                } else {
                    msgBuilder.text("\n🎫 功能票: 无\n")
                }
            }
            bot.sendGroupMsg(groupId, msgBuilder.build(), false)
            bot.sendPrivateMsg(qqUserId, "✅ 已在群内发送你的用户信息！", false)

        } catch (e: Exception) {
            System.err.println("[WhoamiCommand] Error: ${e.message}")
            e.printStackTrace()
            bot.sendPrivateMsg(qqUserId, "登录处理出错: ${e.message}", false)
        }
    }
}
