package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

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

    override fun handleQr(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String) {
         commandScope.launch {
            try {
                val packetResult = UserTokenAndIDPacket(qrToken).execute()

                if (packetResult.first < 10000000) {
                    bot.sendPrivateMsg(qqUserId, "❌ 无效的QrCode Token. 错误代码：${packetResult.first}", false)
                    return@launch
                }

                //TODO:Generate b50 image logic.
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

