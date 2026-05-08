package moe.cuteyuki.kanadebot.managers

import com.mikuac.shiro.core.Bot
import com.mikuac.shiro.dto.event.message.GroupMessageEvent
import com.mikuac.shiro.dto.event.message.MessageEvent
import moe.cuteyuki.kanadebot.command.ICommand
import moe.cuteyuki.kanadebot.utils.replyGroupMsg

object CommandManager {
    /** 文本命令前缀，如 ".help" 中的 "." */
    private const val TEXT_PREFIX = "."

    /** 已注册的命令表：name -> command */
    private val commands: MutableMap<String, ICommand> = mutableMapOf()

    /** 别名映射：alias -> commandName */
    private val aliases: MutableMap<String, String> = mutableMapOf()


    /**
     * 注册一个命令到管理器
     */
    fun register(command: ICommand) {
        commands[command.data.name] = command

        // 注册别名
        command.data.aliases?.forEach { alias ->
            aliases[alias] = command.data.name
        }
    }

    /**
     * 根据命令名获取命令实例
     */
    fun getCommand(name: String): ICommand? {
        return commands[name] ?: aliases[name]?.let { commands[it] }
    }

    /**
     * 获取所有已注册的命令
     */
    fun getCommands(): Map<String, ICommand> {
        return commands.toMap()
    }

    /**
     * 检查是否存在指定名称的命令
     */
    fun hasCommand(name: String): Boolean {
        return commands.containsKey(name) || aliases.containsKey(name)
    }

    /**
     * 从消息中解析并执行命令
     * @return true 如果消息被识别为命令并尝试执行，false 否则
     */
    fun process(bot: Bot, event: MessageEvent): Boolean {
        val rawMessage = event.message.trim()

        if (rawMessage.startsWith(TEXT_PREFIX)) {
            val withoutPrefix = rawMessage.removePrefix(TEXT_PREFIX).trim()
            return executeCommand(bot, event, withoutPrefix)
        }

        return false
    }

    /**
     * 解析并执行去掉前缀后的命令字符串
     */
    private fun executeCommand(bot: Bot, event: MessageEvent, message: String): Boolean {
        val parts = message.split("\\s+".toRegex()).filter { it.isNotEmpty() }
        if (parts.isEmpty()) return false

        val commandName = parts[0].lowercase()
        val args = if (parts.size > 1) parts.drop(1).toTypedArray() else emptyArray()

        // 查找命令（含别名）
        val command = commands[commandName] ?: aliases[commandName]?.let { commands[it] } ?: return false

        return try {
            command.process(bot, event, args)
            true
        } catch (e: Exception) {
            System.err.println("[CommandManager] Error executing command '$commandName': ${e.message}")
            e.printStackTrace()
            // 向用户反馈错误
            if (event is GroupMessageEvent) {
                bot.replyGroupMsg(event, "执行命令时出错: ${e.message}")
            }
            true
        }
    }
}
