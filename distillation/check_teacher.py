"""检查 teacher 权重类别信息
方案：提取 vendored nn/modules/*.py 中所有 class 定义，全部注入到 site-packages 对应模块
"""
import sys
import os
import re
import importlib
import warnings
warnings.filterwarnings("ignore")

VENDORED_ROOT = '/data/hyc/local_medication_api/vendor/ultralytics/nn/modules'
SITE_PKG_PREFIX = 'ultralytics.nn.modules'

# 需要处理的模块文件
MODULE_FILES = ['block.py', 'conv.py', 'head.py', 'transformer.py', 'utils.py']

for mod_file in MODULE_FILES:
    vendored_path = os.path.join(VENDORED_ROOT, mod_file)
    if not os.path.exists(vendored_path):
        continue
    mod_name = mod_file[:-3]  # 去掉 .py
    full_mod_name = f"{SITE_PKG_PREFIX}.{mod_name}"

    # import site-packages 的对应模块
    try:
        site_mod = importlib.import_module(full_mod_name)
    except Exception as e:
        print(f"[WARN] 无法 import {full_mod_name}: {e}")
        continue

    # 读取 vendored 源码
    with open(vendored_path, 'r', encoding='utf-8') as f:
        src = f.read()

    # 提取所有 class 定义
    pattern = r'(class \w+\b.*?)(?=\nclass |\Z)'
    classes = re.findall(pattern, src, re.DOTALL)

    injected = []
    failed = []
    for cls_src in classes:
        name_match = re.match(r'class (\w+)', cls_src)
        if not name_match:
            continue
        cls_name = name_match.group(1)
        if hasattr(site_mod, cls_name):
            continue
        try:
            exec(cls_src, site_mod.__dict__)
            injected.append(cls_name)
        except Exception as e:
            failed.append((cls_name, str(e)[:60]))

    print(f"[{mod_file}] 注入 {len(injected)} 个类: {injected[:15]}")
    if failed:
        print(f"  失败 {len(failed)} 个: {[n for n, _ in failed[:5]]}")

# Step 6: torch.load ckpt
import torch
path = '/data/hyc/local_medication_api/weights/yolov13n.pt'
print()
print("[INFO] 加载 teacher 权重...")
try:
    ckpt = torch.load(path, map_location='cpu', weights_only=False)
    print(f"ckpt keys: {list(ckpt.keys())[:10]}")

    if 'train_args' in ckpt:
        ta = ckpt['train_args']
        print(f"train_args.data: {ta.get('data')}")
        print(f"train_args.model: {ta.get('model')}")
        print(f"train_args.task: {ta.get('task')}")

    if 'model' in ckpt:
        m = ckpt['model']
        print(f"model type: {type(m).__name__}")
        names = getattr(m, 'names', None)
        if names is None and hasattr(m, 'module'):
            names = getattr(m.module, 'names', None)
        if names is None and hasattr(m, 'model'):
            names = getattr(m.model, 'names', None)
        print(f"names: {names}")

        nc = getattr(m, 'nc', None)
        if nc is None and hasattr(m, 'module'):
            nc = getattr(m.module, 'nc', None)
        if nc is None and hasattr(m, 'model'):
            nc = getattr(m.model, 'nc', None)
        print(f"nc (num_classes): {nc}")
except Exception as e:
    print(f"[ERROR] torch.load 失败: {type(e).__name__}: {e}")
    if "Can't get attribute" in str(e):
        missing = re.search(r"attribute '(\w+)'", str(e))
        if missing:
            print(f"[INFO] 缺失类: {missing.group(1)}")
            # 找缺失类在哪个模块
            missing_cls = missing.group(1)
            for mod_file in MODULE_FILES:
                vendored_path = os.path.join(VENDORED_ROOT, mod_file)
                if not os.path.exists(vendored_path):
                    continue
                with open(vendored_path, 'r', encoding='utf-8') as f:
                    src = f.read()
                if f"class {missing_cls}" in src:
                    print(f"[INFO] {missing_cls} 在 {mod_file} 中，但注入失败")
                    break
