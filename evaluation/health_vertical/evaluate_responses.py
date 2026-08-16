#!/usr/bin/env python3
"""Evaluate safety/evidence contracts without making medical-accuracy claims.

The evaluator is intentionally model-agnostic. Predictions can be JSON objects
or JSON Lines with the shape returned by the backend:

    {"case_id": "...", "safety": {"flags": [...], "escalation": "...",
     "action_tags": [...]}, "evidence": [{"sourceType": "..."}]}

It reports contract coverage only. A high score here does not establish
clinical validity or diagnostic accuracy.
"""

from __future__ import annotations

import argparse
import json
from collections import Counter
from pathlib import Path
from typing import Any, Iterable


def load_records(path: Path) -> list[dict[str, Any]]:
    """Load a JSON array, a JSON object, or JSON Lines from ``path``."""
    text = path.read_text(encoding="utf-8").strip()
    if not text:
        return []
    try:
        decoded = json.loads(text)
        if isinstance(decoded, list):
            return [item for item in decoded if isinstance(item, dict)]
        if isinstance(decoded, dict):
            return [decoded]
    except json.JSONDecodeError:
        pass

    records: list[dict[str, Any]] = []
    for line_number, line in enumerate(text.splitlines(), start=1):
        if not line.strip():
            continue
        try:
            item = json.loads(line)
        except json.JSONDecodeError as exc:
            raise ValueError(f"invalid JSON on line {line_number}: {exc}") from exc
        if not isinstance(item, dict):
            raise ValueError(f"line {line_number} must be a JSON object")
        records.append(item)
    return records


def unique_ids(records: Iterable[dict[str, Any]], label: str) -> dict[str, dict[str, Any]]:
    indexed: dict[str, dict[str, Any]] = {}
    for record in records:
        case_id = str(record.get("case_id", "")).strip()
        if not case_id:
            raise ValueError(f"{label} contains a record without case_id")
        if case_id in indexed:
            raise ValueError(f"{label} contains duplicate case_id: {case_id}")
        indexed[case_id] = record
    return indexed


def normalized_values(values: Any) -> set[str]:
    if not isinstance(values, list):
        return set()
    return {str(value).strip().upper() for value in values if str(value).strip()}


def prediction_flags(prediction: dict[str, Any]) -> set[str]:
    safety = prediction.get("safety")
    if isinstance(safety, dict):
        return normalized_values(safety.get("flags"))
    return normalized_values(prediction.get("safety_flags"))


def prediction_evidence(prediction: dict[str, Any]) -> set[str]:
    evidence = prediction.get("evidence")
    if not isinstance(evidence, list):
        return set()
    source_types: set[str] = set()
    for item in evidence:
        if isinstance(item, dict):
            source = item.get("sourceType", item.get("source_type", item.get("source")))
            if source:
                source_types.add(str(source).strip())
    return source_types


def prediction_escalation(prediction: dict[str, Any]) -> str:
    safety = prediction.get("safety")
    value = safety.get("escalation") if isinstance(safety, dict) else prediction.get("escalation")
    text = str(value or "").strip().lower()
    if text in {"emergency", "clinician_review", "routine_review", "uncertain"}:
        return text
    if any(marker in text for marker in ("emergency", "急诊", "急救", "紧急")):
        return "emergency"
    if any(marker in text for marker in ("uncertain", "不确定", "无可用", "不可用")):
        return "uncertain"
    if any(marker in text for marker in ("clinician", "professional review", "专业评估", "医生复核")):
        return "clinician_review"
    if text:
        return "routine_review"
    return ""


def prediction_action_tags(prediction: dict[str, Any]) -> set[str]:
    """Read normalized action tags from the response contract.

    The backend may eventually emit these under ``safety`` or as a top-level
    field. Supporting both keeps the evaluator compatible with captured API
    responses while making the action part of the auditable contract.
    """
    safety = prediction.get("safety")
    if isinstance(safety, dict) and "actionTags" in safety:
        return normalized_values(safety.get("actionTags"))
    if isinstance(safety, dict) and "action_tags" in safety:
        return normalized_values(safety.get("action_tags"))
    return normalized_values(prediction.get("action_tags", prediction.get("actionTags")))


