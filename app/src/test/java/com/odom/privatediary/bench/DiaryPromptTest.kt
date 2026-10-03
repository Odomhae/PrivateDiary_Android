package com.odom.privatediary.bench

import org.junit.Assert.assertTrue
import org.junit.Test

class DiaryPromptTest {
    @Test fun system_instruction_lists_all_labels() {
        val s = DiaryPrompt.SYSTEM_INSTRUCTION
        DiarySchema.EMOTIONS.forEach { assertTrue("emotion $it 누락", s.contains(it)) }
        DiarySchema.CAUSES.forEach { assertTrue("cause $it 누락", s.contains(it)) }
    }

    @Test fun user_prompt_contains_transcript_and_fewshot() {
        val p = DiaryPrompt.buildUserPrompt("오늘은 좀 피곤했어")
        assertTrue(p.contains("오늘은 좀 피곤했어"))
        assertTrue(p.contains("전사문:"))
        assertTrue(p.contains("출력:"))
    }
}
