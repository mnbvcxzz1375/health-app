# -*- coding: utf-8 -*-
"""中药忌口（服药饮食禁忌）人工整理 + LLM 补充

数据源：《中药学》教材"服药饮食禁忌"章节 + 临床经验 + DashScope LLM 补充
性质：公共医学常识，无版权风险
输出：output/tcm_dietary_taboo.json

按药性类目整理通用忌口 + 按药材整理单味药忌口 + LLM 补充扩展药材忌口
"""

import json
import os
import requests
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# ============ LLM 配置（DashScope） ============
DASHSCOPE_API_KEY = (
    os.environ.get("DASHSCOPE_API_KEY")
    or os.environ.get("VITE_LLM_API_KEY")
    or os.environ.get("QWEN_API_KEY")
)
DASHSCOPE_BASE_URL = os.environ.get(
    "DASHSCOPE_BASE_URL", "https://coding.dashscope.aliyuncs.com/v1"
)
DASHSCOPE_CHAT_MODEL = os.environ.get("DASHSCOPE_CHAT_MODEL", "kimi-k2.5")

LLM_SYSTEM_PROMPT = """你是中医药学专家。请基于给定的中药名列表，输出每味中药的饮食禁忌（忌口）。

要求：
1. 严格遵循《中药学》教材和临床经验
2. 输出 JSON 数组，每个元素包含：
   - herb_name: 中药名
   - food_category: 食物类别（如"寒凉食物"、"辛辣刺激"、"油腻食物"等）
   - food_items: 具体食物列表（如["西瓜","绿豆"]）
   - reason: 禁忌原因（如"人参补气，萝卜破气，同食降低人参功效"）
   - severity: 严重程度（low/moderate/high）

只返回 JSON 数组，不要其他文字。"""

# 扩展中药列表（50 味常用中药），用于让 LLM 生成更多忌口
EXTENDED_HERBS = [
    "人参", "黄芪", "党参", "白术", "甘草", "当归", "熟地黄", "白芍",
    "麻黄", "桂枝", "柴胡", "黄芩", "黄连", "金银花", "连翘", "丹参",
    "川芎", "红花", "陈皮", "枳实", "半夏", "桔梗", "天麻", "钩藤",
    "茯苓", "泽泻", "麦冬", "枸杞子", "五味子", "附子", "干姜", "肉桂",
    "吴茱萸", "细辛", "薄荷", "蝉蜕", "桑叶", "菊花", "栀子", "夏枯草",
    "玄参", "牡丹皮", "赤芍", "地骨皮", "威灵仙", "独活", "桑寄生",
    "牛膝", "郁金", "延胡索"
]


def fetch_taboos_from_llm(herb_names):
    """调用 DashScope LLM 获取中药忌口数据"""
    if not DASHSCOPE_API_KEY:
        print("[dietary_taboo] 未配置 DASHSCOPE_API_KEY，跳过 LLM 调用")
        return []

    content = f"请为以下中药列表输出忌口信息：\n{', '.join(herb_names)}"

    try:
        resp = requests.post(
            f"{DASHSCOPE_BASE_URL}/chat/completions",
            headers={
                "Authorization": f"Bearer {DASHSCOPE_API_KEY}",
                "Content-Type": "application/json; charset=utf-8",
            },
            json={
                "model": DASHSCOPE_CHAT_MODEL,
                "messages": [
                    {"role": "system", "content": LLM_SYSTEM_PROMPT},
                    {"role": "user", "content": content},
                ],
                "temperature": 0.2,
            },
            timeout=60,
        )
        resp.raise_for_status()
        data = resp.json()
        text = data["choices"][0]["message"]["content"]
        # 提取 JSON 数组
        start = text.find("[")
        end = text.rfind("]")
        if start >= 0 and end > start:
            return json.loads(text[start:end + 1])
        return []
    except Exception as e:
        print(f"[dietary_taboo] LLM 调用失败: {e}")
        return []

# 通用忌口（所有中药服药期间适用）
GENERAL_TABOOS = [
    {"herb_name": "*", "food_category": "生冷",
     "food_items": ["冰淇淋", "冰水", "生鱼片", "凉拌菜"],
     "reason": "生冷食物伤脾胃，影响药物吸收",
     "severity": "moderate"},
    {"herb_name": "*", "food_category": "油腻",
     "food_items": ["肥肉", "油炸食品", "奶油"],
     "reason": "油腻食物阻碍脾胃运化，影响药效",
     "severity": "moderate"},
    {"herb_name": "*", "food_category": "辛辣",
     "food_items": ["辣椒", "花椒", "生姜（大量）", "大蒜（大量）"],
     "reason": "辛辣食物刺激肠胃，与清热药、养阴药相悖",
     "severity": "moderate"},
    {"herb_name": "*", "food_category": "腥膻",
     "food_items": ["羊肉", "狗肉", "鱼虾（部分）"],
     "reason": "腥膻食物可能影响药效，部分人群易过敏",
     "severity": "low"},
    {"herb_name": "*", "food_category": "浓茶",
     "food_items": ["浓茶"],
     "reason": "茶叶含鞣酸，可与多种中药成分结合，影响吸收",
     "severity": "moderate"},
    {"herb_name": "*", "food_category": "酒类",
     "food_items": ["白酒", "啤酒", "红酒"],
     "reason": "服药期间饮酒可能增加肝脏负担，影响药物代谢",
     "severity": "high"},
]

