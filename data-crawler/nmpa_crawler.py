# -*- coding: utf-8 -*-
"""NMPA 国家药品监督管理局西药主索引爬取

数据源：NMPA 在线查询 (https://www.nmpa.gov.cn/datasearch/)
合规：政府公开数据，低频爬取 <1 QPS，遵守 robots.txt
输出：output/nmpa_drugs.json

注意：NMPA 网站频繁改版，HTML 选择器 .search-result-list .item 已失效，
直接依赖在线解析不稳定。本脚本改为「关键词列表 + 人工整理」模式：
基于《国家基本药物目录（2018 版）》整理 100 个常用西药关键词，
仅生成包含 drug_name 字段的基础药物索引，
其余字段（通用名/批准文号/剂型/规格/生产企业）留待 dxy_api.py 后续补充。

原 NMPA HTML 解析逻辑（crawl_nmpa 函数）作为备用代码保留，
但 main() 默认不再调用，避免长时间网络阻塞。
"""

import json
import os
import time
from pathlib import Path

import requests
from bs4 import BeautifulSoup

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# NMPA 药品查询接口（国产药品）
# 注意：NMPA 网站可能随时调整接口，需定期验证
NMPA_SEARCH_URL = "https://www.nmpa.gov.cn/datasearch/search-result.html"
NMPA_API_URL = "https://www.nmpa.gov.cn/datasearch/data-query/info-search-result.html"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                  "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
    "Accept-Language": "zh-CN,zh;q=0.9,en;q=0.8",
    "Referer": "https://www.nmpa.gov.cn/",
}

# QPS 限制：每次请求间隔至少 1.5 秒
REQUEST_INTERVAL = 1.5


def search_drugs(keyword: str, page: int = 1, page_size: int = 20) -> dict:
    """搜索药品信息（备用：原 NMPA 在线查询接口）

    Args:
        keyword: 搜索关键词（药品名/通用名）
        page: 页码（从 1 开始）
        page_size: 每页数量

    Returns:
        包含药品列表和总数的字典
    """
    params = {
        "keyword": keyword,
        "page": page,
        "pageSize": page_size,
        "condition": "1",  # 国产药品
    }
    try:
        response = requests.get(
            NMPA_API_URL, params=params, headers=HEADERS, timeout=15
        )
        response.raise_for_status()
        # NMPA 返回 HTML 页面，需解析
        soup = BeautifulSoup(response.text, "lxml")
        items = parse_drug_list(soup)
        total_text = soup.select_one(".pagination-total")
        total = int(total_text.text) if total_text else len(items)
        return {"items": items, "total": total, "page": page}
    except requests.RequestException as e:
        print(f"[WARN] 搜索失败 keyword={keyword} page={page}: {e}")
        return {"items": [], "total": 0, "page": page}


def parse_drug_list(soup: BeautifulSoup) -> list:
    """解析药品列表页面（备用，NMPA 改版后选择器已失效）"""
    items = []
    rows = soup.select(".search-result-list .item")
    for row in rows:
        item = {
            "name": extract_text(row.select_one(".name")),
            "generic_name": extract_text(row.select_one(".generic-name")),
            "approval_number": extract_text(row.select_one(".approval-number")),
            "manufacturer": extract_text(row.select_one(".manufacturer")),
            "dosage_form": extract_text(row.select_one(".dosage-form")),
            "specification": extract_text(row.select_one(".specification")),
            "source": "NMPA",
        }
        if item["name"]:
            items.append(item)
    return items


def extract_text(element) -> str:
    """安全提取文本"""
    if element is None:
        return ""
    return element.get_text(strip=True)


def crawl_nmpa(keywords: list, max_per_keyword: int = 50) -> list:
    """备用：尝试通过 NMPA 在线查询爬取西药数据

    NMPA 网站频繁改版，HTML 选择器不稳定。本函数默认不调用，
    保留作为备用方案以备需要时尝试。

    Args:
        keywords: 药品关键词列表
        max_per_keyword: 每个关键词最多爬取数量

    Returns:
        药品记录列表
    """
    all_drugs = []
    seen_approvals = set()

    for keyword in keywords:
        print(f"[INFO] 爬取关键词: {keyword}")
        page = 1
        collected = 0
        while collected < max_per_keyword:
            result = search_drugs(keyword, page=page)
            items = result.get("items", [])
            if not items:
                break
            for item in items:
                approval = item.get("approval_number", "")
                if approval and approval not in seen_approvals:
                    seen_approvals.add(approval)
                    all_drugs.append(item)
                    collected += 1
                    if collected >= max_per_keyword:
                        break
            page += 1
            time.sleep(REQUEST_INTERVAL)

    print(f"[INFO] NMPA 爬取完成，共 {len(all_drugs)} 条")
    return all_drugs


