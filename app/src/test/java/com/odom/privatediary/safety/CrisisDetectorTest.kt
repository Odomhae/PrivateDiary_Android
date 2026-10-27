package com.odom.privatediary.safety

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrisisDetectorTest {
    @Test fun detects_phrases_regardless_of_spacing_and_punctuation() {
        assertTrue(CrisisDetector.detect("요즘 너무 힘들어서 죽고 싶다"))
        assertTrue(CrisisDetector.detect("죽고싶어…"))
        assertTrue(CrisisDetector.detect("그냥 다 사라지고 싶어."))
        assertTrue(CrisisDetector.detect("목숨을 끊을까 생각했어"))
        assertTrue(CrisisDetector.detect("살기 싫다"))
    }

    @Test fun detects_words_inside_a_token() {
        assertTrue(CrisisDetector.detect("자살하고 싶다는 생각"))
        assertTrue(CrisisDetector.detect("또 자해를 했어"))
        assertTrue(CrisisDetector.detect("유서를 써 봤어"))
    }

    @Test fun every_phrase_and_word_is_detected() {
        (CrisisDetector.PHRASES + CrisisDetector.WORDS).forEach {
            assertTrue(it, CrisisDetector.detect("오늘 $it 어"))
        }
    }

    @Test fun ignores_everyday_sentences() {
        assertFalse(CrisisDetector.detect("오늘 일 빨리 끝내고 싶다"))
        assertFalse(CrisisDetector.detect("배고파 죽겠다"))
        assertFalse(CrisisDetector.detect("여자 살 빠졌어"))
        assertFalse(CrisisDetector.detect("친구랑 카페 갔어. 즐거웠어"))
        assertFalse(CrisisDetector.detect(""))
    }
}
