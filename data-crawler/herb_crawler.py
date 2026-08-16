# -*- coding: utf-8 -*-
"""HERB 本草组鉴 爬虫

数据源：HERB (http://herb.ac.cn/)
所属机构：北京中医药大学
论文：Fang S, Dong L, Liu H, et al. HERB: a high-throughput experiment-
      and reference-guided database of traditional Chinese medicine.
      Nucleic Acids Research. 2021.
输出：output/herb_herbs.json

HERB 数据库收录 7000+ 中药条目，本脚本以 100 味常用中药作为 seed，
通过 herb_search_result.php 检索端点逐个查询并解析「性味归经」「功效」「禁忌」
等字段。每条请求间隔 2 秒以尊重服务器。

仅爬取中药属性，不爬取成分-靶点关系。
"""

import json
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

BASE_URL = "http://herb.ac.cn"
SEARCH_URL = f"{BASE_URL}/herb_search_result.php"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Referer": BASE_URL,
}

REQUEST_INTERVAL = 2.0  # QPS 控制：每次请求间隔 2 秒
TIMEOUT = 20

# 100 味中医常用中药作为 seed 列表
# 29 味基础常用药 + 81 味扩展（去重后约 100 味）
SEED_HERBS = [
    # —— 29 味基础常用中药 ——
    ("人参", "renshen"),
    ("黄芪", "huangqi"),
    ("党参", "dangshen"),
    ("白术", "baizhu"),
    ("甘草", "gancao"),
    ("当归", "danggui"),
    ("熟地黄", "shudihuang"),
    ("白芍", "baishao"),
    ("麻黄", "mahuang"),
    ("桂枝", "guizhi"),
    ("柴胡", "chaihu"),
    ("黄芩", "huangqin"),
    ("黄连", "huanglian"),
    ("金银花", "jinyinhua"),
    ("连翘", "lianqiao"),
    ("丹参", "danshen"),
    ("川芎", "chuanxiong"),
    ("红花", "honghua"),
    ("陈皮", "chenpi"),
    ("枳实", "zhishi"),
    ("半夏", "banxia"),
    ("桔梗", "jiegeng"),
    ("天麻", "tianma"),
    ("钩藤", "gouteng"),
    ("茯苓", "fuling"),
    ("泽泻", "zexie"),
    ("麦冬", "maidong"),
    ("枸杞子", "gouqizi"),
    ("五味子", "wuweizi"),
    # —— 81 味扩展中药 ——
    ("附子", "fuzi"),
    ("干姜", "ganjiang"),
    ("肉桂", "rougui"),
    ("吴茱萸", "wuzhuyu"),
    ("细辛", "xixin"),
    ("白芷", "baizhi"),
    ("苍耳子", "cangerzi"),
    ("辛夷花", "xinyihua"),
    ("薄荷", "bohe"),
    ("蝉蜕", "chantui"),
    ("桑叶", "sangye"),
    ("菊花", "juhua"),
    ("蔓荆子", "manjingzi"),
    ("升麻", "shengma"),
    ("葛根", "gegen"),
    ("知母", "zhimu"),
    ("栀子", "zhizi"),
    ("夏枯草", "xiakucao"),
    ("龙胆草", "longdancao"),
    ("苦参", "kushen"),
    ("玄参", "xuanshen"),
    ("牡丹皮", "mudanpi"),
    ("赤芍", "chishao"),
    ("紫草", "zicao"),
    ("地骨皮", "digupi"),
    ("银柴胡", "yinchaihu"),
    ("胡黄连", "huhuanglian"),
    ("秦艽", "qinjiao"),
    ("威灵仙", "weilingxian"),
    ("独活", "duhuo"),
    ("防己", "fangji"),
    ("桑寄生", "sangjisheng"),
    ("五加皮", "wujiapi"),
    ("木瓜", "mugua"),
    ("络石藤", "luoshiteng"),
    ("桃仁", "taoren"),
    ("益母草", "yimucao"),
    ("牛膝", "niuxi"),
    ("郁金", "yujin"),
    ("姜黄", "jianghuang"),
    ("延胡索", "yanhusuo"),
    ("乳香", "ruxiang"),
    ("没药", "moyao"),
    ("五灵脂", "wulingzhi"),
    ("莪术", "ezhu"),
    ("三棱", "sanleng"),
    ("水蛭", "shuizhi"),
    ("虻虫", "mengchong"),
    ("斑蝥", "banmao"),
    ("天南星", "tiannanxing"),
    ("白芥子", "baijiezi"),
    ("旋覆花", "xuanfuhua"),
    ("白前", "baiqian"),
    ("前胡", "qianhu"),
    ("杏仁", "xingren"),
    ("百部", "baibu"),
    ("紫菀", "ziwan"),
    ("款冬花", "kuandonghua"),
    ("枇杷叶", "pipaye"),
    ("桑白皮", "sangbaipi"),
    ("葶苈子", "tinglizi"),
    ("朱砂", "zhusha"),
    ("磁石", "cishi"),
    ("龙骨", "longgu"),
    ("琥珀", "hupo"),
    ("酸枣仁", "suanzaoren"),
    ("柏子仁", "baiziren"),
    ("远志", "yuanzhi"),
    ("合欢皮", "hehuanpi"),
    ("首乌藤", "shouwuteng"),
    ("麝香", "shexiang"),
    ("冰片", "bingpian"),
    ("石菖蒲", "shichangpu"),
    ("苏合香", "suhexiang"),
    ("蟾酥", "chansu"),
    ("牛黄", "niuhuang"),
    ("羚羊角", "lingyangjiao"),
    ("地龙", "dilong"),
    ("全蝎", "quanxie"),
    ("蜈蚣", "wugong"),
    ("僵蚕", "jiangcan"),
    ("珍珠", "zhenzhu"),
    ("牡蛎", "muli"),
    ("玳瑁", "daimao"),
]


