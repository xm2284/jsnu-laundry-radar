pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
        // 华为 AGC 插件（agcp）仓库
        maven { url = uri("https://developer.huawei.com/repo/") }
        // 友盟 SDK 官方 Maven 仓（兜底，组件化产物在 mavenCentral 也有发布）
        maven { url = uri("https://repo.umeng.com/repository/maven-public/") }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 华为 AGC SDK（agconnect-core / auth / cloud-database）仓库
        maven { url = uri("https://developer.huawei.com/repo/") }
        // 友盟 SDK（U-App 统计 / U-Push 推送）
        maven { url = uri("https://repo.umeng.com/repository/maven-public/") }
    }
}

rootProject.name = "jsnu-laundry-android"
include(":app")
