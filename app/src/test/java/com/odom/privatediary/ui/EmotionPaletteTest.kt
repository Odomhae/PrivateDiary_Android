package com.odom.privatediary.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class EmotionPaletteTest {
    @Test fun all_seven_emotions_mapped_distinctly() {
        val labels = listOf("기쁨", "평온", "설렘", "슬픔", "불안", "분노", "피곤")
        val colors = labels.map { EmotionPalette.argb(it) }
        assertEquals(7, colors.toSet().size)
        colors.forEach { assertNotEquals(EmotionPalette.NEUTRAL, it) }
    }

    @Test fun null_and_unknown_map_to_neutral() {
        assertEquals(EmotionPalette.NEUTRAL, EmotionPalette.argb(null))
        assertEquals(EmotionPalette.NEUTRAL, EmotionPalette.argb("짜증"))
    }
}
