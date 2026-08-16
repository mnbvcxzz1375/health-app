# -*- coding: utf-8 -*-
"""TCMSP 中药系统药理学数据库爬取

数据源：TCMSP (https://tcmsp-e.com)
License：学术开放数据，需引用论文
论文：Ru J, Li P, Wang J, et al. TCMSP: a database of systems pharmacology
      for drug discovery from herbal medicines. J Cheminform. 2014;6:13.
输出：output/tcmsp_herbs.json, output/tcmsp_ingredients.json

TCMSP 包含 499 味药典中药、29384 成分、3311 靶点、837 疾病
本脚本爬取中药基础属性（性味归经功效）和活性成分列表
"""

import json
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# 注意：原 URL https://tcmsp-e.com/herbs.php 已迁移，
# 改用旧版镜像站点 old.tcmsp-e.com 维持 herbs 列表页面可访问。
BASE_URL = "https://old.tcmsp-e.com"
HERB_LIST_URL = f"{BASE_URL}/tcmspdb.php/herbs"
HERB_DETAIL_URL = f"{BASE_URL}/herb.php"
INGREDIENT_LIST_URL = f"{BASE_URL}/mol_ingredients.php"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
}

REQUEST_INTERVAL = 2.0  # 学术站点，尊重服务器，2 秒一次


def check_site_available() -> bool:
    """先访问首页验证 TCMSP 站点可用性

    Returns:
        True 如果站点可访问且返回 200
    """
    try:
        response = requests.get(BASE_URL, headers=HEADERS, timeout=20)
        if response.status_code == 200:
            print(f"[INFO] TCMSP 站点可访问: {BASE_URL}")
            return True
        print(f"[WARN] TCMSP 站点返回非 200 状态码: {response.status_code}")
        return False
    except requests.RequestException as e:
        print(f"[WARN] TCMSP 站点不可访问: {e}")
        return False


def get_herb_list() -> list:
    """获取中药列表

    TCMSP 首页列出 499 味药典中药。
    如果站点不可访问，返回空列表，由调用方走 fallback。
    """
    print("[INFO] 获取 TCMSP 中药列表...")

    # 先验证站点可用性
    if not check_site_available():
        print("[WARN] TCMSP 站点不可用，跳过爬取")
        return []

    herbs = []
    try:
        response = requests.get(HERB_LIST_URL, headers=HEADERS, timeout=30)
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "lxml")

        # TCMSP 列表页面结构（旧版镜像）：table.table tr
        # 兼容多种可能的选择器
        rows = soup.select("table.table tr") or soup.select("table tr")
        for row in rows[1:]:  # 跳过表头
            cells = row.select("td")
            if len(cells) >= 4:
                herb = {
                    "tcmsp_id": cells[0].get_text(strip=True),
                    "name": cells[1].get_text(strip=True),
                    "pinyin": cells[2].get_text(strip=True) if len(cells) > 2 else "",
                    "latin": cells[3].get_text(strip=True) if len(cells) > 3 else "",
                    "source": "TCMSP",
                }
                if herb["name"]:
                    herbs.append(herb)
    except requests.RequestException as e:
        print(f"[WARN] 获取 TCMSP 中药列表失败: {e}")

    print(f"[INFO] TCMSP 列表返回 {len(herbs)} 条")
    return herbs


def get_herb_detail(herb_id: str) -> dict:
    """获取单味中药详情（性味归经功效）

    Args:
        herb_id: TCMSP 药材 ID

    Returns:
        药材详细属性字典
    """
    params = {"id": herb_id}
    try:
        response = requests.get(
            HERB_DETAIL_URL, params=params, headers=HEADERS, timeout=30
        )
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "lxml")

        detail = {"tcmsp_id": herb_id}

        # TCMSP 详情页结构（可能变动，需验证）
        # 性味
        nature_elem = soup.select_one(".nature, .property")
        if nature_elem:
            detail["nature"] = nature_elem.get_text(strip=True)

        # 归经
        meridian_elem = soup.select_one(".meridian")
        if meridian_elem:
            detail["meridian"] = meridian_elem.get_text(strip=True)

        # 功效
        efficacy_elem = soup.select_one(".action, .efficacy")
        if efficacy_elem:
            detail["efficacy"] = efficacy_elem.get_text(strip=True)

        return detail
    except requests.RequestException as e:
        print(f"[WARN] 获取药材详情失败 id={herb_id}: {e}")
        return {"tcmsp_id": herb_id}


def get_herb_ingredients(herb_id: str, max_ingredients: int = 50) -> list:
    """获取中药的活性成分列表

    Args:
        herb_id: TCMSP 药材 ID
        max_ingredients: 最多获取成分数量

    Returns:
        成分列表
    """
    ingredients = []
    params = {"herb_id": herb_id, "limit": max_ingredients}
    try:
        response = requests.get(
            INGREDIENT_LIST_URL, params=params, headers=HEADERS, timeout=30
        )
        response.raise_for_status()
        soup = BeautifulSoup(response.text, "lxml")

        rows = soup.select("table.table tr")
        for row in rows[1:]:
            cells = row.select("td")
            if len(cells) >= 3:
                ingredient = {
                    "herb_id": herb_id,
                    "ingredient_name": cells[0].get_text(strip=True),
                    "molecule_name": cells[1].get_text(strip=True) if len(cells) > 1 else "",
                    "ob": cells[2].get_text(strip=True) if len(cells) > 2 else "",  # 口服生物利用度
                    "dl": cells[3].get_text(strip=True) if len(cells) > 3 else "",  # 类药性
                    "source": "TCMSP",
                }
                if ingredient["ingredient_name"]:
                    ingredients.append(ingredient)
    except requests.RequestException as e:
        print(f"[WARN] 获取成分列表失败 herb_id={herb_id}: {e}")

    return ingredients


