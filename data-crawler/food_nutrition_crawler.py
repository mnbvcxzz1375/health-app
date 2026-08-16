# -*- coding: utf-8 -*-
"""食物营养成分数据整理

数据源：中国食物成分表（少量样例引用）+ Open Food Facts API
License：中国食物成分表为版权数据，Open Food Facts 为 ODbL
输出：output/food_nutrition.json

由于中国食物成分表为版权数据，本脚本仅整理常见食物的近似营养值，
并可通过 Open Food Facts API 补充。
"""

import json
import time
from pathlib import Path

import requests

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

OPEN_FOOD_FACTS_API = "https://world.openfoodfacts.org/api/v0/product"

HEADERS = {
    "User-Agent": "HealthKnowledgeGraphBot/1.0",
    "Accept": "application/json",
}

# 常见食物营养成分（基于中国食物成分表，近似值）
# 字段单位：每 100 克
COMMON_FOODS = [
    # 谷物
    {"name": "米饭", "category": "谷物", "calories_per_100g": 116,
     "protein_g": 2.6, "fat_g": 0.3, "carb_g": 25.9, "fiber_g": 0.4,
     "sodium_mg": 2, "potassium_mg": 30, "glycemic_index": 83,
     "tags": ["高GI", "主食"], "source": "中国食物成分表"},
    {"name": "全麦面包", "category": "谷物", "calories_per_100g": 246,
     "protein_g": 9.0, "fat_g": 3.5, "carb_g": 44.0, "fiber_g": 6.0,
     "sodium_mg": 400, "potassium_mg": 200, "glycemic_index": 50,
     "tags": ["低GI", "高纤维", "主食"], "source": "中国食物成分表"},
    {"name": "燕麦", "category": "谷物", "calories_per_100g": 389,
     "protein_g": 16.9, "fat_g": 6.9, "carb_g": 66.0, "fiber_g": 10.6,
     "sodium_mg": 2, "potassium_mg": 429, "glycemic_index": 55,
     "tags": ["低GI", "高蛋白", "高纤维"], "source": "中国食物成分表"},
    {"name": "糙米", "category": "谷物", "calories_per_100g": 112,
     "protein_g": 2.6, "fat_g": 0.9, "carb_g": 23.5, "fiber_g": 1.8,
     "sodium_mg": 5, "potassium_mg": 43, "glycemic_index": 50,
     "tags": ["低GI", "主食"], "source": "中国食物成分表"},

    # 蔬菜
    {"name": "西兰花", "category": "蔬菜", "calories_per_100g": 34,
     "protein_g": 2.8, "fat_g": 0.4, "carb_g": 7.0, "fiber_g": 2.6,
     "sodium_mg": 18, "potassium_mg": 17, "glycemic_index": 15,
     "tags": ["低热量", "高维生素K", "华法林忌口"], "source": "中国食物成分表"},
    {"name": "菠菜", "category": "蔬菜", "calories_per_100g": 23,
     "protein_g": 2.9, "fat_g": 0.4, "carb_g": 4.5, "fiber_g": 1.7,
     "sodium_mg": 79, "potassium_mg": 311, "glycemic_index": 15,
     "tags": ["低热量", "高维生素K", "华法林忌口"], "source": "中国食物成分表"},
    {"name": "胡萝卜", "category": "蔬菜", "calories_per_100g": 39,
     "protein_g": 1.0, "fat_g": 0.2, "carb_g": 9.6, "fiber_g": 2.8,
     "sodium_mg": 71, "potassium_mg": 320, "glycemic_index": 47,
     "tags": ["中GI", "富含胡萝卜素"], "source": "中国食物成分表"},
    {"name": "番茄", "category": "蔬菜", "calories_per_100g": 18,
     "protein_g": 0.9, "fat_g": 0.2, "carb_g": 4.0, "fiber_g": 0.5,
     "sodium_mg": 5, "potassium_mg": 237, "glycemic_index": 30,
     "tags": ["低热量", "低GI", "富含番茄红素"], "source": "中国食物成分表"},
    {"name": "黄瓜", "category": "蔬菜", "calories_per_100g": 15,
     "protein_g": 0.7, "fat_g": 0.1, "carb_g": 3.6, "fiber_g": 0.5,
     "sodium_mg": 5, "potassium_mg": 102, "glycemic_index": 15,
     "tags": ["低热量", "低GI"], "source": "中国食物成分表"},
    {"name": "白菜", "category": "蔬菜", "calories_per_100g": 17,
     "protein_g": 1.5, "fat_g": 0.1, "carb_g": 3.2, "fiber_g": 0.8,
     "sodium_mg": 73, "potassium_mg": 130, "glycemic_index": 15,
     "tags": ["低热量", "低GI"], "source": "中国食物成分表"},

    # 水果
    {"name": "苹果", "category": "水果", "calories_per_100g": 54,
     "protein_g": 0.3, "fat_g": 0.2, "carb_g": 13.5, "fiber_g": 1.2,
     "sodium_mg": 1, "potassium_mg": 119, "glycemic_index": 36,
     "tags": ["低GI", "富含纤维"], "source": "中国食物成分表"},
    {"name": "香蕉", "category": "水果", "calories_per_100g": 93,
     "protein_g": 1.4, "fat_g": 0.2, "carb_g": 22.0, "fiber_g": 1.2,
     "sodium_mg": 1, "potassium_mg": 256, "glycemic_index": 52,
     "tags": ["中GI", "高钾"], "source": "中国食物成分表"},
    {"name": "葡萄柚", "category": "水果", "calories_per_100g": 42,
     "protein_g": 0.8, "fat_g": 0.1, "carb_g": 10.7, "fiber_g": 1.6,
     "sodium_mg": 0, "potassium_mg": 135, "glycemic_index": 25,
     "tags": ["低GI", "降压药忌口", "他汀类忌口"], "source": "中国食物成分表"},
    {"name": "西瓜", "category": "水果", "calories_per_100g": 25,
     "protein_g": 0.6, "fat_g": 0.1, "carb_g": 6.0, "fiber_g": 0.4,
     "sodium_mg": 2, "potassium_mg": 87, "glycemic_index": 72,
     "tags": ["高GI", "低热量", "补益类忌口"], "source": "中国食物成分表"},

    # 肉类
    {"name": "鸡胸肉", "category": "肉类", "calories_per_100g": 133,
     "protein_g": 19.4, "fat_g": 5.0, "carb_g": 2.5, "fiber_g": 0,
     "sodium_mg": 34, "potassium_mg": 235, "glycemic_index": 0,
     "tags": ["高蛋白", "低脂"], "source": "中国食物成分表"},
    {"name": "瘦猪肉", "category": "肉类", "calories_per_100g": 143,
     "protein_g": 20.3, "fat_g": 6.2, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 57, "potassium_mg": 305, "glycemic_index": 0,
     "tags": ["高蛋白"], "source": "中国食物成分表"},
    {"name": "牛肉", "category": "肉类", "calories_per_100g": 125,
     "protein_g": 20.2, "fat_g": 4.2, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 53, "potassium_mg": 216, "glycemic_index": 0,
     "tags": ["高蛋白", "低脂", "富含铁"], "source": "中国食物成分表"},
    {"name": "鱼肉", "category": "肉类", "calories_per_100g": 104,
     "protein_g": 18.0, "fat_g": 3.4, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 56, "potassium_mg": 322, "glycemic_index": 0,
     "tags": ["高蛋白", "低脂", "富含Omega-3"], "source": "中国食物成分表"},
    {"name": "羊肉", "category": "肉类", "calories_per_100g": 203,
     "protein_g": 19.0, "fat_g": 14.1, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 80, "potassium_mg": 232, "glycemic_index": 0,
     "tags": ["高脂", "腥膻", "清热类忌口"], "source": "中国食物成分表"},

    # 乳制品
    {"name": "牛奶", "category": "乳制品", "calories_per_100g": 54,
     "protein_g": 3.0, "fat_g": 3.2, "carb_g": 3.4, "fiber_g": 0,
     "sodium_mg": 37, "potassium_mg": 109, "glycemic_index": 27,
     "tags": ["低GI", "高钙"], "source": "中国食物成分表"},
    {"name": "酸奶", "category": "乳制品", "calories_per_100g": 72,
     "protein_g": 2.5, "fat_g": 2.7, "carb_g": 9.3, "fiber_g": 0,
     "sodium_mg": 39, "potassium_mg": 150, "glycemic_index": 36,
     "tags": ["低GI", "益生菌"], "source": "中国食物成分表"},
    {"name": "鸡蛋", "category": "乳制品", "calories_per_100g": 147,
     "protein_g": 12.7, "fat_g": 9.0, "carb_g": 1.5, "fiber_g": 0,
     "sodium_mg": 125, "potassium_mg": 154, "glycemic_index": 30,
     "tags": ["高蛋白"], "source": "中国食物成分表"},

    # 坚果
    {"name": "核桃", "category": "坚果", "calories_per_100g": 646,
     "protein_g": 14.9, "fat_g": 58.8, "carb_g": 19.1, "fiber_g": 9.5,
     "sodium_mg": 6, "potassium_mg": 540, "glycemic_index": 15,
     "tags": ["高脂", "高热量", "富含Omega-3"], "source": "中国食物成分表"},
    {"name": "杏仁", "category": "坚果", "calories_per_100g": 578,
     "protein_g": 21.0, "fat_g": 49.9, "carb_g": 21.6, "fiber_g": 11.8,
     "sodium_mg": 1, "potassium_mg": 728, "glycemic_index": 15,
     "tags": ["高脂", "高蛋白", "高纤维"], "source": "中国食物成分表"},

    # 豆类
    {"name": "黄豆", "category": "豆类", "calories_per_100g": 390,
     "protein_g": 35.0, "fat_g": 16.0, "carb_g": 34.2, "fiber_g": 15.5,
     "sodium_mg": 2, "potassium_mg": 1503, "glycemic_index": 18,
     "tags": ["高蛋白", "高纤维", "理气类忌口"], "source": "中国食物成分表"},
    {"name": "豆腐", "category": "豆类", "calories_per_100g": 81,
     "protein_g": 8.1, "fat_g": 3.7, "carb_g": 4.2, "fiber_g": 0.4,
     "sodium_mg": 7, "potassium_mg": 125, "glycemic_index": 15,
     "tags": ["低脂", "高蛋白", "低GI"], "source": "中国食物成分表"},

    # 其他
    {"name": "绿茶", "category": "饮品", "calories_per_100g": 0,
     "protein_g": 0.2, "fat_g": 0, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 1, "potassium_mg": 8, "glycemic_index": 0,
     "tags": ["零热量", "富含茶多酚", "服药期间慎用"], "source": "中国食物成分表"},
    {"name": "红酒", "category": "饮品", "calories_per_100g": 85,
     "protein_g": 0.1, "fat_g": 0, "carb_g": 2.6, "fiber_g": 0,
     "sodium_mg": 4, "potassium_mg": 39, "glycemic_index": 0,
     "tags": ["含酒精", "服药忌口"], "source": "中国食物成分表"},
    {"name": "白萝卜", "category": "蔬菜", "calories_per_100g": 16,
     "protein_g": 0.7, "fat_g": 0.1, "carb_g": 4.0, "fiber_g": 1.0,
     "sodium_mg": 4, "potassium_mg": 21, "glycemic_index": 15,
     "tags": ["低热量", "人参忌口"], "source": "中国食物成分表"},

    # === 扩展谷物（4 条） ===
    {"name": "玉米", "category": "谷物", "calories_per_100g": 112,
     "protein_g": 4.0, "fat_g": 1.2, "carb_g": 22.8, "fiber_g": 2.9,
     "sodium_mg": 3, "potassium_mg": 300, "glycemic_index": 55,
     "tags": ["低GI", "高纤维", "主食"], "source": "中国食物成分表"},
    {"name": "小米", "category": "谷物", "calories_per_100g": 358,
     "protein_g": 9.0, "fat_g": 3.1, "carb_g": 73.5, "fiber_g": 1.6,
     "sodium_mg": 4, "potassium_mg": 284, "glycemic_index": 71,
     "tags": ["高GI", "主食"], "source": "中国食物成分表"},
    {"name": "红薯", "category": "谷物", "calories_per_100g": 99,
     "protein_g": 1.1, "fat_g": 0.2, "carb_g": 23.1, "fiber_g": 1.6,
     "sodium_mg": 28, "potassium_mg": 130, "glycemic_index": 54,
     "tags": ["低GI", "高纤维", "主食"], "source": "中国食物成分表"},
    {"name": "荞麦", "category": "谷物", "calories_per_100g": 337,
     "protein_g": 9.3, "fat_g": 2.3, "carb_g": 73.0, "fiber_g": 6.5,
     "sodium_mg": 8, "potassium_mg": 401, "glycemic_index": 54,
     "tags": ["低GI", "高纤维", "主食"], "source": "中国食物成分表"},

    # === 扩展蔬菜（8 条） ===
    {"name": "茄子", "category": "蔬菜", "calories_per_100g": 21,
     "protein_g": 1.1, "fat_g": 0.2, "carb_g": 4.9, "fiber_g": 1.3,
     "sodium_mg": 5, "potassium_mg": 142, "glycemic_index": 15,
     "tags": ["低热量", "低GI"], "source": "中国食物成分表"},
    {"name": "洋葱", "category": "蔬菜", "calories_per_100g": 39,
     "protein_g": 1.1, "fat_g": 0.2, "carb_g": 9.0, "fiber_g": 0.9,
     "sodium_mg": 4, "potassium_mg": 147, "glycemic_index": 5,
     "tags": ["低GI", "富含槲皮素"], "source": "中国食物成分表"},
    {"name": "蘑菇", "category": "蔬菜", "calories_per_100g": 22,
     "protein_g": 3.1, "fat_g": 0.3, "carb_g": 3.1, "fiber_g": 2.1,
     "sodium_mg": 8, "potassium_mg": 312, "glycemic_index": 10,
     "tags": ["低热量", "低GI", "高蛋白"], "source": "中国食物成分表"},
    {"name": "芹菜", "category": "蔬菜", "calories_per_100g": 14,
     "protein_g": 0.9, "fat_g": 0.1, "carb_g": 3.0, "fiber_g": 1.4,
     "sodium_mg": 159, "potassium_mg": 154, "glycemic_index": 15,
     "tags": ["低热量", "低GI", "高钠", "降压药忌口"], "source": "中国食物成分表"},
    {"name": "青椒", "category": "蔬菜", "calories_per_100g": 22,
     "protein_g": 1.0, "fat_g": 0.2, "carb_g": 5.4, "fiber_g": 1.4,
     "sodium_mg": 2, "potassium_mg": 142, "glycemic_index": 15,
     "tags": ["低热量", "低GI", "富含维生素C"], "source": "中国食物成分表"},
    {"name": "生菜", "category": "蔬菜", "calories_per_100g": 13,
     "protein_g": 1.3, "fat_g": 0.3, "carb_g": 2.0, "fiber_g": 0.7,
     "sodium_mg": 33, "potassium_mg": 170, "glycemic_index": 15,
     "tags": ["低热量", "低GI"], "source": "中国食物成分表"},
    {"name": "冬瓜", "category": "蔬菜", "calories_per_100g": 11,
     "protein_g": 0.4, "fat_g": 0.2, "carb_g": 2.6, "fiber_g": 0.7,
     "sodium_mg": 1, "potassium_mg": 78, "glycemic_index": 15,
     "tags": ["低热量", "低GI"], "source": "中国食物成分表"},
    {"name": "南瓜", "category": "蔬菜", "calories_per_100g": 22,
     "protein_g": 0.7, "fat_g": 0.1, "carb_g": 5.3, "fiber_g": 0.8,
     "sodium_mg": 1, "potassium_mg": 340, "glycemic_index": 75,
     "tags": ["高GI", "富含胡萝卜素"], "source": "中国食物成分表"},

    # === 扩展水果（6 条） ===
    {"name": "橙子", "category": "水果", "calories_per_100g": 48,
     "protein_g": 0.8, "fat_g": 0.2, "carb_g": 11.1, "fiber_g": 0.6,
     "sodium_mg": 1, "potassium_mg": 159, "glycemic_index": 43,
     "tags": ["低GI", "富含维生素C"], "source": "中国食物成分表"},
    {"name": "草莓", "category": "水果", "calories_per_100g": 32,
     "protein_g": 1.0, "fat_g": 0.2, "carb_g": 7.1, "fiber_g": 2.0,
     "sodium_mg": 1, "potassium_mg": 154, "glycemic_index": 40,
     "tags": ["低GI", "低热量", "富含维生素C"], "source": "中国食物成分表"},
    {"name": "蓝莓", "category": "水果", "calories_per_100g": 57,
     "protein_g": 0.7, "fat_g": 0.3, "carb_g": 14.5, "fiber_g": 2.4,
     "sodium_mg": 1, "potassium_mg": 77, "glycemic_index": 53,
     "tags": ["中GI", "富含花青素"], "source": "中国食物成分表"},
    {"name": "猕猴桃", "category": "水果", "calories_per_100g": 61,
     "protein_g": 1.4, "fat_g": 0.5, "carb_g": 14.5, "fiber_g": 2.5,
     "sodium_mg": 10, "potassium_mg": 144, "glycemic_index": 50,
     "tags": ["中GI", "富含维生素C"], "source": "中国食物成分表"},
    {"name": "梨", "category": "水果", "calories_per_100g": 50,
     "protein_g": 0.4, "fat_g": 0.2, "carb_g": 13.3, "fiber_g": 3.1,
     "sodium_mg": 1, "potassium_mg": 92, "glycemic_index": 36,
     "tags": ["低GI", "高纤维"], "source": "中国食物成分表"},
    {"name": "葡萄", "category": "水果", "calories_per_100g": 43,
     "protein_g": 0.5, "fat_g": 0.2, "carb_g": 10.3, "fiber_g": 0.4,
     "sodium_mg": 1, "potassium_mg": 104, "glycemic_index": 43,
     "tags": ["低GI"], "source": "中国食物成分表"},

    # === 扩展肉类（5 条） ===
    {"name": "瘦牛肉", "category": "肉类", "calories_per_100g": 113,
     "protein_g": 21.3, "fat_g": 2.5, "carb_g": 1.3, "fiber_g": 0,
     "sodium_mg": 53, "potassium_mg": 245, "glycemic_index": 0,
     "tags": ["高蛋白", "低脂", "富含铁"], "source": "中国食物成分表"},
    {"name": "鲈鱼", "category": "肉类", "calories_per_100g": 105,
     "protein_g": 18.6, "fat_g": 3.4, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 144, "potassium_mg": 264, "glycemic_index": 0,
     "tags": ["高蛋白", "低脂", "富含Omega-3"], "source": "中国食物成分表"},
    {"name": "虾", "category": "肉类", "calories_per_100g": 87,
     "protein_g": 18.6, "fat_g": 0.8, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 165, "potassium_mg": 215, "glycemic_index": 0,
     "tags": ["高蛋白", "低脂", "高胆固醇"], "source": "中国食物成分表"},
    {"name": "鸭肉", "category": "肉类", "calories_per_100g": 240,
     "protein_g": 15.5, "fat_g": 19.7, "carb_g": 0.2, "fiber_g": 0,
     "sodium_mg": 69, "potassium_mg": 191, "glycemic_index": 0,
     "tags": ["高脂", "腥膻", "清热类忌口"], "source": "中国食物成分表"},
    {"name": "鸡大腿", "category": "肉类", "calories_per_100g": 181,
     "protein_g": 16.0, "fat_g": 13.0, "carb_g": 0, "fiber_g": 0,
     "sodium_mg": 64, "potassium_mg": 190, "glycemic_index": 0,
     "tags": ["高蛋白"], "source": "中国食物成分表"},

    # === 扩展乳制品（2 条） ===
    {"name": "脱脂牛奶", "category": "乳制品", "calories_per_100g": 34,
     "protein_g": 3.4, "fat_g": 0.1, "carb_g": 4.9, "fiber_g": 0,
     "sodium_mg": 50, "potassium_mg": 150, "glycemic_index": 27,
     "tags": ["低脂", "高蛋白", "高钙"], "source": "中国食物成分表"},
    {"name": "希腊酸奶", "category": "乳制品", "calories_per_100g": 97,
     "protein_g": 9.0, "fat_g": 5.0, "carb_g": 4.0, "fiber_g": 0,
     "sodium_mg": 35, "potassium_mg": 200, "glycemic_index": 35,
     "tags": ["高蛋白", "益生菌"], "source": "中国食物成分表"},

    # === 扩展豆类（3 条） ===
    {"name": "黑豆", "category": "豆类", "calories_per_100g": 381,
     "protein_g": 36.0, "fat_g": 15.9, "carb_g": 33.6, "fiber_g": 10.2,
     "sodium_mg": 3, "potassium_mg": 1377, "glycemic_index": 18,
     "tags": ["高蛋白", "高纤维", "低GI"], "source": "中国食物成分表"},
    {"name": "红豆", "category": "豆类", "calories_per_100g": 309,
     "protein_g": 21.7, "fat_g": 0.7, "carb_g": 55.7, "fiber_g": 7.7,
     "sodium_mg": 2, "potassium_mg": 860, "glycemic_index": 25,
     "tags": ["高蛋白", "高纤维", "低GI"], "source": "中国食物成分表"},
    {"name": "绿豆", "category": "豆类", "calories_per_100g": 316,
     "protein_g": 21.6, "fat_g": 0.8, "carb_g": 55.6, "fiber_g": 6.4,
     "sodium_mg": 3, "potassium_mg": 787, "glycemic_index": 27,
     "tags": ["高蛋白", "低脂", "低GI"], "source": "中国食物成分表"},

    # === 扩展坚果（3 条） ===
    {"name": "巴旦木", "category": "坚果", "calories_per_100g": 579,
     "protein_g": 21.2, "fat_g": 49.4, "carb_g": 21.6, "fiber_g": 12.5,
     "sodium_mg": 1, "potassium_mg": 733, "glycemic_index": 15,
     "tags": ["高脂", "高蛋白", "高纤维", "低GI"], "source": "中国食物成分表"},
    {"name": "腰果", "category": "坚果", "calories_per_100g": 559,
     "protein_g": 17.3, "fat_g": 44.1, "carb_g": 30.2, "fiber_g": 3.3,
     "sodium_mg": 12, "potassium_mg": 660, "glycemic_index": 22,
     "tags": ["高脂", "中GI"], "source": "中国食物成分表"},
    {"name": "花生", "category": "坚果", "calories_per_100g": 567,
     "protein_g": 25.8, "fat_g": 49.2, "carb_g": 16.1, "fiber_g": 8.5,
     "sodium_mg": 18, "potassium_mg": 705, "glycemic_index": 14,
     "tags": ["高脂", "高蛋白", "低GI"], "source": "中国食物成分表"},

    # === 扩展饮品（2 条） ===
    {"name": "黑咖啡", "category": "饮品", "calories_per_100g": 2,
     "protein_g": 0.2, "fat_g": 0, "carb_g": 0.3, "fiber_g": 0,
     "sodium_mg": 2, "potassium_mg": 49, "glycemic_index": 0,
     "tags": ["零热量", "富含咖啡因", "服药期间慎用"], "source": "中国食物成分表"},
    {"name": "豆浆", "category": "饮品", "calories_per_100g": 31,
     "protein_g": 3.0, "fat_g": 1.6, "carb_g": 1.2, "fiber_g": 0.4,
     "sodium_mg": 3, "potassium_mg": 92, "glycemic_index": 15,
     "tags": ["低GI", "高蛋白"], "source": "中国食物成分表"},
]


