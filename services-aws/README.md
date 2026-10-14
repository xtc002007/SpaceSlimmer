# SpaceSlimmer 轻量服务端（产品级用户与订阅额度模块）

本模块是 **SpaceSlimmer** 移动端媒体压缩应用（Android / iOS）的生产级轻量后端服务。
架构与运行环境 1:1 对齐 **NexusTools**（Node.js 18.x + AWS Lambda + DynamoDB + Serverless Framework v3），专注于**核心用户模块**与**订阅配额模块**，无任何冗余开销。

---

## 架构与线上端点 (Live Production Deployment)

* **AWS 线上网关基地址**：`https://6ta8jul1hf.execute-api.us-east-2.amazonaws.com/dev`
* **部署区域**：AWS `us-east-2` (Ohio)
* **运行环境**：Node.js 18.x + AWS Lambda + DynamoDB (On-Demand PAY_PER_REQUEST)

```
SpaceSlimmer 客户端 (Android / iOS)
       │
       ▼ (HTTPS RESTful API + Bearer JWT)
API Gateway (https://6ta8jul1hf.execute-api.us-east-2.amazonaws.com/dev)
       │
   AWS Lambda (Node.js 18.x)
       ├── auth.js          (注册、登录、个人信息获取、服务端吊销登出)
       └── subscription.js  (订阅状态、自然月额度、每日应急额度、额度核销、PRO激活)
       │
       ▼
Amazon DynamoDB (无服务器轻量存储，按请求计费)
       ├── spaceslimmer-users         (用户账户凭证、tokenVersion、登录设备与IP)
       ├── spaceslimmer-subscriptions (订阅方案、月度配额、每日应急使用记录)
       └── spaceslimmer-licenses      (PRO 兑换码/激活码台账)
```

---

## 核心业务与产品级风控机制

### 1. 免费额度机制（月度池 + 每日应急）
* **自然月额度池（30次/月）**：注册用户默认享有每月 30 次免费压缩额度。
* **月末灵活使用**：前半月若未使用，额度保留在当月池中，月末或旅行归来可一次性用完，高度契合相册清理的“突发、集中”场景。
* **人性化每日应急额度（Emergency Daily Quota）**：当月 30 次免费额度耗尽后，每天为用户仍提供 **1 次应急免费压缩** 机会，避免用户因为一时断粮而直接卸载。
* **跨月自动重置（Lazy Rollover）**：每当新月份到来时，用户首次请求状态或核销额度时，服务端自动识别新月份并重置 `usedQuota` 为 0，无需后台定时 Cron 任务扫描全表。

### 2. 产品级安全风控（Security & Rate Limiting）
* **防撞库与防暴力破解**：登录接口（15次/15分钟/IP）、注册接口（10次/小时/IP）、激活码接口（10次/10分钟/IP）均内置平滑时间窗口限流。
* **并发防超额核销**：额度核销采用 DynamoDB 条件表达式（`ConditionExpression`）进行原子 CAS 更新，即使手机端多线程并发请求也不会超额盗刷。
* **服务端 Token 吊销机制**：支持 `tokenVersion` 机制，用户调用 `/api/auth/logout` 时可立即在服务端失效所有已签发的 JWT 凭据。
* **设备跟踪与风控**：支持通过 `x-device-id` 记录最近活跃设备，便于多设备防共享审计。

### 3. 免费版 vs PRO 版权益矩阵

| 特权项目 | 免费版 (Free) | PRO 专业版 (Monthly / Yearly / Lifetime) |
| :--- | :--- | :--- |
| **压缩配额** | 30 次/月（耗尽后提供每日 1 次应急） | **无限制 (Unlimited)** |
| **批量选择** | 视频 3 个 / 照片 10 张 / 音频 5 个 | **无限制批量全选** (数百文件后台队列) |
| **最高分辨率** | 1080p | **4K / 60fps HDR 原画** |
| **单文件体积限制** | 500 MB | **无限制** (100GB+ 均可) |
| **高效编码算法** | 基础 H.264 / AAC / JPEG | **HEVC / AV1 / AVIF** (多省30%~50%空间) |
| **自定义参数** | 固定智能预设 | **支持 CRF 自定义、目标体积、帧率微调** |
| **保险箱宽限期** | 3 天保留 | **30 天或永久保留** |
| **并行处理速度** | 单任务标准处理 | **多核多线程极速并行** |

---

## 接口定义 (API Specification)

所有接口统一前缀为 `/api`，并支持跨域 CORS。

### 1. 用户模块

#### `POST /api/auth/register`
用户注册，自动开通当月 30 次免费配额并下发完整权益定义。
* **请求头**（可选）：`x-device-id: <device_id>`
* **请求体**：
  ```json
  {
    "email": "user@example.com",
    "password": "password123"
  }
  ```