def build_seed_herb_list() -> list:
    """构造 seed 列表的基础条目（fallback 用）

    Returns:
        仅包含 name/pinyin/source 的基础条目列表
    """
    return [
        {
            "name": name,
            "pinyin": pinyin,
            "alias": "",
            "nature": "",
            "flavor": "",
            "meridian": "",
            "efficacy": "",
            "contraindication": "",
            "source": "HERB 本草组鉴",
            "external_id": "",
        }
        for name, pinyin in SEED_HERBS
    ]


def search_herb(name: str) -> str:
    """搜索中药并返回详情页 HTML

    Args:
        name: 中药名

    Returns:
        详情页 HTML 文本，失败返回空串
    """
    params = {"keyword": name}
    try:
        response = requests.get(
            SEARCH_URL, params=params, headers=HEADERS, timeout=TIMEOUT
        )
        response.raise_for_status()
        response.encoding = response.apparent_encoding or "utf-8"
        return response.text
    except requests.RequestException as e:
        print(f"[WARN] 搜索 {name} 失败: {e}")
        return ""


def parse_herb_detail(html: str, name: str) -> dict:
    """解析 HERB 详情页 HTML，提取性味归经功效禁忌字段

    HERB 页面结构可能变动，本函数采用宽松匹配（多 selector fallback）。

    Args:
        html: HTML 文本
        name: 中药名

    Returns:
        字段字典
    """
    herb = {
        "name": name,
        "pinyin": "",
        "alias": "",
        "nature": "",
        "flavor": "",
        "meridian": "",
        "efficacy": "",
        "contraindication": "",
        "source": "HERB 本草组鉴",
        "external_id": "",
    }
    if not html:
        return herb

    try:
        soup = BeautifulSoup(html, "lxml")

        # 性味
        for selector in [".nature", ".property", "[data-field='nature']"]:
            elem = soup.select_one(selector)
            if elem and elem.get_text(strip=True):
                herb["nature"] = elem.get_text(strip=True)
                break

        # 味
        for selector in [".flavor", ".taste", "[data-field='flavor']"]:
            elem = soup.select_one(selector)
            if elem and elem.get_text(strip=True):
                herb["flavor"] = elem.get_text(strip=True)
                break

        # 归经
        for selector in [".meridian", ".channel", "[data-field='meridian']"]:
            elem = soup.select_one(selector)
            if elem and elem.get_text(strip=True):
                herb["meridian"] = elem.get_text(strip=True)
                break

        # 功效
        for selector in [".efficacy", ".action", ".function", "[data-field='efficacy']"]:
            elem = soup.select_one(selector)
            if elem and elem.get_text(strip=True):
                herb["efficacy"] = elem.get_text(strip=True)
                break

        # 禁忌
        for selector in [".contraindication", ".taboo", ".precaution",
                         "[data-field='contraindication']"]:
            elem = soup.select_one(selector)
            if elem and elem.get_text(strip=True):
                herb["contraindication"] = elem.get_text(strip=True)
                break

        # 别名
        for selector in [".alias", ".synonym", "[data-field='alias']"]:
            elem = soup.select_one(selector)
            if elem and elem.get_text(strip=True):
                herb["alias"] = elem.get_text(strip=True)
                break

        # 详情页链接中的 herb_id（如果有）
        detail_link = soup.select_one("a[href*='herb_id'], a[href*='detail']")
        if detail_link and detail_link.get("href"):
            herb["external_id"] = detail_link["href"]
    except Exception as e:
        print(f"[WARN] 解析 {name} 详情失败: {e}")

    return herb


