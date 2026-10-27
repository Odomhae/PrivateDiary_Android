plugins {
    alias(libs.plugins.android.ai.pack)
}

aiPack {
    packName = "whisper_pack"
    dynamicDelivery {
        deliveryType = "fast-follow"
    }
}
