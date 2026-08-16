# 骨龄检测 + 模型蒸馏 实施计划

> 范围：用户提出的 4 个问题
> 1. 骨头 CT/X 光照片的骨龄检测
> 2. DINO-SO-YOLO → YOLO26 蒸馏
> 3. OpenMed-PII 隐私模型蒸馏（含选型）
> 4. 具体蒸馏策略

---

## 一、当前实现状态盘点

### 1.1 骨龄检测 ✅ 基本完成（仅前端集成有 TS 错误）

**模型层**（`bone_age/`）
- `train.py`：ResNet50 backbone + 单通道 + Sigmoid 回归头（输出 0-19 岁），支持 timm 与 torchvision 双后端
- `inference_service.py`：FastAPI 服务（port 8013），暴露 `/bone-age/estimate`
- 数据基础：RSNA Pediatric Bone Age 手腕 X 光数据集（详见 train.py 注释）
- 注意：当前实现针对**左手腕 X 光片**，不是 CT。CT 不适合骨龄评估（放射性剂量大、解剖结构复杂、公开数据集稀缺），医学界标准是手腕 X 光

**后端 Java**（`backend-java/.../boneage/`）
- `BoneAgeController`：`POST /api/bone-age/estimate`、`GET /api/bone-age/recent`
- `BoneAgeService`：本地推理优先 + DashScope Vision LLM 兜底，结果写入 `bone_age_tasks` 表
- `BackendSchemaInitializer`：新增 `bone_age_tasks` 表
- `UploadService.createTaskByCustomModel`：路由 `type=bone` 到 BoneAgeService
- `UploadDtos.CustomModelTaskResponse`：扩展响应 DTO
- `OpenMedService`：新增 `piiStudentModel` 配置项

**前端**（`健康监测与分析平台/src/.../upload/`）
- `api/modules/upload.ts`：`estimateBoneAge()` + `listRecentBoneAgeTasks()` + mock
- `views/BoneAgeResultCard.vue`：结果卡片组件，展示骨龄/置信度/骨骺分期/异常指标/免责声明
- `views/UploadPage.vue`：**模板已集成 bone 类型 + BoneAgeResultCard，但 script setup 部分未补齐**
  - `UploadType` 类型缺 `'bone'`
  - `typeOptions` 数组缺 bone 选项
  - 缺 `boneAgeResult / boneAgeSource / boneAgeEstimatedAt` 状态变量
  - 缺 `viewBoneAgeHistory` 方法
  - 缺 `submit()` 中对 bone 的同步调用分支

**待修复 TS 错误**（5 个，全部来自 UploadPage.vue 第 195-207 行）
```
TS2367: '"bone"' 与 UploadType 无交集
TS2339: Property 'boneAgeResult' does not exist (×3)
TS2339: Property 'viewBoneAgeHistory' does not exist
```

### 1.2 DINO-SO-YOLO → YOLO26 蒸馏 ✅ 完成

**目录**：`distillation/yolo26/`

| 文件 | 用途 |
|------|------|
| `requirements.txt` | 标准 ultralytics>=8.3.0（含 YOLO26）+ torch 等 |
| `README.md` | 流程文档（环境隔离/3 步流程/策略/验收指标/风险） |
| `data/medical-pills.yaml` | 数据集配置（与 teacher .pt 类别对齐） |
| `dump_teacher_features.py` | 用 vendored ultralytics 导出 teacher P3/P4/P5 特征 + 预测框 |
| `distill_trainer.py` | Student 蒸馏训练器，两阶段（标准训练 + 软标签微调） |
| `benchmark.py` | 对比 mAP / 参数量 / FLOPs / 延迟 / 模型大小 |
| `run_distill.sh` | 一键执行（4090 服务器） |

**集成点**
- `local_medication_api/config.py`：新增 `DEFAULT_DISTILLED_WEIGHTS_PATH` + `use_distilled` 开关
- `local_medication_api/services/detector.py`：根据 `use_distilled` 动态切换 ultralytics 包（vendored vs 标准）

### 1.3 OpenMed-PII → DistilBERT-chinese 蒸馏 ✅ 完成

**目录**：`distillation/pii/`

