#!/bin/bash
# OpenMed-PII → DistilBERT-chinese 蒸馏完整流程
# 在 3080 (20GB) 服务器上执行
#
# 用法:
#   bash distillation/pii/run_distill.sh
#
# 前置条件:
#   1. PyTorch + CUDA 已安装
#   2. transformers>=4.40.0 已安装
#   3. teacher 模型可通过 HuggingFace 下载（OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1）
#   4. bert-base-chinese 可通过 HuggingFace 下载
#   5. 项目根目录为当前工作目录

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

TEACHER_MODEL="${TEACHER_MODEL:-OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1}"
STUDENT_INIT="${STUDENT_INIT:-bert-base-chinese}"
STUDENT_LAYERS="${STUDENT_LAYERS:-6}"
DATA_DIR="${DATA_DIR:-distillation/pii/data}"
OUTPUT_INTERMEDIATE="${OUTPUT_INTERMEDIATE:-distillation/pii/checkpoints/student_intermediate}"
OUTPUT_FINAL="${OUTPUT_FINAL:-distillation/pii/checkpoints/student_final}"
DEVICE="${DEVICE:-cuda:0}"
DEVICE_IDX="${DEVICE_IDX:-0}"  # nvidia-smi 索引（cuda:0 → 0）
EPOCHS_INTERMEDIATE="${EPOCHS_INTERMEDIATE:-300}"
EPOCHS_PREDICTION="${EPOCHS_PREDICTION:-100}"
LR="${LR:-5e-5}"
TEMPERATURE="${TEMPERATURE:-4.0}"
SYNTH_COUNT="${SYNTH_COUNT:-8000}"
PSEUDO_COUNT="${PSEUDO_COUNT:-0}"  # 0 表示仅用合成数据；>0 启用 teacher 伪标注

# ============================================================
# GPU 显存检测：自动选择 batch size
#   teacher (Qwen 600M) + student (DistilBERT 66M) 同时驻留显存
#   ≥18GB → batch 16（3080 20G / 3090 / 4090）
#   ≥10GB → batch 8（3080 10G/12G）
#   <10GB → batch 4（保守，避免 OOM）
# 用户可通过 BATCH=xx 环境变量强制覆盖
# ============================================================
if [ -z "${BATCH:-}" ]; then
    GPU_MEM_MIB=$(nvidia-smi --query-gpu=memory.total --format=csv,noheader,nounits -i "$DEVICE_IDX" 2>/dev/null | head -n1 || echo "0")
    GPU_MEM_MIB=${GPU_MEM_MIB//[^0-9]/}
    if [ -z "$GPU_MEM_MIB" ] || [ "$GPU_MEM_MIB" -le 0 ]; then
        echo "[WARN] 无法读取 GPU 显存，使用保守 batch=4"
        BATCH=4
    elif [ "$GPU_MEM_MIB" -ge 18000 ]; then
        BATCH=16
    elif [ "$GPU_MEM_MIB" -ge 10000 ]; then
        BATCH=8
    else
        BATCH=4
    fi
    echo "[INFO] 检测到 GPU 显存 ${GPU_MEM_MIB} MiB，自动选择 batch=$BATCH"
else
    echo "[INFO] 使用用户指定 batch=$BATCH"
fi

echo "=================================================="
echo " OpenMed-PII → DistilBERT-chinese 蒸馏流程"
echo "=================================================="
echo "Teacher:        $TEACHER_MODEL"
echo "Student init:   $STUDENT_INIT (前 $STUDENT_LAYERS 层)"
echo "Device:         $DEVICE | Batch: $BATCH"
echo "Intermediate:   $EPOCHS_INTERMEDIATE epochs"
echo "Prediction:     $EPOCHS_PREDICTION epochs (T=$TEMPERATURE)"
echo "Synth count:    $SYNTH_COUNT | Pseudo count: $PSEUDO_COUNT"
echo ""

# ============================================================
# Step 1: 数据准备
# ============================================================
echo "[Step 1/4] 数据准备..."
if [ ! -f "$DATA_DIR/train.conll" ]; then
    if [ "$PSEUDO_COUNT" -gt 0 ]; then
        "$PY_BIN" distillation/pii/prepare_data.py \
            --teacher "$TEACHER_MODEL" \
            --source backend-java/src/main/java/com/ahealth/backend/consult/ \
            --output "$DATA_DIR/" \
            --synth-count "$SYNTH_COUNT" \
            --teacher-pseudo-count "$PSEUDO_COUNT" \
            --device "$DEVICE"
    else
        "$PY_BIN" distillation/pii/prepare_data.py \
            --output "$DATA_DIR/" \
            --synth-count "$SYNTH_COUNT"
    fi
else
    echo "[Step 1/4] 数据已存在，跳过准备"
fi

# ============================================================
# Step 2: Stage 1-3 中间层蒸馏
# ============================================================
echo ""
echo "[Step 2/4] Stage 1-3 中间层蒸馏..."
"$PY_BIN" distillation/pii/distill_trainer.py \
    --stage intermediate \
    --teacher "$TEACHER_MODEL" \
    --student-init "$STUDENT_INIT" \
    --student-layers "$STUDENT_LAYERS" \
    --data "$DATA_DIR/" \
    --output "$OUTPUT_INTERMEDIATE/" \
    --epochs "$EPOCHS_INTERMEDIATE" \
    --batch "$BATCH" \
    --lr "$LR" \
    --device "$DEVICE"

# ============================================================
# Step 3: Stage 4 预测层蒸馏
# ============================================================
echo ""
echo "[Step 3/4] Stage 4 预测层蒸馏..."
"$PY_BIN" distillation/pii/distill_trainer.py \
    --stage prediction \
    --teacher "$TEACHER_MODEL" \
    --student "$OUTPUT_INTERMEDIATE/" \
    --data "$DATA_DIR/" \
    --output "$OUTPUT_FINAL/" \
    --epochs "$EPOCHS_PREDICTION" \
    --batch "$BATCH" \
    --lr "$LR" \
    --temperature "$TEMPERATURE" \
    --device "$DEVICE"

# ============================================================
# Step 4: 评估对比
# ============================================================
echo ""
echo "[Step 4/4] 性能对比 benchmark..."
"$PY_BIN" distillation/pii/benchmark.py \
    --teacher "$TEACHER_MODEL" \
    --student "$OUTPUT_FINAL/" \
    --data "$DATA_DIR/" \
    --device "$DEVICE" \
    --output distillation/pii/results/

echo ""
echo "=================================================="
echo " 蒸馏完成"
echo "=================================================="
echo "Student model:  $OUTPUT_FINAL/"
echo "Benchmark:      distillation/pii/results/benchmark.csv"
echo ""
echo "下一步：将 student_final 复制到 backend-java/models/OpenMed-PII-DistilBERT-chinese/"
echo "并设置环境变量 OPENMED_PII_USE_TEACHER=false 启动 openmed-inference-service"