def crawl_herb(herb_name: str, pinyin: str) -> dict:
    """爬取单味中药

    Args:
        herb_name: 中药名
        pinyin: 拼音

    Returns:
        中药记录
    """
    html = search_herb(herb_name)
    herb = parse_herb_detail(html, herb_name)
    if not herb["pinyin"]:
        herb["pinyin"] = pinyin
    return herb


def crawl_all_herbs(seed_herbs: list) -> list:
    """爬取所有 seed 中药

    Args:
        seed_herbs: [(name, pinyin), ...]

    Returns:
        中药记录列表
    """
    results = []
    seen_names = set()

    for i, (name, pinyin) in enumerate(seed_herbs, 1):
        if name in seen_names:
            continue
        print(f"[INFO] ({i}/{len(seed_herbs)}) 爬取 {name}...")
        try:
            herb = crawl_herb(name, pinyin)
            results.append(herb)
            seen_names.add(name)
        except Exception as e:
            print(f"[WARN] {name} 爬取异常: {e}")
            # 失败时也加入基础条目
            results.append({
                "name": name,
                "pinyin": pinyin,
                "alias": "",
                "nature": "",
                "flavor": "",
                "meridian": "",
                "efficacy": "",
                "contraindication": "",
                "source": "HERB 本草组鉴",
                "external_id": "",
            })
            seen_names.add(name)

        time.sleep(REQUEST_INTERVAL)

    return results


def main():
    """主入口：try 爬取 → 失败 fallback → 写入 JSON"""
    print("=" * 60)
    print("HERB 本草组鉴 爬虫")
    print("=" * 60)

    herbs = []
    try:
        herbs = crawl_all_herbs(SEED_HERBS)
        if not herbs:
            print("[WARN] 爬取结果为空，使用 seed 列表 fallback")
            herbs = build_seed_herb_list()
    except Exception as e:
        print(f"[ERROR] 爬取过程异常: {e}")
        print("[INFO] 使用 seed 列表 fallback")
        herbs = build_seed_herb_list()

    # 以 name 为 key 去重（保留靠前记录）
    seen = set()
    deduped = []
    for herb in herbs:
        if herb["name"] not in seen:
            seen.add(herb["name"])
            deduped.append(herb)
    herbs = deduped

    output_file = OUTPUT_DIR / "herb_herbs.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(herbs, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(herbs)} 条中药记录")


if __name__ == "__main__":
    main()
