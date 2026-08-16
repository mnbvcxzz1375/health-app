# DINO-SO-YOLO → YOLO26 蒸馏

## 目标
将 `local_medication_api/vendor/ultralytics/` 中的 DINO-SO-YOLO（teacher）蒸馏到 YOLO26n（student），实现参数量与推理延迟大幅下降，同时保持 mAP 损失 < 5%。

## 环境隔离

Teacher 与 Student 使用**不同的 ultralytics 包**，必须分两步执行：

### Step 1: 导出 Teacher 特征（使用 vendored 包）
```bash
# 设置 PYTHONPATH 指向 vendored ultralytics
export PYTHONPATH=$(pwd)/local_medication_api/vendor/ultralytics
python distillation/yolo26/dump_teacher_features.py \
  --teacher-weights local_medication_api/weights/yolov13n.pt \
  --teacher-cfg local_medication_api/vendor/ultralytics/cfg/models/v13/dfad-yolo.yaml \
  --data distillation/yolo26/data/medical-pills.yaml \
  --output distillation/yolo26/teacher_cache/
```

### Step 2: Student 蒸馏训练（使用标准 ultralytics 包）
```bash
# 取消 PYTHONPATH，确保使用 pip 安装的标准 ultralytics（含 YOLO26）
unset PYTHONPATH
pip install -r distillation/yolo26/requirements.txt
python distillation/yolo26/distill_trainer.py \
  --student-cfg yolo26n.yaml \
  --data distillation/yolo26/data/medical-pills.yaml \
  --teacher-cache distillation/yolo26/teacher_cache/ \
  --epochs 100 --imgsz 640 --batch 32 --device 0
```

### Step 3: 性能对比
```bash
python distillation/yolo26/benchmark.py \
  --teacher local_medication_api/weights/yolov13n.pt \
  --student runs/distill/yolo26n/weights/best.pt \
  --data distillation/yolo26/data/medical-pills.yaml
```

## 蒸馏策略

详见 `distillation_trainer.py` 顶部的 docstring。简要：

- **损失组合**：L = L_task + α·L_logit + β·L_feat
- **L_task**：YOLO26 原生检测损失（box + cls，无 dfl）
- **L_logit**：KL 耗散（温度 T=4.0）
- **L_feat**：FGD-style 特征蒸馏（P3/P4/P5 三尺度 MSE + 注意力门控）
- **权重调度**：前 30 epochs β=0.5，后 70 epochs β=0.1

## 验收指标

| 指标 | Teacher (DINO-SO-YOLO) | Student (YOLO26n) | 目标 |
|------|------------------------|-------------------|------|
| mAP@0.5 | 基准 | ≥ 基准 × 0.95 | 损失 < 5% |
| 参数量 | ~25M (n) / ~600M (含 DINOv3) | < 12M | 减少 > 80% |
| 推理延迟 (4090) | 基准 | ≤ 基准 × 0.5 | 提升 ≥ 2× |

## 关键风险

1. **DINOv3 加载失败**：如 `dinov3_vitb16` 无法从 HF/PyTorch Hub 加载，回退到 `yolov13-dino2.yaml`（DINOv2 更稳定）
2. **Teacher .pt 与 cfg 不匹配**：默认 `weights/yolov13n.pt` 是 YOLOv13n（无 DINO），如需 DINO teacher 需先训练
3. **YOLO26 无 NMS**：teacher 输出需关闭 NMS 以获得 raw logits，student 推理时也跳过 NMS
