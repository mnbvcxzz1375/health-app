# -*- coding: utf-8 -*-
"""多源数据合并去重

将各爬虫脚本产出的 JSON 文件合并去重，输出统一数据集
供 generate_sql.py 生成 SQL 种子文件

输入：
- output/nmpa_drugs.json
- output/dxy_drugs.json
- output/wikidata_tcm_herbs.json
- output/tcmsp_herbs.json
- output/tcm_incompatibility.json
- output/tcm_wm_interaction.json
- output/tcm_dietary_taboo.json
- output/rehab_exercises.json
- output/food_nutrition.json

输出：
- output/merged_herbs.json（合并后的中药材库）
- output/merged_drugs.json（合并后的药品临床信息）
- output/merged_interactions.json（合并后的交互规则）
- output/merged_rehab_exercises.json（合并后的康复动作）
- output/merged_food_nutrition.json（合并后的食物营养）
"""

import json
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent / "output"


def load_json(filename: str) -> list:
    """加载 JSON 文件"""
    path = OUTPUT_DIR / filename
    if not path.exists():
        print(f"[WARN] 文件不存在: {path}")
        return []
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def merge_herbs() -> list:
    """合并中药材数据（Wikidata + TCMSP + 人工整理）"""
    wikidata_herbs = load_json("wikidata_tcm_herbs.json")
    tcmsp_herbs = load_json("tcmsp_herbs.json")

    # 以药材名为去重键
    herbs_by_name = {}

    # 先加入 Wikidata 数据
    for herb in wikidata_herbs:
        name = herb.get("name", "").strip()
        if name:
            herbs_by_name[name] = {
                "name": name,
                "pinyin": herb.get("pinyin", ""),
                "alias": herb.get("alias", ""),
                "nature": herb.get("nature", ""),
                "flavor": herb.get("flavor", ""),
                "meridian": herb.get("meridian", ""),
                "efficacy": herb.get("efficacy", ""),
                "contraindication": herb.get("contraindication", ""),
                "source": herb.get("source", "Wikidata"),
                "external_id": herb.get("wikidata_id", ""),
            }

    # 合并 TCMSP 数据（补充字段）
    for herb in tcmsp_herbs:
        name = herb.get("name", "").strip()
        if not name:
            continue
        if name in herbs_by_name:
            # 合并字段，缺失的才补充
            existing = herbs_by_name[name]
            for key in ["nature", "meridian", "efficacy", "pinyin"]:
                if not existing.get(key) and herb.get(key):
                    existing[key] = herb[key]
            existing["source"] = existing["source"] + "+TCMSP"
            existing["external_id"] = (
                existing.get("external_id", "") + "|" + herb.get("tcmsp_id", "")
            )
        else:
            herbs_by_name[name] = {
                "name": name,
                "pinyin": herb.get("pinyin", ""),
                "alias": "",
                "nature": herb.get("nature", ""),
                "flavor": "",
                "meridian": herb.get("meridian", ""),
                "efficacy": herb.get("efficacy", ""),
                "contraindication": "",
                "source": herb.get("source", "TCMSP"),
                "external_id": herb.get("tcmsp_id", ""),
            }

    return list(herbs_by_name.values())


def merge_drugs() -> list:
    """合并药品临床信息（NMPA 主索引 + 丁香园临床字段）"""
    nmpa_drugs = load_json("nmpa_drugs.json")
    dxy_drugs = load_json("dxy_drugs.json")

    # NMPA 数据仅含基础信息（药名、批准文号），不含临床字段
    # 临床字段主要来自 dxy_drugs（或人工整理的备用数据）
    drugs_by_name = {}

    # 先加入 NMPA 数据（作为主索引）
    # NMPA 爬虫输出的字段为 drug_name（兼容早期 name 字段）
    for drug in nmpa_drugs:
        name = (drug.get("drug_name") or drug.get("name") or "").strip()
        if name:
            drugs_by_name[name] = {
                "drug_name": name,
                "medicine_type": "western",
                "ingredients": [],
                "indications": "",
                "side_effects": [],
                "allergic_reactions": [],
                "contraindicated_groups": [],
                "contraindications": "",
                "interactions": [],
                "dietary_taboos": [],
                "dosing_interval_minutes": 30,
                "source": "NMPA",
            }

    # 合并丁香园数据（补充临床字段）
    for drug in dxy_drugs:
        name = drug.get("drug_name", "").strip()
        if not name:
            continue
        if name in drugs_by_name:
            # 合并字段
            existing = drugs_by_name[name]
            existing.update({
                "ingredients": drug.get("ingredients", []),
                "indications": drug.get("indications", ""),
                "side_effects": drug.get("side_effects", []),
                "allergic_reactions": drug.get("allergic_reactions", []),
                "contraindicated_groups": drug.get("contraindicated_groups", []),
                "contraindications": drug.get("contraindications", ""),
                "interactions": drug.get("interactions", []),
                "dietary_taboos": drug.get("dietary_taboos", []),
                "dosing_interval_minutes": drug.get("dosing_interval_minutes", 30),
                "source": "NMPA+丁香园",
            })
        else:
            drugs_by_name[name] = drug

    return list(drugs_by_name.values())


def merge_interactions() -> dict:
    """合并交互规则"""
    tcm_incompatibility = load_json("tcm_incompatibility.json")
    tcm_wm_interaction = load_json("tcm_wm_interaction.json")

    return {
        "tcm_incompatibility": tcm_incompatibility,
        "tcm_wm_interaction": tcm_wm_interaction,
    }


def merge_rehab_exercises() -> list:
    """合并康复动作"""
    return load_json("rehab_exercises.json")


def merge_food_nutrition() -> list:
    """合并食物营养"""
    return load_json("food_nutrition.json")


def main():
    """主入口"""
    print("=" * 60)
    print("多源数据合并去重")
    print("=" * 60)

    # 合并中药材
    herbs = merge_herbs()
    herbs_file = OUTPUT_DIR / "merged_herbs.json"
    with open(herbs_file, "w", encoding="utf-8") as f:
        json.dump(herbs, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 中药材: {len(herbs)} 条 → {herbs_file}")

    # 合并药品
    drugs = merge_drugs()
    drugs_file = OUTPUT_DIR / "merged_drugs.json"
    with open(drugs_file, "w", encoding="utf-8") as f:
        json.dump(drugs, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 药品: {len(drugs)} 条 → {drugs_file}")

    # 合并交互规则
    interactions = merge_interactions()
    interactions_file = OUTPUT_DIR / "merged_interactions.json"
    with open(interactions_file, "w", encoding="utf-8") as f:
        json.dump(interactions, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 配伍禁忌: {len(interactions['tcm_incompatibility'])} 条")
    print(f"[INFO] 中西药交互: {len(interactions['tcm_wm_interaction'])} 条")
    print(f"[INFO] 交互规则 → {interactions_file}")

    # 合并康复动作
    exercises = merge_rehab_exercises()
    exercises_file = OUTPUT_DIR / "merged_rehab_exercises.json"
    with open(exercises_file, "w", encoding="utf-8") as f:
        json.dump(exercises, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 康复动作: {len(exercises)} 条 → {exercises_file}")

    # 合并食物营养
    foods = merge_food_nutrition()
    foods_file = OUTPUT_DIR / "merged_food_nutrition.json"
    with open(foods_file, "w", encoding="utf-8") as f:
        json.dump(foods, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 食物营养: {len(foods)} 条 → {foods_file}")

    print("\n[INFO] 合并完成，可运行 generate_sql.py 生成 SQL 种子文件")


if __name__ == "__main__":
    main()
