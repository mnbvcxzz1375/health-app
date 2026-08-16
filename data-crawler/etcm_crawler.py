# -*- coding: utf-8 -*-
"""ETCM 中药百科 爬虫

数据源：ETCM (http://www.nrc.ac.cn:9090/ETCM/)
所属机构：中科院上海药物研究所
论文：Xu HY, Zhang YQ, Liu ZM, et al. ETCM: an encyclopaedia of traditional
      Chinese medicines. Nucleic Acids Research. 2019.
输出：output/etcm_herbs.json

ETCM 收录 300+ 中药、9000+ 成分。本脚本先做连通性探测，若失败则直接 fallback；
若连通则按拼音 a-z 索引爬取，每条请求间隔 3 秒。

注意：ETCM 服务器响应较慢且偶有不可达，必须先做连通性探测。
仅爬取中药基础属性，不爬取成分-靶点关系。
"""

import json
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

BASE_URL = "http://www.nrc.ac.cn:9090/ETCM"
INDEX_URL = f"{BASE_URL}/"  # 连通性探测
LETTER_URL = f"{BASE_URL}/herb.php"  # 按字母索引：?letter=A

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Referer": INDEX_URL,
}

REQUEST_INTERVAL = 3.0  # ETCM 服务器较慢，间隔 3 秒
TIMEOUT = 15

# ETCM fallback seed：与 herb_crawler.py 共用 70 味常用中药（取 subset）
ETCM_SEED_HERBS = [
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
    ("附子", "fuzi"),
    ("干姜", "ganjiang"),
    ("肉桂", "rougui"),
    ("吴茱萸", "wuzhuyu"),
    ("细辛", "xixin"),
    ("白芷", "baizhi"),
    ("薄荷", "bohe"),
    ("蝉蜕", "chantui"),
    ("桑叶", "sangye"),
    ("菊花", "juhua"),
    ("葛根", "gegen"),
    ("知母", "zhimu"),
    ("栀子", "zhizi"),
    ("夏枯草", "xiakucao"),
    ("苦参", "kushen"),
    ("玄参", "xuanshen"),
    ("牡丹皮", "mudanpi"),
    ("赤芍", "chishao"),
    ("紫草", "zicao"),
    ("地骨皮", "digupi"),
    ("威灵仙", "weilingxian"),
    ("独活", "duhuo"),
    ("防己", "fangji"),
    ("桑寄生", "sangjisheng"),
    ("桃仁", "taoren"),
    ("益母草", "yimucao"),
    ("牛膝", "niuxi"),
    ("郁金", "yujin"),
    ("姜黄", "jianghuang"),
    ("延胡索", "yanhusuo"),
    ("乳香", "ruxiang"),
    ("没药", "moyao"),
    ("酸枣仁", "suanzaoren"),
    ("柏子仁", "baiziren"),
    ("远志", "yuanzhi"),
    ("合欢皮", "hehuanpi"),
    ("麝香", "shexiang"),
    ("冰片", "bingpian"),
    ("石菖蒲", "shichangpu"),
    ("牛黄", "niuhuang"),
    ("地龙", "dilong"),
]

# 拼音 a-z 索引（ETCM 按字母分组列表）
LETTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"


