# 🧺 JSNU 洗衣雷达 · 海乐生活版 (Haile Life Laundry Radar)

<p align="center">
  <img src="https://img.shields.io/badge/Platform-Android-3DDC84?style=flat-square&logo=android&logoColor=white" alt="Android" />
  <img src="https://img.shields.io/badge/Language-Kotlin-7F52FF?style=flat-square&logo=kotlin&logoColor=white" alt="Kotlin" />
  <img src="https://img.shields.io/badge/UI-Jetpack%20Compose%20Material%203-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white" alt="Compose" />
  <img src="https://img.shields.io/badge/Cloud-Huawei%20AGC-C7000B?style=flat-square&logo=huawei&logoColor=white" alt="AGC" />
  <img src="https://img.shields.io/badge/minSdk-26%20(Android%208.0)-brightgreen?style=flat-square" alt="MinSdk" />
  <img src="https://img.shields.io/badge/targetSdk-35%20(Android%2015)-blue?style=flat-square" alt="TargetSdk" />
  <img src="https://img.shields.io/badge/License-MIT-orange?style=flat-square" alt="License" />
</p>

<p align="center">
  <b>基于「海乐生活」(Haile Life) 校园洗衣平台的洗衣机「空闲雷达」+ 蹲守通知 + 洗衣完成追踪 Android 客户端</b><br>
  实时感知洗衣机/洗鞋机/烘干机状态，自动蹲守空闲机器，洗完自动提醒取衣。
</p>

<p align="center">
  <a href="README.md">简体中文</a> • <a href="README_EN.md">English Documentation</a>
</p>

---

## 💡 项目背景

本项目针对的是 **「海乐生活」校园洗衣平台**（海尔旗下校园共享洗衣服务，`yshz-user.haier-ioc.com`）—— 该平台在江苏师范大学等高校宿舍洗衣房广泛部署，用户通过其小程序/App 扫码使用洗衣机、洗鞋机、烘干机。

高校宿舍区的公共洗衣房普遍存在几类高频痛点：

| 痛点 | 场景描述 | 本项目的解法 |
| --- | --- | --- |
| 🏃 **盲跑与排队** | 端着一盆衣服爬上楼，才发现机器全在运转 / 排长队 | 寝室内实时查看空闲机器，按「最快可用」智能排序 |
| ⏰ **蹲守靠运气** | 想等某台机器空出来，只能反复刷新或守着走廊 | 开启**「我要洗衣」蹲守**，机器一变空闲立刻推送通知 |
| 🙈 **洗完忘记取** | 洗完没有提醒，衣服被遗忘在筒里影响他人 | **「洗衣中」追踪**，洗完高优先级提醒「记得拿/晾晒」 |
| 🔧 **故障机器无提示** | 走到机器前才发现故障 / 不可用 | 故障、不可约等状态一目了然，颜色编码区分 |
| 📱 **不想开 App** | 每次都要打开 App 才能看状态 | 提供**桌面小组件**，锁屏/桌面一眼看空闲数量与倒计时 |

---

## ✨ 核心功能

### 1. 实时设备状态雷达
- 直连「海乐生活」校园洗衣平台**公开只读状态接口**，拉取点位内全部设备；
- 自动派生业务状态：`空闲` / `可预约` / `已被约` / `运行不可约` / `故障`；
- 支持洗衣机、洗鞋机、烘干机分类识别（接口缺 `categoryCode` 时按设备名兜底识别）；
- 按「最快可用」排序：空闲排最前，其余按预计洗完剩余分钟数升序。

### 2. 「我要洗衣」蹲守 (Watch Service)
- 前台常驻服务（`dataSync` 类型），按可配置间隔轮询；
- **边沿触发**：仅当机器由「非空闲/非可约」变为「空闲/可约」时才通知，首轮只建基线不轰炸；
- 命中后自动进入 **15 秒高频刷新 1 分钟**模式，抓住转瞬即逝的空位；
- 单台机器 **30 分钟通知冷却**，避免反复打扰；
- 支持 **TTL 自动停止**，到点自动结束蹲守，省电省心。

