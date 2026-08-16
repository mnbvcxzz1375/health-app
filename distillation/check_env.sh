#!/bin/bash
# 3080 服务器环境检查脚本
echo "=== /data/hyc 目录结构 ==="
ls -la /data/hyc/
echo
echo "=== distillation 结构 ==="
find /data/hyc/distillation -maxdepth 2 -type d 2>/dev/null
echo
echo "=== bone_age 结构 ==="
ls /data/hyc/bone_age/ 2>/dev/null
echo
echo "=== Python ==="
python3 --version 2>&1
which python3
echo
echo "=== PyTorch / CUDA ==="
python3 -c "import torch; print('torch=', torch.__version__); print('cuda=', torch.cuda.is_available()); print('devices=', torch.cuda.device_count()); [print(f'  GPU{i}: {torch.cuda.get_device_name(i)} {torch.cuda.get_device_properties(i).total_memory/1024**3:.1f}GB') for i in range(torch.cuda.device_count())]" 2>&1
echo
echo "=== ultralytics（YOLO26 student 需要）==="
python3 -c "import ultralytics; print('ultralytics=', ultralytics.__version__)" 2>&1
echo
echo "=== transformers（PII 蒸馏需要）==="
python3 -c "import transformers; print('transformers=', transformers.__version__)" 2>&1
echo
echo "=== timm（骨龄训练需要）==="
python3 -c "import timm; print('timm=', timm.__version__)" 2>&1
echo
echo "=== 磁盘空间 ==="
df -h /data/hyc
echo
echo "=== /data/hyc 下是否已有 vendor/ultralytics ==="
ls -la /data/hyc/local_medication_api/vendor/ultralytics 2>&1 | head -5
echo
echo "=== /data/hyc 下是否已有 weights ==="
ls -la /data/hyc/local_medication_api/weights 2>&1 | head -5
