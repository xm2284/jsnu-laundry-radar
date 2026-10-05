plugins {
    id("com.android.application") version "8.7.3" apply false
    id("org.jetbrains.kotlin.android") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.serialization") version "2.0.21" apply false
    id("org.jetbrains.kotlin.plugin.parcelize") version "2.0.21" apply false
    // 华为 AppGallery Connect 插件（读取 agconnect-services.json、编译期校验 CloudDB 实体）
    id("com.huawei.agconnect.agcp") version "1.9.6.300" apply false
}