### 3. 「洗衣中」追踪与取衣提醒
- 把自己正在使用的机器标记为「洗衣中」；
- 每分钟静默刷新一条「洗衣中 · 预计 HH:mm · 还剩 N 分钟」的进行中通知；
- 机器由「运行」变「空闲」的瞬间，发出**高优先级取衣提醒**并自动结束追踪；
- 追踪期间豁免蹲守 TTL 自动停止，直到洗完。

### 4. 桌面小组件
- **洗衣雷达卡片**：一眼查看当前点位空闲/可约数量与最快可用时间；
- **洗衣中追踪卡片**：实时倒计时，一键「定闹钟 / 去海乐」。

### 5. 体验与个性化
- Jetpack Compose + Material 3，支持**跟随系统 / 浅色 / 深色**主题；
- 收藏常用机器、多监控点位切换、常洗时间段设置；
- 首次启动新手引导 (Onboarding)；
- 使用统计（UsageStats）。

### 6. 云端匿名身份 (可选)
- 基于**华为 AGC** 匿名认证 + Cloud DB，为每台设备建立匿名用户档案
  （`uid` / `installId` / `createdAt` / `lastActiveAt` / `appVersion`）；
- **完全可选且自降级**：任何一步失败只记日志，绝不影响核心洗衣功能；
- 不采集任何学生真实身份信息，仅匿名安装标识。

---

## 🏗️ 技术架构

```text
┌──────────────────────────────────────────────────────────────┐
│                     Android App (Jetpack Compose)             │
│                                                                │
│   UI 层        MainScreen / DeviceCard / Widgets / Settings    │
│      │                                                         │
│   VM 层        LaundryViewModel  ──────────────┐              │
│      │                                          │              │
│   Data 层      LaundryRepository                │              │
│      │         (拉取 → 派生状态 → 排序 → 缓存    │              │
│      │          → 边沿触发评估)                  │              │
│      │                                          │              │
│   Service 层   WatchService (前台蹲守/追踪)      │  AgcUser     │
│                PollWorker / WidgetUpdater        │  Repository  │
│      │                                          │              │
│   Network      ApiFactory (Retrofit/OkHttp)     │              │
└──────┼───────────────────────────────────────────┼─────────────┘
       │ HTTPS (只读, 免鉴权)                        │ HTTPS
       ▼                                            ▼
┌──────────────────────┐              ┌──────────────────────────┐
│ 海乐生活开放状态接口   │              │ 华为 AGC (匿名认证+CloudDB)│
│ yshz-user.haier-ioc   │              │ 匿名用户档案 (可选自降级)  │
└──────────────────────┘              └──────────────────────────┘
```

### 技术栈

| 分类 | 选型 |
| --- | --- |
| 语言 | Kotlin 1.9+ / Java 17 |
| UI | Jetpack Compose + Material 3 + Material Icons Extended |
| 架构 | 单 Activity + ViewModel + Repository + 前台 Service |
| 网络 | Retrofit 2.11 + OkHttp 4.12 + kotlinx.serialization |
| 异步 | Kotlin Coroutines / Flow |
| 本地存储 | Jetpack DataStore (Preferences) |
| 后台任务 | WorkManager + Foreground Service |
| 云服务 | 华为 AGC (core / auth / cloud-database 1.9.6.300) |
| 运营统计 | 友盟 U-App / U-Push（可选，未配置 key 自动跳过） |
| 测试 | JUnit + Robolectric + Roborazzi (Compose 截图测试) + MockWebServer |

---

## 🚀 快速开始

### 环境要求
- Android Studio Ladybug / Jellyfish 或更高版本
- JDK 17
- Android SDK 35 (compileSdk 35, minSdk 26)

### 1. 克隆仓库

```bash
git clone https://github.com/xm2284/jsnu-laundry-radar.git
cd jsnu-laundry-radar
```

### 2. 配置华为 AGC（可选）

> 出于安全考虑，真实的 `agconnect-services.json` **不会**进入仓库。

