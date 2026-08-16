-- 数据来源元信息种子数据
-- 记录每个数据源的 license、引用、最近更新时间，供前端「我的 → 数据来源」页面展示

INSERT IGNORE INTO data_sources
  (source_name, source_type, target_table, record_count, license, reference_url, citation, last_updated)
VALUES
-- ===== 开源数据源 =====
('Wikidata', 'open', 'tcm_herbs', 30, 'CC-BY-SA', 'https://www.wikidata.org/wiki/Q188854', 'Wikidata 中药条目（traditional Chinese medicine），通过 SPARQL 端点查询，补足以《中药学》教材人工整理', '2026-07-21 00:00:00'),
('TCMSP', 'open', 'tcm_herbs', 10, '学术开放', 'https://tcmsp-e.com/', 'TCMSP 中药系统药理学数据库（Journal of Cheminformatics 2014），含 OB/DL 评价的活性成分', '2026-07-21 00:00:00'),
('Open Food Facts', 'open', 'food_items', 0, 'ODbL', 'https://world.openfoodfacts.org/', 'Open Food Facts 开放食品数据库（ODbL License），含包装食品营养信息', '2026-07-21 00:00:00'),
('国家基本药物目录', 'open', 'drug_clinical_info', 100, '政府公开数据', 'https://www.nmpa.gov.cn/', '国家基本药物目录 2018 版（人工整理关键词索引，NMPA 在线查询 HTML 解析失败后降级）', '2026-07-21 00:00:00'),

-- ===== 学术免费数据源 =====
('HERB 本草组鉴', 'academic', 'tcm_herbs', 100, '学术开放', 'http://herb.ac.cn/', 'HERB 本草组鉴（Nucleic Acids Research 2022），北京中医药大学团队发布，含 7012 味中药', '2026-07-21 00:00:00'),
('ETCM 中药百科', 'academic', 'tcm_herbs', 50, '学术开放', 'http://www.nrc.ac.cn:9090/ETCM/', 'ETCM 中药百科数据库（Nucleic Acids Research 2018），中科院上海药物研究所发布，含 6036 味中药', '2026-07-21 00:00:00'),
('PubMed Central', 'academic', 'ddi_knowledge', 10, '学术开放', 'https://www.ncbi.nlm.nih.gov/pmc/', 'PubMed Central 学术文献（NCBI E-utilities API），补充 DDI 文献证据', '2026-07-21 00:00:00'),

-- ===== 人工整理数据源 =====
('十八反十九畏', 'manual', 'tcm_incompatibility', 30, '公共医学常识', '', '《本草经集注》《珍珠囊补遗药性赋》整理，含十八反 18 条 + 十九畏 10 对 + 现代研究补充 2 条', '2026-07-21 00:00:00'),
('中西药交互 Top 100', 'manual', 'tcm_wm_interaction', 100, '公共医学常识', '', '基于 TCMBank、临床指南、药理研究人工核对整理的 Top 100 高频中西药交互组合', '2026-07-21 00:00:00'),
('中药忌口', 'manual', 'drug_food_interaction', 80, '公共医学常识', '', '基于《中药学》教材 + 临床经验整理 + DashScope LLM 补充的中药忌口规则', '2026-07-21 00:00:00'),
('健身动作库', 'manual', 'rehab_exercises', 43, '公共医学常识', '', '基于健身动作百科 + 运动训练学整理的 43 个康复/增肌/燃脂/柔韧/维持动作', '2026-07-21 00:00:00'),
('中国食物成分表', 'manual', 'food_items', 62, '版权数据', '', '《中国食物成分表》第 6 版（北京大学医学出版社），仅引用少量样例用于饮食推荐', '2026-07-21 00:00:00'),
('国家基本药物目录（西药）', 'manual', 'drug_clinical_info', 40, '政府公开数据', '', '基于国家基本药物目录 2018 版 + 临床常用药品说明书人工整理的 40 种西药', '2026-07-21 00:00:00'),
('经典 DDI 知识', 'manual', 'ddi_knowledge', 9, '公共医学常识', '', '经典药物相互作用（DDI）人工整理，含阿司匹林×华法林、二甲双胍×碘造影剂等 9 条', '2026-07-21 00:00:00');