* **成功响应 (201)**：
  ```json
  {
    "message": "注册成功",
    "token": "eyJhbGciOiJIUzI1NiIsIn...",
    "user": { "email": "user@example.com" },
    "subscription": {
      "isPro": false,
      "planType": "free",
      "status": "active",
      "expiresAt": null,
      "quota": {
        "period": "2026-10",
        "total": 30,
        "used": 0,
        "remaining": 30,
        "dailyEmergencyAvailable": false
      },
      "limits": {
        "maxBatchFiles": { "video": 3, "photo": 10, "audio": 5 },
        "maxResolution": "1080p",
        "maxFileSizeMb": 500,
        "allowHevc": false,
        "allowAv1": false,
        "allowAvif": false,
        "allowCustomParams": false,
        "vaultRetentionDays": 3,
        "allowHighParallelism": false
      }
    }
  }
  ```

#### `POST /api/auth/login`
用户登录，返回 JWT 及完整的用户权益与额度。
* **请求体**：`{ "email": "user@example.com", "password": "password123" }`
* **成功响应 (200)**：返回与注册相同的 `token`、`user` 与 `subscription` 结构。

#### `GET /api/auth/me`
获取当前用户信息。
* **请求头**：`Authorization: Bearer <token>`
* **成功响应 (200)**：返回账户详情及最新配额。

#### `POST /api/auth/logout`
退出登录，递增 `tokenVersion`，立刻在服务端吊销旧 JWT。
* **请求头**：`Authorization: Bearer <token>`
* **成功响应 (200)**：`{ "message": "登出成功，已在服务端吊销登录凭据" }`

---

### 2. 订阅与配额模块

#### `GET /api/subscription/status`
获取当前用户的订阅特权、额度及各项参数限制。
* **请求头**：`Authorization: Bearer <token>`
* **成功响应 (200)**：返回当前订阅等级、剩余额度、今日应急可用状态及 limits。

#### `POST /api/subscription/consume`
核销压缩额度（在开始压缩前或完成压缩后调用）。
* **请求头**：`Authorization: Bearer <token>`
* **请求体**：`{ "count": 1, "mediaType": "video" }`
* **正常扣除响应 (200)**：
  ```json
  {
    "success": true,
    "isPro": false,
    "isEmergency": false,
    "consumed": 1,
    "quota": { "period": "2026-10", "total": 30, "used": 6, "remaining": 24 }
  }
  ```
* **应急额度触发响应 (200)**：
  ```json
  {
    "success": true,
    "isPro": false,
    "isEmergency": true,
    "consumed": 1,
    "message": "当月免费额度已用尽，已为您激活今日 1 次应急免费压缩！",
    "quota": { "period": "2026-10", "total": 30, "used": 30, "remaining": 0, "dailyEmergencyAvailable": false }
  }
  ```
* **额度超限响应 (403)**：
  ```json
  {
    "error": "QUOTA_EXCEEDED",
    "message": "当月免费压缩额度及今日应急额度已用尽，请升级至PRO版本以享受无限制压缩",
    "isPro": false,
    "quota": { "period": "2026-10", "total": 30, "used": 30, "remaining": 0, "dailyEmergencyAvailable": false }
  }
  ```

#### `POST /api/subscription/activate`
使用激活码/兑换码升级为 PRO 会员。
* **请求头**：`Authorization: Bearer <token>`
* **请求体**：`{ "licenseKey": "SS-PRO-YEARLY-TEST" }`
* **成功响应 (200)**：
  ```json
  {
    "success": true,
    "message": "PRO 版本激活成功！已解锁全部高级压缩特权。",
    "subscription": {
      "isPro": true,
      "planType": "pro-yearly",
      "status": "active",
      "expiresAt": "2027-10-09T21:00:00.000Z",
      "limits": { "maxResolution": "4K", "allowHevc": true, ... }
    }
  }
  ```

---

## 本地开发、运维与测试

### 1. 运行 13 项自动化测试套件
```bash
cd services-aws
npm test
```
包含输入校验、注册登录、自然月额度计算、每日应急配额触发、并发 CAS 防超额核销、PRO 会员兑换、服务端 Token 吊销等全流程测试。

### 2. 初始化 DynamoDB 数据库表
```bash
npm run setup-db
```
自动在当前 AWS 区域创建表并注入开箱即用的测试激活码。

### 3. 批量生成 PRO 激活码（运营工具）
```bash
# 生成 10 个年度 PRO 激活码（控制台预览）
node scripts/generate-licenses.js yearly 10

# 生成 50 个终身买断 PRO 激活码并直接存入 DynamoDB 表
node scripts/generate-licenses.js lifetime 50 --save

# 生成 20 个月度 PRO 激活码并存入 DynamoDB 表
node scripts/generate-licenses.js monthly 20 --save
```

### 4. 本地启动离线网关
```bash
npm run offline
```
默认在 `http://localhost:3000` 启动，可供 Android / iOS 模拟器直接联调。

### 5. 生产部署 (AWS Lambda)
```bash
npm run deploy
```