def crawl_tcmsp(max_herbs: int = 100) -> tuple:
    """爬取 TCMSP 中药和成分数据

    Args:
        max_herbs: 最多爬取药材数量（避免过度请求）

    Returns:
        (herbs, ingredients) 两个列表
    """
    herbs = get_herb_list()
    if not herbs:
        print("[WARN] TCMSP 列表为空，使用备用数据")
        return [], []

    # 限制爬取数量（学术站点，避免过度请求）
    herbs_to_crawl = herbs[:max_herbs]
    print(f"[INFO] 将爬取前 {len(herbs_to_crawl)} 味中药的详情和成分")

    all_ingredients = []
    for i, herb in enumerate(herbs_to_crawl):
        print(f"[INFO] ({i+1}/{len(herbs_to_crawl)}) 爬取 {herb['name']}...")
        herb_id = herb["tcmsp_id"]

        # 获取详情
        detail = get_herb_detail(herb_id)
        herb.update(detail)

        # 获取成分
        ingredients = get_herb_ingredients(herb_id, max_ingredients=20)
        all_ingredients.extend(ingredients)

        time.sleep(REQUEST_INTERVAL)

    return herbs_to_crawl, all_ingredients


# 备用数据（当 TCMSP 网站不可用时使用）
FALLBACK_HERBS = [
    {"tcmsp_id": "MOL000000", "name": "人参", "pinyin": "renshen", "nature": "温",
     "meridian": "脾,肺,心", "efficacy": "大补元气,复脉固脱,补脾益肺,生津,安神",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000001", "name": "黄芪", "pinyin": "huangqi", "nature": "温",
     "meridian": "脾,肺", "efficacy": "补气固表,利尿,托毒排脓,生肌",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000002", "name": "当归", "pinyin": "danggui", "nature": "温",
     "meridian": "肝,心,脾", "efficacy": "补血活血,调经止痛,润肠通便",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000003", "name": "甘草", "pinyin": "gancao", "nature": "平",
     "meridian": "心,肺,脾,胃", "efficacy": "补脾益气,清热解毒,祛痰止咳,缓急止痛,调和诸药",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000004", "name": "丹参", "pinyin": "danshen", "nature": "微寒",
     "meridian": "心,心包,肝", "efficacy": "活血祛瘀,通经止痛,清心除烦,凉血消痈",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000005", "name": "柴胡", "pinyin": "chaihu", "nature": "微寒",
     "meridian": "肝,胆", "efficacy": "和解表里,疏肝,升阳",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000006", "name": "黄芩", "pinyin": "huangqin", "nature": "寒",
     "meridian": "肺,胆,脾,大肠,小肠", "efficacy": "清热燥湿,泻火解毒,止血,安胎",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000007", "name": "茯苓", "pinyin": "fuling", "nature": "平",
     "meridian": "心,肺,脾,肾", "efficacy": "利水渗湿,健脾,宁心",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000008", "name": "白术", "pinyin": "baizhu", "nature": "温",
     "meridian": "脾,胃", "efficacy": "健脾益气,燥湿利水,止汗,安胎",
     "source": "备用数据"},
    {"tcmsp_id": "MOL000009", "name": "川芎", "pinyin": "chuanxiong", "nature": "温",
     "meridian": "肝,胆,心包", "efficacy": "活血行气,祛风止痛",
     "source": "备用数据"},
]


def main():
    """主入口"""
    print("=" * 60)
    print("TCMSP 中药系统药理学数据库爬取")
    print("=" * 60)

    herbs, ingredients = crawl_tcmsp(max_herbs=100)

    # 如果爬取失败，使用备用数据（不要因爬取失败阻塞脚本）
    if not herbs:
        print("[WARN] TCMSP 爬取失败，使用备用数据")
        herbs = FALLBACK_HERBS

    # 保存中药
    herbs_file = OUTPUT_DIR / "tcmsp_herbs.json"
    with open(herbs_file, "w", encoding="utf-8") as f:
        json.dump(herbs, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 中药数据已保存到 {herbs_file}（{len(herbs)} 条）")

    # 保存成分
    ingredients_file = OUTPUT_DIR / "tcmsp_ingredients.json"
    with open(ingredients_file, "w", encoding="utf-8") as f:
        json.dump(ingredients, f, ensure_ascii=False, indent=2)
    print(f"[INFO] 成分数据已保存到 {ingredients_file}（{len(ingredients)} 条）")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="TCMSP 爬取")
    parser.add_argument("--max-herbs", type=int, default=100, help="最多爬取药材数量")
    args = parser.parse_args()
    main()
