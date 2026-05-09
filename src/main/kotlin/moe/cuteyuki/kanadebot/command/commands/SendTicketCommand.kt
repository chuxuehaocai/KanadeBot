package moe.cuteyuki.kanadebot.command.commands

import com.alibaba.fastjson2.JSON
import com.alibaba.fastjson2.JSONObject
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.GroupContext
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.mainetwork.NetworkManager
import moe.cuteyuki.kanadebot.mainetwork.payload.PayloadBuilder
import moe.cuteyuki.kanadebot.mainetwork.packet.UserDataPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserLoginPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserLogoutPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserTokenAndIDPacket
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.utils.replyGroupMsg
import java.util.concurrent.ConcurrentHashMap

/**
 * 使用功能票（发票）命令
 *
 * 完整流程：
 *   1. QR 认证 → 获取 userId & token
 *   2. UserLoginApi → 登录 (获取 loginId, loginDate)
 *   3. GetUserDataApi → 获取用户数据 (playerRating)
 *   4. UpsertUserChargelogApi → 上传票购买记录
 *   5. UploadUserPlaylogListApi → 上传游玩记录（使用票）
 *   6. UpsertUserAllApi → 更新用户数据
 *   7. UserLogoutApi → 登出
 */
class SendTicketCommand : ICommand {

    /** 按 QQ 用户 ID 存储待处理的 ticketId */
    private val pendingTicketIds = ConcurrentHashMap<Long, Int>()

    /**
     * 协程作用域，用于启动异步 API 调用流程
     * SupervisorJob 确保单个协程失败不会影响其他协程
     */
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val data: CommandData
        get() = CommandData(
            name = "getTicket",
            description = "使用功能票（发票），流程：QR登录→发票→游玩→更新数据→登出",
            usage = "getTicket <TicketID>",
            aliases = listOf("发票")
        )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId

        if (args.size != 1) {
            bot.replyGroupMsg(event, "❌无效的参数。 正确方法：" + data.usage)
            return
        }

        // 验证 ticketId 是否为有效数字
        val ticketId: Int = try {
            args[0].toInt()
        } catch (e: NumberFormatException) {
            bot.replyGroupMsg(event, "❌无效的TicketID，请输入数字")
            return
        }

        // 保存 ticketId 供后续 handleQr 使用
        pendingTicketIds[userId] = ticketId

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
     * 挂起版本的 callApi，在 IO 调度器上执行
     */
    private suspend fun callApiSuspend(apiName: String, jsonBody: String, userId: Long): String {
        return NetworkManager.sendToTitleSuspend(jsonBody, apiName, userId)
    }

    override fun handleQr(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String) {
        // 从挂起表中取出 ticketId
        val ticketId = pendingTicketIds.remove(qqUserId)
            ?: run {
                bot.sendPrivateMsg(qqUserId, "❌ ticketId 丢失，请重新发起命令", false)
                return
            }

        // 在协程中启动完整的异步流程，不阻塞当前线程
        commandScope.launch {
            try {
                // 1. QR 认证 → 获取 userId & token
                val packetResult = UserTokenAndIDPacket(qrToken).execute()

                if (packetResult.first < 10000000) {
                    bot.sendPrivateMsg(qqUserId, "无效的QrCode Token. 错误代码：${packetResult.first}", false)
                    return@launch
                }

                val targetUserId = packetResult.first
                val token = packetResult.second
                val cfg = ConfigManager.getConfig()

                // 执行完整流程（挂起函数，不会阻塞线程）
                completeTicketFlow(bot, qqUserId, groupId, messageId, targetUserId, token, cfg, ticketId)

            } catch (e: Exception) {
                System.err.println("[SendTicketCommand] 错误: ${e.message}")
                e.printStackTrace()
                bot.sendPrivateMsg(qqUserId, "处理出错: ${e.message}", false)
            }
        }
    }

