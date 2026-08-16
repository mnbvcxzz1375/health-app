#!/bin/bash
# DINO-SO-YOLO → YOLO26 蒸馏完整流程
# 在 3080 (20GB) 服务器上执行
#
# 用法:
#   bash distillation/yolo26/run_distill.sh
#
# 前置条件:
#   1. PyTorch + CUDA 已安装
#   2. 项目根目录为当前工作目录
#   3. teacher 权重已就位 (local_medication_api/weights/yolov13n.pt, COCO 80类)
#   4. 数据集已下载到 datasets/coco128/

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"

# ============================================================
# Python 环境：优先用 conda vllmenv，否则用系统 python
# ============================================================
if [ -x /home/chenyu/miniconda3/envs/vllmenv/bin/python ]; then
    export PATH="/home/chenyu/miniconda3/envs/vllmenv/bin:$PATH"
    PY_BIN="/home/chenyu/miniconda3/envs/vllmenv/bin/python"
else
    PY_BIN="python"
fi
echo "[INFO] 使用 Python: $PY_BIN ($($PY_BIN --version 2>&1))"

# ============================================================
# HuggingFace 镜像（国内服务器访问 hf.co 超时，改用 hf-mirror.com）
# ============================================================
export HF_ENDPOINT="https://hf-mirror.com"
export HF_HUB_ENABLE_HF_TRANSFER=0
echo "[INFO] HF_ENDPOINT=$HF_ENDPOINT"

TEACHER_WEIGHTS="local_medication_api/weights/yolov13n.pt"
TEACHER_CFG="local_medication_api/vendor/ultralytics/cfg/models/v13/yolov13-dino2.yaml"
# teacher 是 COCO 80 类预训练模型，用 coco128 跑通 pipeline
DATA_YAML="${DATA_YAML:-distillation/yolo26/data/coco128.yaml}"
TEACHER_CACHE="distillation/yolo26/teacher_cache"
DEVICE="${DEVICE:-0}"
IMGSZ="${IMGSZ:-640}"
EPOCHS="${EPOCHS:-100}"

# device 规范化：纯数字 → cuda:N（适配 dump_teacher_features.py）
if [[ "$DEVICE" =~ ^[0-9]+$ ]]; then
    CUDA_DEVICE="cuda:$DEVICE"
else
    CUDA_DEVICE="$DEVICE"
fi

# ============================================================
# GPU 显存检测：自动选择 batch size
#   ≥18GB → batch 24（3080 20G / 3090 / 4090 友好）
#   ≥10GB → batch 12（3080 10G / 3080 12G）
#   <10GB → batch 6（保守，避免 OOM）
# 用户可通过 BATCH=xx 环境变量强制覆盖
# ============================================================
if [ -z "${BATCH:-}" ]; then
    GPU_MEM_MIB=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits -i "${DEVICE}" 2>/dev/null | head -n1 || echo "0")
    GPU_MEM_MIB=${GPU_MEM_MIB//[^0-9]/}
    if [ -z "$GPU_MEM_MIB" ] || [ "$GPU_MEM_MIB" -le 0 ]; then
        echo "[WARN] 无法读取 GPU 显存，使用保守 batch=8"
        BATCH=8
    elif [ "$GPU_MEM_MIB" -ge 18000 ]; then
        BATCH=24
    elif [ "$GPU_MEM_MIB" -ge 10000 ]; then
        BATCH=12
    else
        BATCH=6
    fi
    echo "[INFO] 检测到 GPU 显存 ${GPU_MEM_MIB} MiB，自动选择 batch=$BATCH"
else
    echo "[INFO] 使用用户指定 batch=$BATCH"
fi

echo "================================================"
echo " DINO-SO-YOLO → YOLO26 蒸馏流程"
echo "================================================"
echo "项目根目录: $PROJECT_ROOT"
echo "Teacher 权重: $TEACHER_WEIGHTS"
echo "Teacher CFG: $TEACHER_CFG"
echo "Device: $CUDA_DEVICE | Imgsz: $IMGSZ | Batch: $BATCH | Epochs: $EPOCHS"
echo ""

# ============================================================
# Step 1: 导出 Teacher 特征（使用 vendored ultralytics）
# ============================================================
echo "[Step 1/3] 导出 Teacher 特征..."
PYTHONPATH="$PROJECT_ROOT/local_medication_api/vendor/ultralytics" \
    "$PY_BIN" distillation/yolo26/dump_teacher_features.py \
        --teacher-weights "$TEACHER_WEIGHTS" \
        --teacher-cfg "$TEACHER_CFG" \
        --data "$DATA_YAML" \
        --output "$TEACHER_CACHE" \
        --imgsz "$IMGSZ" \
        --batch "$BATCH" \
        --device "$CUDA_DEVICE"

echo "[Step 1 完成] Teacher 特征已导出到 $TEACHER_CACHE"
echo ""

# ============================================================
# Step 2: Student 蒸馏训练（使用标准 ultralytics，含 YOLO26）
# ============================================================
echo "[Step 2/3] Student 蒸馏训练..."
# 取消 PYTHONPATH，确保用 pip 安装的 ultralytics
unset PYTHONPATH
"$PY_BIN" -m pip install -q "ultralytics>=8.3.0" 2>/dev/null || true

"$PY_BIN" distillation/yolo26/distill_trainer.py \
    --student-cfg yolo11n.yaml \
    --student-weights yolo11n.pt \
    --data "$DATA_YAML" \
    --teacher-cache "$TEACHER_CACHE" \
    --epochs "$EPOCHS" \
    --imgsz "$IMGSZ" \
    --batch "$BATCH" \
    --device "$CUDA_DEVICE" \
    --alpha 1.0 \
    --beta 0.5 \
    --beta-warmup-epochs 30 \
    --temperature 4.0

echo "[Step 2 完成] Student 训练完成"
echo ""

# ============================================================
# Step 3: 性能对比
# ============================================================
echo "[Step 3/3] 性能对比 Benchmark..."
STUDENT_BEST="runs/distill/yolo26n_stage_b/weights/best.pt"
if [ ! -f "$STUDENT_BEST" ]; then
    STUDENT_BEST="runs/distill/yolo26n_stage_a/weights/best.pt"
fi

"$PY_BIN" distillation/yolo26/benchmark.py \
    --teacher "$TEACHER_WEIGHTS" \
    --student "$STUDENT_BEST" \
    --data "$DATA_YAML" \
    --device "$CUDA_DEVICE" \
    --imgsz "$IMGSZ"

echo ""
echo "================================================"
echo " 蒸馏流程全部完成！"
echo " 结果文件: distillation/yolo26/results/"
echo " Student 最佳权重: $STUDENT_BEST"
echo "================================================"
