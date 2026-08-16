#!/bin/bash
# 3080 服务器环境初始化脚本（conda 版）
# 在 /home/chenyu/miniconda3/envs/distill 创建环境
set -e

CONDA_BIN="/home/chenyu/miniconda3/bin/activate"
ENV_NAME="distill"
PROJECT_ROOT="/data/hyc"
LOG_FILE="$PROJECT_ROOT/setup_env.log"

echo "===== 3080 服务器环境初始化（conda 版）=====" | tee "$LOG_FILE"
echo "conda 环境: $ENV_NAME" | tee -a "$LOG_FILE"
echo "项目根目录: $PROJECT_ROOT" | tee -a "$LOG_FILE"
echo

# ============================================================
# Step 1: 激活 conda + 创建环境
# ============================================================
echo "[Step 1/6] 创建 conda 环境 $ENV_NAME..." | tee -a "$LOG_FILE"
source "$CONDA_BIN"
conda env list | tee -a "$LOG_FILE"

if conda env list | grep -q "^$ENV_NAME "; then
    echo "[Step 1 跳过] 环境 $ENV_NAME 已存在" | tee -a "$LOG_FILE"
else
    conda create -n "$ENV_NAME" python=3.10 -y 2>&1 | tail -5 | tee -a "$LOG_FILE"
    echo "[Step 1 完成] 环境 $ENV_NAME 已创建" | tee -a "$LOG_FILE"
fi

conda activate "$ENV_NAME"
echo "[INFO] Python: $(python --version)" | tee -a "$LOG_FILE"
echo "[INFO] python 路径: $(which python)" | tee -a "$LOG_FILE"
echo

# ============================================================
# Step 2: 配置 pip 清华源（国内加速）
# ============================================================
echo "[Step 2/6] 配置 pip 清华源..." | tee -a "$LOG_FILE"
pip install --upgrade pip -i https://pypi.tuna.tsinghua.edu.cn/simple 2>&1 | tail -3 | tee -a "$LOG_FILE"
pip config set global.index-url https://pypi.tuna.tsinghua.edu.cn/simple 2>&1 | tee -a "$LOG_FILE"
pip config set install.trusted-host pypi.tuna.tsinghua.edu.cn 2>&1 | tee -a "$LOG_FILE"
echo "[Step 2 完成]" | tee -a "$LOG_FILE"
echo

# ============================================================
# Step 3: 安装 PyTorch CUDA 12.1（兼容 CUDA 13.0 driver）
# 3080 显存 20GB，cu121 完全兼容
# ============================================================
echo "[Step 3/6] 安装 PyTorch CUDA 12.1..." | tee -a "$LOG_FILE"
pip install torch torchvision --index-url https://download.pytorch.org/whl/cu121 2>&1 | tail -5 | tee -a "$LOG_FILE"
echo "[Step 3 验证]" | tee -a "$LOG_FILE"
python -c "import torch; print(f'  torch={torch.__version__}'); print(f'  cuda_available={torch.cuda.is_available()}'); print(f'  device_count={torch.cuda.device_count()}'); [print(f'  GPU{i}: {torch.cuda.get_device_name(i)} {torch.cuda.get_device_properties(i).total_memory/1024**3:.1f}GB') for i in range(torch.cuda.device_count())]" 2>&1 | tee -a "$LOG_FILE"
echo

# ============================================================
# Step 4: 安装 YOLO26 蒸馏依赖
# ============================================================
echo "[Step 4/6] 安装 ultralytics + 辅助库..." | tee -a "$LOG_FILE"
pip install "ultralytics>=8.3.0" 2>&1 | tail -3 | tee -a "$LOG_FILE"
pip install pyyaml pandas matplotlib tqdm scipy numpy 2>&1 | tail -3 | tee -a "$LOG_FILE"
echo "[Step 4 验证]" | tee -a "$LOG_FILE"
python -c "import ultralytics; print(f'  ultralytics={ultralytics.__version__}')" 2>&1 | tee -a "$LOG_FILE"
echo

# ============================================================
# Step 5: 安装 PII 蒸馏依赖
# ============================================================
echo "[Step 5/6] 安装 transformers / datasets / seqeval..." | tee -a "$LOG_FILE"
pip install "transformers>=4.40.0" datasets tokenizers accelerate 2>&1 | tail -3 | tee -a "$LOG_FILE"
pip install seqeval scikit-learn 2>&1 | tail -3 | tee -a "$LOG_FILE"
echo "[Step 5 验证]" | tee -a "$LOG_FILE"
python -c "import transformers; print(f'  transformers={transformers.__version__}')" 2>&1 | tee -a "$LOG_FILE"
python -c "import seqeval; print('  seqeval OK')" 2>&1 | tee -a "$LOG_FILE"
echo

# ============================================================
# Step 6: 安装骨龄训练依赖
# ============================================================
echo "[Step 6/6] 安装 timm / fastapi / pydicom..." | tee -a "$LOG_FILE"
pip install "timm>=0.9.0" 2>&1 | tail -3 | tee -a "$LOG_FILE"
pip install fastapi uvicorn pillow pydicom 2>&1 | tail -3 | tee -a "$LOG_FILE"
echo "[Step 6 验证]" | tee -a "$LOG_FILE"
python -c "import timm; print(f'  timm={timm.__version__}')" 2>&1 | tee -a "$LOG_FILE"
python -c "import fastapi; print(f'  fastapi={fastapi.__version__}')" 2>&1 | tee -a "$LOG_FILE"
echo

# ============================================================
# 汇总
# ============================================================
echo "===== 环境初始化完成 =====" | tee -a "$LOG_FILE"
echo "conda 环境名: $ENV_NAME" | tee -a "$LOG_FILE"
echo "激活方式: conda activate $ENV_NAME" | tee -a "$LOG_FILE"
echo "项目目录: $PROJECT_ROOT" | tee -a "$LOG_FILE"
echo
echo "运行 YOLO26 蒸馏:" | tee -a "$LOG_FILE"
echo "  cd /data/hyc && conda activate distill && bash distillation/yolo26/run_distill.sh" | tee -a "$LOG_FILE"
echo
echo "运行 PII 蒸馏:" | tee -a "$LOG_FILE"
echo "  cd /data/hyc && conda activate distill && bash distillation/pii/run_distill.sh" | tee -a "$LOG_FILE"
echo
echo "运行骨龄训练:" | tee -a "$LOG_FILE"
echo "  cd /data/hyc/bone_age && conda activate distill && python train.py --data /path/to/rsna --epochs 100 --device cuda:0" | tee -a "$LOG_FILE"
