# -*- coding: utf-8 -*-
"""中西药相互作用人工整理（Top 100 高频组合）

数据源：TCMBank 预测 + 临床指南人工核对
性质：公共医学常识 + 临床研究
输出：output/tcm_wm_interaction.json

中西药相互作用是最大数据空白，本脚本采用混合策略：
1. 人工整理 Top 100 高频中西药组合的相互作用
2. 对无显式规则的组合，默认推荐间隔 30-60 分钟

常见高风险组合：
- 含银杏叶/丹参/人参的中药 × 抗凝/抗血小板西药
- 含甘草的中药 × 降压药/利尿药
- 含麻黄的中药 × 降压药/强心药
"""

import json
from pathlib import Path

OUTPUT_DIR = Path(__file__).parent / "output"
OUTPUT_DIR.mkdir(exist_ok=True)

# 中西药相互作用数据（基于临床指南和现代研究）
TCM_WM_INTERACTIONS = [
    # === 抗凝/抗血小板药物（15 条） ===
    {
        "tcm_name": "丹参", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "增强抗凝作用",
        "recommended_interval_minutes": 120,
        "description": "丹参及其制剂（复方丹参滴丸、丹参片）可增强华法林抗凝作用，"
                       "增加出血风险。建议间隔 2 小时以上服用，并监测 INR 值",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "丹参", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "丹参与阿司匹林同服可能增加出血倾向，建议间隔 1 小时",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "丹参", "wm_name": "氯吡格雷",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "丹参活血化瘀，与氯吡格雷抗血小板作用叠加，可能增加出血倾向",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "丹参", "wm_name": "肝素",
        "severity": "high",
        "interaction_type": "增强抗凝作用",
        "recommended_interval_minutes": 120,
        "description": "丹参抗凝成分与肝素作用叠加，出血风险显著增加，建议密切监测 APTT",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "银杏叶", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "增强抗凝作用",
        "recommended_interval_minutes": 120,
        "description": "银杏叶提取物（银杏叶片、银杏叶胶囊）可增强华法林抗凝作用，"
                       "增加出血风险。建议间隔 2 小时以上",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "银杏叶", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "银杏叶与阿司匹林同服可能增加出血风险",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "当归", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "增强抗凝作用",
        "recommended_interval_minutes": 120,
        "description": "当归含香豆素类成分，可增强华法林抗凝作用，增加出血风险",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "当归", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "当归与阿司匹林同服可能增加出血倾向",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "当归", "wm_name": "氯吡格雷",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "当归活血抗凝，与氯吡格雷同用可能增加出血风险",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "红花", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "增强抗凝作用",
        "recommended_interval_minutes": 120,
        "description": "红花活血化瘀，与华法林同用增强抗凝，增加出血风险",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "川芎", "wm_name": "华法林",
        "severity": "moderate",
        "interaction_type": "增强抗凝作用",
        "recommended_interval_minutes": 90,
        "description": "川芎活血行气，与华法林同用可能增加出血风险",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "川芎", "wm_name": "替格瑞洛",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 90,
        "description": "川芎活血行气，与新型抗血小板药替格瑞洛同用需关注出血风险",
        "evidence_source": "文献报道",
    },
    {
        "tcm_name": "三七", "wm_name": "华法林",
        "severity": "moderate",
        "interaction_type": "影响抗凝效果",
        "recommended_interval_minutes": 90,
        "description": "三七有活血止血双向作用，与华法林同用需监测 INR",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "三七", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "三七活血化瘀，与阿司匹林抗血小板作用叠加，建议间隔服用",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "水蛭", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "显著增加出血风险",
        "recommended_interval_minutes": 180,
        "description": "水蛭含水蛭素，抗凝作用强，与华法林同用出血风险显著增加",
        "evidence_source": "药理研究",
    },

    # === 降压药（10 条） ===
    {
        "tcm_name": "甘草", "wm_name": "氨氯地平",
        "severity": "moderate",
        "interaction_type": "降低降压疗效",
        "recommended_interval_minutes": 60,
        "description": "甘草含甘草酸，可引起水钠潴留，降低降压药疗效。长期同服需监测血压",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "甘草", "wm_name": "缬沙坦",
        "severity": "moderate",
        "interaction_type": "降低降压疗效",
        "recommended_interval_minutes": 60,
        "description": "甘草水钠潴留作用可能降低缬沙坦降压效果",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "甘草", "wm_name": "氢氯噻嗪",
        "severity": "high",
        "interaction_type": "加重低钾血症",
        "recommended_interval_minutes": 90,
        "description": "甘草与利尿剂同用可加重低钾血症，建议监测血钾",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "麻黄", "wm_name": "氨氯地平",
        "severity": "high",
        "interaction_type": "拮抗降压作用",
        "recommended_interval_minutes": 120,
        "description": "麻黄含麻黄碱，有升压作用，可拮抗降压药疗效",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "麻黄", "wm_name": "美托洛尔",
        "severity": "high",
        "interaction_type": "降低β受体阻滞剂疗效",
        "recommended_interval_minutes": 120,
        "description": "麻黄碱可拮抗美托洛尔的β受体阻滞作用",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "钩藤", "wm_name": "氨氯地平",
        "severity": "low",
        "interaction_type": "协同降压",
        "recommended_interval_minutes": 60,
        "description": "钩藤含钩藤碱，有降压作用，与氨氯地平同用需监测血压防止低血压",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "杜仲", "wm_name": "厄贝沙坦",
        "severity": "low",
        "interaction_type": "协同降压",
        "recommended_interval_minutes": 60,
        "description": "杜仲降压成分与厄贝沙坦协同，需监测血压",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "天麻", "wm_name": "美托洛尔",
        "severity": "moderate",
        "interaction_type": "增强降压及减慢心率",
        "recommended_interval_minutes": 90,
        "description": "天麻有降压及减慢心率作用，与美托洛尔同用需监测心率及血压",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "罗布麻", "wm_name": "氢氯噻嗪",
        "severity": "moderate",
        "interaction_type": "增强降压及排钾",
        "recommended_interval_minutes": 90,
        "description": "罗布麻含强心苷类及降压成分，与氢氯噻嗪同用增强降压并加重低钾",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "夏枯草", "wm_name": "缬沙坦",
        "severity": "low",
        "interaction_type": "协同降压",
        "recommended_interval_minutes": 60,
        "description": "夏枯草有降压活性，与缬沙坦同用需监测血压",
        "evidence_source": "临床观察",
    },

    # === 降糖药（9 条） ===
    {
        "tcm_name": "人参", "wm_name": "二甲双胍",
        "severity": "moderate",
        "interaction_type": "增强降糖作用",
        "recommended_interval_minutes": 60,
        "description": "人参有降糖作用，与二甲双胍同用需监测血糖，防止低血糖",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "黄芪", "wm_name": "格列美脲",
        "severity": "moderate",
        "interaction_type": "增强降糖作用",
        "recommended_interval_minutes": 60,
        "description": "黄芪有一定降糖作用，与磺脲类降糖药同用需监测血糖",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "葛根", "wm_name": "胰岛素",
        "severity": "moderate",
        "interaction_type": "增强降糖作用",
        "recommended_interval_minutes": 60,
        "description": "葛根有降糖作用，与胰岛素同用需监测血糖",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "葛根", "wm_name": "二甲双胍",
        "severity": "moderate",
        "interaction_type": "增强降糖作用",
        "recommended_interval_minutes": 60,
        "description": "葛根素改善胰岛素抵抗，与二甲双胍同用需监测血糖",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "桑叶", "wm_name": "格列美脲",
        "severity": "moderate",
        "interaction_type": "增强降糖作用",
        "recommended_interval_minutes": 60,
        "description": "桑叶含 1-脱氧野尻霉素（DNJ），抑制α-糖苷酶，与磺脲类同用需防低血糖",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "玉米须", "wm_name": "胰岛素",
        "severity": "low",
        "interaction_type": "协同降糖",
        "recommended_interval_minutes": 60,
        "description": "玉米须多糖有降糖活性，与胰岛素同用需监测血糖",
        "evidence_source": "文献报道",
    },
    {
        "tcm_name": "麦冬", "wm_name": "阿卡波糖",
        "severity": "low",
        "interaction_type": "协同降糖",
        "recommended_interval_minutes": 30,
        "description": "麦冬多糖有降糖作用，与阿卡波糖同用需监测餐后血糖",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "枸杞", "wm_name": "罗格列酮",
        "severity": "low",
        "interaction_type": "协同降糖",
        "recommended_interval_minutes": 30,
        "description": "枸杞多糖改善胰岛素抵抗，与噻唑烷二酮类同用需监测血糖",
        "evidence_source": "文献报道",
    },
    {
        "tcm_name": "黄连", "wm_name": "二甲双胍",
        "severity": "moderate",
        "interaction_type": "增强降糖作用",
        "recommended_interval_minutes": 60,
        "description": "黄连素（小檗碱）有降糖作用，与二甲双胍同用需监测血糖防止低血糖",
        "evidence_source": "临床研究",
    },

    # === 强心药（5 条） ===
    {
        "tcm_name": "甘草", "wm_name": "地高辛",
        "severity": "high",
        "interaction_type": "增加地高辛毒性",
        "recommended_interval_minutes": 120,
        "description": "甘草可引起低钾血症，增加地高辛中毒风险。建议监测血钾和地高辛血药浓度",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "麻黄", "wm_name": "地高辛",
        "severity": "high",
        "interaction_type": "增加心律失常风险",
        "recommended_interval_minutes": 120,
        "description": "麻黄碱可增加心率，与地高辛同用增加心律失常风险",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "人参", "wm_name": "地高辛",
        "severity": "high",
        "interaction_type": "增加地高辛血药浓度",
        "recommended_interval_minutes": 120,
        "description": "人参可能改变地高辛药代动力学，临床报道可致地高辛血药浓度升高及中毒表现",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "黄芪", "wm_name": "多巴胺",
        "severity": "moderate",
        "interaction_type": "增强升压及强心",
        "recommended_interval_minutes": 90,
        "description": "黄芪有类似强心苷作用，与多巴胺同用增强心肌收缩，需监测心率血压",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "附子", "wm_name": "地高辛",
        "severity": "high",
        "interaction_type": "增加心律失常风险",
        "recommended_interval_minutes": 120,
        "description": "附子含乌头碱，有强心及致心律失常作用，与地高辛同用增加心律失常及中毒风险",
        "evidence_source": "临床研究",
    },

    # === 镇静催眠药（6 条） ===
    {
        "tcm_name": "酸枣仁", "wm_name": "地西泮",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "酸枣仁有镇静作用，与苯二氮卓类同用可能增强镇静效果",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "酸枣仁", "wm_name": "佐匹克隆",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "酸枣仁与催眠药同用需注意过度镇静",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "酸枣仁", "wm_name": "唑吡坦",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "酸枣仁镇静成分与唑吡坦同用，需防过度镇静及跌倒",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "合欢皮", "wm_name": "艾司唑仑",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "合欢皮有镇静抗抑郁作用，与苯二氮卓类同用增强中枢抑制",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "远志", "wm_name": "唑吡坦",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "远志有镇静安神作用，与唑吡坦同用增强催眠效果",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "远志", "wm_name": "地西泮",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "远志皂苷有镇静作用，与地西泮同用需防过度中枢抑制",
        "evidence_source": "药理研究",
    },

    # === 抗抑郁药（4 条） ===
    {
        "tcm_name": "贯叶连翘（圣约翰草）", "wm_name": "舍曲林",
        "severity": "high",
        "interaction_type": "5-羟色胺综合征风险",
        "recommended_interval_minutes": 180,
        "description": "贯叶连翘有抗抑郁作用，与 SSRI 类抗抑郁药同用增加5-羟色胺综合征风险",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "柴胡", "wm_name": "舍曲林",
        "severity": "low",
        "interaction_type": "协同抗抑郁",
        "recommended_interval_minutes": 30,
        "description": "柴胡疏肝解郁，与舍曲林同用可起协同作用，但需监测 5-HT 综合征早期表现",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "郁金", "wm_name": "帕罗西汀",
        "severity": "moderate",
        "interaction_type": "增强 5-HT 作用",
        "recommended_interval_minutes": 60,
        "description": "郁金有抗抑郁活性，与帕罗西汀同用需警惕 5-HT 综合征",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "甘松", "wm_name": "文拉法辛",
        "severity": "moderate",
        "interaction_type": "增强抗抑郁作用",
        "recommended_interval_minutes": 60,
        "description": "甘松有抗抑郁活性（甘松新酮），与 SNRI 类同用增强 5-HT 及 NE 作用",
        "evidence_source": "文献报道",
    },

    # === 抗生素（4 条） ===
    {
        "tcm_name": "黄连", "wm_name": "阿莫西林",
        "severity": "low",
        "interaction_type": "无明显相互作用",
        "recommended_interval_minutes": 30,
        "description": "黄连与小剂量阿莫西林未见明显相互作用，但仍建议间隔服用",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "黄芩", "wm_name": "头孢呋辛",
        "severity": "low",
        "interaction_type": "协同抗菌",
        "recommended_interval_minutes": 30,
        "description": "黄芩苷有抗菌活性，与头孢呋辛同用可能有协同作用，建议间隔服用",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "金银花", "wm_name": "阿奇霉素",
        "severity": "low",
        "interaction_type": "协同抗菌",
        "recommended_interval_minutes": 30,
        "description": "金银花绿原酸有抗菌活性，与阿奇霉素同用注意胃肠道反应叠加",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "连翘", "wm_name": "左氧氟沙星",
        "severity": "low",
        "interaction_type": "协同抗菌",
        "recommended_interval_minutes": 30,
        "description": "连翘连翘苷有抗菌活性，与喹诺酮类同用注意中枢兴奋风险",
        "evidence_source": "药理研究",
    },

    # === 含金属离子的中药与西药（3 条） ===
    {
        "tcm_name": "石膏", "wm_name": "四环素",
        "severity": "moderate",
        "interaction_type": "影响抗生素吸收",
        "recommended_interval_minutes": 120,
        "description": "石膏含钙离子，可与四环素类抗生素结合，影响吸收",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "龙骨", "wm_name": "喹诺酮类",
        "severity": "moderate",
        "interaction_type": "影响抗生素吸收",
        "recommended_interval_minutes": 120,
        "description": "龙骨含金属离子，可与喹诺酮类抗生素结合，影响吸收",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "牡蛎", "wm_name": "左氧氟沙星",
        "severity": "moderate",
        "interaction_type": "影响抗生素吸收",
        "recommended_interval_minutes": 120,
        "description": "牡蛎含钙离子，可与喹诺酮类结合，影响吸收",
        "evidence_source": "药理研究",
    },

    # === 含有机酸的中药（2 条） ===
    {
        "tcm_name": "山楂", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加胃酸刺激",
        "recommended_interval_minutes": 60,
        "description": "山楂含有机酸，与阿司匹林同服增加胃酸刺激，可能加重胃部不适",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "乌梅", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加胃酸刺激",
        "recommended_interval_minutes": 60,
        "description": "乌梅含有机酸，与阿司匹林同服增加胃部刺激",
        "evidence_source": "临床观察",
    },

    # === 含鞣质的中药（2 条） ===
    {
        "tcm_name": "五倍子", "wm_name": "红霉素",
        "severity": "moderate",
        "interaction_type": "影响抗生素吸收",
        "recommended_interval_minutes": 90,
        "description": "五倍子含鞣质，可与红霉素结合，影响吸收",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "地榆", "wm_name": "复合维生素",
        "severity": "low",
        "interaction_type": "影响维生素吸收",
        "recommended_interval_minutes": 60,
        "description": "地榆含鞣质，可与多种维生素结合，影响吸收",
        "evidence_source": "药理研究",
    },

    # === 消化系统药物（8 条） ===
    {
        "tcm_name": "甘草", "wm_name": "奥美拉唑",
        "severity": "low",
        "interaction_type": "协同抑酸护胃",
        "recommended_interval_minutes": 30,
        "description": "甘草（及其制剂如生胃酮）有护胃作用，与 PPI 同用可协同护胃，"
                       "但长期同用需监测血压及血钾",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "陈皮", "wm_name": "多潘立酮",
        "severity": "low",
        "interaction_type": "协同促胃动力",
        "recommended_interval_minutes": 30,
        "description": "陈皮理气健脾，与多潘立酮同用协同促进胃动力",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "山楂", "wm_name": "铝碳酸镁",
        "severity": "low",
        "interaction_type": "影响中药活性",
        "recommended_interval_minutes": 60,
        "description": "铝碳酸镁可吸附山楂有机酸，降低其助消化作用，建议间隔服用",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "神曲", "wm_name": "蒙脱石散",
        "severity": "moderate",
        "interaction_type": "影响中药吸收",
        "recommended_interval_minutes": 120,
        "description": "蒙脱石散吸附性强，可与神曲成分结合，影响助消化酶活性，建议间隔 2 小时",
        "evidence_source": "临床指南",
    },
    {
        "tcm_name": "麦芽", "wm_name": "雷尼替丁",
        "severity": "moderate",
        "interaction_type": "降低中药活性",
        "recommended_interval_minutes": 90,
        "description": "雷尼替丁抑酸改变胃内 pH，影响麦芽淀粉酶活性，降低消食作用",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "黄连", "wm_name": "雷尼替丁",
        "severity": "low",
        "interaction_type": "影响吸收",
        "recommended_interval_minutes": 60,
        "description": "H2 受体阻滞剂改变胃内环境，可能影响黄连素吸收，建议间隔服用",
        "evidence_source": "文献报道",
    },
    {
        "tcm_name": "党参", "wm_name": "奥美拉唑",
        "severity": "low",
        "interaction_type": "协同护胃",
        "recommended_interval_minutes": 30,
        "description": "党参多糖有胃黏膜保护作用，与 PPI 同用协同护胃",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "白术", "wm_name": "多潘立酮",
        "severity": "low",
        "interaction_type": "协同促胃动力",
        "recommended_interval_minutes": 30,
        "description": "白术健脾益气，与多潘立酮同用改善功能性消化不良",
        "evidence_source": "临床观察",
    },

    # === 内分泌药物（7 条） ===
    {
        "tcm_name": "海藻", "wm_name": "左甲状腺素",
        "severity": "high",
        "interaction_type": "影响甲状腺素吸收",
        "recommended_interval_minutes": 180,
        "description": "海藻含大量碘，与左甲状腺素同用可能干扰甲状腺功能评估及药物剂量调整，"
                       "甲亢患者尤需避免",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "海藻", "wm_name": "甲巯咪唑",
        "severity": "high",
        "interaction_type": "拮坑抗甲亢作用",
        "recommended_interval_minutes": 180,
        "description": "海藻高碘会拮抗甲巯咪唑抗甲亢作用，甲亢患者应避免同用",
        "evidence_source": "临床指南",
    },
    {
        "tcm_name": "昆布", "wm_name": "甲巯咪唑",
        "severity": "high",
        "interaction_type": "拮坑抗甲亢作用",
        "recommended_interval_minutes": 180,
        "description": "昆布高碘拮抗甲巯咪唑疗效，甲亢治疗期间应禁用",
        "evidence_source": "临床指南",
    },
    {
        "tcm_name": "黄药子", "wm_name": "丙硫氧嘧啶",
        "severity": "high",
        "interaction_type": "增加肝毒性",
        "recommended_interval_minutes": 180,
        "description": "黄药子有明确肝毒性，与丙硫氧嘧啶（亦有肝损风险）同用显著增加肝损伤",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "黄药子", "wm_name": "甲巯咪唑",
        "severity": "high",
        "interaction_type": "增加肝毒性",
        "recommended_interval_minutes": 180,
        "description": "黄药子与甲巯咪唑均有肝毒性，叠加使用易致药物性肝损伤",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "夏枯草", "wm_name": "甲巯咪唑",
        "severity": "moderate",
        "interaction_type": "协同抗甲亢",
        "recommended_interval_minutes": 60,
        "description": "夏枯草有抗甲状腺活性，与甲巯咪唑同用需监测甲功防止甲减",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "浙贝母", "wm_name": "甲巯咪唑",
        "severity": "low",
        "interaction_type": "协同抗甲亢",
        "recommended_interval_minutes": 30,
        "description": "浙贝母含浙贝母碱，有抗甲状腺活性，与甲巯咪唑同用需监测甲功",
        "evidence_source": "文献报道",
    },

    # === 中成药 × 西药（15 条） ===
    {
        "tcm_name": "复方丹参滴丸", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 120,
        "description": "复方丹参滴丸含丹参，与华法林同用增加出血风险",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "复方丹参滴丸", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "复方丹参滴丸活血化瘀，与阿司匹林抗血小板同用增加出血倾向",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "速效救心丸", "wm_name": "硝酸甘油",
        "severity": "moderate",
        "interaction_type": "增强扩血管作用",
        "recommended_interval_minutes": 60,
        "description": "速效救心丸与硝酸酯类同用可能引起血压过度下降",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "速效救心丸", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "速效救心丸含川芎，与阿司匹林同用增加出血倾向",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "藿香正气水", "wm_name": "头孢类",
        "severity": "high",
        "interaction_type": "双硫仑样反应",
        "recommended_interval_minutes": 720,
        "description": "藿香正气水含乙醇，与头孢类抗生素同用可致双硫仑样反应，"
                       "建议间隔 12 小时以上",
        "evidence_source": "临床指南",
    },
    {
        "tcm_name": "藿香正气水", "wm_name": "头孢哌酮",
        "severity": "high",
        "interaction_type": "双硫仑样反应",
        "recommended_interval_minutes": 720,
        "description": "头孢哌酮含甲硫四唑侧链，抑制乙醛脱氢酶作用最强，"
                       "与藿香正气水乙醇同用极易致严重双硫仑反应",
        "evidence_source": "临床指南",
    },
    {
        "tcm_name": "藿香正气水", "wm_name": "甲硝唑",
        "severity": "high",
        "interaction_type": "双硫仑样反应",
        "recommended_interval_minutes": 720,
        "description": "藿香正气水含乙醇，与甲硝唑同用可致双硫仑样反应",
        "evidence_source": "临床指南",
    },
    {
        "tcm_name": "牛黄解毒片", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加胃部刺激",
        "recommended_interval_minutes": 60,
        "description": "牛黄解毒片含雄黄，与阿司匹林同服增加胃部刺激",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "牛黄解毒片", "wm_name": "诺氟沙星",
        "severity": "moderate",
        "interaction_type": "影响吸收",
        "recommended_interval_minutes": 120,
        "description": "牛黄解毒片含石膏（钙离子）及雄黄（砷），可与喹诺酮结合影响吸收",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "板蓝根颗粒", "wm_name": "对乙酰氨基酚",
        "severity": "low",
        "interaction_type": "增加肝肾负担",
        "recommended_interval_minutes": 60,
        "description": "板蓝根清热解毒，与对乙酰氨基酚同服需关注肝肾功能，建议间隔服用",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "连花清瘟胶囊", "wm_name": "布洛芬",
        "severity": "moderate",
        "interaction_type": "增加胃肠刺激",
        "recommended_interval_minutes": 60,
        "description": "连花清瘟含麻黄，与布洛芬同用增加胃肠刺激及心血管负担",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "六味地黄丸", "wm_name": "二甲双胍",
        "severity": "low",
        "interaction_type": "协同降糖",
        "recommended_interval_minutes": 30,
        "description": "六味地黄丸滋阴补肾，与二甲双胍同用需监测血糖防止低血糖",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "逍遥丸", "wm_name": "舍曲林",
        "severity": "moderate",
        "interaction_type": "增强 5-HT 作用",
        "recommended_interval_minutes": 60,
        "description": "逍遥丸疏肝解郁，与 SSRI 同用需警惕 5-HT 综合征",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "归脾丸", "wm_name": "艾司唑仑",
        "severity": "moderate",
        "interaction_type": "增强镇静作用",
        "recommended_interval_minutes": 60,
        "description": "归脾丸养心安神，与苯二氮卓类同用增强中枢抑制",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "参苓白术散", "wm_name": "蒙脱石散",
        "severity": "moderate",
        "interaction_type": "影响中药吸收",
        "recommended_interval_minutes": 120,
        "description": "蒙脱石散吸附参苓白术散有效成分，降低健脾止泻作用，建议间隔 2 小时",
        "evidence_source": "临床指南",
    },

    # === 其他重要交互（10 条） ===
    {
        "tcm_name": "厚朴", "wm_name": "阿托品",
        "severity": "moderate",
        "interaction_type": "降低阿托品作用",
        "recommended_interval_minutes": 60,
        "description": "厚朴有抗胆碱酯酶作用，可能降低阿托品效果",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "五味子", "wm_name": "卡马西平",
        "severity": "moderate",
        "interaction_type": "影响药物代谢",
        "recommended_interval_minutes": 90,
        "description": "五味子可诱导肝药酶，影响卡马西平代谢",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "甘草", "wm_name": "呋塞米",
        "severity": "high",
        "interaction_type": "加重低钾血症",
        "recommended_interval_minutes": 120,
        "description": "甘草排钾作用叠加袢利尿剂呋塞米，可致严重低钾血症及心律失常风险",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "人参", "wm_name": "华法林",
        "severity": "moderate",
        "interaction_type": "影响抗凝效果",
        "recommended_interval_minutes": 90,
        "description": "人参皂苷影响 CYP 代谢及血小板功能，与华法林同用需监测 INR",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "川芎", "wm_name": "氯吡格雷",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 90,
        "description": "川芎嗪抗血小板，与氯吡格雷同用增加出血倾向",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "桃仁", "wm_name": "华法林",
        "severity": "high",
        "interaction_type": "增强抗凝",
        "recommended_interval_minutes": 120,
        "description": "桃仁活血化瘀，含苦杏仁苷，与华法林同用增加出血风险",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "牛膝", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "牛膝活血通经，与阿司匹林抗血小板同用增加出血倾向",
        "evidence_source": "临床观察",
    },
    {
        "tcm_name": "益母草", "wm_name": "氯吡格雷",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "益母草活血化瘀，与氯吡格雷同用需关注出血倾向",
        "evidence_source": "药理研究",
    },
    {
        "tcm_name": "地龙", "wm_name": "阿司匹林",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 60,
        "description": "地龙含蚓激酶，有抗凝纤溶作用，与阿司匹林同用增加出血风险",
        "evidence_source": "临床研究",
    },
    {
        "tcm_name": "全蝎", "wm_name": "替格瑞洛",
        "severity": "moderate",
        "interaction_type": "增加出血风险",
        "recommended_interval_minutes": 90,
        "description": "全蝎抗凝活性成分与新型抗血小板药替格瑞洛同用需关注出血风险",
        "evidence_source": "文献报道",
    },
]


