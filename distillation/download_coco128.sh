#!/bin/bash
# 下载 coco128 数据集并配置 YOLO26 蒸馏
# coco128 是 COCO 的 128 张子集，约 100MB，适合验证蒸馏 pipeline
set -e

PY=/home/chenyu/miniconda3/envs/vllmenv/bin/python
DATASETS_DIR=/data/hyc/datasets

echo "===== 1. 下载 coco128 数据集 ====="
cd "$DATASETS_DIR"
if [ -d "coco128" ]; then
    echo "[SKIP] coco128 已存在"
else
    # 用 ultralytics 自动下载
    $PY -c "
from ultralytics import YOLO
# 触发 coco128 下载（用 YOLO 加载会自动下载到 datasets_dir）
import yaml
cfg = {'path': '../datasets/coco128', 'train': 'images/train2017', 'val': 'images/train2017', 'names': {i: n for i, n in enumerate(['person','bicycle','car','motorcycle','airplane','bus','train','truck','boat','traffic light','fire hydrant','stop sign','parking meter','bench','bird','cat','dog','horse','sheep','cow','elephant','bear','zebra','giraffe','backpack','umbrella','handbag','tie','suitcase','frisbee','skis','snowboard','sports ball','kite','baseball bat','baseball glove','skateboard','surfboard','tennis racket','bottle','wine glass','cup','fork','knife','spoon','bowl','banana','apple','sandwich','orange','broccoli','carrot','hot dog','pizza','donut','cake','chair','couch','potted plant','bed','dining table','toilet','tv','laptop','mouse','remote','keyboard','cell phone','microwave','oven','toaster','sink','refrigerator','book','clock','vase','scissors','teddy bear','hair drier','toothbrush'])}}
with open('/tmp/coco128.yaml', 'w') as f:
    yaml.safe_dump(cfg, f)
print('[INFO] coco128.yaml 已生成')
# 用 YOLO 触发下载
m = YOLO('yolo11n.pt')  # 占位用 yolo11n 触发 ultralytics 初始化
" 2>&1 | tail -10

    # 直接下载 coco128.zip
    if [ ! -d "coco128" ]; then
        echo "[INFO] 直接下载 coco128.zip..."
        wget -q "https://ultralytics.com/assets/coco128.zip" -O coco128.zip
        unzip -q coco128.zip
        rm coco128.zip
        echo "[INFO] coco128 下载解压完成"
    fi
fi

echo ""
echo "===== 2. 验证 coco128 结构 ====="
ls -la "$DATASETS_DIR/coco128/" 2>&1 | head -10
echo ""
echo "图片数量:"
find "$DATASETS_DIR/coco128" -name "*.jpg" 2>/dev/null | wc -l
echo "标签数量:"
find "$DATASETS_DIR/coco128" -name "*.txt" 2>/dev/null | wc -l

echo ""
echo "===== 3. 生成 coco128.yaml ====="
cat > /data/hyc/distillation/yolo26/data/coco128.yaml << 'EOF'
# COCO128 数据集（用于 YOLO26 蒸馏 pipeline 验证）
# teacher yolov13n.pt 是 COCO 80 类预训练模型

path: ../datasets/coco128  # dataset root dir
train: images/train2017   # train images
val: images/train2017     # val images (coco128 无独立 val，用 train 代替)
test:                     # test images (optional)

# Classes (COCO 80 类)
names:
  0: person
  1: bicycle
  2: car
  3: motorcycle
  4: airplane
  5: bus
  6: train
  7: truck
  8: boat
  9: traffic light
  10: fire hydrant
  11: stop sign
  12: parking meter
  13: bench
  14: bird
  15: cat
  16: dog
  17: horse
  18: sheep
  19: cow
  20: elephant
  21: bear
  22: zebra
  23: giraffe
  24: backpack
  25: umbrella
  26: handbag
  27: tie
  28: suitcase
  29: frisbee
  30: skis
  31: snowboard
  32: sports ball
  33: kite
  34: baseball bat
  35: baseball glove
  36: skateboard
  37: surfboard
  38: tennis racket
  39: bottle
  40: wine glass
  41: cup
  42: fork
  43: knife
  44: spoon
  45: bowl
  46: banana
  47: apple
  48: sandwich
  49: orange
  50: broccoli
  51: carrot
  52: hot dog
  53: pizza
  54: donut
  55: cake
  56: chair
  57: couch
  58: potted plant
  59: bed
  60: dining table
  61: toilet
  62: tv
  63: laptop
  64: mouse
  65: remote
  66: keyboard
  67: cell phone
  68: microwave
  69: oven
  70: toaster
  71: sink
  72: refrigerator
  73: book
  74: clock
  75: vase
  76: scissors
  77: teddy bear
  78: hair drier
  79: toothbrush
EOF

echo "[INFO] coco128.yaml 已生成: /data/hyc/distillation/yolo26/data/coco128.yaml"
echo ""
echo "===== DONE ====="
