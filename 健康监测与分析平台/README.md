# 健康监测与分析平台（Vue 企业级骨架）

这是将原有 React/Vite 的 UI 代码改造为 **Vue 3 + TypeScript + Vite + Tailwind** 的版本，并按企业级开发习惯做了模块化拆分与注释。

## 技术栈

- Vue 3 + `<script setup>` + TypeScript
- Vue Router（路由模块化、懒加载）
- Pinia（全局状态）
- Axios（统一请求实例/拦截器）
- ECharts（趋势图）
- Tailwind CSS v4（保留原设计风格）

## 目录结构（按模块拆分）

```text
src/
  api/                 # 接口层（Axios 实例 + 业务模块 API）
  config/              # 环境与配置
  layouts/             # 布局（底部导航等）
  modules/             # 业务模块（home/monitor/upload/rehab/profile）
  shared/              # 可复用组件、工具函数、类型定义
  stores/              # Pinia stores
  styles/              # Tailwind/主题样式
server/
  index.js             # 后端 API（MySQL）
  sql/                 # 初始化建表与示例数据
```

## 本地运行

```bash
npm i
npm run dev
```

可选：类型检查

```bash
npm run typecheck
```

## 如何对接后端

前端：
- 在 `.env` 中配置 `VITE_API_BASE_URL`。开发期建议保持 `/api`（由 Vite 代理到后端）。
- 生产环境可改成你的反向代理地址，例如 `https://api.example.com/api`。

后端（MySQL + Express 示例）：
1. 初始化数据库。

```bash
mysql -uroot -p123456 < server/sql/init.sql
```

2. 安装依赖并启动服务。

```bash
cd server
npm install
npm run dev
```

监测数据来自 `monitor_records` 表，可自行插入或导入业务数据；`init.sql` 已包含首页/康复/设置等示例数据。

默认端口为 `3001`，数据库默认 `root/123456`、`health_monitoring`，可在 `server/.env` 覆盖。

默认示例用户为 `user_profiles.id = 1`，可直接在数据库中调整姓名/邮箱/风险评分等字段。
认证示例账号：`liming@example.com` / `123456`（可在 `auth_users` 表中修改）。

## 已接入真实接口的页面

- 总览（Home）：`/api/home/summary`
- 监测（Monitor）：`/api/monitor/latest`、`/api/monitor/trends`
- 上传（Upload）：`/api/analyze/tasks`、`/api/analyze/tasks/:taskId`
- 上传页新增「用药与保健品提醒」表单（目前仅前端展示与拍照预览，OCR/YOLO 端口预留）
- 用药提醒（Medication）：`/api/medications`、`/api/medications/:id`、`/api/medications/:id/toggle`（支持新增/编辑/暂停/删除）
- 康复（Rehab）：`/api/rehab/plan`、`/api/rehab/plan/:id/toggle`（页面主列表）
- 我的（Profile/Settings）：`/api/profile/summary`、`/api/profile/settings`、`/api/profile/avatar`
- 认证（Auth）：`/api/auth/register`、`/api/auth/login`、`/api/auth/me`、`/api/auth/logout`

说明：
- 报告/影像文件不建议直接写入数据库，建议放对象存储或模型服务中，只在数据库保存元信息与分析结果。
- 若当天没有训练计划，后端会自动用最近一次计划（或 `rehab_exercises` 表前几条动作）生成当天计划，确保卡片可展示与可打卡。
- 本次更新新增 `auth_users` / `auth_sessions` 表，注册与登录信息会写入数据库；如你已初始化过数据库，请重新执行 `server/sql/init.sql`。
  - 后端启动时会自动补建 `auth_users` / `auth_sessions`，无需清库也可直接注册。
