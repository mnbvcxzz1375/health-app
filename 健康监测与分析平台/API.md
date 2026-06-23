# AHealthApp API 契约文档（UI 阶段企业级草案）

## 1. 基础约定

- 全局前缀：`/api`
- 开发环境前端调用（Vite 代理）：`http://localhost:5173/api/...`
- 本地后端直连：`http://localhost:3001/api/...`
- 认证头：`Authorization: Bearer <token>`
- 请求类型：
  - JSON：`Content-Type: application/json`
  - 文件上传：`multipart/form-data`

### 1.1 完整路径示例

- 文档路径：`POST /auth/register`
- 前端实际调用：`POST /api/auth/register`
- 后端直连地址：`POST http://localhost:3001/api/auth/register`

### 1.2 通用响应与错误

成功响应由各接口定义，错误统一建议：

```json
{
  "message": "错误说明",
  "code": "OPTIONAL_ERROR_CODE"
}
```

通用状态码：

| 状态码 | 含义 | 场景 |
| --- | --- | --- |
| 400 | 参数错误 | 缺少必填字段、字段格式不合法 |
| 401 | 未认证 | Token 缺失或会话失效 |
| 404 | 资源不存在 | 任务/动作/设备不存在 |
| 409 | 资源冲突 | 邮箱已注册 |
| 500 | 服务异常 | 服务内部错误 |

---

## 2. Auth

### 2.1 注册
- 用途：创建账户并返回登录态
- Method/Path：`POST /auth/register`
- Headers：无
- Body：

```json
{
  "name": "李明",
  "email": "liming@example.com",
  "password": "123456"
}
```

- Response：

```json
{
  "token": "token_xxx",
  "user": {
    "id": "1",
    "name": "李明",
    "email": "liming@example.com",
    "avatarUrl": ""
  }
}
```

- 前端调用页面：`/auth/register`

### 2.2 登录
- Method/Path：`POST /auth/login`
- Body：`{ email, password }`
- Response：同注册
- 前端调用页面：`/auth/login`

### 2.3 当前会话
- Method/Path：`GET /auth/me`
- Headers：`Authorization`
- Response：`{ user }`
- 前端调用模块：`src/stores/auth.ts`

### 2.4 退出登录
- Method/Path：`POST /auth/logout`
- Headers：`Authorization`
- Response：`{ "success": true }`
- 前端调用模块：`src/stores/auth.ts`

---

## 3. Home

### 3.1 首页总览
- 用途：首页评分、指标、建议
- Method/Path：`GET /home/summary`
- Headers：`Authorization`（可选）
- Response（核心字段）：

```json
{
  "userName": "李明",
  "healthScore": 84,
  "statusBadge": "总体稳定",
  "statusBadgeVariant": "success",
  "statusSummary": "...",
  "stepsTarget": 9000,
  "stepsNow": 5320,
  "keyMetrics": [
    {
      "key": "hr",
      "value": 71,
      "badge": "正常",
      "badgeVariant": "success",
      "hint": "静息心率较昨日 -1"
    }
  ],
  "suggestions": ["..."]
}
```

- 前端调用页面：`/home`

---

## 4. Monitor

### 4.1 最新监测值
- Method/Path：`GET /monitor/latest`
- Response：

```json
{
  "hr": 71,
  "sleep": 86,
  "deepSleep": 2.1,
  "awake": 1,
  "stress": 48,
  "updatedAt": "2026-03-11T09:36:00.000Z"
}
```

- 前端调用页面：`/monitor`

### 4.2 趋势数据
- Method/Path：`GET /monitor/trends`
- Query：
  - `metric`: `hr | sleep | stress`
  - `range`: `hour | day | month`
- Response：

```json
{
  "labels": ["03-05", "03-06"],
  "values": [74, 73],
  "insight": "趋势解读",
  "suggestion": "建议内容"
}
```

- 前端调用页面：`/monitor`

---

## 5. Upload

