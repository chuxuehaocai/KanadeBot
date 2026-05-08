package moe.cuteyuki.kanadebot.command

data class CommandData(
    val name: String,
    val description: String? = null,
    val usage: String? = null,
    /** 命令别名 */
    val aliases: List<String>? = null
)
