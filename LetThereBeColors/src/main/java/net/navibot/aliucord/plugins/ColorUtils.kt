package net.navibot.aliucord.plugins

import com.discord.models.message.Message
import com.discord.widgets.chat.MessageContent
import com.discord.widgets.chat.list.entries.MessageEntry
import net.navibot.aliucord.plugins.error.ParseException
import java.lang.reflect.Field
import java.lang.reflect.Modifier

fun MessageContent.set(text: String) {
    try {
        val field = MessageContent::class.java.getDeclaredField("textContent").apply {
            isAccessible = true
        }
        field.set(this, text)
    } catch (e: Exception) {
        // Fallback: try alternative field names
        try {
            val field = MessageContent::class.java.getDeclaredField("content").apply {
                isAccessible = true
            }
            field.set(this, text)
        } catch (e2: Exception) {
            throw RuntimeException("Could not set message content", e2)
        }
    }
}

class ColorUtils {
    companion object {
        private val map = (('A'..'F').mapIndexed { i, c -> Pair(c, "\u200B".repeat(i + 1)) }.toMap() +
                ('0'..'9').mapIndexed { i, c -> Pair(c, "\u200E".repeat(i + 1)) }.toMap())

        fun strip(text: String): String {
            return text.replace(Regex("\u200D[\u200C\u200B\u200E]+\u200D"), "")
        }

        fun encode(hex: String): String {
            val cleanHex = hex.replace("#", "").uppercase()
            
            // Validate hex format
            if (!cleanHex.matches(Regex("^[0-9A-F]{3}$|^[0-9A-F]{6}$"))) {
                throw ParseException("Invalid HEX format! Use 3 or 6 digit hex (e.g., #FF0000 or #F00)")
            }
            
            // Convert 3-digit to 6-digit hex
            val fullHex = if (cleanHex.length == 3) {
                cleanHex.map { "$it$it" }.joinToString("")
            } else {
                cleanHex
            }
            
            val builder = StringBuilder()
            fullHex.forEach { c ->
                builder.append(map[c] ?: throw ParseException("Invalid HEX character: $c"))
                    .append("\u200C")
            }

            return "\u200D$builder\u200D"
        }

        fun isDiscordEmote(data: String): Boolean {
            return data.matches(Regex("^((<a?:[a-zA-Z0-9_]+:[0-9]+>|:[a-zA-Z0-9_]+:)\\s*)+$"))
        }

        fun decode(data: String): String {
            val pattern = Regex("\u200D([\u200C\u200B\u200E]+)\u200D")
            val match = pattern.find(data) ?: throw ParseException("No valid encoded HEX found!")
            
            val chunk = match.groupValues[1]
            return try {
                val hexChars = chunk.split("\u200C").filter { it.isNotEmpty() }.map { encodedChar ->
                    map.entries.firstOrNull { it.value == encodedChar }?.key
                        ?: throw ParseException("Invalid encoded character found")
                }
                String(hexChars.toCharArray())
            } catch (e: Exception) {
                throw ParseException("Failed to decode HEX: ${e.message}")
            }
        }

        fun isCommand(textContent: String): Boolean {
            return textContent.trimStart().startsWith("/")
        }
        
        fun isValidHex(hex: String): Boolean {
            val cleanHex = hex.replace("#", "")
            return cleanHex.matches(Regex("^[0-9A-Fa-f]{3}$|^[0-9A-Fa-f]{6}$"))
        }
    }
}