plugins {
    alias(libs.plugins.android.ai.pack)
}

aiPack {
    packName = "gemma_pack"
    dynamicDelivery {
        deliveryType = "fast-follow"
    }
}
