# -*- coding: utf-8 -*-
"""生成 SQL 种子文件

读取 merged_*.json 文件，生成 SQL INSERT 语句，使用 INSERT IGNORE 避免冲突

输出：
- sql/seed_knowledge.sql
- sql/seed_rehab_exercises.sql
- sql/seed_food_nutrition.sql

生成的 SQL 文件将被复制到 backend-java/src/main/resources/db/seed/ 目录
由 BackendSchemaInitializer.java 在应用启动时执行
"""

import json
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent / "output"
SQL_DIR = Path(__file__).parent / "sql"
SQL_DIR.mkdir(exist_ok=True)


def load_json(filename: str) -> list:
    """加载 JSON 文件"""
    path = OUTPUT_DIR / filename
    if not path.exists():
        print(f"[WARN] 文件不存在: {path}")
        return []
    with open(path, "r", encoding="utf-8") as f:
        return json.load(f)


def escape_sql_string(s) -> str:
    """转义 SQL 字符串值"""
    if s is None:
        return "NULL"
    if isinstance(s, (int, float)):
        return str(s)
    s = str(s).replace("\\", "\\\\").replace("'", "''")
    return f"'{s}'"


def to_json_field(obj) -> str:
    """将 Python 对象转为 JSON 字段值"""
    if obj is None or (isinstance(obj, (list, dict)) and not obj):
        return "NULL"
    return escape_sql_string(json.dumps(obj, ensure_ascii=False))


def generate_herb_sql(herbs: list) -> str:
    """生成中药材 SQL"""
    lines = [
        "-- 中药材库种子数据",
        "-- 数据来源：Wikidata + TCMSP + 人工整理",
        "",
    ]
    for herb in herbs:
        lines.append(
            "INSERT IGNORE INTO tcm_herbs "
            "(name, pinyin, alias, nature, flavor, meridian, efficacy, "
            "contraindication, source, external_id) VALUES "
            f"({escape_sql_string(herb.get('name', ''))}, "
            f"{escape_sql_string(herb.get('pinyin', ''))}, "
            f"{escape_sql_string(herb.get('alias', ''))}, "
            f"{escape_sql_string(herb.get('nature', ''))}, "
            f"{escape_sql_string(herb.get('flavor', ''))}, "
            f"{escape_sql_string(herb.get('meridian', ''))}, "
            f"{escape_sql_string(herb.get('efficacy', ''))}, "
            f"{escape_sql_string(herb.get('contraindication', ''))}, "
            f"{escape_sql_string(herb.get('source', ''))}, "
            f"{escape_sql_string(herb.get('external_id', ''))});"
        )
    return "\n".join(lines) + "\n"


def generate_drug_clinical_sql(drugs: list) -> str:
    """生成药品临床信息 SQL"""
    lines = [
        "-- 药品临床信息种子数据",
        "-- 数据来源：NMPA + 丁香园 API + 人工整理",
        "",
    ]
    for drug in drugs:
        lines.append(
            "INSERT IGNORE INTO drug_clinical_info "
            "(drug_name, medicine_type, ingredients, indications, "
            "side_effects, allergic_reactions, contraindicated_groups, "
            "contraindications, interactions, dietary_taboos, "
            "dosing_interval_minutes, source) VALUES "
            f"({escape_sql_string(drug.get('drug_name', ''))}, "
            f"{escape_sql_string(drug.get('medicine_type', 'western'))}, "
            f"{to_json_field(drug.get('ingredients'))}, "
            f"{escape_sql_string(drug.get('indications', ''))}, "
            f"{to_json_field(drug.get('side_effects'))}, "
            f"{to_json_field(drug.get('allergic_reactions'))}, "
            f"{to_json_field(drug.get('contraindicated_groups'))}, "
            f"{escape_sql_string(drug.get('contraindications', ''))}, "
            f"{to_json_field(drug.get('interactions'))}, "
            f"{to_json_field(drug.get('dietary_taboos'))}, "
            f"{escape_sql_string(drug.get('dosing_interval_minutes', 30))}, "
            f"{escape_sql_string(drug.get('source', ''))});"
        )
    return "\n".join(lines) + "\n"


