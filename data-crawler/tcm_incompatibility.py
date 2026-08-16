# -*- coding: utf-8 -*-
"""十八反十九畏配伍禁忌人工整理

数据源：《本草经集注》《珍珠囊补遗药性赋》《中华人民共和国药典》一部 + 《中药学》教材
性质：公共医学常识，无版权风险
输出：output/tcm_incompatibility.json

十八反：18 种相反药对（同用会产生毒副作用）
十九畏：10 对相畏药对（同用会降低疗效或产生不良反应）
现代补充：2 对（基于现代药理研究）
"""

import json
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# 十八反（18 种相反药对）
# 来源：《本草经集注》及后世医家补充
# 歌诀："本草明言十八反，半蒌贝蔹及攻乌，藻戟遂芫俱战草，诸参辛芍叛藜芦"
SHIBA_FAN = [
    # 甘草反（4 对）：藻戟遂芫俱战草
    {"herb_a": "甘草", "herb_b": "甘遂", "type": "反",
     "description": "甘草反甘遂，同用产生毒副作用",
     "source": "《本草经集注》十八反"},
    {"herb_a": "甘草", "herb_b": "芫花", "type": "反",
     "description": "甘草反芫花，同用产生毒副作用",
     "source": "《本草经集注》十八反"},
    {"herb_a": "甘草", "herb_b": "海藻", "type": "反",
     "description": "甘草反海藻，同用产生毒副作用",
     "source": "《本草经集注》十八反"},
    {"herb_a": "甘草", "herb_b": "大戟", "type": "反",
     "description": "甘草反大戟（京大戟），同用产生毒副作用",
     "source": "《本草经集注》十八反"},
    # 乌头反（5 对）：半蒌贝蔹及攻乌
    {"herb_a": "乌头", "herb_b": "半夏", "type": "反",
     "description": "乌头（川乌、草乌、附子）反半夏，同用增强毒性",
     "source": "《本草经集注》十八反"},
    {"herb_a": "乌头", "herb_b": "瓜蒌", "type": "反",
     "description": "乌头反瓜蒌（瓜蒌皮、瓜蒌仁、天花粉）",
     "source": "《本草经集注》十八反"},
    {"herb_a": "乌头", "herb_b": "贝母", "type": "反",
     "description": "乌头反贝母（川贝母、浙贝母、土贝母）",
     "source": "《本草经集注》十八反"},
    {"herb_a": "乌头", "herb_b": "白蔹", "type": "反",
     "description": "乌头反白蔹",
     "source": "《本草经集注》十八反"},
    {"herb_a": "乌头", "herb_b": "白及", "type": "反",
     "description": "乌头反白及",
     "source": "《本草经集注》十八反"},
    # 藜芦反（9 对）：诸参辛芍叛藜芦
    {"herb_a": "藜芦", "herb_b": "人参", "type": "反",
     "description": "藜芦反人参",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "党参", "type": "反",
     "description": "藜芦反党参（诸参之一）",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "丹参", "type": "反",
     "description": "藜芦反丹参",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "玄参", "type": "反",
     "description": "藜芦反玄参（元参）",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "苦参", "type": "反",
     "description": "藜芦反苦参",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "沙参", "type": "反",
     "description": "藜芦反沙参（南沙参、北沙参）",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "太子参", "type": "反",
     "description": "藜芦反太子参（诸参之一）",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "细辛", "type": "反",
     "description": "藜芦反细辛",
     "source": "《本草经集注》十八反"},
    {"herb_a": "藜芦", "herb_b": "芍药", "type": "反",
     "description": "藜芦反芍药（白芍、赤芍）",
     "source": "《本草经集注》十八反"},
]

# 十九畏（10 对相畏药对）
# 来源：《珍珠囊补遗药性赋》
# 歌诀：
#   硫黄原是火中精，朴硝一见便相争
#   水银莫与砒霜见，狼毒最怕密陀僧
#   巴豆性烈最为上，偏与牵牛不顺情
#   丁香莫与郁金见，牙硝难合京三棱
#   川乌草乌不顺犀，人参最怕五灵脂
#   官桂善能调冷气，若逢石脂便相欺
SHIJIU_WEI = [
    {"herb_a": "硫黄", "herb_b": "朴硝", "type": "畏",
     "description": "硫黄畏朴硝（芒硝），同用产生不良反应",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "水银", "herb_b": "砒霜", "type": "畏",
     "description": "水银畏砒霜",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "狼毒", "herb_b": "密陀僧", "type": "畏",
     "description": "狼毒畏密陀僧",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "巴豆", "herb_b": "牵牛", "type": "畏",
     "description": "巴豆畏牵牛（牵牛子），同用泻下力过猛",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "丁香", "herb_b": "郁金", "type": "畏",
     "description": "丁香畏郁金",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "牙硝", "herb_b": "京三棱", "type": "畏",
     "description": "牙硝（芒硝之一种）畏京三棱（三棱），同用降低疗效",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "川乌", "herb_b": "犀角", "type": "畏",
     "description": "川乌畏犀角（现以水牛角代）",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "草乌", "herb_b": "犀角", "type": "畏",
     "description": "草乌畏犀角（现以水牛角代）",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "人参", "herb_b": "五灵脂", "type": "畏",
     "description": "人参畏五灵脂",
     "source": "《珍珠囊补遗药性赋》十九畏"},
    {"herb_a": "官桂", "herb_b": "赤石脂", "type": "畏",
     "description": "官桂畏赤石脂（石脂），同用降低疗效",
     "source": "《珍珠囊补遗药性赋》十九畏"},
]

# 现代研究补充的配伍禁忌（与经典十八反十九畏不重复）
MODERN_INCOMPATIBILITY = [
    {"herb_a": "朱砂", "herb_b": "碘化钾", "type": "畏",
     "description": "朱砂含硫化汞，与碘化钾同服可在肠道生成可溶性汞盐，"
                    "刺激肠胃并显著增加汞吸收，引起慢性汞中毒",
     "source": "现代研究补充"},
    {"herb_a": "雄黄", "herb_b": "亚铁盐", "type": "畏",
     "description": "雄黄含硫化砷，与亚铁盐（如硫酸亚铁）同服可生成硫砷酸盐，"
                    "影响吸收并增加砷毒性",
     "source": "现代研究补充"},
]


def main():
    """主入口：生成十八反十九畏数据"""
    print("=" * 60)
    print("十八反十九畏配伍禁忌整理")
    print("=" * 60)

    all_rules = SHIBA_FAN + SHIJIU_WEI + MODERN_INCOMPATIBILITY

    # 去重（按 herb_a + herb_b 组合，不区分顺序）
    seen_pairs = set()
    unique_rules = []
    for rule in all_rules:
        pair = tuple(sorted([rule["herb_a"], rule["herb_b"]]))
        if pair not in seen_pairs:
            seen_pairs.add(pair)
            unique_rules.append(rule)

    output_file = OUTPUT_DIR / "tcm_incompatibility.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(unique_rules, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(unique_rules)} 条配伍禁忌")

    # 统计
    fan_count = sum(1 for r in unique_rules if r["type"] == "反")
    wei_count = sum(1 for r in unique_rules if r["type"] == "畏")
    print(f"[INFO] 十八反: {fan_count} 条")
    print(f"[INFO] 十九畏: {wei_count} 条")


if __name__ == "__main__":
    main()
