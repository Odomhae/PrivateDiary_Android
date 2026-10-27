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

    @Test fun detects_common_variants() {
        assertTrue(CrisisDetector.detect("그냥 죽고만 싶다"))
        assertTrue(CrisisDetector.detect("더는 살고 싶지 않아"))
        assertTrue(CrisisDetector.detect("확 죽어버릴까"))
        assertTrue(CrisisDetector.detect("번개탄을 샀어"))
        assertTrue(CrisisDetector.detect("목을 매달까 생각했어"))
    }

    @Test fun nfd_input_is_normalized() {
        val nfd = java.text.Normalizer.normalize("죽고 싶다", java.text.Normalizer.Form.NFD)
        assertTrue(CrisisDetector.detect(nfd))
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
        assertFalse(CrisisDetector.detect("목을 매일 스트레칭했다"))
        assertFalse(CrisisDetector.detect("유서 깊은 절에 다녀왔다"))
        assertFalse(CrisisDetector.detect("친구랑 카페 갔어. 즐거웠어"))
        assertFalse(CrisisDetector.detect(""))
    }
}
