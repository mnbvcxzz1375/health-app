# 设备聚合平台 — OAuth 凭证配置指南

> 本文档说明如何为 14 家厂商申请 OAuth 凭证并配置到后端环境变量中。
> 所有 Provider 在 `isConfigured()=false` 时优雅降级（前端显示"未配置"），不影响其他功能。

---

## 通用配置

在 `backend-java/src/main/resources/application.yml` 的 `device:` 块下配置：

```yaml
device:
  aggregation:
    encryption:
      key: ${DEVICE_TOKEN_ENCRYPTION_KEY:}   # 32 字节 base64 AES 密钥（必须配置）
    oauth:
      redirect-base-url: ${OAUTH_REDIRECT_BASE_URL:http://localhost:3302}
      state-secret: ${DEVICE_OAUTH_STATE_SECRET:} # 生产环境必须配置固定随机密钥
  # OAuth state 注册表优先使用 Spring Data Redis；多实例部署必须共享同一 Redis。
  # REDIS_HOST / REDIS_PORT / REDIS_PASSWORD 由 spring.data.redis 读取。
  providers:
    oura:
      client-id: ${OURA_CLIENT_ID:}
      client-secret: ${OURA_CLIENT_SECRET:}
    fitbit:
      client-id: ${FITBIT_CLIENT_ID:}
      client-secret: ${FITBIT_CLIENT_SECRET:}
    # ... 其余厂商同理
```

### 生成加密密钥

```bash
# Linux / macOS
openssl rand -base64 32

# Windows PowerShell
[Convert]::ToBase64String((1..32 | ForEach-Object { Get-Random -Max 256 }) -as [byte[]])
```

将输出设置为环境变量 `DEVICE_TOKEN_ENCRYPTION_KEY`。

### OAuth state 与多实例部署

设备 OAuth 回调的 `state` 由服务端签名，并在 Redis 中以十分钟 TTL 保存为单次消费记录。生产环境必须同时配置：

- `DEVICE_OAUTH_STATE_SECRET`：所有实例一致的高熵随机密钥；
- `REDIS_HOST`、`REDIS_PORT`、`REDIS_PASSWORD`：所有实例可访问的共享 Redis；
- `DEVICE_AGG_ENCRYPTION_KEY`：用于加密 OAuth access/refresh token 的 Base64 AES-256 密钥。

Redis 不可用时只允许当前进程开发回退，不能把该回退当作集群级防重放保证。

---

## 各厂商申请步骤

### 1. Oura Ring

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://cloud.ouraring.com/personal-access-tokens |
| OAuth 类型 | OAuth 2.0 Authorization Code |
| Authorize URL | `https://cloud.ouraring.com/oauth/authorize` |
| Token URL | `https://api.ouraring.com/oauth/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/oura` |
| Scope | `personal daily` |
| 审批周期 | 即时（Personal Access Token）/ 1-2 周（OAuth App） |

**步骤**：
1. 注册 Oura 开发者账号
2. 创建 OAuth Application，填写 redirect_uri
3. 获取 Client ID / Client Secret
4. 配置环境变量 `OURA_CLIENT_ID` / `OURA_CLIENT_SECRET`

---

### 2. Fitbit

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://dev.fitbit.com/apps |
| OAuth 类型 | OAuth 2.0 Authorization Code + PKCE |
| Authorize URL | `https://www.fitbit.com/oauth2/authorize` |
| Token URL | `https://api.fitbit.com/oauth2/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/fitbit` |
| Scope | `activity heartrate sleep profile` |
| 审批周期 | 即时（开发）/ 审核（生产） |

**步骤**：
1. 登录 Fitbit 开发者后台
2. "Register an App" → 选择 "Personal" 类型
3. 设置 Callback URL
4. 获取 Client ID / Client Secret

---

### 3. Withings

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://developer.withings.com/dashboard |
| OAuth 类型 | OAuth 2.0 Authorization Code |
| Authorize URL | `https://account.withings.com/oauth2_user/authorize2` |
| Token URL | `https://wbsapi.withings.net/v2/oauth2` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/withings` |
| Scope | `user.metrics,user.activity,user.sleepevents` |
| 审批周期 | 1-3 天 |

---

### 4. Garmin

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://developer.garmin.com/connect/overview/ |
| OAuth 类型 | OAuth 2.0（从 1.0a 迁移） |
| Authorize URL | `https://connectapi.garmin.com/oauth2/authorize` |
| Token URL | `https://connectapi.garmin.com/oauth2/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/garmin` |
| Scope | 默认全量 |
| 审批周期 | 3-7 天（需审核） |

**注意**：Garmin 需要申请 "Health API" 权限，审核较严格。

---

