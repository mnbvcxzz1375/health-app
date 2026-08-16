# -*- coding: utf-8 -*-
"""丁香园用药助手 API 客户端

数据源：丁香园 open.dxy.cn
License：商用需授权，需申请 API Key
输出：output/dxy_drugs.json

获取西药临床字段：成分、适应症、用法用量、禁忌、不良反应、相互作用
若无 API Key，则跳过实际调用，使用 mock 数据演示
"""

import json
import os
import time
from pathlib import Path

import requests

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# 从环境变量读取 API Key
DXY_API_KEY = os.environ.get("DXY_API_KEY", "")
DXY_API_BASE = "https://open.dxy.cn/api"

HEADERS = {
    "User-Agent": "HealthKnowledgeGraphBot/1.0",
    "Accept": "application/json",
}

REQUEST_INTERVAL = 1.0


def search_drug_by_name(drug_name: str) -> dict:
    """通过药品名搜索药品

    接口编号：1305（疾病查药品）
    需要有效的 API Key

    Args:
        drug_name: 药品名

    Returns:
        药品详情字典
    """
    if not DXY_API_KEY:
        return {}

    url = f"{DXY_API_BASE}/v1/drug/search"
    params = {
        "drug_name": drug_name,
        "api_key": DXY_API_KEY,
    }
    try:
        response = requests.get(url, params=params, headers=HEADERS, timeout=15)
        response.raise_for_status()
        return response.json()
    except requests.RequestException as e:
        print(f"[WARN] 丁香园 API 搜索失败 {drug_name}: {e}")
        return {}


def get_drug_detail(drug_id: str) -> dict:
    """获取药品详情

    Args:
        drug_id: 丁香园药品 ID

    Returns:
        药品详情字典
    """
    if not DXY_API_KEY:
        return {}

    url = f"{DXY_API_BASE}/v1/drug/detail"
    params = {
        "drug_id": drug_id,
        "api_key": DXY_API_KEY,
    }
    try:
        response = requests.get(url, params=params, headers=HEADERS, timeout=15)
        response.raise_for_status()
        return response.json()
    except requests.RequestException as e:
        print(f"[WARN] 获取药品详情失败 id={drug_id}: {e}")
        return {}


