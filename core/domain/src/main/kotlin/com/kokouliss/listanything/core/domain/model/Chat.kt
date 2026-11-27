package com.kokouliss.listanything.core.domain.model

import kotlin.math.pow

/** What a chat message carries besides text. [key] is the stored name. */
enum class MessageKind(val key: String) {
    PHOTO("photo"),
    VOICE("voice"),
}

/**
 * One message in the chat of a space. [thumbnail] is the small picture of a photo message, whose
 * full size is stored next to it; [seconds] is the length of a voice message.
 */
data class ChatMessage(
    val id: String,
    val byId: String,
    val byName: String,
    val text: String,
    val atMillis: Long,
    val kind: MessageKind? = null,
    val thumbnail: ImageData? = null,
    val seconds: Int? = null,
    /** Written on this phone and not yet on the server. */
    val isPending: Boolean = false,
)

/** People under their current names ([names] by id); someone who left keeps the stored name. */
fun List<ChatMessage>.withNames(names: Map<String, String>): List<ChatMessage> =
    map { message -> names[message.byId]?.let { message.copy(byName = it) } ?: message }

/**
 * How the chat of a space looks to everyone in it. [color] is ARGB, or null for the app's own ink
 * blue; [emoji] is what the quick button sends with one tap.
 */
data class ChatLook(val color: Int? = null, val emoji: String = DEFAULT_CHAT_EMOJI)

const val DEFAULT_CHAT_EMOJI = "👍"

/** Mirrors the limits in firebase/firestore.rules. */
const val MAX_MESSAGE_LENGTH = 2000
const val MAX_CHAT_EMOJI_LENGTH = 16

/** How long the chat of a space stays quiet for one member. [minutes] null: until they turn it back on. */
enum class ChatMute(val minutes: Long?) {
    HOUR(60),
    EIGHT_HOURS(8 * 60),
    DAY(24 * 60),
    FOREVER(null),
    ;

    fun until(nowMillis: Long): Long = minutes?.let { nowMillis + it * 60_000 } ?: MUTED_FOREVER
}

/** The last day of the year 9999: a whole number firebase/functions can compare, unlike Long.MAX_VALUE. */
const val MUTED_FOREVER = 253_402_300_799_000L

/** Until when [userId] muted the chat, or null when they hear it now. */
fun Space.chatMutedUntil(userId: String, nowMillis: Long): Long? = chatMutedUntil[userId]?.takeIf { it > nowMillis }

/**
 * True for a message of one to three emoji and nothing else, which the chat draws large and
 * without a bubble.
 */
// shortcut: counts symbols, not what a font draws: a letter-like symbol such as ™ passes too. Harmless.
fun isOnlyEmoji(text: String): Boolean {
    val trimmed = text.trim()
    if (trimmed.isEmpty() || trimmed.length > 24) return false
    var pictures = 0
    var i = 0
    while (i < trimmed.length) {
        val code = trimmed.codePointAt(i)
        i += Character.charCount(code)
        when {
            // Joiners, variation selectors, skin tones and the keycap mark belong to the emoji before them.
            code == 0x200D || code == 0xFE0F || code == 0x20E3 || code in 0x1F3FB..0x1F3FF -> Unit
            Character.isWhitespace(code) -> Unit
            Character.isLetterOrDigit(code) -> return false
            code >= 0x2190 -> pictures++
            else -> return false
        }
    }
    // A family or a flag is several symbols for one picture, so the bound is generous.
    return pictures in 1..9
}

private fun luminance(color: Int): Double {
    fun channel(shift: Int): Double {
        val c = ((color shr shift) and 0xFF) / 255.0
        return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
}

// WCAG asks for 4.5 to 1. White text has it on a fill up to the first luminance, the app's dark ink
// (#0E1014) from the second; on the few mid tones between the two neither reads.
private const val WHITE_READS_UP_TO = 0.183
private const val INK_READS_FROM = 0.199

/**
 * The fill a chat bubble gets for the ARGB [color] a member chose: the colour itself, or a touch
 * darker for the few mid tones on which neither white nor dark text would read. With
 * [readsDarkOn] this keeps any colour, also a custom one, readable.
 */
fun chatFill(color: Int): Int {
    var fill = color or (0xFF shl 24)
    while (luminance(fill) > WHITE_READS_UP_TO && luminance(fill) < INK_READS_FROM) {
        fill = (0xFF shl 24) or
            ((((fill shr 16) and 0xFF) * 15 / 16) shl 16) or ((((fill shr 8) and 0xFF) * 15 / 16) shl 8) or ((fill and 0xFF) * 15 / 16)
    }
    return fill
}

/** True when dark text, not white, reads on a bubble filled with [fill], which comes from [chatFill]. */
fun readsDarkOn(fill: Int): Boolean = luminance(fill) >= INK_READS_FROM

/**
 * The chat this phone has on screen right now, by space id, or null: a notification about it would
 * only repeat what the person is reading.
 */
object OpenChat {
    @Volatile
    var spaceId: String? = null
}