### 5.1 创建分析任务
- Method/Path：`POST /analyze/tasks`
- Content-Type：`multipart/form-data`
- Body 字段：
  - `type`: `image | lab | text | symptom`
  - `file`: 文件（可选）
  - `text`: 文本（可选）
- Response：

```json
{ "taskId": "task_174159..." }
```

- 前端调用页面：`/upload`

### 5.2 查询分析结果
- Method/Path：`GET /analyze/tasks/:taskId`
- Response：

```json
{
  "status": "DONE",
  "points": ["关注点1", "关注点2"],
  "advice": ["建议1", "建议2"]
}
```

- 前端调用页面：`/upload`

---

## 6. Medication

### 6.1 药品列表
- Method/Path：`GET /medications`
- Response：药品数组（含提醒数组）
- 前端调用页面：`/medication`、`/upload`

### 6.2 新增药品
- Method/Path：`POST /medications`
- Body：药品对象（含 `reminders`）
- Response：创建后的完整药品
- 前端调用页面：`/medication`

### 6.3 更新药品
- Method/Path：`PUT /medications/:id`
- Body：同新增
- Response：更新后的完整药品

### 6.4 启停药品
- Method/Path：`POST /medications/:id/toggle`
- Response：`{ id, enabled }`

### 6.5 删除药品
- Method/Path：`DELETE /medications/:id`
- Response：`{ "success": true }`

---

## 7. Rehab

### 7.1 康复计划
- Method/Path：`GET /rehab/plan`
- Response：
  - `label`
  - `exercises[]`
  - `weekTrend`
  - `planSummary`
  - `reminderSummary`
- 前端调用页面：`/rehab`

### 7.2 动作完成切换
- Method/Path：`POST /rehab/plan/:id/toggle`
- Response：返回最新计划对象
- 前端调用页面：`/rehab`

### 7.3 按名称读取动作
- Method/Path：`GET /rehab/exercises/by-name?name=鸟狗式`
- Response：单个动作详情
- 前端调用页面：`/rehab/exercise`

### 7.4 读取动作提醒
- Method/Path：`GET /rehab/reminder?name=鸟狗式`
- Response：

```json
{
  "name": "鸟狗式",
  "time": "08:00",
  "days": ["mon", "wed", "fri"],
  "pushEnabled": true
}
```

- 前端调用页面：`/rehab/reminder`

### 7.5 保存动作提醒
- Method/Path：`POST /rehab/reminder`
- Body：同读取提醒结构
- Response：同请求体
- 前端调用页面：`/rehab/reminder`

### 7.6 康复视频任务（本次新增并联调）
- 用途：录后上传视频并轮询动作纠错结果
- Method/Path：`POST /rehab/video/tasks`
- Content-Type：`multipart/form-data`
- Body：
  - `exerciseName`: string
  - `file`: 视频文件
- Response：

```json
{ "taskId": "rehab_video_0001" }
```

- 前端调用页面：`/rehab`

### 7.7 康复视频结果（本次新增并联调）
- Method/Path：`GET /rehab/video/tasks/:taskId`
- Response：

```json
{
  "status": "DONE",
  "score": 86,
  "issues": ["..."],
  "tips": ["..."],
  "segments": [
    {
      "start": "00:04",
      "end": "00:08",
      "issue": "肩胛稳定不足",
      "suggestion": "收紧下沉肩胛"
    }
  ]
}
```

- 前端调用页面：`/rehab`

### 7.8 训练计划设置（预留接口）
- Method/Path：`GET /rehab/plan/settings`
- Response：

```json
{
  "focus": "核心稳定",
  "frequency": "每周 3 次",
  "duration": "单次 22 分钟",
  "intensity": "低-中"
}
```

- 前端调用页面：`/rehab`

### 7.9 保存训练计划设置（预留接口）
- Method/Path：`POST /rehab/plan/settings`
- Body：同 7.8 响应结构
- Response：保存后的设置对象
- 前端调用页面：`/rehab`

