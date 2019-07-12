package info.kurozeropb.jeanne.core

import com.github.ajalt.mordant.TermColors
import info.kurozeropb.jeanne.BotLists
import info.kurozeropb.jeanne.CommandData
import net.dv8tion.jda.core.MessageBuilder
import net.dv8tion.jda.core.events.message.MessageReceivedEvent
import net.dv8tion.jda.core.requests.RestAction
import kotlinx.coroutines.future.await
import info.kurozeropb.jeanne.Jeanne
import info.kurozeropb.jeanne.PlayingGame
import info.kurozeropb.jeanne.commands.Registry
import info.kurozeropb.jeanne.managers.DatabaseManager
import net.dv8tion.jda.core.EmbedBuilder
import net.dv8tion.jda.core.entities.*
import net.dv8tion.jda.core.events.guild.GuildBanEvent
import net.dv8tion.jda.core.events.guild.GuildUnbanEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberJoinEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberLeaveEvent
import net.dv8tion.jda.core.events.guild.member.GuildMemberNickChangeEvent
import net.dv8tion.jda.webhook.WebhookClientBuilder
import net.dv8tion.jda.webhook.WebhookMessageBuilder
import okhttp3.*
import org.litote.kmongo.eq
import org.litote.kmongo.findOne
import org.litote.kmongo.updateOne
import java.awt.Color
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.lang.NumberFormatException
import java.util.function.Consumer
import java.util.logging.Logger
import kotlin.system.measureTimeMillis

val games = listOf(
        PlayingGame("with Senpai", Game.GameType.DEFAULT),
        PlayingGame("with my master", Game.GameType.DEFAULT),
        PlayingGame("anime", Game.GameType.WATCHING),
        PlayingGame("secret things", Game.GameType.WATCHING),
        PlayingGame("with your feelings", Game.GameType.DEFAULT),
        PlayingGame("https://jeannebot.info", Game.GameType.WATCHING),
        PlayingGame("with %USERSIZE% users", Game.GameType.DEFAULT),
        PlayingGame("in %GUILDSIZE% servers", Game.GameType.DEFAULT),
        PlayingGame("%GUILDSIZE% servers", Game.GameType.WATCHING),
        PlayingGame("%USERSIZE% users", Game.GameType.WATCHING),
        PlayingGame("music", Game.GameType.LISTENING)
)

@Suppress("MemberVisibilityCanBePrivate")
class Utils(private val e: MessageReceivedEvent) {

    fun embedColor(): Color = e.guild?.selfMember?.color ?: Jeanne.embedColor

    fun reply(msg: Message, success: Consumer<Message>? = null) {
        if (!e.isFromType(ChannelType.TEXT) || e.textChannel.canTalk()) {
            e.channel.sendMessage(stripEveryoneHere(msg)).queue(success)
        }
    }

    fun reply(builder: EmbedBuilder, success: Consumer<Message>? = null) {
        if (!e.isFromType(ChannelType.TEXT) || e.textChannel.canTalk()) {
            val embed = builder
                    .setColor(embedColor())
                    .build()
            e.channel.sendMessage(embed).queue(success)
        }
    }

    fun reply(data: InputStream, fileName: String, message: String? = null, success: Consumer<Message>? = null) {
        if (!e.isFromType(ChannelType.TEXT) || e.textChannel.canTalk()) {
            if (message != null)
                e.channel.sendFile(data, fileName, build(message)).queue(success)
            else
                e.channel.sendFile(data, fileName).queue(success)
        }
    }

    fun reply(file: File, fileName: String, message: String? = null, success: Consumer<Message>? = null) {
        if (!e.isFromType(ChannelType.TEXT) || e.textChannel.canTalk()) {
            if (message != null)
                e.channel.sendFile(file, fileName, build(message)).queue(success)
            else
                e.channel.sendFile(file, fileName).queue(success)
        }
    }