### 5. Polar

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://flow.polar.com/openapi |
| OAuth 类型 | OAuth 2.0 Authorization Code |
| Authorize URL | `https://flow.polar.com/oauth2/authorization` |
| Token URL | `https://polarremote.com/v2/oauth2/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/polar` |
| Scope | 默认 |
| 审批周期 | 即时 |

---

### 6. WHOOP

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://developer.whoop.com/ |
| OAuth 类型 | OAuth 2.0 Authorization Code |
| Authorize URL | `https://api.prod.whoop.com/oauth/oauth2/auth` |
| Token URL | `https://api.prod.whoop.com/oauth/oauth2/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/whoop` |
| Scope | `read:recovery read:cycle read:sleep read:profile` |
| 审批周期 | 即时（开发）|

---

### 7. Dexcom

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://developer.dexcom.com/ |
| OAuth 类型 | OAuth 2.0 Authorization Code |
| Authorize URL | `https://api.dexcom.com/v2/oauth2/login` |
| Token URL | `https://api.dexcom.com/v2/oauth2/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/dexcom` |
| Scope | `offline_access` |
| 审批周期 | 需申请 Sandbox → Production（1-2 周） |

**注意**：Dexcom 仅支持 CGM（连续血糖监测）设备数据。

---

### 8. Strava

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://www.strava.com/settings/api |
| OAuth 类型 | OAuth 2.0 Authorization Code |
| Authorize URL | `https://www.strava.com/oauth/authorize` |
| Token URL | `https://www.strava.com/oauth/token` |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/strava` |
| Scope | `activity:read_all` |
| 审批周期 | 即时 |

---

### 9. Samsung Health

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://developer.samsung.com/health |
| OAuth 类型 | OAuth 2.0 |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/samsung_health` |
| 审批周期 | 需企业合作（2-4 周） |

**注意**：Samsung Health SDK 需要签署合作协议，个人开发者可能无法获取。

---

### 10. Mi Fitness（小米）

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://dev.mi.com/ |
| OAuth 类型 | OAuth 2.0 |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/mi_fitness` |
| 审批周期 | 1-2 周 |

---

### 11. Zepp（华米）

| 项目 | 值 |
|------|-----|
| 开发者后台 | https://developer.zepp.com/ |
| OAuth 类型 | OAuth 2.0 + GraphQL API |
| Redirect URI | `http://localhost:3302/api/devices/oauth/callback/zepp` |
| 审批周期 | 3-5 天 |

---

### 12-14. 非 OAuth Provider（无需凭证）

以下 Provider 不走 OAuth 流程，无需申请凭证：

| Provider | 说明 |
|----------|------|
| `manual` | 手动输入，始终可用 |
| `apple_health` | 前端 JS Bridge 推送快照 |
| `bluetooth` | Web Bluetooth API 读取 |
| `health_connect` | Android SDK 推送 |
| `android` | Android 传感器推送 |
| `sdk` | 第三方 SDK 推送（API Key 鉴权） |

---

## 环境变量汇总

```bash
# 必须
DEVICE_TOKEN_ENCRYPTION_KEY=<32字节base64>

# 可选（按需配置，未配置的 Provider 前端显示"未配置"）
OURA_CLIENT_ID=
OURA_CLIENT_SECRET=
FITBIT_CLIENT_ID=
FITBIT_CLIENT_SECRET=
WITHINGS_CLIENT_ID=
WITHINGS_CLIENT_SECRET=
GARMIN_CLIENT_ID=
GARMIN_CLIENT_SECRET=
POLAR_CLIENT_ID=
POLAR_CLIENT_SECRET=
WHOOP_CLIENT_ID=
WHOOP_CLIENT_SECRET=
DEXCOM_CLIENT_ID=
DEXCOM_CLIENT_SECRET=
STRAVA_CLIENT_ID=
STRAVA_CLIENT_SECRET=
SAMSUNG_HEALTH_CLIENT_ID=
SAMSUNG_HEALTH_CLIENT_SECRET=
MI_FITNESS_CLIENT_ID=
MI_FITNESS_CLIENT_SECRET=
ZEPP_CLIENT_ID=
ZEPP_CLIENT_SECRET=

# OAuth 回调基础 URL（生产环境覆盖）
OAUTH_REDIRECT_BASE_URL=http://localhost:3302
```

---

## 验证配置

启动后端后：

```bash
# 查看所有 Provider 配置状态
curl http://localhost:3302/api/devices/providers

# 尝试授权（未配置时返回 503）
curl -X POST http://localhost:3302/api/devices/bindings/oura/authorize \
  -H "Authorization: Bearer <token>"
```

前端 `/devices/brands` 页面会显示每家厂商的配置状态（已配置/未配置）。