---

## 8. Profile

### 8.1 我的页摘要
- Method/Path：`GET /profile/summary`
- Response：

```json
{
  "devices": "1 台（设备）",
  "uploads": "6 份",
  "riskScore": "18 · 低风险"
}
```

- 前端调用页面：`/profile`

### 8.2 获取个人设置
- Method/Path：`GET /profile/settings`
- Response：用户设置对象
- 前端调用页面：`/profile/settings`

### 8.3 保存个人设置
- Method/Path：`POST /profile/settings`
- Body：同获取设置响应
- Response：保存后的设置对象
- 前端调用页面：`/profile/settings`

### 8.4 更新头像
- Method/Path：`POST /profile/avatar`
- Body：`{ avatarUrl }`
- Response：`{ "success": true }`
- 前端调用页面：`/profile`

---

## 9. Consult（本次新增并联调）

### 9.1 单轮问询
- 用途：首页问询卡片提交问题
- Method/Path：`POST /consult/questions`
- Headers：`Authorization`（可选）
- Body：

```json
{
  "question": "为什么我最近下午容易疲劳？",
  "scene": "home_overview"
}
```

- Response：

```json
{
  "requestId": "consult_0001",
  "answer": "...",
  "suggestions": ["...", "..."],
  "disclaimer": "该回答仅用于健康管理建议，不替代医生诊疗。"
}
```

- 前端调用页面：`/home`

---

## 10. Device（UI 已接入，后端接口预留）

### 10.1 设备列表
- Method/Path：`GET /devices`
- Response：

```json
[
  {
    "id": 1,
    "name": "腕部手表",
    "brand": "HealthOne",
    "model": "S3",
    "type": "watch",
    "connected": true,
    "battery": 68,
    "lastSyncAt": "2026-03-11T09:36:00.000Z"
  }
]
```

- 前端调用页面：`/home`

### 10.2 新增设备
- Method/Path：`POST /devices`
- Body：

```json
{
  "name": "腕部手表",
  "brand": "HealthOne",
  "model": "S3",
  "type": "watch"
}
```

- Response：新增后的设备对象
- 前端调用页面：`/home`

### 10.3 同步设备
- Method/Path：`POST /devices/:id/sync`
- Response：同步后的设备对象（更新时间、电量等）
- 前端调用页面：`/home`

### 10.4 删除设备
- Method/Path：`DELETE /devices/:id`
- Response：`{ "success": true }`（建议）
- 前端调用页面：`/home`

---

## 11. 当前已接入接口（真实接口）

- Auth：`/auth/register`、`/auth/login`、`/auth/me`、`/auth/logout`
- Home：`/home/summary`
- Monitor：`/monitor/latest`、`/monitor/trends`
- Upload：`/analyze/tasks`、`/analyze/tasks/:taskId`
- Medication：`/medications`、`/medications/:id`、`/medications/:id/toggle`
- Rehab：`/rehab/plan`、`/rehab/plan/:id/toggle`、`/rehab/exercises/by-name`、`/rehab/reminder`
- Profile：`/profile/summary`、`/profile/settings`、`/profile/avatar`

## 12. 本次新增并联调

- `POST /consult/questions`
- `POST /rehab/video/tasks`
- `GET /rehab/video/tasks/:taskId`

## 13. 预留接口（后续服务化）

- `GET /devices`
- `POST /devices`
- `POST /devices/:id/sync`
- `DELETE /devices/:id`
- `GET /rehab/plan/settings`
- `POST /rehab/plan/settings`

---

## 14. 联调备注

- 前端统一通过 `VITE_API_BASE_URL` 控制前缀，默认值 `/api`。
- 当前前端具备开发期兜底逻辑：真实接口失败后自动回退演示数据，以保证页面不空白。
- 若后端上线对应新增接口，前端无需改路由与页面结构，仅需按本契约返回字段。
