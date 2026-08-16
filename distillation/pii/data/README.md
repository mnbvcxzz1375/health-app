# PII 蒸馏数据集

## 标签集（与 OpenMed-PII teacher 对齐）

| 标签 | 含义 | 示例 |
|------|------|------|
| PERSON | 患者姓名 | 张明、李华 |
| DOCTOR | 医生姓名 | 王医生、张主任 |
| HOSPITAL | 医院名称 | 北京协和医院、上海市第一人民医院 |
| DEPARTMENT | 科室 | 心内科、骨科 |
| PHONE | 电话 | 13800138000、010-12345678 |
| ID_CARD | 身份证号 | 110101199001011234 |
| ADDRESS | 地址 | 北京市朝阳区建国路 88 号 |
| EMAIL | 邮箱 | zhangsan@example.com |
| DATE | 日期 | 2024-03-15、3月15日 |
| AGE | 年龄 | 45 岁 |

## 数据来源（三选一或组合）

### 1. Teacher 伪标注（self-training，推荐主力）
- 加载 `OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1`
- 输入：项目 `backend-java/src/main/java/com/ahealth/backend/consult/` 模块下的脱敏医疗咨询文本
- 优点：领域匹配（中文医疗咨询），无需额外标注成本
- 缺点：受 teacher 上限影响

### 2. 公开数据集（CLUE / CCKS）
- CLUE CMeEE：中文医学实体识别
- CCKS 2020 面向中文电子病历的命名实体识别
- 优点：标签质量高
- 缺点：标签集需映射到 PII 类别

### 3. 合成数据（模板生成）
- 用模板生成含姓名/电话/身份证/地址的医疗文本
- 优点：可控、量大
- 缺点：分布与真实数据差异大

## 文件格式（CoNLL-2003）

每行一个 token + 标签，空行分隔句子：

```
张 B-PERSON
明 I-PERSON
男 O
， O
45 B-AGE
岁 I-AGE
， O
北 B-ADDRESS
京 I-ADDRESS
市 I-ADDRESS
朝 I-ADDRESS
阳 I-ADDRESS
区 I-ADDRESS
```

## 数据文件

- `train.conll` — 训练集（≥10000 句）
- `val.conll` — 验证集（≥1000 句）
- `test.conll` — 测试集（≥1000 句）

## 数据准备脚本

```bash
python distillation/pii/prepare_data.py \
    --teacher OpenMed/OpenMed-PII-Chinese-QwenMed-XLarge-600M-v1 \
    --source backend-java/src/main/java/com/ahealth/backend/consult/ \
    --output distillation/pii/data/
```
