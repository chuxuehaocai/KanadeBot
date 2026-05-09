# 🎵 KanadeBot

> A QQ bot for **Maimai DX** game account management, built with Kotlin & Spring Boot.

> 一个用于 **舞萌DX** 游戏账号管理的 QQ 机器人，基于 Kotlin 和 Spring Boot 构建。

---

## 📖 Overview / 概述

**KanadeBot** is a [Shiro](https://github.com/Mikuac/Shiro)-based QQ bot that communicates with the Maimai DX game server to provide account management features directly through QQ group chats. It supports QR code authentication, ticket management, mileage modification, and AI-powered B50 rating analysis.

**KanadeBot** 是一个基于 [Shiro](https://github.com/Mikuac/Shiro) 框架的 QQ 机器人，通过与舞萌 DX 游戏服务器通信，直接在 QQ 群聊中提供账号管理功能。支持二维码认证、功能票管理、舞里程修改以及 AI 驱动的 B50 数据锐评分析。

---

## ✨ Features / 功能

| Command / 命令 | Description / 描述 |
|---|---|
| `.test` / `.ping` | Health check / 健康检查 |
| `.whoami` / `.wami` | View your account info (rating, tickets, etc.) / 查看账号信息（Rating、功能票等） |
| `.maiMile <amount>` / `.加里程` | Modify MaiMile (DX2025) / 修改舞里程 |
| `.getTicket <id>` / `.发票` | Use a game ticket (功能票) / 使用功能票 |
| `.b50` / `.b50锐评` / `.看看实力` | Analyze your B50 data with AI (DeepSeek) / 使用 AI 分析你的 B50 数据 |

### Ticket Types / 功能票类型

| ID | Name / 名称 |
|---|---|
| 1 | 功能票 (Normal Ticket) |
| 2 | 6倍功能票 (6x Ticket) |
| 3 | 3倍功能票 (3x Ticket) |
| 4 | 自由模式票 (Free Mode Ticket) |
| 5 | 段位认定票 (Course Ticket) |

---

## 🏗️ Architecture / 架构

```
KanadeBot
├── KanadeBot.kt              # Main bot handler (group & private messages)
├── KanadeBotApplication.kt   # Spring Boot entry point
├── command/
│   ├── ICommand.kt           # Command interface
│   ├── CommandData.kt        # Command metadata
│   ├── GroupContext.kt       # Group context data class
│   └── commands/
│       ├── Test.kt                    # Test/ping command
│       ├── WhoamiCommand.kt           # Account info query
│       ├── MaiMileCommand.kt          # MaiMile modification
│       ├── SendTicketCommand.kt       # Ticket usage
│       └── EvaluateRatingCommand.kt   # B50 AI analysis
├── config/
│   └── Config.kt             # Configuration data class
├── managers/
│   ├── CommandManager.kt     # Command registration & dispatch
│   ├── ConfigManager.kt      # JSON config file management
│   ├── PendingLoginManager.kt # QR code callback management
│   └── ResourceManager.kt    # Image resource caching
├── mainetwork/
│   ├── IPacket.kt            # Packet interface
│   ├── NetworkManager.kt     # Game server communication (AES + Zlib)
│   ├── beans/                # Response data models
│   └── packet/               # Request packet definitions
│       ├── UserTokenAndIDPacket.kt  # QR authentication
│       ├── UserLoginPacket.kt       # Login
│       ├── UserLogoutPacket.kt      # Logout
│       ├── UserDataPacket.kt        # User data query
│       ├── UserPreviewPacket.kt     # User preview
│       ├── UserChargePacket.kt      # Charge/ticket data
│       └── GetUserRatingPacket.kt   # Rating data
│   └── payload/
│       └── PayloadBuilder.kt # API request payload builder
└── utils/
    ├── CipherAES.kt          # AES-CBC encryption/decryption
    ├── DeepSeekConnector.kt  # DeepSeek AI API integration
    ├── ExtendsFunction.kt    # Kotlin extension functions
    ├── HttpClient.kt         # HTTP client wrapper
    ├── ImageBuilder.kt       # Image generation (placeholder)
    ├── Logger.kt             # Simple logger
    ├── MusicDataProvider.kt  # Music data from diving-fish API
    └── QRCodeUtil.kt         # QR code detection & decoding
```

### Key Technical Details / 关键技术细节

- **Framework**: [Shiro](https://github.com/Mikuac/Shiro) — a Kotlin QQ bot framework based on OneBot protocol via WebSocket
- **Game Protocol**: AES-CBC encryption + Zlib compression, matching the official Maimai DX title server protocol
- **QR Authentication**: Uses the AIME authentication server for QR code login
- **AI Integration**: DeepSeek API for B50 data analysis with two modes: "sharp review" (毒舌锐评) and "cute cat-girl analysis" (猫娘分析)
- **Music Data**: Fetches song difficulty data from [水鱼查分器](https://www.diving-fish.com) API

---

## 🚀 Getting Started / 快速开始

### Prerequisites / 前置要求

- JDK 21+
- A running OneBot-compatible QQ client (e.g., [Lagrange.Core](https://github.com/LagrangeDev/Lagrange.Core), [go-cqhttp](https://github.com/Mrs4s/go-cqhttp))
- A Maimai DX arcade machine or access to the game server

### Configuration / 配置

1. **Clone the repository / 克隆仓库**
   ```bash
   git clone https://github.com/chuxuehaocai/KanadeBot.git
   cd KanadeBot
   ```

2. **Configure `application.yml`** (in `src/main/resources/`)
   ```yaml
   server:
     port: 8080
   shiro:
     ws:
       access-token: "your_token"
       client:
         enable: true
         url: "ws://your-qq-bot-address:port"
   ```

3. **Configure `kanade.json`** (auto-generated on first run, placed in project root)
   ```json
   {
     "keychipId": "",
     "aimeSalt": "",
     "aesIv": "",
     "aesKey": "",
     "titleServerUrl": "",
     "aimeUrl": "",
     "packetSalt": "",
     "obfuscateParam": "",
     "apiVersion": "",
     "clientId": "",
     "regionId": 0,
     "regionName": "",
     "placeId": 0,
     "placeName": "",
     "deepSeekApiKey": ""
   }
   ```

   > **Note**: The configuration values are game-server-specific parameters. You need to obtain them from your own Maimai DX setup. These values are intentionally left blank in this documentation for legal and security reasons.

4. **Build and run / 构建并运行**
   ```bash
   ./gradlew bootJar
   java -jar build/libs/KanadeBot-1.0-SNAPSHOT.jar
   ```

---

## 💬 Usage / 使用方法

All commands are triggered by prefixing with `.` (dot) in group chats.

所有命令通过在群聊中输入 `.` 前缀触发。

### Examples / 示例

```
# Check account info / 查看账号信息
.whoami
# → Bot replies: "请私聊发送你的登陆二维码给我"
# → Send your game QR code to the bot privately

# Modify MaiMile / 修改舞里程
.maiMile 5000

# Use a ticket / 使用功能票
.getTicket 1

# B50 analysis (sharp mode) / B50 锐评
.b50锐评
# or / 或者
.看看实力

# B50 analysis (normal mode) / B50 普通分析
.b50
```

### Workflow / 工作流程

1. Send a command in the group chat
2. The bot prompts you to send your game QR code privately
3. Send the QR code image (from your game's AIME login screen) to the bot in a private chat
4. The bot processes the request and replies in the group

---

## 🛠️ Tech Stack / 技术栈

| Technology / 技术 | Purpose / 用途 |
|---|---|
| **Kotlin** | Core language / 核心语言 |
| **Spring Boot 4.x** | Application framework / 应用框架 |
| **Shiro** | QQ bot framework / QQ 机器人框架 |
| **Fastjson2** | JSON serialization / JSON 序列化 |
| **OkHttp** | HTTP client / HTTP 客户端 |
| **ZXing** | QR code detection / 二维码检测 |
| **Kotlin Coroutines** | Async operations / 异步操作 |
| **DeepSeek API** | AI analysis / AI 分析 |

---

## 📁 Project Structure / 项目结构

```
KanadeBot/
├── build.gradle.kts          # Gradle build configuration
├── settings.gradle.kts       # Gradle settings
├── gradle.properties         # Gradle properties
├── kanade.json               # Runtime configuration (auto-generated)
├── resource/                 # Resource files
│   ├── whoami.png
│   └── icons/                # Downloaded icon cache
└── src/
    └── main/
        ├── kotlin/moe/cuteyuki/kanadebot/
        │   ├── KanadeBot.kt
        │   ├── KanadeBotApplication.kt
        │   ├── command/
        │   ├── config/
        │   ├── managers/
        │   ├── mainetwork/
        │   └── utils/
        └── resources/
            └── application.yml
```

---

## 📝 Notes / 注意事项

- ⚠️ This project is for **educational and research purposes only**. Use at your own risk.
- ⚠️ 本项目仅用于**学习和研究目的**，请自行承担使用风险。
- The QR code authentication flow requires a valid game QR code from a Maimai DX cabinet.
- MaiMile modification and ticket usage interact with the live game server — use responsibly.
- The DeepSeek API key is optional; B50 analysis will work without it (but without AI commentary).

---

## 📄 License / 许可

This project is open source under the MIT License.

本项目基于 MIT 许可证开源。

---

## 🙏 Acknowledgements / 致谢

- [Shiro](https://github.com/Mikuac/Shiro) — QQ bot framework
- [水鱼查分器](https://www.diving-fish.com) — Music data API
- [DeepSeek](https://deepseek.com) — AI analysis API
- [reverseMai](https://github.com/K0-RR/reverseMai) — Protocol reference