| 文件 | 用途 |
|------|------|
| `requirements.txt` | transformers>=4.40.0 / datasets / seqeval 等 |
| `data/README.md` | 10 类 PII 标签集 + 数据来源 + CoNLL 格式说明 |
| `prepare_data.py` | 合成数据生成 + teacher 伪标注 + CoNLL 转换 |
| `distill_trainer.py` | TinyBERT 4 阶段蒸馏训练器 |
| `benchmark.py` | F1 / Precision / Recall / 参数量 / 延迟 |
| `run_distill.sh` | 一键执行 |

**集成点**
- `openmed-inference-service/app.py`：新增 `OPENMED_PII_USE_TEACHER` 环境变量切换 teacher/student
- `backend-java/.../ai/OpenMedService.java`：新增 `piiStudentModel` 配置

---

## 二、蒸馏选型与策略详解（针对用户第 3、4 问）

### 2.1 DINO-SO-YOLO → YOLO26 策略

**为什么选 YOLO26 作为 student？**
- YOLO26 是 ultralytics ≥8.3 内置的最新版，**端到端无 NMS**（基于 OneNet 思路），延迟显著低于 YOLOv8/v13
- 与 teacher 同为 YOLO 系，特征图结构（P3/P4/P5）对齐，便于特征蒸馏
- YOLO26n 仅 ~12M 参数，对比 DINO-SO-YOLO ~25M（n）或含 DINOv3 ~600M，参数下降 80%+

**蒸馏损失组合**
```
L_total = L_task + α · L_logit + β · L_feat
```
- `L_task`：student 原生检测损失（box + cls，YOLO26 无 dfl）
- `L_logit`：KL 耗散（T=4.0），只对 teacher 与 student 共有预测框做 KL（基于 IoU 匹配）
- `L_feat`：FGD-style 特征蒸馏（P3/P4/P5 三尺度 MSE + teacher 通道均值作注意力门）

**权重调度（两阶段）**
| 阶段 | Epochs | α | β | 目的 |
|------|--------|---|---|------|
| Warmup | 0-30 | 1.0 | 0.5 | 强特征蒸馏，让 student 学到 teacher 表达 |
| Finetune | 30-100 | 1.0 | 0.1 | task loss 主导收敛，避免过拟合 teacher |

**环境隔离方案（关键）**
- Teacher（DINO-SO-YOLO）必须用 vendored 包：`local_medication_api/vendor/ultralytics/`
- Student（YOLO26）必须用标准 pip 包：`ultralytics>=8.3.0`
- 通过 `PYTHONPATH` 切换，避免包冲突
- 流程：先 `dump_teacher_features.py` 离线导出特征 → 再 `distill_trainer.py` 训练 student（不直接加载 teacher）

**验收指标**
| 指标 | Teacher | Student | 目标 |
|------|---------|---------|------|
| mAP@0.5 | 基准 | ≥ 基准×0.95 | 损失<5% |
| 参数量 | ~25M | <12M | ↓80% |
| 延迟 (4090) | 基准 | ≤基准×0.5 | ↑2× |

### 2.2 OpenMed-PII → DistilBERT-chinese 策略

**为什么选 DistilBERT-chinese 作为 student？**
| 候选 | 参数量 | 中文支持 | 适配 NER | 评估 |
|------|--------|---------|---------|------|
| DistilBERT-chinese (6L) | ~66M | ✅ WordPiece | ✅ token-classification | **首选**：原生支持 NER，与 teacher 同为 Transformer，便于中间层蒸馏 |
| BERT-tiny-chinese (2L) | ~6M | ✅ | ✅ | 极致轻量但 F1 损失大（预计 >10%） |
| TextCNN | ~2M | ✅ | ⚠️ | 不擅长序列标注，PII 跨 token 实体多 |
| Qwen-0.5B | ~500M | ✅ BPE | ✅ | 仍偏大，与 teacher 同架构但压缩比仅 1.2× |

**结论**：DistilBERT-chinese（6 层，从 bert-base-chinese 截取前 6 层初始化）在性能/压缩比/实现难度上最优。