    fun reply(bytes: ByteArray, fileName: String, message: String? = null, success: Consumer<Message>? = null) {
        if (!e.isFromType(ChannelType.TEXT) || e.textChannel.canTalk()) {
            if (message != null)
                e.channel.sendFile(bytes, fileName, build(message)).queue(success)
            else
                e.channel.sendFile(bytes, fileName).queue(success)
        }
    }

    fun reply(text: String, success: Consumer<Message>? = null) {
        reply(build(text), success)
    }

    companion object {
        const val ZERO_WIDTH_SPACE = "\u200E"
        val discordIdPattern = Regex("\\d{17,20}")
        val userMentionPattern = Regex("<@!?(\\d{17,20})>")
        val channelMentionPattern = Regex("<#(\\d{17,20})>")
        val roleMentionPattern = Regex("<@&\\d{17,20}>")
        val emotePattern = Regex("<:.+?:(\\d{17,20})>")
        val userDiscrimPattern = Regex("(.{1,32})#(\\d{4})")
        val urlPattern = Regex("\\s*(https?|attachment)://\\S+\\s*", RegexOption.IGNORE_CASE)
        val nullToNull = null to null

        fun sendGuildCountAll(guildCount: Int, shardCount: Int? = null) {
            catchAll("Exception occured in sendGuildCountAll func", null) {
                Jeanne.config.tokens.botlists.forEach { (k, _) ->
                    when (k) {
                        BotLists.BOTLIST_SPACE.name -> sendGuildCount(BotLists.BOTLIST_SPACE, guildCount)
                        BotLists.BOTSFORDISCORD.name -> sendGuildCount(BotLists.BOTSFORDISCORD, guildCount)
                        BotLists.BOTS_ONDISCORD.name -> sendGuildCount(BotLists.BOTS_ONDISCORD, guildCount)
                        BotLists.DISCORDBOATS.name -> sendGuildCount(BotLists.DISCORDBOATS, guildCount)
                        BotLists.DISCORDBOTS_ORG.name -> sendGuildCount(BotLists.DISCORDBOTS_ORG, guildCount, shardCount)
                        BotLists.DISCORDBOT_WORLD.name -> sendGuildCount(BotLists.DISCORDBOT_WORLD, guildCount, shardCount)
                        BotLists.DISCORD_BOTS_GG.name -> sendGuildCount(BotLists.DISCORD_BOTS_GG, guildCount, shardCount)
                        BotLists.DISCORDBOTS_GROUP.name -> sendGuildCount(BotLists.DISCORDBOTS_GROUP, guildCount)
                    }
                }
            }
        }

        fun sendGuildCount(list: BotLists, guildCount: Int, shardCount: Int? = null) {
            val logger = Logger.getGlobal()
            val token = Jeanne.config.tokens.botlists[list.name] ?: return
            val headers = mutableMapOf("Content-Type" to "application/json", "Accept" to "application/json", "Authorization" to token)
            headers.putAll(Jeanne.defaultHeaders)

            val json = when (list) {
                BotLists.BOTS_ONDISCORD -> "{\"guildCount\": $guildCount}"
                BotLists.DISCORDBOT_WORLD -> "{\"guild_count\": $guildCount, \"shard_count\": $shardCount}"
                BotLists.DISCORD_BOTS_GG -> "{\"guildCount\": $guildCount, \"shardCount\": $shardCount}"
                BotLists.DISCORDBOTS_GROUP -> "{\"shards\": ${Jeanne.shardManager.shards.map { it.guilds.size }}"
                else -> if (shardCount != null) "{\"server_count\": $guildCount, \"shard_count\": $shardCount}" else "{\"server_count\": $guildCount}"
            }

            val mediaType = MediaType.parse("application/json; charset=utf-8")
            val requestBody = RequestBody.create(mediaType, json)

            val request = Request.Builder()
                    .headers(Headers.of(headers))
                    .post(requestBody)
                    .url(list.url)
                    .build()

            Jeanne.httpClient.newCall(request).enqueue(object : Callback {
                override fun onFailure(call: Call, exception: IOException) {
                    catchAll("Exception occured while sending guild count to ${list.name}", null) {
                        throw exception
                    }
                }

                override fun onResponse(call: Call, response: Response) {
                    catchAll("Exception occured while sending guild count to ${list.name}", null) {
                        if (response.isSuccessful) {
                            logger.info("Success sending guild count to ${list.name}")
                            response.close()
                        } else {
                            val code = response.code()
                            val message = response.message()
                            response.close()
                            throw HttpException(code, message)
                        }
                    }
                }
            })
        }

        fun edit(msg: Message, newContent: String) {
            if (!msg.isFromType(ChannelType.TEXT) || msg.textChannel.canTalk())
                msg.editMessage(newContent).queue()
        }

        fun build(o: Any): Message = MessageBuilder().append(o).build()

        private fun stripEveryoneHere(text: String): String = text.replace("@here", "@\u180Ehere")
                .replace("@everyone", "@\u180Eeveryone")

        fun stripEveryoneHere(msg: Message): Message = build(stripEveryoneHere(msg.contentRaw))

        fun stripFormatting(text: String): String = text.replace("@", "\\@")
                .replace("~~", "\\~\\~")
                .replace("*", "\\*")
                .replace("`", "\\`")
                .replace("_", "\\_")

        fun parseTime(milliseconds: Long): String {
            val seconds = milliseconds / 1000 % 60
            val minutes = milliseconds / (1000 * 60) % 60
            val hours = milliseconds / (1000 * 60 * 60) % 24
            val days = milliseconds / (1000 * 60 * 60 * 24)

            return String.format("%02d:%02d:%02d:%02d", days, hours, minutes, seconds)
        }

        // Code from: https://github.com/KawaiiBot/KawaiiBot/blob/master/src/main/kotlin/me/alexflipnote/kawaiibot/utils/Helpers.kt#L48
        fun splitText(content: String, limit: Int): Array<String> {
            val pages = ArrayList<String>()

            val lines = content.trim { it <= ' ' }.split("\n".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
            var chunk = StringBuilder()

            for (line in lines) {
                if (chunk.isNotEmpty() && chunk.length + line.length > limit) {
                    pages.add(chunk.toString())
                    chunk = StringBuilder()
                }

                if (line.length > limit) {
                    val lineChunks = line.length / limit

                    for (i in 0 until lineChunks) {
                        val start = limit * i
                        val end = start + limit
                        pages.add(line.substring(start, end))
                    }
                } else {
                    chunk.append(line).append("\n")
                }
            }

            if (chunk.isNotEmpty())
                pages.add(chunk.toString())

            return pages.toTypedArray()

        }

        // Code from: https://github.com/KawaiiBot/KawaiiBot/blob/master/src/main/kotlin/me/aurieh/ichigo/extensions/RestAction.kt
        suspend fun <T : Any> queueInOrder(actions: Collection<RestAction<T>>): List<T> {
            return actions.map { it.await() }
        }

        suspend fun <T> RestAction<T>.await(): T {
            return submit().await()
        }

        inline fun catchAll(message: String, channel: MessageChannel?, action: () -> Unit) {
            try {
                action()
            } catch (exception: Throwable) {
                if (exception is HttpException) {
                    channel?.sendMessage("```diff\n- ${exception.message ?: "HTTP Exception Unkown"}```")?.queue()
                    return
                }

                val errorMessage = "```diff\n$message\n- ${exception.message ?: exception::class.simpleName ?: "Unkown exception"}```"
                channel?.sendMessage(errorMessage)?.queue()

                if (exception.message?.contains("timeout") == true) {
                    channel?.sendMessage("Something went wrong, please try again later.")?.queue()
                    return
                }

                if (message.contains("eval command")) {
                    return
                }

                val webhookUrl = if (Jeanne.config.env.startsWith("prod")) Jeanne.config.tokens.exception_hook else Jeanne.config.tokens.dev_exception_hook
                val webhook = WebhookClientBuilder(webhookUrl).build()
                val webhookMessage = WebhookMessageBuilder()
                        .setAvatarUrl(Jeanne.shardManager.applicationInfo.jda.selfUser.effectiveAvatarUrl)
                        .setUsername(Jeanne.shardManager.applicationInfo.jda.selfUser.name)
                        .setContent(errorMessage)
                        .build()
                webhook.send(webhookMessage)
                webhook.close()
            }
        }

        fun embedColor(e: GuildMemberJoinEvent): Color = e.guild.selfMember.color ?: Jeanne.embedColor
        fun embedColor(e: GuildMemberLeaveEvent): Color = e.guild.selfMember.color ?: Jeanne.embedColor
        fun embedColor(e: GuildBanEvent): Color = e.guild.selfMember.color ?: Jeanne.embedColor
        fun embedColor(e: GuildUnbanEvent): Color = e.guild.selfMember.color ?: Jeanne.embedColor
        fun embedColor(e: GuildMemberNickChangeEvent): Color = e.guild.selfMember.color ?: Jeanne.embedColor

        suspend fun findUser(str: String, e: MessageReceivedEvent? = null): User? {
            if (str.isEmpty())
                return null

            val jda = e?.jda ?: Jeanne.shardManager.applicationInfo.jda

            val id = userMentionPattern.find(str)?.groups?.get(1)?.value ?: str
            val isValidID = discordIdPattern.matches(id)
            return if (isValidID) {
                // Just to be sure we do e.jda.getUserById() ourselves too
                jda.getUserById(id) ?: jda.retrieveUserById(id).await()
            } else {
                jda.users.find { it.name == id }
            }
        }

        fun convertMember(str: String, e: MessageReceivedEvent): Member? {
            if (str.isEmpty())
                return null

            val id = userMentionPattern.find(str)?.groups?.get(1)?.value ?: str
            val member = try {
                e.guild.getMemberById(id)
            } catch (e: NumberFormatException) {
                null
            }
            if (member != null)
                return member

            val (username, discrim) = convertUsernameDiscrim(str)
            return if (discrim != null)
                e.guild.members.find { it.user.name == username && it.user.discriminator == discrim }
            else
                e.guild.members.find { it.user.name == str }
        }

        fun convertUsernameDiscrim(str: String): Pair<String?, String?> {
            return userDiscrimPattern.find(str).let {
                if (it == null)
                    nullToNull
                else
                    it.groups[1]?.value to it.groups[2]?.value
            }
        }

        fun updateCommandDatabase() {
            println("Updating commands in database... ")
            val milli = measureTimeMillis {
                Registry.commands.forEach {
                    val command = DatabaseManager.commands.findOne(CommandData::name eq it.name)
                    val commandData = it.asData()
                    if (command == null) {
                        DatabaseManager.commands.insertOne(commandData)
                        with(TermColors(TermColors.Level.TRUECOLOR)) {
                            println("Inserted new command ${bold(rgb("#00b5d9")(commandData.name))} into the database")
                        }
                    } else {
                        DatabaseManager.commands.updateOne(CommandData::name eq it.name, commandData)
                    }
                }
            }
            println("Updating commands done! (${milli}ms) ")
        }
    }
}

private fun Member.getHighestRole() = if (roles.size == 0) {
    null
} else roles.reduce { prev, next ->
    if (prev != null) {
        if (next.position > prev.position) next else prev
    } else {
        next
    }
}

fun Member.isKickableBy(kicker: Member): Boolean = isBannableBy(kicker)

fun Member.isBannableBy(banner: Member): Boolean {
    if (this == banner) {
        return false
    }

    val owner = guild.owner
    if (this == owner) {
        return false
    }

    val highestRoleSelf = this.getHighestRole()
    val highestRoleBanner = banner.getHighestRole()

    return if (highestRoleSelf == null || highestRoleBanner == null) {
        highestRoleBanner != null
    } else {
        highestRoleSelf.position < highestRoleBanner.position
    }
}