package com.kokouliss.listanything.core.domain.model

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import kotlin.math.pow
import org.junit.Test

class ChatTest {

    @Test
    fun `one to three emoji and nothing else are drawn large`() {
        listOf("👍", "❤️", "😂😂😂", " 🎉 ", "👨‍👩‍👧", "🇬🇷", "👍🏽").forEach { assertWithMessage(it).that(isOnlyEmoji(it)).isTrue() }
        listOf("", " ", "ok", "ok 👍", "👍 ok", "123", "Καλά", ":)").forEach { assertWithMessage(it).that(isOnlyEmoji(it)).isFalse() }
        assertThat(isOnlyEmoji("😂".repeat(20))).isFalse()
    }

    @Test
    fun `a mute lasts as long as it says, or until it is turned back on`() {
        val now = 1_000_000L
        assertThat(ChatMute.HOUR.until(now)).isEqualTo(now + 3_600_000)
        assertThat(ChatMute.EIGHT_HOURS.until(now)).isEqualTo(now + 8 * 3_600_000)
        assertThat(ChatMute.DAY.until(now)).isEqualTo(now + 24 * 3_600_000)
        assertThat(ChatMute.FOREVER.until(now)).isEqualTo(MUTED_FOREVER)

        val space = Space("s1", "Family", "u1", "ABCD2345", emptyList(), emptyList(), chatMutedUntil = mapOf("u1" to now + 1, "u2" to now - 1))
        assertThat(space.chatMutedUntil("u1", now)).isEqualTo(now + 1)
        // A mute that ran out, and someone who never muted.
        assertThat(space.chatMutedUntil("u2", now)).isNull()
        assertThat(space.chatMutedUntil("u3", now)).isNull()
    }

    @Test
    fun `messages show the names their people use now, and keep the old one for someone who left`() {
        val messages = listOf(ChatMessage("1", "u1", "Old", "Hi", 0), ChatMessage("2", "gone", "Nikos", "Bye", 0))
        assertThat(messages.withNames(mapOf("u1" to "Panos")).map { it.byName }).containsExactly("Panos", "Nikos").inOrder()
    }

    @Test
    fun `text reads on a bubble of any colour a member can choose`() {
        // Every hue and brightness of the sliders, and well beyond: greys, pale and full colours.
        val colors = buildList {
            for (red in 0..255 step 15) for (green in 0..255 step 15) for (blue in 0..255 step 15) add((red shl 16) or (green shl 8) or blue)
        }
        colors.forEach { color ->
            val fill = chatFill(color)
            val text = if (readsDarkOn(fill)) 0x0E1014 else 0xFFFFFF
            assertWithMessage("#%06X on #%06X".format(text, fill and 0xFFFFFF)).that(contrast(fill, text)).isAtLeast(4.5)
        }
        // A colour that reads already is left as it was chosen.
        assertThat(chatFill(0x1F6F82)).isEqualTo(0xFF1F6F82.toInt())
    }

    // The WCAG contrast ratio of two colours.
    private fun contrast(a: Int, b: Int): Double {
        fun luminance(color: Int): Double {
            fun channel(shift: Int): Double {
                val c = ((color shr shift) and 0xFF) / 255.0
                return if (c <= 0.03928) c / 12.92 else ((c + 0.055) / 1.055).pow(2.4)
            }
            return 0.2126 * channel(16) + 0.7152 * channel(8) + 0.0722 * channel(0)
        }
        val (light, dark) = listOf(luminance(a), luminance(b)).sortedDescending()
        return (light + 0.05) / (dark + 0.05)
    }
}
