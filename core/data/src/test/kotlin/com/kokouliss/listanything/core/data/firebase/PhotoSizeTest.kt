package com.kokouliss.listanything.core.data.firebase

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PhotoSizeTest {

    @Test
    fun `a large photo is scaled so its longer side fits`() {
        assertThat(fitWithin(4000, 3000, 1280)).isEqualTo(1280 to 960)
        assertThat(fitWithin(3000, 4000, 1280)).isEqualTo(960 to 1280)
    }

    @Test
    fun `a small photo is never scaled up`() {
        assertThat(fitWithin(640, 480, 1280)).isEqualTo(640 to 480)
    }

    @Test
    fun `a very thin photo keeps at least one pixel`() {
        assertThat(fitWithin(10_000, 2, 160)).isEqualTo(160 to 1)
    }
}