**TinyBERT 4 阶段蒸馏策略**
| Stage | 损失 | 数据需求 | Epochs |
|-------|------|---------|--------|
| 1. Embedding | MSE(student_embed, teacher_embed) | 无需标签 | 300 |
| 2. Hidden | Σ MSE(student_i, proj(teacher_j)) | 无需标签 | (合并 1) |
| 3. Attention | Σ KL(teacher_attn, student_attn) | 无需标签 | (合并 1) |
| 4. Prediction | CE(true) + KL(teacher/T, student/T)·T² | **需真实标签** | 100 |

**层映射**：teacher 24 层 → student 6 层，每 4 层映射 1 层
```
student_0 ← teacher_{0,1,2,3}
student_1 ← teacher_{4,5,6,7}
...
student_5 ← teacher_{20,21,22,23}
```

**Tokenizer 对齐**：必须用 teacher tokenizer（Qwen BPE）分词，student 通过 embedding projection 对齐

**温度**：T=4.0（PII 软标签分布较尖锐，T 偏高让分布更软便于学习）

**数据策略**
- 主力：teacher 伪标注（用 OpenMed-PII 给 backend-java/consult 模块的医疗咨询文本打标）
- 补充：合成数据（模板生成姓名/电话/身份证/地址等）
- 可选：CLUE CMeEE / CCKS 公开数据集（需标签映射）

**验收指标**
| 指标 | Teacher (Qwen 600M) | Student (DistilBERT 66M) | 目标 |
|------|---------------------|--------------------------|------|
| F1 (entity-level) | 基准 | ≥ 基准×0.92 | 损失<8% |
| 参数量 | ~600M | ~66M | ↓89% |
| 延迟 (4090) | 基准 | ≤基准×0.3 | ↑3×+ |

---

## 三、剩余待执行工作

### 3.1 修复 UploadPage.vue 骨龄评估集成 TS 错误（5 个错误）

**修改文件**：`健康监测与分析平台/src/modules/upload/views/UploadPage.vue`

**修改点**：
1. `UploadType` 类型加上 `'bone'`：
   ```ts
   type UploadType = 'image' | 'lab' | 'text' | 'symptom' | 'bone' | null
   ```
2. `typeOptions` 数组新增 bone 选项：
   ```ts
   { key: 'bone' as const, label: '骨龄评估', hint: '左手腕 X 光片', icon: 'solar:bone-outline' },
   ```
3. 导入 BoneAgeResultCard 组件 + 骨龄 API：
   ```ts
   import BoneAgeResultCard from './BoneAgeResultCard.vue'
   import { estimateBoneAge, type BoneAgeResult, type BoneAgeEstimateResponse } from '@/api/modules/upload'
   ```
4. 新增状态变量：
   ```ts
   const boneAgeResult = ref<BoneAgeResult | null>(null)
   const boneAgeSource = ref<string>('')
   const boneAgeEstimatedAt = ref<string>('')
   ```
5. `isFileType` computed 加上 bone（因为骨龄需上传图片）：
   ```ts
   const isFileType = computed(() => ['image','lab','bone'].includes(uploadType.value ?? ''))
   ```
6. `submit()` 新增 bone 分支：骨龄走同步调用，不走创建任务+轮询流程
7. `clearResult()` 清空 boneAge 相关状态
8. `viewBoneAgeHistory()` 方法：跳转到历史记录页（或先 toast 提示）

### 3.2 验证

```powershell
cd 健康监测与分析平台; npm run typecheck
```
应输出 0 errors。

### 3.3 不在本次范围

- 实际跑蒸馏训练（需 4090 服务器 + 数据集 + teacher 权重，已在 run_distill.sh 中提供一键脚本，用户在服务器执行即可）
- 骨龄模型实际训练（需 RSNA 数据集 + GPU，train.py 已就绪）
- CT 骨龄评估（医学上不推荐，未实现）

---

## 四、执行顺序

1. 修复 UploadPage.vue TS 错误（5 个错误）→ 新增 bone 状态/类型/方法
2. 跑 `npm run typecheck` 确认通过
3. 完成

**预计改动文件**：1 个（UploadPage.vue）
**预计新增代码**：约 30-50 行
