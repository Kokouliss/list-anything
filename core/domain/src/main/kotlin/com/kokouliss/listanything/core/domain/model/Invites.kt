package com.kokouliss.listanything.core.domain.model

import java.security.SecureRandom

/** Invite codes are typed by hand, so the alphabet leaves out look-alikes (0/O, 1/I). */
object InviteCode {
    const val LENGTH = 8
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
    private val random = SecureRandom()

    fun generate(): String = buildString(LENGTH) {
        repeat(LENGTH) { append(ALPHABET[random.nextInt(ALPHABET.length)]) }
    }

    /** Accepts what people actually paste: lower case, spaces, dashes. */
    fun normalize(input: String): String = input.uppercase().filter { it in ALPHABET }

    fun isValid(code: String): Boolean = code.length == LENGTH && code.all { it in ALPHABET }

    // Keep the host in step with the intent filter in app/src/main/AndroidManifest.xml. The page is
    // served from firebase/hosting.
    private const val LINK_PREFIX = "https://list-anything-12208.web.app/join/"

    /** A web link that opens the app and joins the space. It is also what the QR code holds. */
    fun link(code: String): String = LINK_PREFIX + code

    /** The code inside an invite link or a scanned QR code, or null when [text] is something else. */
    fun fromLink(text: String): String? =
        text.trim().takeIf { it.startsWith(LINK_PREFIX) }?.removePrefix(LINK_PREFIX)?.let(::normalize)?.takeIf(::isValid)
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

/** Returns the address in the form it is stored and matched (trimmed, lower case), or null if it is not an email. */
fun normalizeEmail(input: String): String? = input.trim().lowercase().takeIf { EMAIL.matches(it) }