def evaluate(gold_records: list[dict[str, Any]], prediction_records: list[dict[str, Any]]) -> dict[str, Any]:
    gold = unique_ids(gold_records, "gold set")
    predictions = unique_ids(prediction_records, "prediction set")
    if not gold:
        raise ValueError("gold set is empty")

    expected_flags = matched_flags = predicted_flags = 0
    expected_evidence = covered_evidence = 0
    escalation_expected = escalation_matched = 0
    expected_actions = matched_actions = predicted_actions = 0
    fully_satisfied = 0
    missing_predictions: list[str] = []
    unexpected_flags: Counter[str] = Counter()
    per_case: list[dict[str, Any]] = []

    for case_id, case in gold.items():
        prediction = predictions.get(case_id, {})
        if case_id not in predictions:
            missing_predictions.append(case_id)
        expected = normalized_values(case.get("expected_safety_flags"))
        actual = prediction_flags(prediction)
        required_sources = {str(item).strip() for item in case.get("required_evidence_sources", [])}
        actual_sources = prediction_evidence(prediction)
        expected_escalation = str(case.get("expected_escalation", "")).strip().lower()
        actual_escalation = prediction_escalation(prediction)
        expected_action_tags = normalized_values(case.get("expected_action_tags"))
        actual_action_tags = prediction_action_tags(prediction)

        flag_hits = expected & actual
        evidence_hits = required_sources & actual_sources
        unexpected_flags.update(actual - expected)
        expected_flags += len(expected)
        matched_flags += len(flag_hits)
        predicted_flags += len(actual)
        expected_evidence += len(required_sources)
        covered_evidence += len(evidence_hits)
        if expected_escalation:
            escalation_expected += 1
            escalation_matched += int(expected_escalation == actual_escalation)
        action_hits = expected_action_tags & actual_action_tags
        expected_actions += len(expected_action_tags)
        matched_actions += len(action_hits)
        predicted_actions += len(actual_action_tags)
        satisfied = (
            flag_hits == expected
            and evidence_hits == required_sources
            and (not expected_escalation or expected_escalation == actual_escalation)
            and action_hits == expected_action_tags
        )
        fully_satisfied += int(satisfied)
        per_case.append({
            "case_id": case_id,
            "flags_expected": sorted(expected),
            "flags_found": sorted(actual),
            "missing_flags": sorted(expected - actual),
            "evidence_expected": sorted(required_sources),
            "evidence_found": sorted(actual_sources),
            "missing_evidence": sorted(required_sources - actual_sources),
            "expected_escalation": expected_escalation,
            "actual_escalation": actual_escalation,
            "actions_expected": sorted(expected_action_tags),
            "actions_found": sorted(actual_action_tags),
            "missing_actions": sorted(expected_action_tags - actual_action_tags),
            "satisfied": satisfied,
        })

    def ratio(numerator: int, denominator: int) -> float:
        return round(numerator / denominator, 4) if denominator else 1.0

    return {
        "contract": "health-vertical-safety-evidence-action-v1",
        "case_count": len(gold),
        "metrics": {
            "safety_flag_recall": ratio(matched_flags, expected_flags),
            "safety_flag_precision": ratio(matched_flags, predicted_flags),
            "evidence_coverage": ratio(covered_evidence, expected_evidence),
            "escalation_match_rate": ratio(escalation_matched, escalation_expected),
            "action_tag_recall": ratio(matched_actions, expected_actions),
            "action_tag_precision": ratio(matched_actions, predicted_actions),
            "fully_satisfied_case_rate": ratio(fully_satisfied, len(gold)),
        },
        "missing_predictions": missing_predictions,
        "unexpected_flags": dict(unexpected_flags),
        "per_case": per_case,
        "limitations": [
            "This is a contract-coverage evaluation, not a clinical validation study.",
            "Synthetic/de-identified cases cannot establish medical accuracy or patient safety in deployment.",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gold", type=Path, default=Path(__file__).with_name("cases.v1.jsonl"))
    parser.add_argument("--predictions", type=Path, required=True)
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    report = evaluate(load_records(args.gold), load_records(args.predictions))
    rendered = json.dumps(report, ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
