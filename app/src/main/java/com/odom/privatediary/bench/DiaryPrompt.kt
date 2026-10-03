package com.odom.privatediary.bench

object DiaryPrompt {
    val SYSTEM_INSTRUCTION = """
        당신은 한국어 음성 일기를 분석하는 보조자입니다.
        전사문을 읽고 아래 JSON 객체 하나만 출력하세요. 설명이나 코드펜스는 쓰지 않습니다.
        emotion은 다음 7개 중 하나만: ${DiarySchema.EMOTIONS.joinToString(" | ")}.
        causes는 다음 중 0~3개만: ${DiarySchema.CAUSES.joinToString(", ")}.
        intensity는 1~5 정수. keywords는 자유 태그 최대 3개.
        reply는 조언 없이 들은 내용을 되짚는 공감 1~2문장.
        스키마: {"summary","emotion","intensity","causes","keywords","reply","crisis"}
    """.trimIndent()

    private val FEW_SHOT = listOf(
        "오늘 드디어 이사했어. 짐 정리하느라 몸은 힘든데 새 집이라 설레네." to
            """{"summary":"이사 마치고 설렘","emotion":"설렘","intensity":4,"causes":["관계"],"keywords":["이사"],"reply":"새 집으로 옮기느라 고생 많으셨겠어요. 설레는 마음이 느껴져요.","crisis":false}""",
        "회사에서 상사한테 깨졌어. 잠도 못 잤고 머리가 아파." to
            """{"summary":"직장 스트레스로 지침","emotion":"피곤","intensity":3,"causes":["일","수면"],"keywords":["상사"],"reply":"혼나고 잠까지 설치셨다니 많이 지치셨겠어요.","crisis":false}""",
        "면접 결과를 기다리는데 너무 불안해." to
            """{"summary":"면접 결과 대기로 불안","emotion":"불안","intensity":4,"causes":["일"],"keywords":["면접"],"reply":"결과를 기다리며 마음 졸이고 계시는군요.","crisis":false}""",
    )

    fun buildUserPrompt(transcript: String): String = buildString {
        appendLine("다음은 예시입니다.")
        FEW_SHOT.forEach { (input, output) ->
            appendLine("전사문: $input")
            appendLine("출력: $output")
        }
        appendLine()
        appendLine("이제 아래 전사문을 같은 형식의 JSON으로 분석하세요.")
        appendLine("전사문: $transcript")
        append("출력: ")
    }
}