def fetch_from_open_food_facts(barcode: str) -> dict:
    """从 Open Food Facts 获取产品信息

    Args:
        barcode: 产品条形码

    Returns:
        产品营养信息
    """
    try:
        response = requests.get(
            f"{OPEN_FOOD_FACTS_API}/{barcode}.json",
            headers=HEADERS, timeout=15
        )
        response.raise_for_status()
        data = response.json()
        if data.get("status") == 1:
            product = data.get("product", {})
            nutriments = product.get("nutriments", {})
            return {
                "name": product.get("product_name", ""),
                "calories_per_100g": nutriments.get("energy-kcal_100g", 0),
                "protein_g": nutriments.get("proteins_100g", 0),
                "fat_g": nutriments.get("fat_100g", 0),
                "carb_g": nutriments.get("carbohydrates_100g", 0),
                "fiber_g": nutriments.get("fiber_100g", 0),
                "sodium_mg": nutriments.get("sodium_100g", 0) * 1000,
                "source": "Open Food Facts",
            }
    except requests.RequestException as e:
        print(f"[WARN] Open Food Facts 查询失败 barcode={barcode}: {e}")
    return {}


def main():
    """主入口"""
    print("=" * 60)
    print("食物营养成分整理")
    print("=" * 60)

    output_file = OUTPUT_DIR / "food_nutrition.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(COMMON_FOODS, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(COMMON_FOODS)} 种食物")

    # 统计
    by_category = {}
    for food in COMMON_FOODS:
        cat = food["category"]
        by_category[cat] = by_category.get(cat, 0) + 1
    print(f"[INFO] 按类别分类: {by_category}")

    # 标记有药物忌口的食物
    taboo_foods = [f["name"] for f in COMMON_FOODS
                   if any("忌口" in tag for tag in f.get("tags", []))]
    print(f"[INFO] 含药物忌口标记的食物: {taboo_foods}")


if __name__ == "__main__":
    main()
