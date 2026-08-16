# -*- coding: utf-8 -*-
"""Wikidata SPARQL 中药条目爬取

数据源：Wikidata SPARQL Endpoint (https://query.wikidata.org/sparql)
License：CC-BY-SA，需署名
输出：output/wikidata_tcm_herbs.json

通过 SPARQL 查询 instance-of=中药/草药 的条目，
提取药材名、性味、归经、功效、禁忌等结构化字段。
"""

import json
from pathlib import Path

import requests

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

SPARQL_ENDPOINT = "https://query.wikidata.org/sparql"

HEADERS = {
    "User-Agent": "HealthKnowledgeGraphBot/1.0 (health monitoring platform)",
    "Accept": "application/sparql-results+json",
}

# Wikidata 属性 P-IDs
# P31 = instance of
# Q188854 = traditional Chinese medicine
# Q189503 = herb
#
# 注意：Wikidata 没有标准的「中药性味归经」属性（P527 是 has-part，
# P2175 是 medical condition treated，均与性味归经无关）。
# 因此 SELECT 中不再硬绑定 ?natureLabel ?flavorLabel ?meridianLabel
# 等不存在的属性，改为只取 name、description、altLabel 三个稳定字段，
# 性味归经功效等字段通过 enrich_with_chinese_herb_data() 的人工整理数据补全。

SPARQL_QUERY_TCM_HERBS = """
SELECT DISTINCT ?herb ?herbLabel ?description ?altLabel WHERE {
  ?herb wdt:P31 wd:Q188854.
  OPTIONAL { ?herb schema:description ?description. FILTER(LANG(?description) = "zh") }
  OPTIONAL { ?herb skos:altLabel ?altLabel. FILTER(LANG(?altLabel) = "zh") }
  SERVICE wikibase:label { bd:serviceParam wikibase:language "zh,en". }
}
LIMIT 500
"""

SPARQL_QUERY_HERB_DETAILS = """
SELECT ?herb ?herbLabel ?herbDescription WHERE {
  ?herb wdt:P31 wd:Q188854.
  SERVICE wikibase:label { bd:serviceParam wikibase:language "zh,en". }
}
LIMIT 5000
"""


def run_sparql_query(query: str) -> dict:
    """执行 SPARQL 查询

    Args:
        query: SPARQL 查询字符串

    Returns:
        JSON 格式的查询结果
    """
    params = {"query": query, "format": "json"}
    try:
        response = requests.get(
            SPARQL_ENDPOINT, params=params, headers=HEADERS, timeout=60
        )
        response.raise_for_status()
        return response.json()
    except requests.RequestException as e:
        print(f"[ERROR] SPARQL 查询失败: {e}")
        return {"results": {"bindings": []}}


def parse_sparql_results(results: dict) -> list:
    """解析 SPARQL 查询结果

    Args:
        results: SPARQL JSON 结果

    Returns:
        药材记录列表
    """
    herbs = []
    bindings = results.get("results", {}).get("bindings", [])
    for binding in bindings:
        # schema:description 中文描述作为 efficacy 的初步来源
        description = get_value(binding, "description")
        herb = {
            "name": get_value(binding, "herbLabel"),
            "wikidata_id": extract_wikidata_id(get_value(binding, "herb")),
            # 性味归经等字段不在 Wikidata 稳定属性中，留空由 enrich 函数补全
            "nature": "",
            "flavor": "",
            "meridian": "",
            "efficacy": description,  # 用中文描述作为功效初稿
            "contraindication": "",
            "pinyin": "",
            "alias": get_value(binding, "altLabel"),
            "source": "Wikidata",
        }
        if herb["name"]:
            herbs.append(herb)
    return herbs


def get_value(binding: dict, key: str) -> str:
    """安全获取 binding 值"""
    val = binding.get(key, {})
    return val.get("value", "") if isinstance(val, dict) else ""


def extract_wikidata_id(uri: str) -> str:
    """从 URI 提取 Wikidata Q-ID"""
    if not uri:
        return ""
    return uri.rsplit("/", 1)[-1]


def crawl_tcm_herbs() -> list:
    """爬取中药条目

    Returns:
        中药记录列表
    """
    print("[INFO] 执行 SPARQL 查询中药条目...")
    results = run_sparql_query(SPARQL_QUERY_TCM_HERBS)
    herbs = parse_sparql_results(results)
    print(f"[INFO] Wikidata 查询返回 {len(herbs)} 条中药")

    # 去重（按 name）
    seen_names = set()
    unique_herbs = []
    for herb in herbs:
        name = herb["name"]
        if name and name not in seen_names:
            seen_names.add(name)
            unique_herbs.append(herb)

    print(f"[INFO] 去重后 {len(unique_herbs)} 条")
    return unique_herbs