def build_seed_herb_list() -> list:
    """构造 ETCM seed 列表的基础条目（fallback 用）

    Returns:
        70 味常用中药的基础条目
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
            "source": "ETCM 中药百科",
            "external_id": "",
        }
        for name, pinyin in ETCM_SEED_HERBS
    ]


def check_connectivity() -> bool:
    """探测 ETCM 站点连通性

    Returns:
        True 表示可达
    """
    try:
        response = requests.get(INDEX_URL, headers=HEADERS, timeout=10)
        if response.status_code < 500:
            print(f"[INFO] ETCM 站点可达，状态码 {response.status_code}")
            return True
        print(f"[WARN] ETCM 站点不可达，状态码 {response.status_code}")
        return False
    except requests.RequestException as e:
        print(f"[WARN] ETCM 站点连通性探测失败: {e}")
        return False


def get_herbs_by_letter(letter: str) -> list:
    """按字母索引获取该字母开头的所有中药

    Args:
        letter: 单个大写字母

    Returns:
        药材列表 [{name, pinyin, detail_url}, ...]
    """
    herbs = []
    params = {"letter": letter}
    try:
        response = requests.get(
            LETTER_URL, params=params, headers=HEADERS, timeout=TIMEOUT
        )
        response.raise_for_status()
        response.encoding = response.apparent_encoding or "utf-8"
        soup = BeautifulSoup(response.text, "lxml")

        # ETCM 列表页结构（按经验推测，需根据实际页面调整）
        # 尝试多种 selector
        links = soup.select("table a, .herb-list a, ul.herb-list li a, a[href*='herb_id']")
        for link in links:
            name = link.get_text(strip=True)
            href = link.get("href", "")
            if not name or len(name) > 20:
                continue
            herbs.append({
                "name": name,
                "pinyin": "",  # 详情页可补充
                "detail_url": href if href.startswith("http") else f"{BASE_URL}/{href}",
            })
    except requests.RequestException as e:
        print(f"[WARN] 字母 {letter} 列表获取失败: {e}")

    return herbs


def parse_herb_detail(html: str, name: str) -> dict:
    """解析 ETCM 详情页 HTML

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
        "source": "ETCM 中药百科",
        "external_id": "",
    }
    if not html:
        return herb

    try:
        soup = BeautifulSoup(html, "lxml")

        # 多 selector fallback
        for sel in [".nature", ".property", "[data-field='nature']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["nature"] = elem.get_text(strip=True)
                break

        for sel in [".flavor", ".taste", "[data-field='flavor']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["flavor"] = elem.get_text(strip=True)
                break

        for sel in [".meridian", ".channel", "[data-field='meridian']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["meridian"] = elem.get_text(strip=True)
                break

        for sel in [".efficacy", ".action", ".function", "[data-field='efficacy']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["efficacy"] = elem.get_text(strip=True)
                break

        for sel in [".contraindication", ".taboo", ".precaution",
                    "[data-field='contraindication']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["contraindication"] = elem.get_text(strip=True)
                break

        for sel in [".alias", ".synonym", "[data-field='alias']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["alias"] = elem.get_text(strip=True)
                break

        for sel in [".pinyin", ".pin-yin", "[data-field='pinyin']"]:
            elem = soup.select_one(sel)
            if elem and elem.get_text(strip=True):
                herb["pinyin"] = elem.get_text(strip=True)
                break
    except Exception as e:
        print(f"[WARN] 解析 {name} 详情失败: {e}")

    return herb


def get_herb_detail(detail_url: str, name: str) -> dict:
    """获取单味中药详情

    Args:
        detail_url: 详情页 URL
        name: 中药名

    Returns:
        中药记录
    """
    try:
        response = requests.get(detail_url, headers=HEADERS, timeout=TIMEOUT)
        response.raise_for_status()
        response.encoding = response.apparent_encoding or "utf-8"
        return parse_herb_detail(response.text, name)
    except requests.RequestException as e:
        print(f"[WARN] 获取 {name} 详情失败: {e}")
        return {
            "name": name,
            "pinyin": "",
            "alias": "",
            "nature": "",
            "flavor": "",
            "meridian": "",
            "efficacy": "",
            "contraindication": "",
            "source": "ETCM 中药百科",
            "external_id": detail_url,
        }


def crawl_etcm(target_count: int = 100) -> list:
    """爬取 ETCM 中药

    Args:
        target_count: 目标爬取条数

    Returns:
        中药记录列表
    """
    results = []
    seen_names = set()

    for letter in LETTERS:
        if len(results) >= target_count:
            break

        print(f"[INFO] 爬取字母索引 {letter}...")
        herbs = get_herbs_by_letter(letter)
        if not herbs:
            time.sleep(REQUEST_INTERVAL)
            continue

        print(f"[INFO] 字母 {letter} 共 {len(herbs)} 条")

        for herb_meta in herbs:
            if len(results) >= target_count:
                break
            name = herb_meta["name"]
            if name in seen_names or not herb_meta["detail_url"]:
                continue
            print(f"[INFO] 爬取 {name}...")
            detail = get_herb_detail(herb_meta["detail_url"], name)
            detail["external_id"] = herb_meta["detail_url"]
            results.append(detail)
            seen_names.add(name)
            time.sleep(REQUEST_INTERVAL)

        time.sleep(REQUEST_INTERVAL)

    return results


def main():
    """主入口：先探测 → 失败 fallback → 写入 JSON"""
    print("=" * 60)
    print("ETCM 中药百科 爬虫")
    print("=" * 60)

    # 连通性探测
    if not check_connectivity():
        print("[WARN] ETCM 站点不可达，直接使用 seed fallback")
        herbs = build_seed_herb_list()
    else:
        try:
            herbs = crawl_etcm(target_count=100)
            if not herbs:
                print("[WARN] ETCM 爬取结果为空，使用 seed fallback")
                herbs = build_seed_herb_list()
        except Exception as e:
            print(f"[ERROR] ETCM 爬取异常: {e}")
            print("[INFO] 使用 seed fallback")
            herbs = build_seed_herb_list()

    # 以 name 为 key 去重
    seen = set()
    deduped = []
    for herb in herbs:
        if herb["name"] not in seen:
            seen.add(herb["name"])
            deduped.append(herb)
    herbs = deduped

    output_file = OUTPUT_DIR / "etcm_herbs.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(herbs, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(herbs)} 条中药记录")


if __name__ == "__main__":
    main()
