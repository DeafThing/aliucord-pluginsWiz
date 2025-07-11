package net.navibot.aliucord.plugins

import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.view.View
import com.aliucord.Utils
import com.aliucord.annotations.AliucordPlugin
import com.aliucord.api.SettingsAPI
import com.aliucord.entities.Plugin
import com.aliucord.patcher.after
import com.aliucord.patcher.before
import com.aliucord.views.TextInput
import com.aliucord.widgets.BottomSheet
import com.discord.utilities.view.text.SimpleDraweeSpanTextView
import com.discord.utilities.view.text.TextWatcher
import com.discord.widgets.chat.MessageContent
import com.discord.widgets.chat.MessageManager
import com.discord.widgets.chat.input.ChatInputViewModel
import com.discord.widgets.chat.list.adapter.WidgetChatListAdapterItemMessage
import com.discord.widgets.chat.list.entries.MessageEntry
import net.navibot.aliucord.plugins.error.ParseException

@AliucordPlugin(requiresRestart = true)
class LetThereBeColors : Plugin() {
    init {
        settingsTab = SettingsTab(
            TextColorSettings::class.java,
            SettingsTab.Type.BOTTOM_SHEET
        ).withArgs(settings)
    }

    override fun start(context: Context) {
        try {
            // Patch message display to show custom colors
            patcher.before<WidgetChatListAdapterItemMessage>(
                "processMessageText", 
                SimpleDraweeSpanTextView::class.java, 
                MessageEntry::class.java
            ) {
                val textView = it.args[0] as SimpleDraweeSpanTextView
                val entry = it.args[1] as MessageEntry
                
                // Skip if message is loading or is just emotes
                if (entry.message.isLoading || ColorUtils.isDiscordEmote(entry.message.content)) {
                    return@before
                }

                try {
                    val decodedColor = ColorUtils.decode(entry.message.content)
                    val color = Color.parseColor("#$decodedColor")
                    textView.setTextColor(color)
                } catch (e: ParseException) {
                    // No color encoded, use default
                    textView.setTextColor(Color.parseColor("#DCDDDE")) // Discord's default text color
                } catch (e: Exception) {
                    logger.error("Error parsing color", e)
                }
            }

            // Patch message sending to add color encoding
            patcher.before<ChatInputViewModel>(
                "sendMessage",
                Context::class.java,
                MessageManager::class.java,
                MessageContent::class.java,
                List::class.java,
                Boolean::class.javaPrimitiveType!!,
                Function1::class.java
            ) {
                try {
                    val color = settings.getString("globalTextColor", null)
                    
                    if (!color.isNullOrEmpty() && ColorUtils.isValidHex(color)) {
                        val content = it.args[2] as MessageContent
                        val text = content.textContent
                        
                        // Don't modify emotes, commands, or empty messages
                        if (text.isNotBlank() && !ColorUtils.isDiscordEmote(text) && !ColorUtils.isCommand(text)) {
                            val strippedText = ColorUtils.strip(text)
                            val encodedColor = ColorUtils.encode(color)
                            content.set("$strippedText $encodedColor")
                        }
                    }
                } catch (e: Exception) {
                    logger.error("Error encoding color in message", e)
                }
            }
        } catch (e: Exception) {
            logger.error("Error starting LetThereBeColors plugin", e)
        }
    }

    override fun stop(context: Context) {
        patcher.unpatchAll()
    }

    class TextColorSettings(private val settings: SettingsAPI) : BottomSheet() {
        override fun onViewCreated(view: View, bundle: Bundle?) {
            super.onViewCreated(view, bundle)
            val ctx = view.context

            TextInput(ctx, "Global text color (HEX format, e.g., #FF0000 or #F00)").run {
                editText.run {
                    maxLines = 1
                    hint = "#FF0000"
                    setText(settings.getString("globalTextColor", ""))

                    addTextChangedListener(object : TextWatcher() {
                        override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: Editable) {
                            val input = s.toString().trim()
                            
                            if (input.isEmpty()) {
                                settings.setString("globalTextColor", "")
                                return
                            }
                            
                            if (ColorUtils.isValidHex(input)) {
                                settings.setString("globalTextColor", input)
                                Utils.showToast("Color saved! Restart required.")
                            } else {
                                Utils.showToast("Invalid HEX format! Use #FF0000 or #F00")
                            }
                        }
                    })
                }
                linearLayout.addView(this)
            }
        }
    }
}