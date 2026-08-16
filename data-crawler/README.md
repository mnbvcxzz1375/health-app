# 数据采集层（data-crawler）

本目录是健康知识图谱的数据采集层，负责从多个数据源爬取中西药、食物、康复动作等数据，合并去重后生成 SQL 种子文件供后端使用。

## 目录结构

```
data-crawler/
├── README.md                    # 本文件
├── requirements.txt             # Python 依赖
├── output/                      # 爬取的原始数据（JSON/CSV）
├── sql/                         # 生成的 SQL 种子文件
├── nmpa_crawler.py              # NMPA 西药主索引爬取
├── wikidata_tcm.py              # Wikidata SPARQL 中药条目
├── tcmsp_crawler.py             # TCMSP 中药成分靶点
├── herb_crawler.py              # HERB 本草组鉴
├── etcm_crawler.py              # ETCM 中药百科
├── dxy_api.py                   # 丁香园 API 客户端
├── tcm_incompatibility.py       # 十八反十九畏（人工整理）
├── tcm_wm_interaction.py        # 中西药交互（Top 100）
├── dietary_taboo.py             # 中药忌口（人工整理 + LLM）
├── rehab_exercise_crawler.py    # 健身动作库
├── food_nutrition_crawler.py    # 食物营养成分
├── merge_and_dedup.py           # 多源合并去重
└── generate_sql.py              # 生成 SQL 种子文件
```

## 爬取顺序

### 阶段 1：开源数据（优先执行）
1. `python nmpa_crawler.py` —— NMPA 西药主索引
2. `python wikidata_tcm.py` —— Wikidata 中药条目
3. `python tcmsp_crawler.py` —— TCMSP 中药成分靶点
4. `python herb_crawler.py` —— HERB 本草组鉴
5. `python etcm_crawler.py` —— ETCM 中药百科

### 阶段 2：API + LLM
6. `python dxy_api.py` —— 丁香园 API（需 API Key）

### 阶段 3：人工整理 + LLM
7. `python tcm_incompatibility.py` —— 十八反十九畏（一次性人工录入）
8. `python dietary_taboo.py` —— 中药忌口（4 大药性类目）
9. `python tcm_wm_interaction.py` —— 中西药交互 Top 100

### 阶段 4：食物 + 动作
10. `python food_nutrition_crawler.py` —— 食物营养成分
11. `python rehab_exercise_crawler.py` —— 健身动作库

### 阶段 5：合并 + 生成 SQL
12. `python merge_and_dedup.py` —— 多源合并去重
13. `python generate_sql.py` —— 生成 SQL 种子文件

最终输出：
- `sql/seed_knowledge.sql` —— 药物知识图谱种子
- `sql/seed_rehab_exercises.sql` —— 康复动作库种子
- `sql/seed_food_nutrition.sql` —— 食物营养成分种子

## 数据源合规说明

| 数据源 | License | 商用限制 | 获取方式 |
|--------|---------|----------|----------|
| NMPA 在线查询 | 政府公开数据 | 需引用来源，低频访问 | 爬虫 <1 QPS |
| Wikidata | CC-BY-SA | 需署名 + 共享 | SPARQL API |
| TCMSP | 学术开放 | 引用论文 | HTTP 爬取 |
| HERB 本草组鉴 | 学术开放 | 引用论文 | HTTP 爬取 |
| ETCM | 学术开放 | 引用论文 | HTTP 爬取 |
| 丁香园 API | 商用需授权 | 需签协议 | RESTful API |
| 十八反十九畏 | 公共医学常识 | 无版权 | 人工整理 |
| 中药忌口 | 公共医学常识 | 无版权 | 人工整理 + LLM |
| 中国食物成分表 | 版权数据 | 需购买 | 仅引用少量样例 |
| Open Food Facts | ODbL | 需署名 | API |

## 重要注意事项

1. **遵守 robots.txt**：所有爬虫脚本默认遵守目标站点 robots.txt
2. **低频访问**：默认 QPS < 1，避免对数据源造成压力
3. **数据来源标注**：所有爬取的数据必须记录 `source` 字段
4. **合规底线**：第三方付费库（药智/米内）一律走授权 API，禁止爬虫
5. **用户反馈机制**：爬取的数据在前端展示时需显示来源和更新时间

## 输出 SQL 执行方式

生成的 SQL 文件通过 `BackendSchemaInitializer.java` 在后端启动时执行：

```java
// BackendSchemaInitializer.java 中追加
executeScript("classpath:db/seed/seed_knowledge.sql");
executeScript("classpath:db/seed/seed_rehab_exercises.sql");
executeScript("classpath:db/seed/seed_food_nutrition.sql");
```

所有 INSERT 语句使用 `INSERT IGNORE` 避免重复插入冲突。
