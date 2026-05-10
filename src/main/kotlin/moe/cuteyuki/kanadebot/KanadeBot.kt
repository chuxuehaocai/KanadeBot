package moe.cuteyuki.kanadebot

import com.mikuac.shiro.annotation.GroupMessageHandler
import com.mikuac.shiro.annotation.PrivateMessageHandler
import com.mikuac.shiro.annotation.common.Shiro
import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.core.BotPlugin
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.PrivateMessageEvent
import com.mikuac.shiro.enums.MsgTypeEnum
import jakarta.annotation.PostConstruct
import moe.cuteyuki.kanadebot.command.commands.Best50ImageCommand
import moe.cuteyuki.kanadebot.command.commands.EvaluateRatingCommand
import moe.cuteyuki.kanadebot.command.commands.MaiMileCommand
import moe.cuteyuki.kanadebot.command.commands.SendTicketCommand
import moe.cuteyuki.kanadebot.command.commands.Test
import moe.cuteyuki.kanadebot.command.commands.UpdateMusicDataCommand
import moe.cuteyuki.kanadebot.command.commands.WhoamiCommand
import moe.cuteyuki.kanadebot.managers.CommandManager
import moe.cuteyuki.kanadebot.managers.ConfigManager
import moe.cuteyuki.kanadebot.managers.PendingLoginManager
import moe.cuteyuki.kanadebot.managers.ResourceManager
import moe.cuteyuki.kanadebot.utils.QRCodeUtil
import org.springframework.stereotype.Component

@Component
@Shiro
class KanadeBot: BotPlugin() {
    @PostConstruct
    fun initialize(){
        println("KanadeBot initializing...")
        ConfigManager.initialize()
        ResourceManager.initialize()
        CommandManager.register(Test())
        CommandManager.register(WhoamiCommand())
        CommandManager.register(SendTicketCommand())
        CommandManager.register(MaiMileCommand())
        CommandManager.register(EvaluateRatingCommand())
        CommandManager.register(UpdateMusicDataCommand())
        CommandManager.register(Best50ImageCommand())
        println("KanadeBot initialized. ${CommandManager.getCommands().size} command(s) registered.")
    }

    @GroupMessageHandler
    override fun onGroupMessage(bot: Bot, event: GroupMessageEvent): Int{
        CommandManager.process(bot, event)
        return MESSAGE_IGNORE
    }

    @PrivateMessageHandler
    override fun onPrivateMessage(bot: Bot, event: PrivateMessageEvent): Int {
        val arrayMsg = event.arrayMsg
        if (arrayMsg.isNullOrEmpty()) return MESSAGE_IGNORE

        for (msg in arrayMsg) {
            if (msg.type == MsgTypeEnum.image) {
                val imageUrl = msg.getStringData("url") ?: continue
                val qrResult = QRCodeUtil.decodeFromUrl(imageUrl)
                if (qrResult == null) continue

                // 检查是否有待处理的 QR 回调
                val consumed = PendingLoginManager.consume(event.userId, bot, qrResult)

                // 如果没有待处理的回调，且二维码不是 SGWCMAID 开头，提示无效
                if (!consumed && !qrResult.startsWith("SGWCMAID")) {
                    bot.sendPrivateMsg(event.userId, "无效的登陆二维码", false)
                }

                return MESSAGE_IGNORE
            }
        }

        return MESSAGE_IGNORE
    }
}