如果你的华为项目中已开通 AGC 认证与云数据库：
1. 登录 [华为 AppGallery Connect 控制台](https://developer.huawei.com/consumer/cn/service/josp/agc/index.html)；
2. 下载你专属的 `agconnect-services.json`；
3. 复制模板 `app/agconnect-services.json.example` 为 `app/agconnect-services.json`，并填入真实字段。

> 若跳过此步，App 仍可正常使用核心洗衣功能，AGC 相关能力会自动降级。

### 3. 配置友盟（可选）

复制 `local.properties.example` 为 `local.properties`（该文件已被 `.gitignore` 忽略），填入：

```properties
sdk.dir=你的 Android SDK 路径
UMENG_APPKEY=
UMENG_MESSAGE_SECRET=
UMENG_CHANNEL=official
```

也可改用环境变量 `UMENG_APPKEY` / `UMENG_MESSAGE_SECRET` / `UMENG_CHANNEL`。留空时友盟 SDK 会安全跳过、不初始化。

### 4. 编译运行

```bash
# 命令行编译 Debug 包
./gradlew assembleDebug

# 运行单元测试
./gradlew testDebugUnitTest
```

或直接用 Android Studio 打开工程运行。APK 输出：`app/build/outputs/apk/debug/app-debug.apk`。

---

## 📂 工程结构

```text
jsnu-laundry-radar/
├── app/
│   ├── src/main/java/com/jsnu/laundry/
│   │   ├── MainActivity.kt              # 单 Activity 入口
│   │   ├── LaundryApp.kt                # Application，初始化 AGC / 通知渠道
│   │   ├── data/
│   │   │   ├── LaundryRepository.kt     # 拉取/派生/排序/边沿触发评估
│   │   │   ├── api/                     # 海乐接口定义与 DTO
│   │   │   ├── model/                   # 业务模型与状态派生 (BizStatus)
│   │   │   ├── prefs/                   # DataStore 配置 + 内置点位表
│   │   │   ├── agc/                     # 华为 AGC 匿名认证 + CloudDB
│   │   │   └── stat/                    # 使用统计
│   │   ├── watch/                       # 蹲守前台服务 / WorkManager / 逻辑
│   │   ├── ui/                          # Compose 界面与主题
│   │   ├── widget/                      # 桌面小组件
│   │   ├── system/                      # 通知 / 闹钟 / 外部 App 跳转
│   │   ├── push/ · analytics/           # 友盟推送与统计
│   │   └── viewmodel/                   # LaundryViewModel
│   ├── src/test/                        # 单元测试 + Roborazzi 截图测试
│   ├── agconnect-services.json.example  # AGC 配置脱敏模板
│   └── build.gradle.kts
├── .github/workflows/gitee-mirror.yml   # 推送后自动镜像到 Gitee
├── .gitignore                           # 忽略凭据 / 构建产物 / 签名文件
├── local.properties.example             # 本地私密配置模板
├── LICENSE
└── README.md
```

---

## 🔐 安全与隐私设计

- **凭据零硬编码**：华为 AGC 凭据通过 `agconnect-services.json`（已忽略）注入；友盟 key 通过 `local.properties` / 环境变量注入；
- **失败自降级**：AGC / 友盟未配置或初始化失败时，核心洗衣功能完全不受影响；
- **匿名身份**：不采集任何学生真实身份，仅使用 AGC 匿名 UID 与本机随机安装标识；
- **只读接口**：仅调用洗衣平台公开的设备状态查询接口，不做任何写操作。

> ⚠️ 若你 fork 本项目，请务必**不要**把自己真实的 `agconnect-services.json`、友盟密钥或签名文件提交到公开仓库。

---

## ⚠️ 免责声明

1. 本项目为**第三方非官方**工具，与「海乐生活」/海尔及相关校方无任何隶属或合作关系；
2. 本项目仅调用平台公开、免鉴权的只读设备状态接口，不涉及任何账号私有数据或写操作；
3. 代码仅供学习交流与软件工程实践，请遵守平台服务条款与学校宿舍管理规范，合理使用公共洗衣设施；
4. 内置点位表为作者所在校区（江苏师范大学泉山校区）的公开楼栋信息，其他学校使用请在设置中自行选点。

---

## 📄 开源许可证

本项目基于 [MIT License](LICENSE) 开源。
