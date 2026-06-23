# 后端 API 接口文档

本文档描述 `Spring Boot` 后端对外提供的接口，包括请求参数、字段类型、返回值结构和常见错误响应。

## 基础信息

- Base URL: `http://localhost:8080`
- Content-Type:
  - 上传接口: `multipart/form-data`
  - 查询接口: `application/json`
- 字符编码: `UTF-8`

## 枚举说明

### `ExerciseType`

| 值 | 含义 |
| --- | --- |
| `SQUAT` | 深蹲 |
| `PUSH_UP` | 俯卧撑 |
| `PLANK` | 平板支撑 |
| `LUNGE` | 弓步蹲 |

### `CameraView`

| 值 | 含义 |
| --- | --- |
| `SIDE` | 侧视角，推荐 |
| `FRONT` | 正视角 |
| `ANGLED` | 斜侧视角 |

### `JobStatus`

| 值 | 含义 |
| --- | --- |
| `PENDING` | 已创建，等待处理 |
| `RUNNING` | 分析中 |
| `SUCCEEDED` | 分析完成 |
| `FAILED` | 分析失败 |
| `LOW_CONFIDENCE` | 已完成，但关键点稳定性不足，结果仅供参考 |

### `Verdict`

| 值 | 含义 |
| --- | --- |
| `STANDARD` | 动作整体标准 |
| `NEEDS_IMPROVEMENT` | 动作存在明显改进点 |
| `LOW_CONFIDENCE` | 视频质量或关键点稳定性不足 |

### `Severity`

| 值 | 含义 |
| --- | --- |
| `MAJOR` | 严重问题，默认扣 15 分 |
| `MEDIUM` | 中等问题，默认扣 8 分 |
| `MINOR` | 轻微问题，默认扣 4 分 |

## 1. 创建体态分析任务

- 方法: `POST`
- 路径: `/api/v1/posture/jobs`
- Content-Type: `multipart/form-data`

### 请求参数

| 参数名 | 位置 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- | --- |
| `userId` | form-data | `string` | 是 | 用户 ID |
| `exerciseType` | form-data | `string` | 是 | 动作类型，取值见 `ExerciseType` |
| `cameraView` | form-data | `string` | 是 | 机位类型，取值见 `CameraView` |
| `videoFile` | form-data | `file` | 是 | 用户上传的视频文件 |

### 请求示例

```bash
curl -X POST "http://localhost:8080/api/v1/posture/jobs" \
  -F "userId=u001" \
  -F "exerciseType=SQUAT" \
  -F "cameraView=SIDE" \
  -F "videoFile=@E:\Atitaishibie\sample.mp4"
```

### 成功响应

- 状态码: `202 Accepted`

#### 响应体

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `jobId` | `string` | 任务唯一 ID |
| `status` | `string` | 初始状态，一般为 `PENDING` |

#### 响应示例

```json
{
  "jobId": "f125df7b-ae00-4e59-98fe-fbabcd716134",
  "status": "PENDING"
}
```

## 2. 查询任务状态

- 方法: `GET`
- 路径: `/api/v1/posture/jobs/{jobId}`
- Content-Type: `application/json`

### 路径参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `jobId` | `string` | 是 | 创建任务时返回的任务 ID |

### 成功响应

- 状态码: `200 OK`

#### 响应体

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `jobId` | `string` | 任务 ID |
| `status` | `string` | 任务状态，取值见 `JobStatus` |
| `progress` | `integer` | 任务进度，范围 `0-100` |
| `failReason` | `string \| null` | 失败原因或低置信度原因，没有则为 `null` |

#### 响应示例

```json
{
  "jobId": "f125df7b-ae00-4e59-98fe-fbabcd716134",
  "status": "RUNNING",
  "progress": 70,
  "failReason": null
}
```

## 3. 查询分析报告

- 方法: `GET`
- 路径: `/api/v1/posture/jobs/{jobId}/report`
- Content-Type: `application/json`

### 路径参数

| 参数名 | 类型 | 必填 | 说明 |
| --- | --- | --- | --- |
| `jobId` | `string` | 是 | 创建任务时返回的任务 ID |

### 成功响应

- 状态码: `200 OK`

#### 响应体

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `exerciseType` | `string` | 动作类型，取值见 `ExerciseType` |
| `score` | `number` | 综合评分，范围通常为 `0-100` |
| `verdict` | `string` | 动作判定，取值见 `Verdict` |
| `summary` | `string \| null` | 总结说明 |
| `issues` | `IssueView[]` | 问题列表 |
| `suggestions` | `string[]` | 纠正建议列表 |
| `warnings` | `string[]` | 风险提示或置信度提示 |
| `reps` | `RepAnalysis[]` | 分次动作分析结果 |
| `evidenceFrames` | `EvidenceFrame[]` | 证据帧列表 |
| `validFrameRatio` | `number` | 有效关键点帧比例，范围通常为 `0-1` |

