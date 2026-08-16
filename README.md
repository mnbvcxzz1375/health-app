# 康复智伴（AHealth）

面向多病共存老年人的居家康复安全决策大模型系统。项目将健康监测、用药识别、检查报告、康复训练和设备数据汇聚到统一的病例上下文中，再通过垂直领域知识检索、证据排序、安全规则合并和个性化计划生成，形成“数据采集—证据检索—安全决策—康复执行—结果回写”的闭环。

## 项目定位

本项目服务于健康管理和居家康复辅助场景，不替代医生诊断、处方或急救决策。系统对数据来源、证据、置信度、数据质量和升级提示进行显式展示；当证据不足、模型置信度较低或检测任务失败时，系统返回可解释的降级状态。

## 主要能力

- 多源健康数据：心率、血压、睡眠、步数、饮食、用药、检查报告、设备同步和个人资料。
- 病例上下文：按用户隔离健康数据，维护时间范围、已确认事实、约束条件、风险状态和数据质量标记。
- 垂域知识增强：支持医学知识库、混合检索、向量检索、BM25、MMR、多路召回和证据重排序。
- 安全问答与计划：回答中返回证据片段、风险等级、行动标签和升级建议；康复计划受病例约束和安全规则限制。
- 药品识别：结合图像预处理、OCR、实体识别、药品目录校准和大模型复核，覆盖中药与西药的识别辅助流程。
- 姿态与训练反馈：通过姿态推理服务分析训练视频，在低置信度或数据缺失时返回明确的降级状态。
- 设备聚合：为 Apple Health、ROOK 及可选的 Garmin、Oura、Fitbit、Withings、Polar、WHOOP、Dexcom、Strava 等设备预留统一接入层。

## 技术栈

| 层次 | 技术 |
| --- | --- |
| 前端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Vuetify、Tailwind CSS、ECharts |
| 主后端 | Java 17、Spring Boot 3.5、Spring Web、Spring Security、JPA/JDBC、Flyway、MySQL |
| AI 编排 | LangChain4j、OpenAI 兼容模型接口、模型路由、结构化输出、安全合并 |
| 检索与缓存 | Redis Stack、向量检索、BM25、MMR、语义缓存 |
| 推理服务 | Python、FastAPI、Uvicorn、MediaPipe、OpenCV、PyTorch、OCR/视觉模型适配器 |
| 测试 | Vitest、Testing Library、JUnit 5、MockMvc、Testcontainers |

## 目录结构

```text
健康监测与分析平台/        Vue 3 + TypeScript 前端
backend-java/              Spring Boot 主后端、认证、病例、问答、RAG、设备与康复模块
posture-backend/           姿态任务编排后端
posture-inference-service/ 姿态视频推理服务
local_medication_api/      本地药品图像识别服务
openmed-inference-service/ 开放药品/医学实体推理服务
bone_age/                  骨龄评估推理服务
data-crawler/              垂域知识与数据采集脚本
evaluation/                数据卡、契约和多臂评测工具
docs/                      技术方案、验收记录和接口说明
docker-compose.dev.yml     Redis Stack 开发依赖
start-local-stack.ps1      Windows 本地多服务启动脚本
```

## 环境要求

- Windows、PowerShell 7+
- Node.js 20+ 与 npm/pnpm
- Java 17+
- Python 3.10+
- MySQL 8+
- Docker Desktop（用于 Redis Stack，或自行提供 Redis）

## 快速启动

### 1. 配置环境变量

复制 `健康监测与分析平台/.env.example` 为 `.env`，填写本地数据库、模型服务和可选设备服务配置。不要把 `.env`、API Key、OAuth Secret 或数据库密码提交到 Git。

主后端使用以下环境变量：

```text
DB_URL=jdbc:mysql://127.0.0.1:3306/health_monitoring
DB_USERNAME=root
DB_PASSWORD=your-local-password
DASHSCOPE_API_KEY=your-api-key
DEVICE_OAUTH_STATE_SECRET=your-random-secret
DEVICE_AGG_ENCRYPTION_KEY=your-base64-key
```

### 2. 启动 Redis

```powershell
docker compose -f docker-compose.dev.yml up -d redis-stack
```

### 3. 一键启动本地服务

```powershell
.\start-local-stack.ps1
```

脚本会检查可用端口并启动前端、主后端、姿态服务和药品识别服务。默认端口如下：

| 服务 | 默认端口 |
| --- | ---: |
| Vue 前端 | 4173 |
| Spring Boot 主后端 | 3302 |
| 姿态任务后端 | 8080 |
| 姿态推理服务 | 8000 |
| 药品识别服务 | 8011 |
| OpenMed 推理服务 | 8012 |
| 骨龄评估服务 | 8013 |
| Redis | 6379 |

### 4. 分别启动

前端：

```powershell
cd 健康监测与分析平台
npm install
npm run dev
```

主后端：

```powershell
cd backend-java
.\mvnw.cmd spring-boot:run
```

药品识别服务：

```powershell
python -m uvicorn local_medication_api.app:app --host 127.0.0.1 --port 8011
```

姿态推理服务：

```powershell
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

## 常用检查命令

```powershell
cd 健康监测与分析平台
npm run typecheck
npm run test
npm run build

cd ..\backend-java
.\mvnw.cmd test
```

## 数据与模型说明

项目代码不包含模型权重、个人健康数据或生产密钥。模型权重和外部服务凭据需要在本地或部署环境中单独配置。用于演示和测试的数据应使用合成数据、脱敏数据或获得授权的数据，并保留数据来源与版本记录。

## 安全边界

- 所有用户数据查询和写入必须经过当前用户边界校验。
- 没有检索证据时，问答不得生成确定性的个性化医疗结论。
- 低置信度、服务不可用或数据不足时，系统返回降级状态并提示进一步核验。
- 正式部署必须配置固定的 OAuth state secret、设备加密密钥、数据库密码和 Redis 共享实例。

## 许可证与使用

当前仓库用于项目研发、竞赛展示和技术验证。第三方模型、数据集、字体、图标和依赖库分别遵循其原始许可证；正式发布前请逐项完成许可证和数据授权核查。
