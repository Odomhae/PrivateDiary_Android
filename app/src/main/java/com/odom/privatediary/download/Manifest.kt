package com.odom.privatediary.download

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class ModelFile(val name: String, val path: String, val bytes: Long, val sha256: String)

@Serializable
data class ModelManifest(val version: Int, val models: List<ModelFile>)

object ManifestParser {
    private val json = Json { ignoreUnknownKeys = true }
    fun parse(text: String): ModelManifest = json.decodeFromString(ModelManifest.serializer(), text)
}