### `IssueView` 对象

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `code` | `string` | 问题编码，例如 `SQUAT_DEPTH_INSUFFICIENT` |
| `severity` | `string` | 严重级别，取值见 `Severity` |
| `phase` | `string` | 问题出现阶段，例如 `top`、`bottom`、`hold` |
| `metricName` | `string` | 对应的评估指标名 |
| `actualValue` | `number` | 实际测量值 |
| `targetRange` | `string` | 目标范围描述 |
| `evidenceTimestampMs` | `integer` | 证据帧时间戳，单位毫秒 |
| `description` | `string` | 问题解释 |

### `RepAnalysis` 对象

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `repIndex` | `integer` | 第几次动作，从 `1` 开始 |
| `startMs` | `integer` | 本次动作开始时间，单位毫秒 |
| `endMs` | `integer` | 本次动作结束时间，单位毫秒 |
| `score` | `number` | 本次动作评分 |
| `issues` | `IssueView[]` | 本次动作对应的问题列表 |

### `EvidenceFrame` 对象

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `label` | `string` | 证据帧标签，一般与问题编码相关 |
| `timestampMs` | `integer` | 证据帧时间戳，单位毫秒 |
| `imageUrl` | `string \| null` | 证据帧访问地址 |

### 响应示例

```json
{
  "exerciseType": "SQUAT",
  "score": 78.5,
  "verdict": "NEEDS_IMPROVEMENT",
  "summary": "检测到 2 个动作问题，建议优先修正高严重度项目。",
  "issues": [
    {
      "code": "SQUAT_DEPTH_INSUFFICIENT",
      "severity": "MAJOR",
      "phase": "bottom",
      "metricName": "kneeAngle",
      "actualValue": 108.2,
      "targetRange": "<= 100",
      "evidenceTimestampMs": 1350,
      "description": "下蹲深度不足。"
    }
  ],
  "suggestions": [
    "下蹲阶段继续把髋部坐向后下方，直到大腿接近平行地面再起身。"
  ],
  "warnings": [],
  "reps": [
    {
      "repIndex": 1,
      "startMs": 300,
      "endMs": 1800,
      "score": 85.0,
      "issues": [
        {
          "code": "SQUAT_DEPTH_INSUFFICIENT",
          "severity": "MAJOR",
          "phase": "bottom",
          "metricName": "kneeAngle",
          "actualValue": 108.2,
          "targetRange": "<= 100",
          "evidenceTimestampMs": 1350,
          "description": "下蹲深度不足。"
        }
      ]
    }
  ],
  "evidenceFrames": [
    {
      "label": "SQUAT_DEPTH_INSUFFICIENT",
      "timestampMs": 1350,
      "imageUrl": "http://localhost:8080/api/v1/posture/storage/jobs/f125df7b-ae00-4e59-98fe-fbabcd716134/evidence/01_squat_depth_insufficient.jpg"
    }
  ],
  "validFrameRatio": 0.91
}
```

## 4. 证据帧静态资源访问

后端会把证据帧图片暴露为静态资源地址，通常不需要前端手动拼接，直接使用 `/report` 返回的 `evidenceFrames[].imageUrl` 即可。

- 方法: `GET`
- 路径: `/api/v1/posture/storage/**`

### 说明

- `**` 为运行时生成的相对路径，例如:
  - `/api/v1/posture/storage/jobs/{jobId}/evidence/01_squat_depth_insufficient.jpg`
- 响应内容为图片文件

## 错误响应

后端统一错误响应格式如下:

| 字段名 | 类型 | 说明 |
| --- | --- | --- |
| `status` | `integer` | HTTP 状态码 |
| `error` | `string` | HTTP 状态短语 |
| `message` | `string` | 错误说明 |

### 常见错误状态码

| 状态码 | 场景 |
| --- | --- |
| `400 Bad Request` | 参数缺失、枚举值非法、上传文件为空 |
| `404 Not Found` | `jobId` 不存在 |
| `409 Conflict` | 报告尚未生成，或任务状态还未达到可读报告阶段 |

### 错误响应示例

```json
{
  "status": 400,
  "error": "Bad Request",
  "message": "Required request part 'videoFile' is not present"
}
```

## 使用建议

- 上传接口里，`cameraView` 必须传枚举值，不能传文件路径。
- `videoFile` 必须作为 `multipart/form-data` 的文件字段上传。
- 建议前端流程:
  1. 调用创建任务接口获取 `jobId`
  2. 轮询任务状态接口直到 `status` 为 `SUCCEEDED`、`LOW_CONFIDENCE` 或 `FAILED`
  3. 若状态为 `SUCCEEDED` 或 `LOW_CONFIDENCE`，调用报告接口获取完整分析结果
