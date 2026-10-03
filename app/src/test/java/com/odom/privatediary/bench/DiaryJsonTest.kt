package com.odom.privatediary.bench

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryJsonTest {
    @Test fun extracts_first_balanced_object_from_prose() {
        val raw = "설명입니다 ```json\n{\"a\":{\"b\":1}}\n``` 끝"
        assertEquals("{\"a\":{\"b\":1}}", DiaryJson.extractJsonObject(raw))
    }

    @Test fun returns_null_when_no_object() {
        assertNull(DiaryJson.extractJsonObject("no json here"))
    }

    @Test fun parses_valid_output_and_normalizes() {
        val raw = """여기 결과: {"summary":"면접 대기로 불안","emotion":"불안","intensity":9,
            "causes":["일","엉뚱","관계","수면"],"keywords":["면접","결과","대기","초과"],
            "reply":"마음 졸이셨겠어요.","crisis":false}"""
        val r = DiaryJson.parse(raw)
        assertTrue(r.parseOk)
        assertEquals("면접 대기로 불안", r.summary)
        assertEquals("불안", r.emotion)
        assertEquals(5, r.intensity)
        assertEquals(listOf("일", "관계", "수면"), r.causes)
        assertEquals(3, r.keywords.size)
        assertFalse(r.crisis)
    }

    @Test fun invalid_emotion_becomes_null_but_parse_ok() {
        val raw = """{"summary":"x","emotion":"짜증","intensity":2,"causes":[],"keywords":[],"reply":"","crisis":false}"""
        val r = DiaryJson.parse(raw)
        assertTrue(r.parseOk)
        assertNull(r.emotion)
    }

    @Test fun no_json_returns_parse_failed() {
        val r = DiaryJson.parse("모델이 그냥 말로 답했어요")
        assertFalse(r.parseOk)
        assertEquals("모델이 그냥 말로 답했어요", r.raw)
    }
}