def generate_tcm_incompatibility_sql(rules: list) -> str:
    """生成十八反十九畏 SQL"""
    lines = [
        "-- 十八反十九畏配伍禁忌种子数据",
        "-- 数据来源：《本草经集注》+ 《珍珠囊补遗药性赋》+ 现代研究",
        "",
    ]
    for rule in rules:
        lines.append(
            "INSERT IGNORE INTO tcm_incompatibility "
            "(herb_a, herb_b, type, description, source) VALUES "
            f"({escape_sql_string(rule.get('herb_a', ''))}, "
            f"{escape_sql_string(rule.get('herb_b', ''))}, "
            f"{escape_sql_string(rule.get('type', ''))}, "
            f"{escape_sql_string(rule.get('description', ''))}, "
            f"{escape_sql_string(rule.get('source', ''))});"
        )
    return "\n".join(lines) + "\n"


def generate_tcm_wm_interaction_sql(interactions: list) -> str:
    """生成中西药交互 SQL"""
    lines = [
        "-- 中西药相互作用种子数据",
        "-- 数据来源：TCMBank + 临床指南 + 人工核对",
        "",
    ]
    for inter in interactions:
        lines.append(
            "INSERT IGNORE INTO tcm_wm_interaction "
            "(tcm_name, wm_name, severity, interaction_type, "
            "recommended_interval_minutes, description, evidence_source) VALUES "
            f"({escape_sql_string(inter.get('tcm_name', ''))}, "
            f"{escape_sql_string(inter.get('wm_name', ''))}, "
            f"{escape_sql_string(inter.get('severity', ''))}, "
            f"{escape_sql_string(inter.get('interaction_type', ''))}, "
            f"{escape_sql_string(inter.get('recommended_interval_minutes', 30))}, "
            f"{escape_sql_string(inter.get('description', ''))}, "
            f"{escape_sql_string(inter.get('evidence_source', ''))});"
        )
    return "\n".join(lines) + "\n"


def generate_drug_food_interaction_sql(taboos: list) -> str:
    """生成药物-食物交互 SQL（基于中药忌口）"""
    lines = [
        "-- 药物-食物相互作用种子数据",
        "-- 数据来源：《中药学》教材 + 临床经验",
        "",
    ]
    for taboo in taboos:
        herb_name = taboo.get("herb_name", "")
        if herb_name == "*":
            continue  # 通用忌口不写入药物-食物交互表
        food_category = taboo.get("food_category", "")
        food_items = taboo.get("food_items", [])
        lines.append(
            "INSERT IGNORE INTO drug_food_interaction "
            "(drug_name, food_category, food_items, severity, description, source) VALUES "
            f"({escape_sql_string(herb_name)}, "
            f"{escape_sql_string(food_category)}, "
            f"{to_json_field(food_items)}, "
            f"{escape_sql_string(taboo.get('severity', 'moderate'))}, "
            f"{escape_sql_string(taboo.get('reason', ''))}, "
            f"'人工整理');"
        )
    return "\n".join(lines) + "\n"


def generate_rehab_exercise_sql(exercises: list) -> str:
    """生成康复动作 SQL"""
    lines = [
        "-- 康复训练动作库种子数据",
        "-- 数据来源：健身动作百科 + 运动训练学",
        "",
    ]
    for ex in exercises:
        steps = ex.get("steps", [])
        benefits = ex.get("benefits", [])
        lines.append(
            "INSERT IGNORE INTO rehab_exercises "
            "(user_id, name, category, duration, level, minutes, "
            "steps_json, caution, focus, benefits_json, video_minutes, "
            "goal_type, muscle_group, equipment, calories_burn_per_min, bmi_range) VALUES "
            f"(NULL, "
            f"{escape_sql_string(ex.get('name', ''))}, "
            f"{escape_sql_string(ex.get('category', ''))}, "
            f"{escape_sql_string(ex.get('duration', ''))}, "
            f"{escape_sql_string(ex.get('level', ''))}, "
            f"{escape_sql_string(ex.get('minutes', 0))}, "
            f"{to_json_field(steps)}, "
            f"{escape_sql_string(ex.get('caution', ''))}, "
            f"{escape_sql_string(ex.get('focus', ''))}, "
            f"{to_json_field(benefits)}, "
            f"{escape_sql_string(ex.get('video_minutes', 0))}, "
            f"{escape_sql_string(ex.get('goal_type', ''))}, "
            f"{escape_sql_string(ex.get('muscle_group', ''))}, "
            f"{escape_sql_string(ex.get('equipment', ''))}, "
            f"{escape_sql_string(ex.get('calories_burn_per_min', 0))}, "
            f"{escape_sql_string(ex.get('bmi_range', ''))});"
        )
    return "\n".join(lines) + "\n"