def enrich_with_chinese_herb_data(herbs: list) -> list:
    """补充常见中药的性味归经功效信息

    Wikidata 对中药的结构化字段覆盖不全，这里人工补充常见药材。
    """
    # 常见中药的人工整理数据（基于《中药学》教材）
    common_herbs_data = [
        # 补益药
        {"name": "人参", "nature": "温", "flavor": "甘,微苦", "meridian": "脾,肺,心",
         "efficacy": "大补元气,复脉固脱,补脾益肺,生津养血,安神益智",
         "contraindication": "实证、热证忌服；反藜芦，畏五灵脂；不宜与萝卜同食"},
        {"name": "黄芪", "nature": "温", "flavor": "甘", "meridian": "脾,肺",
         "efficacy": "补气固表,利尿托毒,排脓,敛疮生肌",
         "contraindication": "表实邪盛、气滞湿阻、食积停滞、痈疽初起或溃后热毒尚盛者忌服"},
        {"name": "党参", "nature": "平", "flavor": "甘", "meridian": "脾,肺",
         "efficacy": "补中益气,健脾益肺",
         "contraindication": "实证、热证者不宜"},
        {"name": "白术", "nature": "温", "flavor": "苦,甘", "meridian": "脾,胃",
         "efficacy": "健脾益气,燥湿利水,止汗,安胎",
         "contraindication": "阴虚内热、津液亏耗者慎服"},
        {"name": "甘草", "nature": "平", "flavor": "甘", "meridian": "心,肺,脾,胃",
         "efficacy": "补脾益气,清热解毒,祛痰止咳,缓急止痛,调和诸药",
         "contraindication": "不宜与海藻、芫花、甘遂、大戟同用（十八反）；水肿者慎用"},
        {"name": "当归", "nature": "温", "flavor": "甘,辛", "meridian": "肝,心,脾",
         "efficacy": "补血活血,调经止痛,润肠通便",
         "contraindication": "湿盛中满、大便溏泄者慎服"},
        {"name": "熟地黄", "nature": "微温", "flavor": "甘", "meridian": "肝,肾",
         "efficacy": "补血滋阴,益精填髓",
         "contraindication": "气滞痰多、脘腹胀痛、食少便溏者忌服"},
        {"name": "白芍", "nature": "微寒", "flavor": "苦,酸", "meridian": "肝,脾",
         "efficacy": "平肝止痛,养血调经,敛阴止汗",
         "contraindication": "不宜与藜芦同用（十八反）"},
        # 解表药
        {"name": "麻黄", "nature": "温", "flavor": "辛,微苦", "meridian": "肺,膀胱",
         "efficacy": "发汗解表,宣肺平喘,利水消肿",
         "contraindication": "表虚自汗、阴虚盗汗、虚喘者慎用"},
        {"name": "桂枝", "nature": "温", "flavor": "辛,甘", "meridian": "心,肺,膀胱",
         "efficacy": "发汗解肌,温通经脉,助阳化气",
         "contraindication": "热证、阴虚阳亢、出血证、孕妇忌服"},
        {"name": "柴胡", "nature": "微寒", "flavor": "苦,辛", "meridian": "肝,胆",
         "efficacy": "和解表里,疏肝解郁,升阳举陷",
         "contraindication": "肝阳上亢、肝风内动、阴虚火旺者忌用"},
        # 清热药
        {"name": "黄芩", "nature": "寒", "flavor": "苦", "meridian": "肺,胆,脾,大肠,小肠",
         "efficacy": "清热燥湿,泻火解毒,止血,安胎",
         "contraindication": "脾胃虚寒、食少便溏者忌用"},
        {"name": "黄连", "nature": "寒", "flavor": "苦", "meridian": "心,脾,胃,肝,胆,大肠",
         "efficacy": "清热燥湿,泻火解毒",
         "contraindication": "脾胃虚寒者忌用；阴虚津伤者慎用"},
        {"name": "金银花", "nature": "寒", "flavor": "甘", "meridian": "肺,心,胃",
         "efficacy": "清热解毒,疏散风热",
         "contraindication": "脾胃虚寒及气虚疮疡脓清者忌用"},
        {"name": "连翘", "nature": "微寒", "flavor": "苦", "meridian": "肺,心,小肠",
         "efficacy": "清热解毒,消肿散结,疏散风热",
         "contraindication": "脾胃虚寒及气虚脓清者不宜"},
        # 活血化瘀药
        {"name": "丹参", "nature": "微寒", "flavor": "苦", "meridian": "心,心包,肝",
         "efficacy": "活血祛瘀,通经止痛,清心除烦,凉血消痈",
         "contraindication": "无瘀血者慎用；孕妇慎用；不宜与藜芦同用（十八反）"},
        {"name": "川芎", "nature": "温", "flavor": "辛", "meridian": "肝,胆,心包",
         "efficacy": "活血行气,祛风止痛",
         "contraindication": "阴虚火旺、多汗、月经过多者慎用；孕妇慎用"},
        {"name": "红花", "nature": "温", "flavor": "辛", "meridian": "心,肝",
         "efficacy": "活血通经,散瘀止痛",
         "contraindication": "孕妇忌用；月经过多、有出血倾向者慎用"},
        # 理气药
        {"name": "陈皮", "nature": "温", "flavor": "辛,苦", "meridian": "脾,肺",
         "efficacy": "理气健脾,燥湿化痰",
         "contraindication": "气虚证、阴虚燥咳、吐血证慎用"},
        {"name": "枳实", "nature": "微寒", "flavor": "苦,辛,酸", "meridian": "脾,胃,大肠",
         "efficacy": "破气消积,化痰散痞",
         "contraindication": "脾胃虚弱及孕妇慎用"},
        # 化痰止咳平喘药
        {"name": "半夏", "nature": "温", "flavor": "辛", "meridian": "脾,胃,肺",
         "efficacy": "燥湿化痰,降逆止呕,消痞散结",
         "contraindication": "不宜与乌头类同用（十八反）；阴虚燥咳、津伤口渴者忌用；孕妇慎用"},
        {"name": "桔梗", "nature": "平", "flavor": "苦,辛", "meridian": "肺",
         "efficacy": "宣肺,利咽,祛痰,排脓",
         "contraindication": "气机上逆、呕吐、呛咳、眩晕者不宜"},
        # 平肝息风药
        {"name": "天麻", "nature": "平", "flavor": "甘", "meridian": "肝",
         "efficacy": "息风止痉,平抑肝阳,祛风通络",
         "contraindication": "气血虚弱者慎用"},
        {"name": "钩藤", "nature": "凉", "flavor": "甘", "meridian": "肝,心包",
         "efficacy": "息风定惊,清热平肝",
         "contraindication": "无火者勿服"},
        # 利水渗湿药
        {"name": "茯苓", "nature": "平", "flavor": "甘,淡", "meridian": "心,肺,脾,肾",
         "efficacy": "利水渗湿,健脾,宁心",
         "contraindication": "阴虚而无湿热、虚寒滑精、气虚下陷者慎服"},
        {"name": "泽泻", "nature": "寒", "flavor": "甘,淡", "meridian": "肾,膀胱",
         "efficacy": "利水渗湿,泄热,化浊降脂",
         "contraindication": "肾虚精滑者慎用"},
        # 补阴药
        {"name": "麦冬", "nature": "微寒", "flavor": "甘,微苦", "meridian": "心,肺,胃",
         "efficacy": "养阴生津,润肺清心",
         "contraindication": "脾胃虚寒、感冒风寒者忌服"},
        {"name": "枸杞子", "nature": "平", "flavor": "甘", "meridian": "肝,肾",
         "efficacy": "滋补肝肾,益精明目",
         "contraindication": "脾虚便溏者慎用"},
        # 收涩药
        {"name": "五味子", "nature": "温", "flavor": "酸,甘", "meridian": "肺,心,肾",
         "efficacy": "收敛固涩,益气生津,补肾宁心",
         "contraindication": "外有表邪、内有实热、咳嗽初起者忌用"},
    ]

    # 将人工整理数据合并到 Wikidata 结果（人工数据优先）
    herbs_by_name = {h["name"]: h for h in herbs}
    for herb_data in common_herbs_data:
        name = herb_data["name"]
        if name in herbs_by_name:
            # 合并字段，人工数据覆盖
            herbs_by_name[name].update(herb_data)
            herbs_by_name[name]["source"] = "Wikidata+人工整理"
        else:
            herb_data["source"] = "人工整理"
            herbs_by_name[name] = herb_data
            herbs.append(herb_data)

    return herbs


def main():
    """主入口"""
    print("=" * 60)
    print("Wikidata SPARQL 中药条目爬取")
    print("=" * 60)

    herbs = crawl_tcm_herbs()
    herbs = enrich_with_chinese_herb_data(herbs)

    output_file = OUTPUT_DIR / "wikidata_tcm_herbs.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(herbs, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(herbs)} 条中药记录")

    # 统计
    natures = set(h.get("nature", "") for h in herbs if h.get("nature"))
    print(f"[INFO] 涉及 {len(natures)} 种药性: {natures}")


if __name__ == "__main__":
    main()
