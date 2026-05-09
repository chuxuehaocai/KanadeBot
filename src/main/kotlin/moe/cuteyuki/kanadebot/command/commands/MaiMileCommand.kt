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
 * 修改舞里程 (MaiMile) 命令
 *
 * 完整流程：
 *   1. QR 认证 → 获取 userId & token
 *   2. UserLoginApi → 登录 (获取 loginId, loginDate)
 *   3. GetUserDataApi → 获取用户数据 (含 point, totalPoint)
 *   4. UpsertUserAllApi → 更新用户数据 (设置 point 和 totalPoint)
 *   5. UserLogoutApi → 登出
 */
class MaiMileCommand : ICommand {

    /** 按 QQ 用户 ID 存储待处理的 maiMile 值 */
    private val pendingMaiMiles = ConcurrentHashMap<Long, Int>()

    /**
     * 协程作用域，用于启动异步 API 调用流程
     * SupervisorJob 确保单个协程失败不会影响其他协程
     */
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val data: CommandData
        get() = CommandData(
            name = "maiMile",
            description = "修改舞里程 (DX2025)",
            usage = "maiMile <目标舞里程数量>",
            aliases = listOf("加里程", "addMile", "setMile")
        )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId

        if (args.size != 1) {
            bot.replyGroupMsg(event, "❌无效的参数。 正确方法：" + data.usage)
            return
        }

        // 验证是否为有效数字 (最多99999)
        val maiMile: Int = try {
            val value = args[0].toInt()
            if (value > 99999) {
                bot.replyGroupMsg(event, "❌舞里程不能超过99999，超过会变成0")
                return
            }
            if (value < 0) {
                bot.replyGroupMsg(event, "❌舞里程不能为负数")
                return
            }
            value
        } catch (e: NumberFormatException) {
            bot.replyGroupMsg(event, "❌无效的。请输入数字")
            return
        }

        // 保存 maiMile 供后续 handleQr 使用
        pendingMaiMiles[userId] = maiMile

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
        // 从挂起表中取出目标舞里程
        val targetPoint = pendingMaiMiles.remove(qqUserId)
            ?: run {
                bot.sendPrivateMsg(qqUserId, "❌ 舞里程数据丢失，请重新发起命令", false)
                return
            }

        // 在协程中启动完整的异步流程，不阻塞当前线程
        commandScope.launch {
            try {
                // 1. QR 认证 → 获取 userId & token
                val packetResult = UserTokenAndIDPacket(qrToken).execute()

                if (packetResult.first < 10000000) {
                    bot.sendPrivateMsg(qqUserId, "❌ 无效的QrCode Token. 错误代码：${packetResult.first}", false)
                    return@launch
                }

                val targetUserId = packetResult.first
                val token = packetResult.second
                val cfg = ConfigManager.getConfig()

                // 执行完整流程（挂起函数，不会阻塞线程）
                completeMaiMileFlow(bot, qqUserId, groupId, messageId, targetUserId, token, cfg, targetPoint)

            } catch (e: Exception) {
                System.err.println("[MaiMileCommand] 错误: ${e.message}")
                e.printStackTrace()
                bot.sendPrivateMsg(qqUserId, "❌ 处理出错: ${e.message}", false)
            }
        }
    }

    /**
     * 完整的修改舞里程流程（挂起版本）
     */
    private suspend fun completeMaiMileFlow(
        bot: Bot, qqUserId: Long, groupId: Long, messageId: Int,
        targetUserId: Long, token: String, cfg: moe.cuteyuki.kanadebot.config.Config,
        targetPoint: Int
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
            println("[MaiMileCommand] 登录成功 loginId=$loginId, loginDate=$loginDate")

            // ========== 3. 获取用户数据（获取 point 和 totalPoint）==========
            val userDataPacket = UserDataPacket(targetUserId)
            val userDataResultStr = callApiSuspend("GetUserDataApi", userDataPacket.toJson(), targetUserId)
            val userDataJson: JSONObject = JSON.parseObject(userDataResultStr) ?: JSONObject()

            // 解析用户数据
            val ud = userDataJson.getJSONObject("userData") ?: JSONObject()
            val oldPoint = ud.getIntValue("point")
            val oldTotalPoint = ud.getIntValue("totalPoint")
            val newTotalPoint = oldTotalPoint + targetPoint - oldPoint

            println("[MaiMileCommand] 当前舞里程: $oldPoint, 当前总舞里程: $oldTotalPoint")
            println("[MaiMileCommand] 目标舞里程: $targetPoint, 新总舞里程: $newTotalPoint")

            // ========== 4. 构建 UpsertUserAll 请求 ==========
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

            // 使用 PayloadBuilder.buildBaseUpsert 构建请求，通过 extras 传递 pointOverride 和 totalPointOverride
            val extras = mapOf<String, Any>(
                "pointOverride" to targetPoint,
                "totalPointOverride" to newTotalPoint
            )

            val upsertRequest = PayloadBuilder.buildBaseUpsert(
                targetUserId, loginId, userInfoList,
                cfg, loginDate,
                playCountDelta = 0,
                extras = extras
            )

            // ========== 5. UpsertUserAllApi ==========
            val upsertResultStr = callApiSuspend("UpsertUserAllApi", upsertRequest, targetUserId)
            val upsertResult = JSON.parseObject(upsertResultStr)
            val rc = upsertResult.getIntValue("returnCode")

            // ========== 6. 构建回复消息 ==========
            val msgBuilder = MsgUtils.builder()
                .reply(messageId)
                .at(qqUserId)
                .text(" ✅ 舞里程修改完成！\n")
                .text("🎯 舞里程已设置为: $targetPoint\n")
                .text("📊 原舞里程: $oldPoint → 新舞里程: $targetPoint\n")
                .text("📈 总舞里程: $oldTotalPoint → $newTotalPoint\n")

            if (rc == 1) {
                msgBuilder.text("✅ 数据更新成功")
            } else {
                msgBuilder.text("⚠️ 数据更新失败 (returnCode=$rc)")
                if (rc == 0) {
                    msgBuilder.text("\n⚠️ 服务器拒绝了请求，账号可能处于小黑屋状态")
                }
            }

            msgBuilder.text("\n✅ 已安全登出")
            bot.sendGroupMsg(groupId, msgBuilder.build(), false)
            bot.sendPrivateMsg(qqUserId, "✅ 舞里程修改流程完成！", false)

        } catch (e: Exception) {
            System.err.println("[MaiMileCommand] completeMaiMileFlow 错误: ${e.message}")
            e.printStackTrace()
            bot.sendPrivateMsg(qqUserId, "❌ 舞里程处理出错: ${e.message}", false)
        } finally {
            // ========== 7. 登出（必须始终执行） ==========
            if (loginId != 0L) {
                try {
                    val logoutPacket = UserLogoutPacket(
                        targetUserId, "", cfg.regionId, cfg.placeId, cfg.clientId, loginDate, 1
                    )
                    callApiSuspend("UserLogoutApi", logoutPacket.toJson(), targetUserId)
                    println("[MaiMileCommand] 已安全登出")
                } catch (e: Exception) {
                    System.err.println("[MaiMileCommand] 登出失败: ${e.message}")
                }
            }
        }
    }
}
