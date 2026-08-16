#!/bin/bash
# 检查 CUDA / conda / venv 可用性
echo "=== nvidia-smi ==="
nvidia-smi | head -20
echo
echo "=== nvcc ==="
nvcc --version 2>&1 | head -5
echo
echo "=== conda ==="
which conda 2>&1
conda --version 2>&1
echo
echo "=== python3 venv ==="
python3 -m venv --help 2>&1 | head -3
echo
echo "=== /data/hyc 已上传文件确认 ==="
find /data/hyc/local_medication_api -maxdepth 3 -type f -name "*.py" -o -name "*.pt" -o -name "*.yaml" | head -20
echo
echo "=== distillation 主要文件 ==="
ls /data/hyc/distillation/yolo26/
echo "---"
ls /data/hyc/distillation/pii/
echo "---"
ls /data/hyc/bone_age/
echo
echo "=== 当前磁盘 ==="
df -h /data/hyc
