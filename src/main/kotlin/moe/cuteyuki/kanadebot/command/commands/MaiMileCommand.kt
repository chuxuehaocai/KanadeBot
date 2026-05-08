package moe.cuteyuki.kanadebot.command.commands

import com.mikuac.shiro.common.utils.MsgUtils
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.CommandData
import moe.cuteyuki.kanadebot.command.GroupContext
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.main
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.utils.replyGroupMsg
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class MaiMileCommand: ICommand {
    /** 按 QQ 用户 ID 存储待处理的 maimile */
    private val pendingMaimile = ConcurrentHashMap<Long, Int>()
    override fun process(bot: Bot, event: MessageEvent, args: Array<String>) {
        if (event !is GroupMessageEvent) return

        val userId = event.sender.userId

        if (args.size != 1) {
            bot.replyGroupMsg(event, "❌无效的参数。 正确方法：" + data.usage)
            return
        }

        // 验证是否为有效数字
        val maiMile: Int = try {
            if(args[0].toInt() > 99999 || args[0].toInt() < 1) {
                bot.replyGroupMsg(event, "❌无效的。数字不可以大于99999或者小于1.")
                return
            }
            args[0].toInt()
        } catch (e: NumberFormatException) {
            bot.replyGroupMsg(event, "❌无效的。请输入数字")
            return
        }

        // 保存 maiMile 供后续 handleQr 使用
        pendingMaimile[userId] = maiMile

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

    override fun handleQr(bot: Bot, qqUserId: Long, groupId: Long, messageId: Int, qrToken: String) {
        super.handleQr(bot, qqUserId, groupId, messageId, qrToken)
    }

    override val data: CommandData
        get() = CommandData(
            name = "maiMile",
            description = "A test command",
            usage = "maiMile <target Maimile>",
            aliases = listOf("加里程", "addMile")
        )
}