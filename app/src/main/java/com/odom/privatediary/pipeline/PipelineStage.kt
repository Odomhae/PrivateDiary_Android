package com.odom.privatediary.pipeline

/** Waiting = 주간 회고 등 다른 모델 작업이 끝나길 기다리는 중(ModelGate). */
enum class PipelineStage { Idle, Recording, Waiting, Trimming, Transcribing, Analyzing, Done, Failed }
