# 康复智伴能力—证据矩阵（代码版）

> 这张表只标记当前仓库能复现的工程证据；“专家/临床验证”不由代码测试替代。

| 能力/主张 | 代码证据 | 自动化证据 | 当前边界 |
|---|---|---|---|
| 用户级健康问答 | `backend-java/src/main/java/com/ahealth/backend/consult/ConsultService.java`；`健康监测与分析平台/src/modules/assistant/views/AssistantPage.vue` | Consult safety contract tests；前端 Assistant tests | 需要真实盲测和专家评分，不宣称医学准确率 |
| 证据与安全升级 | `ConsultSafetyService`、`ConsultDtos.SafetyInfo`、RAG evidence mapping | 9 个垂域契约单测；6 个数据卡病例 | 合成病例只验证字段契约 |
| 统一康复病例/保守计划 | `RehabCaseService`、`SmartRehabPlannerService`、`/api/rehab/case` | Rehab service/controller tests | 动作安全性仍需康复人员复核 |
| 饮食多模态识别 | `FoodRecognitionService`：视觉候选→食物目录校准 | Food recognition tests；前端 upload/diet tests | 未命中目录时不计算营养；估计分量不是称量事实 |
| 饮食记录可追溯 | `DietLogService`、`diet_log_audits`、FoodCameraPage 更正/删除入口 | DietLogService 的新增/更正/删除审计测试 | 审计保证可追溯，不证明营养估计正确 |
| 多源监测与用户隔离 | `MonitorService`、`DeviceService`、`ContextService`、设备聚合模块 | 后端全量测试；前端 Home/monitor tests | 真机 HealthKit/BLE/OAuth 需部署凭据和原生桥 |
| RAG 知识库 | `RagIngestionService`、`RagSearchService`、Redis/InMemory repository | RAG/query tests；契约评测工具 | Redis/embedding 服务不可用时仅安全降级 |
| 数据卡和三臂评测 | `evaluation/health_vertical` 的 data card、split、compare 工具 | 9 tests；6 cases validation | 当前公开样本是 synthetic fixture，真实 blind set 尚未冻结 |

## 当前回归快照

- 后端：91 tests passed。
- 前端：15 files / 42 tests passed，typecheck 和 production build passed。
- 垂域评测：9 tests passed，6 cases satisfy the data-card contract。
