package com.kokouliss.listanything

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import java.io.File
import org.junit.Test

class PushTextTest {
    @Test
    fun `every string the server names is known to the app`() {
        // Unit tests run in the app folder; the server code sits next to it.
        val server = File("../firebase/functions").listFiles { file -> file.extension == "js" && !file.name.endsWith(".test.js") }!!
        val keys = server.flatMap { Regex("'(push_[a-z_]+)'").findAll(it.readText()).map { match -> match.groupValues[1] }.toList() }.toSet()

        assertThat(keys).isNotEmpty()
        keys.forEach { assertWithMessage(it).that(pushText(it)).isNotNull() }
    }

    @Test
    fun `an unknown string falls back to the text the server sent`() {
        assertThat(pushText("push_something_new")).isNull()
        assertThat(pushText(null)).isNull()
    }
}
