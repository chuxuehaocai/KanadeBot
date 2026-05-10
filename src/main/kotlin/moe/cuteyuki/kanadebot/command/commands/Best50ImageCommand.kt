package moe.cuteyuki.kanadebot.command.commands

import com.alibaba.fastjson2.JSON
import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.mainetwork.NetworkManager
import moe.cuteyuki.kanadebot.mainetwork.beans.UserPreviewDataBean
import moe.cuteyuki.kanadebot.mainetwork.packet.UserPreviewPacket
import moe.cuteyuki.kanadebot.mainetwork.packet.UserTokenAndIDPacket
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.managers.ResourceManager
import moe.cuteyuki.kanadebot.utils.Logger
import java.io.File

class Best50ImageCommand: ICommand {
    private val commandScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    
    override val data: CommandData
        get() = CommandData(
            name = "b50",
            description = "Get your best50 image",
            usage = "b50",
            aliases = listOf()
        )

    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return
        val userId = event.sender.userId

        // 先检查本地缓存文件 resource/DataCache/<qq uid>_b50.json
        val cacheFile = File(ResourceManager.dataCacheFolder, "${userId}_b50.json")
        if (cacheFile.exists()) {
            val replyMsg = MsgUtils.builder()
                .reply(event.messageId)
                .at(userId)
                .text(" ✅ 已有你的b50缓存数据，直接生成图片中...")
                .build()
            bot.sendGroupMsg(event.groupId, replyMsg, false)
            // TODO: 从缓存文件读取数据并生成b50图片
            return
        }

        PendingLoginManager.register(userId, B50ImageContext(event.groupId, event.messageId)) { b, uid, qrResult, context ->
            val ctx = context as B50ImageContext
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
         commandScope.launch {
            try {
                val packetResult = UserTokenAndIDPacket(qrToken).execute()

                if (packetResult.first < 10000000) {
                    bot.sendPrivateMsg(qqUserId, "❌ 无效的QrCode Token. 错误代码：${packetResult.first}", false)
                    return@launch
                }

                val targetUserId = packetResult.first
                val token = packetResult.second
                val cfg = ConfigManager.getConfig()

                // 发送 UserPreviewPacket 并反序列化到 userPreviewData
                val previewPacket = UserPreviewPacket(targetUserId, "", token, cfg.clientId)
                val previewResultStr = callApiSuspend("GetUserPreviewApi", previewPacket.toJson(), targetUserId)
                Logger.log(previewResultStr, Logger.LogType.INFO)
                val userPreviewData = JSON.parseObject(previewResultStr, UserPreviewDataBean::class.java)
                //TODO:Generate b50 image logic using userPreviewData.
                val avatarFilePath = ResourceManager.iconImagePath(userPreviewData.iconId.toString())

            } catch (e: Exception) {
                System.err.println("[Best50ImageCommand] Error: ${e.message}")
                e.printStackTrace()
                bot.sendPrivateMsg(qqUserId, "❌ 处理出错: ${e.message}", false)
            }
        }
    }

    private data class B50ImageContext(
        val groupId: Long,
        val messageId: Int,
    )
}