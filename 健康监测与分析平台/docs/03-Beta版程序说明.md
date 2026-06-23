# 康复智伴 Beta 版程序说明

## 1. 交付内容

当前 Beta 版本包含：

- Web 前端源码
- Java 主业务后端源码
- 姿态识别微服务源码
- 本地药品识别副链源码
- MySQL 初始化脚本
- 自动化测试代码
- 开发与测试文档

## 2. 数据库初始化

数据库：

- 用户名：`root`
- 密码：`123456`
- 库名：`health_monitoring`

初始化：

```bash
mysql -uroot -p123456 < "健康监测与分析平台/server/sql/init.sql"
```

## 3. 安装依赖

前端：

```bash
cd "E:\VScodeProject\health-app\健康监测与分析平台"
npm install
```

Java 主后端：

```bash
cd "E:\VScodeProject\health-app\backend-java"
.\mvnw.cmd -q -DskipTests "-Dspring-boot.repackage.skip=true" package
```

姿态识别后端：

```bash
cd "E:\VScodeProject\health-app\posture-backend"
.\mvnw.cmd -q -DskipTests package
```

Python 服务：

```bash
cd "E:\VScodeProject\health-app"
python -m pip install -r .\local_medication_api\requirements.txt
python -m pip install -r .\posture-inference-service\requirements.txt
```

## 4. 启动方式

### 4.1 一键启动

```powershell
cd E:\VScodeProject\health-app
.\start-local-stack.ps1
```

如果暂时不启动本地药品副链：

```powershell
.\start-local-stack.ps1 -SkipMedicationSidecar
```

### 4.2 分别启动

前端：

```bash
cd "E:\VScodeProject\health-app\健康监测与分析平台"
npm run dev
```

Java 主后端：

```bash
cd "E:\VScodeProject\health-app\backend-java"
.\mvnw.cmd spring-boot:run
```

姿态识别后端：

```bash
cd "E:\VScodeProject\health-app\posture-backend"
.\mvnw.cmd spring-boot:run
```

姿态推理服务：

```bash
cd "E:\VScodeProject\health-app\posture-inference-service"
python -m uvicorn app.main:app --host 127.0.0.1 --port 8000
```

本地药品副链：

```bash
cd "E:\VScodeProject\health-app"
python -m uvicorn local_medication_api.app:app --host 127.0.0.1 --port 8011
```

## 5. 默认地址

| 服务 | 地址 |
| --- | --- |
| 前端 | `http://127.0.0.1:4173` |
| Java 主后端 | `http://127.0.0.1:3302` |
| 姿态识别后端 | `http://127.0.0.1:8080` |
| 姿态推理服务 | `http://127.0.0.1:8000` |
| 本地药品副链 | `http://127.0.0.1:8011` |

## 6. 演示账号

- 邮箱：`liming@example.com`
- 密码：`123456`

## 7. 当前可演示功能

- 登录 / 注册
- 总览页
- 上传分析
- 报告保存 / 删除 / 全览
- 保存报告后生成康复计划草案
- 应用康复计划
- 康复提醒
- 用药提醒
- 正式大模型药品识别
- 本地药品识别副链联调
- AI 助手
- 姿态识别视频上传与分析

## 8. 模型说明

### 8.1 上传分析主链

- 接口：`POST /api/analyze/tasks`
- 当前默认：大模型

### 8.2 药品识别主链

- 接口：`POST /api/medications/recognize`
- 当前默认：大模型

### 8.3 药品识别副链

- 接口：`POST /api/medications/recognize/custom-model`
- 下游：`CUSTOM_MEDICATION_RECOGNIZE_URL`
- 本地实现：`E:\VScodeProject\health-app\local_medication_api`
- 说明：副链已接上，但不替换正式主链

### 8.4 AI 助手

- 接口：`POST /api/consult/questions`
- 流式接口：`POST /api/consult/stream`
- 当前默认：大模型

## 9. 当前限制

- 外部大模型调用可能受网络时延影响
- Web 通知不是原生后台闹钟
- Health Connect 仍是桥接预留
- 本地药品副链是并行副链，不是正式默认入口
