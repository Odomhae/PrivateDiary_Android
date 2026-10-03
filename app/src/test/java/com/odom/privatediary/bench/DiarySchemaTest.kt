package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DiarySchemaTest {
    @Test fun emotion_valid_is_kept() {
        assertEquals("불안", DiarySchema.normalizeEmotion(" 불안 "))
    }

    @Test fun emotion_invalid_is_null() {
        assertNull(DiarySchema.normalizeEmotion("짜증"))
        assertNull(DiarySchema.normalizeEmotion(null))
    }

    @Test fun causes_filtered_deduped_and_capped_at_3() {
        val input = listOf("일", "엉뚱", "관계", "일", "수면", "돈")
        assertEquals(listOf("일", "관계", "수면"), DiarySchema.normalizeCauses(input))
    }

    @Test fun intensity_is_clamped_to_1_5() {
        assertEquals(1, DiarySchema.clampIntensity(0))
        assertEquals(5, DiarySchema.clampIntensity(9))
        assertEquals(3, DiarySchema.clampIntensity(3))
    }
}