    /**
     * 完整的功能票使用流程（挂起版本）
     */
    private suspend fun completeTicketFlow(
        bot: Bot, qqUserId: Long, groupId: Long, messageId: Int,
        targetUserId: Long, token: String, cfg: moe.cuteyuki.kanadebot.config.Config,
        ticketId: Int
    ) {
        var loginId: Long = 0
        var loginDate: Any = 0L

        try {
            // ========== 2. 登录 ==========
            val ts = System.currentTimeMillis() / 1000
            val loginPacket = UserLoginPacket(
                targetUserId, "", cfg.regionId, cfg.placeId, cfg.clientId,
                ts - 600, ts, false, 0, token
            )
            val loginResultStr = callApiSuspend("UserLoginApi", loginPacket.toJson(), targetUserId)
            val loginResult = JSON.parseObject(loginResultStr)
            val returnCode = loginResult.getIntValue("returnCode")
            if (returnCode != 1) {
                bot.sendPrivateMsg(qqUserId, "❌ 登录失败 (returnCode=$returnCode)", false)
                return
            }
            loginId = loginResult.getLongValue("loginId")
            loginDate = loginResult.get("lastLoginDate") ?: 0L
            println("[SendTicketCommand] 登录成功 loginId=$loginId, loginDate=$loginDate")

            // ========== 3. 获取用户数据（获取 playerRating 用于发票）==========
            val userDataPacket = UserDataPacket(targetUserId)
            val userDataResultStr = callApiSuspend("GetUserDataApi", userDataPacket.toJson(), targetUserId)
            val userDataJson: JSONObject = JSON.parseObject(userDataResultStr) ?: JSONObject()
            val playerRating = userDataJson.getJSONObject("userData")
                ?.getIntValue("playerRating") ?: 0
            println("[SendTicketCommand] 当前 Rating: $playerRating")

            // ========== 4. UpsertUserChargelogApi → 发票 ==========
            val ticketRequest = PayloadBuilder.generateTicketRequest(
                targetUserId, ticketId, loginDate, cfg, playerRating
            )
            callApiSuspend("UpsertUserChargelogApi", ticketRequest, targetUserId)
            println("[SendTicketCommand] 功能票 (ID: $ticketId) 已发送")

            // ========== 5. 上传游玩记录（使用票）==========
            // 构造 musicData
            val musicData = mapOf<String, Any>(
                "musicId" to 417,
                "level" to 3,
                "playCount" to 1,
                "achievement" to 1010000,
                "comboStatus" to 4,
                "syncStatus" to 4,
                "deluxscoreMax" to 2277,
                "scoreRank" to 13,
                "extNum1" to 0
            )

            // 构建 userInfoList (7 elements)
            val userInfoList = listOf(
                userDataJson,  // GetUserDataApi result (index 0)
                JSONObject(),  // userExtend (index 1)
                JSONObject(),  // userOption (index 2)
                JSONObject(),  // userRating (index 3)
                JSONObject(),  // userCharge (index 4)
                JSONObject(),  // userActivity (index 5)
                JSONObject()   // userMissionData (index 6)
            )

            // 生成 playlog 请求
            val playlogRequest = PayloadBuilder.generatePlaylogRequest(
                loginId, musicData, userInfoList, cfg, ticketId
            )
            callApiSuspend("UploadUserPlaylogListApi", playlogRequest, targetUserId)
            println("[SendTicketCommand] 游玩记录已上传")

            // ========== 6. UpsertUserAllApi → 更新用户数据 ==========
            val userAllRequest = PayloadBuilder.generateUserAllRequest(
                loginId, loginDate, musicData, userInfoList, cfg, ticketId, targetUserId
            )
            val userAllResult = callApiSuspend("UpsertUserAllApi", userAllRequest, targetUserId)

            // ========== 7. 构建回复消息 ==========
            val msgBuilder = MsgUtils.builder()
                .reply(messageId)
                .at(qqUserId)
                .text(" ✅ 功能票使用完成！\n")

            val ticketName = TICKET_MAP.getOrDefault(ticketId, "票$ticketId")
            msgBuilder.text("🎫 使用票: $ticketName (ID: $ticketId)\n")

            val userAllJson = JSON.parseObject(userAllResult)
            val rc = userAllJson.getIntValue("returnCode")
            msgBuilder.text(if (rc == 1) "✅ 数据更新成功\n" else "⚠️ 数据更新 returnCode=$rc\n")

            // 检查 userGetPointList 奖励
            val upsertAll = userAllJson.getJSONObject("upsertUserAll")
            if (upsertAll != null) {
                val pointList = upsertAll.getJSONArray("userGetPointList")
                if (pointList != null && pointList.isNotEmpty()) {
                    msgBuilder.text("🎁 获得奖励:\n")
                    for (i in 0 until pointList.size) {
                        val reward = pointList.getJSONObject(i)
                        msgBuilder.text("  - pointType: ${reward.getIntValue("pointType")}, point: ${reward.getIntValue("addPoint")}\n")
                    }
                }
            }

            msgBuilder.text("✅ 已安全登出")
            bot.sendGroupMsg(groupId, msgBuilder.build(), false)
            bot.sendPrivateMsg(qqUserId, "✅ 功能票使用流程完成！", false)

        } catch (e: Exception) {
            System.err.println("[SendTicketCommand] completeTicketFlow 错误: ${e.message}")
            e.printStackTrace()
            bot.sendPrivateMsg(qqUserId, "❌ 功能票处理出错: ${e.message}", false)
        } finally {
            // ========== 8. 登出（必须始终执行） ==========
            if (loginId != 0L) {
                try {
                    val logoutPacket = UserLogoutPacket(
                        targetUserId, "", cfg.regionId, cfg.placeId, cfg.clientId, loginDate, 1
                    )
                    callApiSuspend("UserLogoutApi", logoutPacket.toJson(), targetUserId)
                    println("[SendTicketCommand] 已安全登出")
                } catch (e: Exception) {
                    System.err.println("[SendTicketCommand] 登出失败: ${e.message}")
                }
            }
        }
    }

    companion object {
        private val TICKET_MAP = mapOf(
            1 to "功能票",
            2 to "6倍功能票",
            3 to "3倍功能票",
            4 to "自由模式票",
            5 to "段位认定票"
        )
    }
}