# 按药性类目的忌口规则
PROPERTY_BASED_TABOOS = [
    # 补益类（忌生冷油腻、破气食物）
    {"herb_name": "补益类", "food_category": "破气食物",
     "food_items": ["萝卜", "萝卜籽", "山楂", "陈皮（大量）"],
     "reason": "补益药（如人参、黄芪）服药期间忌食萝卜等破气食物，以免抵消补气功效",
     "severity": "high"},
    {"herb_name": "补益类", "food_category": "生冷",
     "food_items": ["西瓜", "苦瓜", "绿豆"],
     "reason": "补益药忌生冷，以免损伤脾胃阳气，影响补益效果",
     "severity": "moderate"},
    # 清热类（忌辛辣温热）
    {"herb_name": "清热类", "food_category": "辛辣温热",
     "food_items": ["辣椒", "花椒", "羊肉", "狗肉", "桂圆", "荔枝"],
     "reason": "清热药性寒凉，忌食辛辣温热食物，以免抵消清热功效",
     "severity": "high"},
    # 温里类（忌生冷寒凉）
    {"herb_name": "温里类", "food_category": "生冷寒凉",
     "food_items": ["西瓜", "苦瓜", "绿豆", "海带", "紫菜"],
     "reason": "温里药（如附子、干姜）性温热，忌食生冷寒凉，以免抵消温里功效",
     "severity": "high"},
    # 祛湿类（忌甜腻发物）
    {"herb_name": "祛湿类", "food_category": "甜腻发物",
     "food_items": ["甜食", "糯米", "肥肉", "虾蟹"],
     "reason": "祛湿药忌甜腻发物，以免助湿生痰，影响祛湿效果",
     "severity": "moderate"},
    # 理气类（慎豆类）
    {"herb_name": "理气类", "food_category": "豆类",
     "food_items": ["黄豆", "黑豆", "绿豆", "红豆"],
     "reason": "理气药慎食豆类（易产气），以免加重气滞",
     "severity": "low"},
]

# 单味药忌口（基于《本草纲目》及临床经验）
HERB_SPECIFIC_TABOOS = [
    # 人参
    {"herb_name": "人参", "food_category": "破气食物",
     "food_items": ["萝卜", "萝卜籽", "茶"],
     "reason": "人参补气，萝卜破气，同食降低人参功效；茶叶含鞣酸影响吸收",
     "severity": "high"},
    # 地黄
    {"herb_name": "地黄", "food_category": "葱蒜",
     "food_items": ["葱", "蒜", "韭菜"],
     "reason": "地黄忌葱蒜，同食影响药效",
     "severity": "moderate"},
    {"herb_name": "熟地黄", "food_category": "葱蒜",
     "food_items": ["葱", "蒜", "韭菜"],
     "reason": "熟地黄忌葱蒜", "severity": "moderate"},
    # 何首乌
    {"herb_name": "何首乌", "food_category": "动物血",
     "food_items": ["猪血", "鸭血", "羊血"],
     "reason": "何首乌忌动物血，同食可能影响药效",
     "severity": "moderate"},
    {"herb_name": "何首乌", "food_category": "葱蒜萝卜",
     "food_items": ["葱", "蒜", "萝卜"],
     "reason": "何首乌忌葱蒜萝卜", "severity": "moderate"},
    # 薄荷
    {"herb_name": "薄荷", "food_category": "鳖肉",
     "food_items": ["鳖肉"],
     "reason": "薄荷忌鳖肉", "severity": "low"},
    # 甘草
    {"herb_name": "甘草", "food_category": "海藻类",
     "food_items": ["海带", "紫菜", "海藻"],
     "reason": "甘草反海藻（十八反延伸），服药期间忌食海藻类",
     "severity": "high"},
    # 茯苓
    {"herb_name": "茯苓", "food_category": "醋",
     "food_items": ["醋"],
     "reason": "茯苓忌醋，同食可能影响药效",
     "severity": "moderate"},
    # 黄连
    {"herb_name": "黄连", "food_category": "猪肉",
     "food_items": ["猪肉"],
     "reason": "黄连忌猪肉（冷），同食可能影响药效",
     "severity": "low"},
    # 附子
    {"herb_name": "附子", "food_category": "豆豉",
     "food_items": ["豆豉"],
     "reason": "附子忌豆豉", "severity": "moderate"},
    # 威灵仙
    {"herb_name": "威灵仙", "food_category": "茶面汤",
     "food_items": ["浓茶", "面汤"],
     "reason": "威灵仙忌茶面汤", "severity": "low"},
    # 当归
    {"herb_name": "当归", "food_category": "湿面",
     "food_items": ["湿面"],
     "reason": "当归忌湿面", "severity": "low"},
    # 巴豆
    {"herb_name": "巴豆", "food_category": "芦笋野鸭",
     "food_items": ["芦笋", "野鸭"],
     "reason": "巴豆忌芦笋野鸭", "severity": "moderate"},
    # 半夏
    {"herb_name": "半夏", "food_category": "羊肉羊血",
     "food_items": ["羊肉", "羊血", "饴糖"],
     "reason": "半夏忌羊肉羊血饴糖", "severity": "moderate"},
    # 丹参
    {"herb_name": "丹参", "food_category": "醋酸物",
     "food_items": ["醋", "酸性水果"],
     "reason": "丹参忌醋及酸性食物", "severity": "moderate"},
    # 龙骨
    {"herb_name": "龙骨", "food_category": "鱼",
     "food_items": ["鱼"],
     "reason": "龙骨忌鱼", "severity": "low"},
    # 常山
    {"herb_name": "常山", "food_category": "生葱生菜",
     "food_items": ["生葱", "生菜"],
     "reason": "常山忌生葱生菜", "severity": "moderate"},
]

