package info.kurozeropb.prinzeugen.commands.`fun`

import info.kurozeropb.prinzeugen.commands.Command
import info.kurozeropb.prinzeugen.utils.Utils
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.MessageReceivedEvent

class Aesthetic : Command(
        name = "aesthetic",
        aliases = listOf("aes"),
        category = "fun",
        description = "Convert text to aesthetic text",
        botPermissions = listOf(Permission.MESSAGE_WRITE)
) {

    override suspend fun execute(args: List<String>, e: MessageReceivedEvent) {
        Utils.catchAll("Exception occured in ping command", e.channel) {
            if (args.isEmpty()) {
                e.reply("Insufficient argument count")
                return
            }

            var message = args.joinToString(" ")
            message = message.replace(Regex("[a-zA-Z0-9!?.'\";:\\]\\[}{)(@#\$%^&*\\-_=+`~><]")) { c -> c.value[0].plus(0xFEE0).toString() }
            message = message.replace(Regex(" "), "　")
            e.reply(message)
        }
    }
}