# 健康垂域安全—证据契约评测

这个目录提供一个可复现的评测夹具，用来检查后端或模型输出是否遵守 `RehabCase` / `ConsultResponse` 的安全与证据契约。它不生成医学准确率，也不替代专业人员评审。

## 数据卡

- **版本**：`health-vertical-contract-v1`
- **样本**：6 个合成病例，覆盖静息心率偏高、睡眠恢复不足、用药复核、已保存报告高风险、急症升级、无可用证据。
- **机器可校验数据卡**：`data_card.v1.json`；`validate_dataset.py` 会检查必填字段、枚举值、重复叙事、直接标识符启发式和正式盲测拆分要求。
- **分组拆分工具**：`split_cases.py` 按 `source_case_family` 生成 train/dev/blind_test，防止同源叙事跨集合泄漏；公开 6 例仍只是开发夹具。
- **来源与许可**：病例由项目组人工编写，使用合成/去标识化描述，不包含真实患者记录；仅用于接口回归、演示和评测流程开发。
- **标注字段**：`expected_safety_flags`、`required_evidence_sources`、`expected_escalation`、`expected_action_tags`、`gold_action`、`risk_level`。
- **适用边界**：只能衡量结构化安全字段、证据覆盖和升级策略的一致性，不能证明临床事实正确、诊断能力或真实世界安全性。
- **拆分策略**：`cases.v1.jsonl` 是公开开发夹具；正式答辩前应另建不提交的盲测集，按病例来源去重后拆分 train/dev/test，避免同一模板泄漏。

## 运行

在此目录执行：

```powershell
python -m unittest discover -s . -p "test_*.py"
python .\validate_dataset.py --cases .\cases.v1.jsonl --manifest .\data_card.v1.json
python .\evaluate_responses.py --predictions .\predictions.example.jsonl --output .\report.example.json
# 真实盲测完成后，分别生成三组报告，再合并为对照表（不做结果池化）
python .\compare_reports.py --arm baseline=baseline.report.json --arm rag=rag.report.json --arm rag_safety=rag_safety.report.json --output comparison.json
```

示例输出应达到：

```text
safety_flag_recall        1.0
evidence_coverage         1.0
escalation_match_rate     1.0
action_tag_recall         1.0
action_tag_precision      1.0
fully_satisfied_case_rate 1.0
```

接入真实系统时，将 `predictions.example.jsonl` 替换为每个病例一行的接口响应，保留 `case_id`，并把报告作为评测工件保存；不要把示例分数表述为模型医学准确率。