def generate_food_nutrition_sql(foods: list) -> str:
    """生成食物营养 SQL"""
    lines = [
        "-- 食物营养成分种子数据",
        "-- 数据来源：中国食物成分表",
        "",
    ]
    for food in foods:
        tags = food.get("tags", [])
        lines.append(
            "INSERT IGNORE INTO food_items "
            "(name, category, calories_per_100g, protein_g, fat_g, carb_g, "
            "fiber_g, sodium_mg, potassium_mg, glycemic_index, tags, source) VALUES "
            f"({escape_sql_string(food.get('name', ''))}, "
            f"{escape_sql_string(food.get('category', ''))}, "
            f"{escape_sql_string(food.get('calories_per_100g', 0))}, "
            f"{escape_sql_string(food.get('protein_g', 0))}, "
            f"{escape_sql_string(food.get('fat_g', 0))}, "
            f"{escape_sql_string(food.get('carb_g', 0))}, "
            f"{escape_sql_string(food.get('fiber_g', 0))}, "
            f"{escape_sql_string(food.get('sodium_mg', 0))}, "
            f"{escape_sql_string(food.get('potassium_mg', 0))}, "
            f"{escape_sql_string(food.get('glycemic_index', 0))}, "
            f"{to_json_field(tags)}, "
            f"{escape_sql_string(food.get('source', ''))});"
        )
    return "\n".join(lines) + "\n"


def main():
    """主入口"""
    print("=" * 60)
    print("生成 SQL 种子文件")
    print("=" * 60)

    # 加载合并后的数据
    herbs = load_json("merged_herbs.json")
    drugs = load_json("merged_drugs.json")
    interactions = load_json("merged_interactions.json")
    exercises = load_json("merged_rehab_exercises.json")
    foods = load_json("merged_food_nutrition.json")
    taboos = load_json("tcm_dietary_taboo.json")

    # 生成知识图谱 SQL
    knowledge_sql_parts = [
        "-- ============================================",
        "-- 健康知识图谱种子数据",
        "-- 自动生成，请勿手动编辑",
        "-- ============================================",
        "",
        generate_herb_sql(herbs),
        generate_drug_clinical_sql(drugs),
        generate_tcm_incompatibility_sql(
            interactions.get("tcm_incompatibility", []) if interactions else []
        ),
        generate_tcm_wm_interaction_sql(
            interactions.get("tcm_wm_interaction", []) if interactions else []
        ),
        generate_drug_food_interaction_sql(taboos),
    ]
    knowledge_sql = "\n".join(knowledge_sql_parts)

    knowledge_file = SQL_DIR / "seed_knowledge.sql"
    with open(knowledge_file, "w", encoding="utf-8") as f:
        f.write(knowledge_sql)
    print(f"[INFO] 知识图谱 SQL → {knowledge_file}")
    print(f"[INFO]   中药材: {len(herbs)} 条")
    print(f"[INFO]   药品临床信息: {len(drugs)} 条")
    print(f"[INFO]   配伍禁忌: {len(interactions.get('tcm_incompatibility', []) if interactions else [])} 条")
    print(f"[INFO]   中西药交互: {len(interactions.get('tcm_wm_interaction', []) if interactions else [])} 条")
    print(f"[INFO]   药物-食物交互: {sum(1 for t in taboos if t.get('herb_name') != '*')} 条")

    # 生成康复动作 SQL
    rehab_sql = generate_rehab_exercise_sql(exercises)
    rehab_file = SQL_DIR / "seed_rehab_exercises.sql"
    with open(rehab_file, "w", encoding="utf-8") as f:
        f.write(rehab_sql)
    print(f"[INFO] 康复动作 SQL → {rehab_file}")
    print(f"[INFO]   动作: {len(exercises)} 条")

    # 生成食物营养 SQL
    food_sql = generate_food_nutrition_sql(foods)
    food_file = SQL_DIR / "seed_food_nutrition.sql"
    with open(food_file, "w", encoding="utf-8") as f:
        f.write(food_sql)
    print(f"[INFO] 食物营养 SQL → {food_file}")
    print(f"[INFO]   食物: {len(foods)} 条")

    print("\n[INFO] SQL 生成完成")
    print(f"[INFO] 请将 {SQL_DIR} 目录下的 SQL 文件复制到")
    print(f"[INFO] backend-java/src/main/resources/db/seed/ 目录")
    print(f"[INFO] 并在 BackendSchemaInitializer.java 中添加加载逻辑")


if __name__ == "__main__":
    main()