# 孕妇忌口（特殊人群）
PREGNANCY_TABOOS = [
    {"herb_name": "孕妇禁用药", "food_category": "活血食物",
     "food_items": ["山楂", "黑木耳", "薏苡仁"],
     "reason": "孕妇服药期间忌活血食物，以免动胎",
     "severity": "high"},
]


def main():
    """主入口：生成中药忌口数据（硬编码 + LLM 补充）"""
    print("=" * 60)
    print("中药忌口（服药饮食禁忌）整理")
    print("=" * 60)

    output_path = OUTPUT_DIR / "tcm_dietary_taboo.json"

    # 1. 硬编码数据
    all_taboos = []
    all_taboos.extend(GENERAL_TABOOS)
    all_taboos.extend(PROPERTY_BASED_TABOOS)
    all_taboos.extend(HERB_SPECIFIC_TABOOS)
    all_taboos.extend(PREGNANCY_TABOOS)

    print(f"[dietary_taboo] 硬编码忌口条目: {len(all_taboos)}")

    # 2. LLM 补充
    print(f"[dietary_taboo] 调用 LLM 补充忌口数据...")
    llm_taboos = fetch_taboos_from_llm(EXTENDED_HERBS)
    print(f"[dietary_taboo] LLM 返回忌口条目: {len(llm_taboos)}")

    # 3. 去重合并（以 herb_name + food_category 为 key），并标准化字段
    # 统一字段：herb_name, food_category, food_items, reason, severity, source
    llm_taboo_ids = {id(t) for t in llm_taboos}
    seen = set()
    merged = []
    for t in all_taboos + llm_taboos:
        key = (t.get("herb_name", ""), t.get("food_category", ""))
        if key in seen:
            continue
        seen.add(key)
        standardized = {
            "herb_name": t.get("herb_name", ""),
            "food_category": t.get("food_category", ""),
            "food_items": t.get("food_items", []),
            "reason": t.get("reason", t.get("description", "")),
            "severity": t.get("severity", "moderate"),
            "source": "LLM 补充" if id(t) in llm_taboo_ids else "人工整理",
        }
        merged.append(standardized)

    # 4. 写入
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(merged, f, ensure_ascii=False, indent=2)

    print(f"[dietary_taboo] 写入 {len(merged)} 条到 {output_path}")

    # 统计
    general = sum(1 for t in merged if t["herb_name"] == "*")
    property_based = sum(1 for t in merged if "类" in t["herb_name"])
    herb_specific = sum(
        1 for t in merged
        if t["herb_name"] not in ["*"] and "类" not in t["herb_name"]
        and "孕妇" not in t["herb_name"]
    )
    llm_count = sum(1 for t in merged if t.get("source") == "LLM 补充")
    print(f"[INFO] 通用忌口: {general} 条")
    print(f"[INFO] 药性类目忌口: {property_based} 条")
    print(f"[INFO] 单味药忌口: {herb_specific} 条")
    print(f"[INFO] LLM 补充: {llm_count} 条")


if __name__ == "__main__":
    main()