# 常见西药关键词（覆盖《国家基本药物目录 2018 版》常用处方药和 OTC）
# 共 100 个关键词，覆盖心血管、降糖、消化、解热镇痛、抗感染、呼吸、
# 神经、精神、内分泌、泌尿、抗过敏、维生素等主要临床用药类别。
COMMON_DRUG_KEYWORDS = [
    # 心血管系统 (18)
    "阿司匹林", "华法林", "氨氯地平", "硝苯地平", "美托洛尔", "依那普利", "厄贝沙坦",
    "辛伐他汀", "阿托伐他汀", "瑞舒伐他汀", "氯吡格雷", "替格瑞洛",
    "比索洛尔", "硝酸甘油", "地高辛", "胺碘酮", "利伐沙班", "非诺贝特",

    # 降糖药 (10)
    "二甲双胍", "格列美脲", "阿卡波糖", "胰岛素", "罗格列酮", "吡格列酮",
    "西格列汀", "维格列汀", "利拉鲁肽", "达格列净",

    # 消化系统 (9)
    "奥美拉唑", "雷尼替丁", "多潘立酮", "蒙脱石散", "铝碳酸镁",
    "法莫替丁", "兰索拉唑", "莫沙必利", "枸橼酸铋钾",

    # 解热镇痛 (6)
    "布洛芬", "对乙酰氨基酚", "双氯芬酸", "塞来昔布",
    "吲哚美辛", "美洛昔康",

    # 抗感染 (10)
    "阿莫西林", "头孢氨苄", "头孢呋辛", "阿奇霉素", "左氧氟沙星", "莫西沙星",
    "克拉霉素", "甲硝唑", "复方磺胺甲噁唑", "氟康唑",

    # 呼吸系统 (7)
    "氨溴索", "沙丁胺醇", "布地奈德", "孟鲁司特",
    "氨茶碱", "沙美特罗", "乙酰半胱氨酸",

    # 神经系统 (9)
    "左旋多巴", "卡马西平", "丙戊酸", "苯妥英钠", "地西泮", "艾司唑仑",
    "多奈哌齐", "美金刚", "苯巴比妥",

    # 精神类 (8)
    "舍曲林", "帕罗西汀", "文拉法辛", "米氮平", "奥氮平", "利培酮",
    "阿普唑仑", "喹硫平",

    # 内分泌 (6)
    "左甲状腺素", "甲巯咪唑", "丙硫氧嘧啶",
    "泼尼松", "甲泼尼龙", "地塞米松",

    # 泌尿系统 (5)
    "坦索罗辛", "非那雄胺", "呋塞米", "螺内酯", "氢氯噻嗪",

    # 抗过敏 (4)
    "氯雷他定", "西替利嗪", "异丙嗪", "阿司咪唑",

    # 维生素与抗贫血 (5)
    "维生素B1", "维生素B12", "维生素C", "叶酸", "硫酸亚铁",

    # 其他 (3)
    "甲氨蝶呤", "羟氯喹", "别嘌醇",
]


def build_manual_drug_index(keywords: list = None) -> list:
    """基于关键词列表生成基础药物索引

    从 NMPA 网站爬取失败，改为人工整理国家基本药物目录。
    本函数仅生成包含 drug_name 字段的基础药物条目，
    其他字段（通用名/批准文号/剂型/规格/生产企业）留待
    dxy_api.py 后续补充。

    Args:
        keywords: 药品关键词列表，默认使用 COMMON_DRUG_KEYWORDS

    Returns:
        基础药物记录列表
    """
    if keywords is None:
        keywords = COMMON_DRUG_KEYWORDS

    drugs = []
    seen_names = set()
    for keyword in keywords:
        # 跳过空字符串和重复项
        if not keyword or keyword in seen_names:
            continue
        seen_names.add(keyword)
        drug = {
            "drug_name": keyword,
            # 以下字段待 dxy_api.py 后续补充
            "generic_name": "",
            "approval_number": "",
            "manufacturer": "",
            "dosage_form": "",
            "specification": "",
            "source": "国家基本药物目录（人工整理）",
        }
        drugs.append(drug)

    print(f"[INFO] 人工整理生成 {len(drugs)} 条基础西药索引")
    return drugs


def main():
    """主入口：生成基础西药索引（人工整理模式）

    说明：NMPA 网站频繁改版，HTML 选择器失效，已切换为
    人工整理国家基本药物目录的模式。如需调用在线爬取，
    可手动调用 crawl_nmpa() 函数。
    """
    print("=" * 60)
    print("NMPA 西药主索引构建（人工整理模式）")
    print("=" * 60)
    print("[INFO] 说明：NMPA 网站爬取失败，改为人工整理国家基本药物目录")

    drugs = build_manual_drug_index()

    output_file = OUTPUT_DIR / "nmpa_drugs.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(drugs, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(drugs)} 条药品记录")

    # 统计
    sources = set(d.get("source", "") for d in drugs)
    print(f"[INFO] 数据来源: {sources}")


if __name__ == "__main__":
    import argparse
    parser = argparse.ArgumentParser(description="NMPA 西药主索引爬取")
    parser.add_argument("--limit", type=int, default=20, help="每个关键词最多爬取数量（备用 NMPA 模式）")
    parser.add_argument("--keyword", type=str, help="单个关键词爬取（调试用，调用 NMPA 备用模式）")
    parser.add_argument(
        "--use-nmpa", action="store_true",
        help="使用备用 NMPA 在线爬取模式（不推荐，网站改版不稳定）",
    )
    args = parser.parse_args()

    if args.use_nmpa:
        # 备用模式：调用 NMPA 在线查询（默认不启用）
        if args.keyword:
            drugs = crawl_nmpa([args.keyword], max_per_keyword=args.limit)
        else:
            drugs = crawl_nmpa(COMMON_DRUG_KEYWORDS, max_per_keyword=args.limit)
        output_file = OUTPUT_DIR / "nmpa_drugs.json"
        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(drugs, f, ensure_ascii=False, indent=2)
        print(f"[INFO] 已保存到 {output_file}")
    else:
        # 默认模式：人工整理基础索引
        main()
