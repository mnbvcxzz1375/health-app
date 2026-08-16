#!/bin/bash
# 在已存在的 conda vllmenv 环境中补装蒸馏所需依赖
# 用户偏好：使用 vllmenv 而非新建 distill 环境
set -e

PIP=/home/chenyu/miniconda3/envs/vllmenv/bin/pip
PY=/home/chenyu/miniconda3/envs/vllmenv/bin/python
LOG=/data/hyc/setup_vllmenv.log

echo "===== 补装蒸馏依赖到 vllmenv =====" > "$LOG"
echo "Python: $($PY --version)" >> "$LOG"
echo "" >> "$LOG"

# ============================================================
# Step 1: ultralytics + 辅助库（YOLO26 蒸馏需要）
# ============================================================
echo "[1/3] ultralytics + 辅助库..." >> "$LOG"
$PIP install "ultralytics>=8.3.0" pyyaml matplotlib 2>&1 | tail -5 >> "$LOG"
echo "" >> "$LOG"

# ============================================================
# Step 2: transformers 生态（PII 蒸馏需要）
# ============================================================
echo "[2/3] datasets / seqeval / scikit-learn..." >> "$LOG"
$PIP install datasets seqeval scikit-learn 2>&1 | tail -5 >> "$LOG"
echo "" >> "$LOG"

# ============================================================
# Step 3: 骨龄训练 + OCR 依赖
# ============================================================
echo "[3/3] timm / pydicom..." >> "$LOG"
$PIP install "timm>=0.9.0" pydicom 2>&1 | tail -5 >> "$LOG"
echo "" >> "$LOG"

# ============================================================
# 验证
# ============================================================
echo "===== 验证 =====" >> "$LOG"
$PY -c "
import torch, transformers
print(f'torch={torch.__version__} cuda_available={torch.cuda.is_available()} device_count={torch.cuda.device_count()}')
for i in range(torch.cuda.device_count()):
    print(f'  GPU{i}: {torch.cuda.get_device_name(i)} {torch.cuda.get_device_properties(i).total_memory/1024**3:.1f}GB')
print(f'transformers={transformers.__version__}')
import ultralytics
print(f'ultralytics={ultralytics.__version__}')
import datasets, seqeval, sklearn, timm, fastapi, pydicom
print(f'datasets={datasets.__version__}')
print(f'timm={timm.__version__}')
print(f'fastapi={fastapi.__version__}')
print('ALL OK')
" >> "$LOG" 2>&1

echo "" >> "$LOG"
echo "===== DONE =====" >> "$LOG"
echo "vllmenv 环境已就绪，可直接运行蒸馏脚本" >> "$LOG"
