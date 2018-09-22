package info.kurozeropb.prinzeugen.commands.info

import info.kurozeropb.prinzeugen.Prinz
import info.kurozeropb.prinzeugen.User
import info.kurozeropb.prinzeugen.commands.Command
import info.kurozeropb.prinzeugen.managers.DatabaseManager
import info.kurozeropb.prinzeugen.utils.Utils
import net.dv8tion.jda.core.Permission
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import okhttp3.MediaType
import okhttp3.Request
import okhttp3.RequestBody
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.set

class Profile : Command(
        name = "profile",
        category = "info",
        description = "View your profile card",
        aliases = listOf("me", "level", "card"),
        cooldown = 30,
        botPermissions = listOf(Permission.MESSAGE_WRITE, Permission.MESSAGE_ATTACH_FILES)
) {

    override suspend fun execute(args: List<String>, e: MessageReceivedEvent) {
        Utils.catchAll("Exception occured in profile command", e.channel) {
            var user = DatabaseManager.users.findOne(User::id eq e.author.id)

            if (args.isNotEmpty() && args[0] == "update") {
                if (args.size <= 2) {
                    e.reply("Please specify what to update and the value to update.\n" +
                            "For more help about this command please visit https://prinz-eugen.info/settings")
                    return
                }

                when (args[1]) {
                    "bg", "background" -> {
                        val regex = """^(http)?s?:?(//[^"']*\.(?:png|jpg|jpeg|gif|svg))$""".toRegex()

                        if (!args[2].matches(regex)) {
                            e.reply("I don't think that's a valid image url, if it is please talk to my owner about it")
                            return
                        }

                        if (user == null) {
                            DatabaseManager.users.insertOne(User(e.author.id, background = args[2]))
                            e.reply("Successfully updated your profile background")
                            return
                        }

                        DatabaseManager.users.updateOne(User::id eq e.author.id, set(User::background, args[2]))
                        e.reply("Successfully updated your profile background")
                    }
                    "about" -> {
                        val message = args.subList(2, args.size).joinToString(" ")

                        if (user == null) {
                            DatabaseManager.users.insertOne(User(e.author.id, about = message))
                            e.reply("Successfully updated your about description")
                            return
                        }

                        DatabaseManager.users.updateOne(User::id eq e.author.id, set(User::about, message))
                        e.reply("Successfully updated your about description")
                    }
                }
                return
            }

            if (user == null) {
                DatabaseManager.users.insertOne(User(e.author.id))
                user = User(e.author.id)
            }

            val size = if (args.isNotEmpty()) args[0].toLowerCase() else "small"
            if (size != "large" && size != "small") {
                e.reply("Invalid size, only sizes 'large' and 'small' are allowed.")
                return
            }
            val json = """
                {
                    "username": "${e.author.name}",
                    "avatar": "${e.author.effectiveAvatarUrl}",
                    "about": "${user.about}",
                    "level": ${user.level},
                    "points": ${user.points},
                    "background": "${user.background}",
                    "size": "$size"
                }
            """.trimIndent()

            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val requestBody = RequestBody.create(mediaType, json)
            val apiUrl = Prinz.config.api.url + "/profile"
            val request = Request.Builder()
                    .url(apiUrl)
                    .addHeader("authorization", Prinz.config.api.token)
                    .post(requestBody)
                    .build()

            val response = Prinz.httpClient.newCall(request).execute()

            if (response.isSuccessful) {
                val file = response.body()!!.bytes()
                e.channel.sendFile(file, "${e.author.name.toLowerCase().replace(" ", "_")}-profile-card-$size.png").queue()
            } else {
                val message = response.message()
                response.close()
                throw Exception(message)
            }
            response.close()
        }
    }
}