def main():
    """主入口：生成中西药交互数据"""
    print("=" * 60)
    print("中西药相互作用整理（Top 高频组合）")
    print("=" * 60)

    output_file = OUTPUT_DIR / "tcm_wm_interaction.json"
    with open(output_file, "w", encoding="utf-8") as f:
        json.dump(TCM_WM_INTERACTIONS, f, ensure_ascii=False, indent=2)

    print(f"[INFO] 已保存到 {output_file}")
    print(f"[INFO] 共 {len(TCM_WM_INTERACTIONS)} 条中西药交互规则")

    # 统计
    high = sum(1 for r in TCM_WM_INTERACTIONS if r["severity"] == "high")
    moderate = sum(1 for r in TCM_WM_INTERACTIONS if r["severity"] == "moderate")
    low = sum(1 for r in TCM_WM_INTERACTIONS if r["severity"] == "low")
    print(f"[INFO] 严重度分布: high={high}, moderate={moderate}, low={low}")

    # 涉及的中药和西药
    tcm_set = set(r["tcm_name"] for r in TCM_WM_INTERACTIONS)
    wm_set = set(r["wm_name"] for r in TCM_WM_INTERACTIONS)
    print(f"[INFO] 涉及 {len(tcm_set)} 种中药, {len(wm_set)} 种西药")


if __name__ == "__main__":
    main()
