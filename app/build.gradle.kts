plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.huawei.agconnect")
}

android {
    namespace = "com.jsnu.laundry"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.jsnu.laundry"
        minSdk = 26
        targetSdk = 35
        versionCode = 13
        versionName = "1.2.1"

        // ===== 友盟客户端配置（可从 local.properties 或环境变量安全注入）=====
        val localProps = java.util.Properties().apply {
            val propFile = rootProject.file("local.properties")
            if (propFile.exists()) {
                load(propFile.inputStream())
            }
        }
        val umengAppKey = (localProps.getProperty("UMENG_APPKEY")
            ?: System.getenv("UMENG_APPKEY")
            ?: "").trim()
        val umengMessageSecret = (localProps.getProperty("UMENG_MESSAGE_SECRET")
            ?: System.getenv("UMENG_MESSAGE_SECRET")
            ?: "").trim()
        val umengChannel = (localProps.getProperty("UMENG_CHANNEL")
            ?: System.getenv("UMENG_CHANNEL")
            ?: "official").trim()

        // 当 AppKey 为空时，工程内置的 UmengAnalyticsManager 会安全跳过初始化，不影响正常核心功能运行
        buildConfigField("String", "UMENG_APPKEY", "\"$umengAppKey\"")
        buildConfigField("String", "UMENG_MESSAGE_SECRET", "\"$umengMessageSecret\"")
        buildConfigField("String", "UMENG_CHANNEL", "\"$umengChannel\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("roborazzi.test.record", "true") }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")

    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")

    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-kotlinx-serialization:2.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    implementation("androidx.work:work-runtime-ktx:2.10.0")

    // 华为 AGC：核心 + 匿名认证 + 云数据库；版本与 agcp 插件统一 1.9.6.300
    implementation("com.huawei.agconnect:agconnect-core:1.9.6.300")
    implementation("com.huawei.agconnect:agconnect-auth:1.9.6.300")
    implementation("com.huawei.agconnect:agconnect-cloud-database:1.9.6.300")

    // 友盟 U-App 移动统计 + U-Push 基础推送（组件化，mavenCentral/友盟仓；未接 OPPO 等厂商通道）
    // 版本取自 Maven Central 当前正式 release（common/asms/push 的 POM 互不锁版本，可同取最新稳定版）；
    // AAR 为 Java7 字节码，AGP8/JDK17/minSdk26 兼容；所用 API 经 javap 核对在新版保持不变。
    implementation("com.umeng.umsdk:common:9.9.9")
    implementation("com.umeng.umsdk:asms:1.8.7.2")
    implementation("com.umeng.umsdk:push:6.7.6")


    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    testImplementation("org.robolectric:robolectric:4.14.1")
    testImplementation("androidx.test:core:1.6.1")
    testImplementation("io.github.takahirom.roborazzi:roborazzi:1.52.0")
    testImplementation("io.github.takahirom.roborazzi:roborazzi-compose:1.52.0")
    testImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