# 常见西药的临床信息（人工整理，作为无 API Key 时的备用数据）
COMMON_WESTERN_DRUGS = [
    {
        "drug_name": "阿司匹林",
        "medicine_type": "western",
        "ingredients": ["乙酰水杨酸"],
        "indications": "抗血小板聚集、解热镇痛、抗炎抗风湿",
        "side_effects": ["胃肠道不适", "胃溃疡", "出血倾向", "过敏反应"],
        "allergic_reactions": ["皮疹", "哮喘发作", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇晚期", "哺乳期", "儿童发热（Reye综合征风险）",
                                    "活动性溃疡", "严重肝肾功能不全", "血友病"],
        "contraindications": "活动性消化性溃疡、血友病、严重肝肾功能不全、"
                             "妊娠晚期禁用；对水杨酸过敏者禁用",
        "interactions": ["抗凝药（华法林）", "其他NSAID", "甲氨蝶呤", "糖皮质激素"],
        "dietary_taboos": ["酒精（增加胃出血风险）"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "华法林",
        "medicine_type": "western",
        "ingredients": ["华法林钠"],
        "indications": "抗凝治疗（心房颤动、心脏瓣膜置换、深静脉血栓、肺栓塞）",
        "side_effects": ["出血（牙龈、皮下、消化道、泌尿道）", "脱发", "皮疹",
                         "肝功能异常"],
        "allergic_reactions": ["皮疹", "皮肤坏死（罕见）"],
        "contraindicated_groups": ["孕妇", "严重高血压", "近期手术", "活动性出血",
                                    "严重肝肾功能不全"],
        "contraindications": "妊娠期禁用、活动性出血、严重高血压未控制、"
                             "近期手术或创伤、严重肝肾功能不全",
        "interactions": ["阿司匹林", "广谱抗生素", "西咪替丁", "巴比妥类",
                         "利福平", "维生素K", "丹参", "银杏叶", "当归"],
        "dietary_taboos": ["富含维生素K食物（菠菜、西兰花、甘蓝等绿叶菜）",
                           "葡萄柚", "酒精"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "氨氯地平",
        "medicine_type": "western",
        "ingredients": ["苯磺酸氨氯地平"],
        "indications": "高血压、稳定型心绞痛、变异型心绞痛",
        "side_effects": ["踝部水肿", "面部潮红", "头痛", "心悸", "牙龈增生"],
        "allergic_reactions": ["皮疹", "瘙痒", "血管神经性水肿"],
        "contraindicated_groups": ["严重低血压", "心源性休克", "主动脉瓣狭窄"],
        "contraindications": "对二氢吡啶类过敏者、严重低血压、心源性休克禁用",
        "interactions": ["其他降压药", "强心苷", "西柚汁", "麻黄", "甘草"],
        "dietary_taboos": ["葡萄柚（西柚）汁（影响药物代谢）"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "二甲双胍",
        "medicine_type": "western",
        "ingredients": ["盐酸二甲双胍"],
        "indications": "2型糖尿病（一线用药），多囊卵巢综合征",
        "side_effects": ["胃肠道反应（恶心、腹泻、腹痛）", "维生素 B12 缺乏",
                         "乳酸酸中毒（罕见）"],
        "allergic_reactions": ["皮疹（罕见）"],
        "contraindicated_groups": ["严重肝肾功能不全", "心力衰竭", "严重感染",
                                    "孕妇", "哺乳期", "酗酒者"],
        "contraindications": "严重肝肾功能不全（eGFR<30）、急性心力衰竭、"
                             "严重感染、孕妇禁用",
        "interactions": ["造影剂（需停药48小时）", "酒精", "西咪替丁"],
        "dietary_taboos": ["酒精（增加乳酸酸中毒风险）"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "阿托伐他汀",
        "medicine_type": "western",
        "ingredients": ["阿托伐他汀钙"],
        "indications": "高胆固醇血症、混合性高脂血症、冠心病预防",
        "side_effects": ["肌肉疼痛", "肝酶升高", "头痛", "消化不良",
                         "横纹肌溶解（罕见）"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "哺乳期", "活动性肝病", "肌病"],
        "contraindications": "妊娠期、哺乳期、活动性肝病、不明原因转氨酶升高"
                             ">3倍正常值上限禁用",
        "interactions": ["环孢素", "红霉素", "克拉霉素", "吉非贝齐",
                         "葡萄柚汁", "华法林"],
        "dietary_taboos": ["葡萄柚（西柚）", "酒精（增加肝损伤风险）"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "奥美拉唑",
        "medicine_type": "western",
        "ingredients": ["奥美拉唑"],
        "indications": "胃溃疡、十二指肠溃疡、反流性食管炎、卓-艾综合征",
        "side_effects": ["头痛", "腹泻", "恶心", "腹痛", "维生素 B12 缺乏（长期）"],
        "allergic_reactions": ["皮疹", "瘙痒"],
        "contraindicated_groups": ["严重肝功能不全慎用"],
        "contraindications": "对奥美拉唑过敏者禁用；严重肝功能不全慎用",
        "interactions": ["氯吡格雷（降低活性）", "酮康唑", "地西泮", "苯妥英"],
        "dietary_taboos": ["酒精", "辛辣食物"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "布洛芬",
        "medicine_type": "western",
        "ingredients": ["布洛芬"],
        "indications": "解热镇痛抗炎（头痛、牙痛、痛经、关节炎）",
        "side_effects": ["胃肠道不适", "胃溃疡", "肾功能损害", "高血压加重"],
        "allergic_reactions": ["皮疹", "哮喘发作", "过敏休克（罕见）"],
        "contraindicated_groups": ["活动性溃疡", "严重肝肾功能不全", "孕妇晚期",
                                    "心功能不全", "哮喘患者慎用"],
        "contraindications": "活动性消化性溃疡、严重肝肾功能不全、"
                             "妊娠晚期、对NSAID过敏者禁用",
        "interactions": ["华法林", "阿司匹林", "ACEI类降压药", "利尿剂",
                         "锂剂"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },
    {
        "drug_name": "氯吡格雷",
        "medicine_type": "western",
        "ingredients": ["硫酸氢氯吡格雷"],
        "indications": "心肌梗死、缺血性脑卒中、外周动脉疾病抗血小板治疗",
        "side_effects": ["出血", "胃肠道反应", "皮疹", "血小板减少"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["活动性出血", "严重肝功能不全", "孕妇晚期"],
        "contraindications": "活动性出血、严重肝功能不全、对氯吡格雷过敏者禁用",
        "interactions": ["奥美拉唑（降低氯吡格雷活性）", "华法林", "阿司匹林",
                         "NSAID"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 30,
        "source": "人工整理",
    },

    # === 心血管药物（7 条） ===
    {
        "drug_name": "硝苯地平",
        "medicine_type": "western",
        "ingredients": ["硝苯地平"],
        "indications": "高血压、心绞痛、变异型心绞痛",
        "side_effects": ["面部潮红", "下肢水肿", "心悸", "头痛", "头晕"],
        "allergic_reactions": ["皮疹", "瘙痒", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "哺乳期", "严重主动脉瓣狭窄", "心源性休克"],
        "contraindications": "对二氢吡啶类过敏者、严重主动脉瓣狭窄、心源性休克禁用",
        "interactions": ["葡萄柚汁（增加血药浓度）", "利福平（降低疗效）", "麻黄", "甘草"],
        "dietary_taboos": ["服药期间避免饮用葡萄柚汁"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "美托洛尔",
        "medicine_type": "western",
        "ingredients": ["酒石酸美托洛尔", "琥珀酸美托洛尔"],
        "indications": "高血压、心绞痛、心力衰竭、心律失常、心肌梗死后",
        "side_effects": ["心动过缓", "疲劳", "头晕", "支气管痉挛", "性功能减退"],
        "allergic_reactions": ["皮疹"],
        "contraindicated_groups": ["严重心动过缓", "二度及以上房室传导阻滞",
                                    "严重支气管哮喘", "心源性休克"],
        "contraindications": "严重窦性心动过缓、二度及以上房室传导阻滞、"
                             "严重支气管哮喘、心源性休克禁用",
        "interactions": ["维拉帕米（致严重心动过缓）", "地高辛", "麻黄", "西咪替丁"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "依那普利",
        "medicine_type": "western",
        "ingredients": ["依那普利"],
        "indications": "高血压、心力衰竭、糖尿病肾病",
        "side_effects": ["干咳", "高钾血症", "低血压", "肾功能损害", "血管神经性水肿"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "双侧肾动脉狭窄", "严重肾功能不全",
                                    "血管神经性水肿病史"],
        "contraindications": "妊娠期、双侧肾动脉狭窄、有血管神经性水肿病史、"
                             "对 ACE 抑制剂过敏者禁用",
        "interactions": ["保钾利尿剂（致高钾）", "非甾体抗炎药（降低疗效）", "麻黄"],
        "dietary_taboos": ["避免高钾食物（香蕉、橙子等）", "酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "厄贝沙坦",
        "medicine_type": "western",
        "ingredients": ["厄贝沙坦"],
        "indications": "高血压、糖尿病肾病",
        "side_effects": ["头晕", "高钾血症", "肾功能损害", "低血压"],
        "allergic_reactions": ["皮疹", "瘙痒"],
        "contraindicated_groups": ["孕妇", "双侧肾动脉狭窄", "严重肝肾功能不全"],
        "contraindications": "妊娠期、双侧肾动脉狭窄、对厄贝沙坦过敏者禁用",
        "interactions": ["保钾利尿剂", "非甾体抗炎药", "锂剂", "麻黄", "甘草"],
        "dietary_taboos": ["避免高钾食物", "酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "辛伐他汀",
        "medicine_type": "western",
        "ingredients": ["辛伐他汀"],
        "indications": "高胆固醇血症、混合性高脂血症、冠心病预防",
        "side_effects": ["肌肉疼痛", "肝酶升高", "头痛", "横纹肌溶解（罕见）"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "哺乳期", "活动性肝病", "肌病"],
        "contraindications": "妊娠期、哺乳期、活动性肝病、对辛伐他汀过敏者禁用",
        "interactions": ["环孢素", "红霉素", "克拉霉素", "吉非贝齐", "葡萄柚汁", "华法林"],
        "dietary_taboos": ["葡萄柚（西柚）", "酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "瑞舒伐他汀",
        "medicine_type": "western",
        "ingredients": ["瑞舒伐他汀钙"],
        "indications": "高胆固醇血症、混合性高脂血症、动脉粥样硬化",
        "side_effects": ["肌肉疼痛", "肝酶升高", "头痛", "便秘", "横纹肌溶解（罕见）"],
        "allergic_reactions": ["皮疹", "瘙痒"],
        "contraindicated_groups": ["孕妇", "哺乳期", "活动性肝病", "严重肾功能不全"],
        "contraindications": "妊娠期、哺乳期、活动性肝病、严重肾功能不全禁用",
        "interactions": ["环孢素", "吉非贝齐", "华法林", "避孕药"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "替格瑞洛",
        "medicine_type": "western",
        "ingredients": ["替格瑞洛"],
        "indications": "急性冠脉综合征抗血小板治疗",
        "side_effects": ["出血风险", "呼吸困难", "心动过缓", "血尿酸升高"],
        "allergic_reactions": ["皮疹"],
        "contraindicated_groups": ["活动性出血", "颅内出血病史", "严重肝功能不全"],
        "contraindications": "活动性出血、有颅内出血病史、严重肝功能不全禁用",
        "interactions": ["强效 CYP3A 抑制剂", "强效 CYP3A 诱导剂", "阿司匹林（>100mg）",
                         "丹参", "川芎", "全蝎"],
        "dietary_taboos": ["葡萄柚汁", "酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 降糖药（3 条） ===
    {
        "drug_name": "格列美脲",
        "medicine_type": "western",
        "ingredients": ["格列美脲"],
        "indications": "2 型糖尿病",
        "side_effects": ["低血糖", "体重增加", "过敏反应", "肝功能异常"],
        "allergic_reactions": ["皮疹", "瘙痒"],
        "contraindicated_groups": ["1 型糖尿病", "糖尿病酮症酸中毒",
                                    "孕妇", "严重肝肾功能不全"],
        "contraindications": "1 型糖尿病、糖尿病酮症酸中毒、孕妇、"
                             "严重肝肾功能不全禁用",
        "interactions": ["保泰松", "磺胺类", "华法林", "酒精（双硫仑反应）",
                         "人参", "黄芪", "桑叶"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "阿卡波糖",
        "medicine_type": "western",
        "ingredients": ["阿卡波糖"],
        "indications": "2 型糖尿病（餐后高血糖）",
        "side_effects": ["腹胀", "排气增多", "腹泻", "腹痛"],
        "allergic_reactions": ["皮疹", "荨麻疹"],
        "contraindicated_groups": ["严重肝肾功能不全", "慢性肠功能紊乱",
                                    "孕妇", "哺乳期"],
        "contraindications": "严重肝肾功能不全、慢性肠功能紊乱、孕妇禁用",
        "interactions": ["消化酶", "活性炭", "考来烯胺", "麦冬"],
        "dietary_taboos": ["蔗糖及含蔗糖食物"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "胰岛素（普通胰岛素）",
        "medicine_type": "western",
        "ingredients": ["胰岛素（重组人胰岛素）"],
        "indications": "1 型糖尿病、2 型糖尿病口服药失效、糖尿病急性并发症",
        "side_effects": ["低血糖", "注射部位反应", "体重增加", "脂肪萎缩"],
        "allergic_reactions": ["皮疹", "过敏性休克（罕见）"],
        "contraindicated_groups": ["低血糖"],
        "contraindications": "低血糖发作期禁用；对本品过敏者禁用",
        "interactions": ["口服降糖药", "酒精", "β受体阻滞剂（掩盖低血糖症状）",
                         "葛根", "玉米须", "人参"],
        "dietary_taboos": ["酒精（增加低血糖风险）"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 消化系统药物（3 条） ===
    {
        "drug_name": "雷尼替丁",
        "medicine_type": "western",
        "ingredients": ["盐酸雷尼替丁"],
        "indications": "消化性溃疡、反流性食管炎、卓-艾综合征",
        "side_effects": ["头痛", "头晕", "恶心", "皮疹", "肝功能异常"],
        "allergic_reactions": ["皮疹", "过敏休克（罕见）"],
        "contraindicated_groups": ["孕妇", "哺乳期", "严重肝肾功能不全", "卟啉病"],
        "contraindications": "对雷尼替丁过敏者、卟啉病史者禁用；孕妇哺乳期慎用",
        "interactions": ["抗酸药", "华法林", "普鲁卡因胺", "麦芽", "木香", "黄连"],
        "dietary_taboos": ["酒精", "辛辣刺激食物"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "多潘立酮",
        "medicine_type": "western",
        "ingredients": ["多潘立酮"],
        "indications": "消化不良、胃排空延迟、恶心呕吐",
        "side_effects": ["口干", "头痛", "高催乳素血症", "QT 间期延长（罕见）"],
        "allergic_reactions": ["皮疹", "瘙痒"],
        "contraindicated_groups": ["胃肠道出血", "机械性梗阻", "穿孔", "催乳素瘤"],
        "contraindications": "胃肠道出血、机械性梗阻、消化道穿孔、催乳素瘤禁用",
        "interactions": ["抗胆碱药（拮抗）", "红霉素", "酮康唑", "陈皮", "白术"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "蒙脱石散",
        "medicine_type": "western",
        "ingredients": ["蒙脱石"],
        "indications": "急慢性腹泻、肠易激综合征",
        "side_effects": ["便秘（过量）"],
        "allergic_reactions": ["皮疹（罕见）"],
        "contraindicated_groups": ["肠梗阻", "严重便秘"],
        "contraindications": "肠梗阻、严重便秘者禁用",
        "interactions": ["其他口服药物（吸附降低吸收）", "神曲", "参苓白术散"],
        "dietary_taboos": [],
        "dosing_interval_minutes": 120,
        "source": "药品说明书+人工整理",
    },

    # === 解热镇痛药（2 条） ===
    {
        "drug_name": "对乙酰氨基酚",
        "medicine_type": "western",
        "ingredients": ["对乙酰氨基酚"],
        "indications": "发热、轻中度疼痛（头痛、牙痛、关节痛、痛经）",
        "side_effects": ["肝毒性（过量）", "皮疹", "粒细胞减少（罕见）"],
        "allergic_reactions": ["皮疹", "荨麻疹"],
        "contraindicated_groups": ["严重肝功能不全", "酗酒者"],
        "contraindications": "严重肝功能不全、对本品过敏者禁用",
        "interactions": ["华法林", "苯巴比妥", "利福平", "酒精", "板蓝根颗粒"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "双氯芬酸",
        "medicine_type": "western",
        "ingredients": ["双氯芬酸钠"],
        "indications": "关节炎、软组织损伤疼痛、术后疼痛、痛经",
        "side_effects": ["胃肠道反应", "肝酶升高", "头痛", "水肿"],
        "allergic_reactions": ["皮疹", "哮喘发作"],
        "contraindicated_groups": ["活动性溃疡", "严重肝肾功能不全", "孕妇晚期",
                                    "心功能不全", "对 NSAID 过敏"],
        "contraindications": "活动性消化性溃疡、严重肝肾功能不全、"
                             "妊娠晚期、对 NSAID 过敏者禁用",
        "interactions": ["华法林", "阿司匹林", "ACEI 类降压药", "利尿剂", "锂剂"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 抗感染药物（5 条） ===
    {
        "drug_name": "阿莫西林",
        "medicine_type": "western",
        "ingredients": ["阿莫西林"],
        "indications": "呼吸道感染、尿路感染、皮肤软组织感染、幽门螺杆菌根除",
        "side_effects": ["腹泻", "恶心", "皮疹", "过敏休克"],
        "allergic_reactions": ["皮疹", "荨麻疹", "过敏性休克"],
        "contraindicated_groups": ["青霉素过敏者", "传染性单核细胞增多症"],
        "contraindications": "青霉素过敏者禁用；传染性单核细胞增多症禁用",
        "interactions": ["丙磺舒", "口服避孕药", "别嘌醇", "黄连"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "头孢氨苄",
        "medicine_type": "western",
        "ingredients": ["头孢氨苄"],
        "indications": "呼吸道感染、尿路感染、皮肤软组织感染",
        "side_effects": ["腹泻", "恶心", "皮疹", "肝酶升高"],
        "allergic_reactions": ["皮疹", "荨麻疹", "过敏性休克"],
        "contraindicated_groups": ["头孢类过敏者", "青霉素过敏性休克史"],
        "contraindications": "对头孢类过敏者禁用；青霉素过敏性休克史者禁用",
        "interactions": ["丙磺舒", "利尿剂", "酒精（双硫仑反应）", "抗凝药"],
        "dietary_taboos": ["酒精（用药期间及停药后 7 天内）"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "阿奇霉素",
        "medicine_type": "western",
        "ingredients": ["阿奇霉素"],
        "indications": "呼吸道感染、皮肤软组织感染、支原体衣原体感染",
        "side_effects": ["腹泻", "恶心", "腹痛", "QT 间期延长", "肝酶升高"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["大环内酯类过敏", "严重肝功能不全", "QT 间期延长"],
        "contraindications": "对大环内酯类过敏者、严重肝功能不全、QT 间期延长者禁用",
        "interactions": ["抗酸药", "华法林", "地高辛", "麦角胺", "金银花"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "左氧氟沙星",
        "medicine_type": "western",
        "ingredients": ["左氧氟沙星"],
        "indications": "呼吸道感染、尿路感染、皮肤软组织感染、肠道感染",
        "side_effects": ["恶心", "头晕", "肌腱炎（罕见）", "周围神经病变",
                         "QT 间期延长"],
        "allergic_reactions": ["皮疹", "血管神经性水肿", "光敏反应"],
        "contraindicated_groups": ["喹诺酮类过敏", "孕妇", "哺乳期", "18 岁以下",
                                    "癫痫"],
        "contraindications": "对喹诺酮类过敏者、孕妇、哺乳期、18 岁以下青少年、"
                             "癫痫患者禁用",
        "interactions": ["抗酸药（含钙镁铝）", "华法林", "降糖药",
                         "非甾体抗炎药", "牡蛎", "石膏", "连翘"],
        "dietary_taboos": ["乳制品（影响吸收）", "酒精"],
        "dosing_interval_minutes": 120,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "甲硝唑",
        "medicine_type": "western",
        "ingredients": ["甲硝唑"],
        "indications": "厌氧菌感染、滴虫病、阿米巴病、幽门螺杆菌感染",
        "side_effects": ["恶心", "金属味", "头痛", "周围神经病变（长期）"],
        "allergic_reactions": ["皮疹", "荨麻疹"],
        "contraindicated_groups": ["孕妇早期", "哺乳期", "中枢神经系统疾病",
                                    "血液病"],
        "contraindications": "妊娠早期、哺乳期、活动性中枢神经系统疾病、"
                             "血液病禁用",
        "interactions": ["酒精（双硫仑反应）", "华法林", "苯巴比妥", "西咪替丁",
                         "藿香正气水"],
        "dietary_taboos": ["酒精（用药期间及停药后 3 天内）"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 呼吸系统药物（3 条） ===
    {
        "drug_name": "氨溴索",
        "medicine_type": "western",
        "ingredients": ["盐酸氨溴索"],
        "indications": "急慢性支气管炎、痰液粘稠不易咳出",
        "side_effects": ["胃肠道反应", "皮疹", "过敏反应（罕见）"],
        "allergic_reactions": ["皮疹", "过敏性休克（罕见）"],
        "contraindicated_groups": ["对氨溴索过敏者", "妊娠早期"],
        "contraindications": "对氨溴索过敏者、妊娠早期禁用",
        "interactions": ["抗生素（增加肺内浓度）", "镇咳药（痰液阻塞风险）"],
        "dietary_taboos": [],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "沙丁胺醇",
        "medicine_type": "western",
        "ingredients": ["硫酸沙丁胺醇"],
        "indications": "支气管哮喘、慢性阻塞性肺疾病急性发作",
        "side_effects": ["心悸", "震颤", "头痛", "低钾血症", "心动过速"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["对沙丁胺醇过敏者", "肥厚性心肌病", "甲亢"],
        "contraindications": "对沙丁胺醇过敏者、肥厚性梗阻性心肌病禁用；甲亢慎用",
        "interactions": ["β 受体阻滞剂", "单胺氧化酶抑制剂", "利尿剂", "茶碱类"],
        "dietary_taboos": ["酒精", "咖啡因"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "布地奈德",
        "medicine_type": "western",
        "ingredients": ["布地奈德"],
        "indications": "哮喘、慢性阻塞性肺疾病、过敏性鼻炎",
        "side_effects": ["口腔念珠菌感染", "声音嘶哑", "咽部刺激", "咳嗽"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["对布地奈德过敏者", "严重活动性肺结核",
                                    "未控制的感染"],
        "contraindications": "对布地奈德过敏者、严重活动性肺结核、"
                             "未控制的真菌或细菌感染禁用",
        "interactions": ["酮康唑", "伊曲康唑", "利托那韦", "克拉霉素"],
        "dietary_taboos": ["葡萄柚汁"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 神经系统药物（4 条） ===
    {
        "drug_name": "左旋多巴",
        "medicine_type": "western",
        "ingredients": ["左旋多巴"],
        "indications": "帕金森病、帕金森综合征",
        "side_effects": ["恶心呕吐", "体位性低血压", "幻觉", "运动障碍",
                         "心律失常"],
        "allergic_reactions": ["皮疹", "瘙痒"],
        "contraindicated_groups": ["严重精神病", "闭角型青光眼", "孕妇",
                                    "严重心血管病"],
        "contraindications": "严重精神病患者、闭角型青光眼、孕妇、"
                             "严重心血管病患者禁用",
        "interactions": ["维生素 B6", "单胺氧化酶抑制剂", "抗精神病药",
                         "利血平", "高蛋白饮食"],
        "dietary_taboos": ["高蛋白饮食（影响吸收）"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "卡马西平",
        "medicine_type": "western",
        "ingredients": ["卡马西平"],
        "indications": "癫痫、三叉神经痛、双相情感障碍",
        "side_effects": ["头晕", "嗜睡", "共济失调", "皮疹", "粒细胞减少",
                         "肝功能异常"],
        "allergic_reactions": ["皮疹", "Stevens-Johnson 综合征", "过敏性休克"],
        "contraindicated_groups": ["房室传导阻滞", "严重肝功能不全", "骨髓抑制",
                                    "孕妇", "对三环类抗抑郁药过敏"],
        "contraindications": "房室传导阻滞、严重肝功能不全、骨髓抑制、"
                             "有 SJS 病史者禁用",
        "interactions": ["华法林", "苯巴比妥", "苯妥英", "口服避孕药",
                         "葡萄柚汁", "五味子"],
        "dietary_taboos": ["葡萄柚汁", "酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "地西泮",
        "medicine_type": "western",
        "ingredients": ["地西泮"],
        "indications": "焦虑症、失眠、癫痫持续状态、酒精戒断",
        "side_effects": ["嗜睡", "乏力", "共济失调", "依赖性", "呼吸抑制"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "重症肌无力", "严重肝功能不全",
                                    "睡眠呼吸暂停", "急性闭角型青光眼"],
        "contraindications": "孕妇、重症肌无力、严重肝功能不全、"
                             "急性闭角型青光眼禁用",
        "interactions": ["酒精", "阿片类", "抗精神病药", "西咪替丁",
                         "酸枣仁", "远志"],
        "dietary_taboos": ["酒精", "葡萄柚汁"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "苯妥英钠",
        "medicine_type": "western",
        "ingredients": ["苯妥英钠"],
        "indications": "癫痫大发作、局限性发作、心律失常",
        "side_effects": ["牙龈增生", "多毛", "共济失调", "皮疹", "巨幼细胞贫血"],
        "allergic_reactions": ["皮疹", "Stevens-Johnson 综合征"],
        "contraindicated_groups": ["房室传导阻滞", "严重心动过缓", "孕妇",
                                    "对乙内酰脲类过敏"],
        "contraindications": "房室传导阻滞、严重心动过缓、孕妇、"
                             "对乙内酰脲类过敏者禁用",
        "interactions": ["华法林", "异烟肼", "氯霉素", "西咪替丁", "苯巴比妥"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 精神系统药物（4 条） ===
    {
        "drug_name": "舍曲林",
        "medicine_type": "western",
        "ingredients": ["盐酸舍曲林"],
        "indications": "抑郁症、强迫症、社交焦虑障碍、创伤后应激障碍",
        "side_effects": ["恶心", "腹泻", "失眠", "性功能障碍", "震颤"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "哺乳期", "与单胺氧化酶抑制剂合用",
                                    "癫痫"],
        "contraindications": "与单胺氧化酶抑制剂合用禁用；"
                             "对舍曲林过敏者禁用",
        "interactions": ["单胺氧化酶抑制剂", "色氨酸", "华法林", "锂剂",
                         "贯叶连翘", "柴胡", "逍遥丸"],
        "dietary_taboos": ["酒精", "葡萄柚汁"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "帕罗西汀",
        "medicine_type": "western",
        "ingredients": ["盐酸帕罗西汀"],
        "indications": "抑郁症、强迫症、惊恐障碍、社交焦虑障碍",
        "side_effects": ["恶心", "嗜睡", "出汗", "性功能障碍", "体重增加"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["孕妇", "哺乳期", "与单胺氧化酶抑制剂合用",
                                    "躁狂期"],
        "contraindications": "与单胺氧化酶抑制剂合用禁用；"
                             "对帕罗西汀过敏者禁用",
        "interactions": ["单胺氧化酶抑制剂", "色氨酸", "华法林", "三环类抗抑郁药",
                         "郁金"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "奥氮平",
        "medicine_type": "western",
        "ingredients": ["奥氮平"],
        "indications": "精神分裂症、双相情感障碍躁狂发作",
        "side_effects": ["体重增加", "嗜睡", "代谢综合征", "锥体外系反应",
                         "体位性低血压"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["窄角型青光眼", "严重心功能不全",
                                    "孕妇", "哺乳期"],
        "contraindications": "窄角型青光眼、严重心功能不全、对奥氮平过敏者禁用",
        "interactions": ["中枢神经抑制剂", "抗高血压药", "卡马西平",
                         "氟伏沙明", "吸烟（降低血药浓度）"],
        "dietary_taboos": ["酒精", "葡萄柚汁"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "利培酮",
        "medicine_type": "western",
        "ingredients": ["利培酮"],
        "indications": "精神分裂症、双相情感障碍躁狂发作、孤独症易激惹",
        "side_effects": ["锥体外系反应", "体重增加", "嗜睡", "高催乳素血症",
                         "体位性低血压"],
        "allergic_reactions": ["皮疹", "血管神经性水肿"],
        "contraindicated_groups": ["对利培酮过敏者", "痴呆伴路易小体",
                                    "严重心功能不全"],
        "contraindications": "对利培酮过敏者禁用；痴呆伴路易小体者慎用",
        "interactions": ["卡马西平", "利福平", "氟西汀", "帕罗西汀",
                         "中枢神经抑制剂"],
        "dietary_taboos": ["酒精"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },

    # === 内分泌药物（2 条） ===
    {
        "drug_name": "左甲状腺素",
        "medicine_type": "western",
        "ingredients": ["左甲状腺素钠"],
        "indications": "甲状腺功能减退症、甲状腺癌术后替代治疗",
        "side_effects": ["心悸", "失眠", "体重下降", "骨质疏松（长期过量）"],
        "allergic_reactions": ["皮疹（罕见）"],
        "contraindicated_groups": ["甲亢未控制", "急性心肌梗死", "肾上腺功能不全"],
        "contraindications": "甲亢未控制、急性心肌梗死、肾上腺功能不全未纠正者禁用",
        "interactions": ["铁剂", "钙剂", "抗酸药", "华法林", "海藻", "浙贝母"],
        "dietary_taboos": ["大豆制品", "高纤维食物", "咖啡"],
        "dosing_interval_minutes": 60,
        "source": "药品说明书+人工整理",
    },
    {
        "drug_name": "甲巯咪唑",
        "medicine_type": "western",
        "ingredients": ["甲巯咪唑"],
        "indications": "甲状腺功能亢进症、甲亢术前准备",
        "side_effects": ["皮疹", "粒细胞减少", "肝功能异常", "关节痛",
                         "瘙痒"],
        "allergic_reactions": ["皮疹", "荨麻疹", "粒细胞缺乏（严重）"],
        "contraindicated_groups": ["孕妇早期", "哺乳期", "严重肝功能不全",
                                    "粒细胞减少者"],
        "contraindications": "严重肝功能不全、粒细胞减少者、对甲巯咪唑过敏者禁用",
        "interactions": ["抗凝药", "洋地黄", "阿托品", "海藻", "昆布",
                         "黄药子", "夏枯草", "浙贝母"],
        "dietary_taboos": ["高碘食物（海带、紫菜等）"],
        "dosing_interval_minutes": 0,
        "source": "药品说明书+人工整理",
    },
]


def crawl_drug_clinical_info(drug_names: list) -> list:
    """爬取药品临床信息

    优先调用丁香园 API，若无 API Key 则使用人工整理的备用数据

    Args:
        drug_names: 药品名列表

    Returns:
        药品临床信息列表
    """
    if DXY_API_KEY:
        print("[INFO] 检测到丁香园 API Key，调用 API 获取药品信息...")
        all_drugs = []
        for drug_name in drug_names:
            search_result = search_drug_by_name(drug_name)
            if search_result and search_result.get("data"):
                drug_id = search_result["data"][0].get("drug_id")
                if drug_id:
                    detail = get_drug_detail(drug_id)
                    if detail:
                        all_drugs.append(detail)
                    time.sleep(REQUEST_INTERVAL)
        return all_drugs
    else:
        print("[WARN] 未检测到丁香园 API Key（DXY_API_KEY 环境变量），使用人工整理的备用数据")
        return COMMON_WESTERN_DRUGS


def main():
    """主入口"""
    print("=" * 60)
    print("丁香园用药助手 API 客户端")
    print("=" * 60)

    if DXY_API_KEY:
        print("[INFO] API Key 已配置")
    else:
        print("[WARN] 未配置 DXY_API_KEY 环境变量")
        print("[WARN] 将使用人工整理的备用数据（覆盖 8 种常见西药）")

    drug_names = [d["drug_name"] for d in COMMON_WESTERN_DRUGS]
    drugs = crawl_drug_clinical_info(drug_names)

    output_file = OUTPUT_DIR / "dxy_drugs.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(drugs, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(drugs)} 条药品临床信息")


if __name__ == "__main__":
    main()
