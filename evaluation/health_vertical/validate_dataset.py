#!/usr/bin/env python3
"""Validate a health-vertical case file against its data card.

The validator is deliberately conservative: a malformed or ambiguous case
must fail before it can be used as a training, development, or blind-test
artifact. It validates structure and privacy heuristics only; it does not
judge medical correctness.
"""

from __future__ import annotations

import argparse
import json
import re
from pathlib import Path
from typing import Any

from evaluate_responses import load_records, unique_ids


DIRECT_IDENTIFIER_PATTERNS = {
    "email": re.compile(r"\b[\w.+-]+@[\w-]+\.[\w.-]+\b"),
    "phone": re.compile(r"(?<!\d)(?:\+?86[- ]?)?1[3-9]\d{9}(?!\d)"),
}


def load_json(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict):
        raise ValueError(f"{path} must contain a JSON object")
    return value


def _require(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def validate_dataset(records: list[dict[str, Any]], manifest: dict[str, Any]) -> list[str]:
    errors: list[str] = []
    schema = manifest.get("schema") if isinstance(manifest.get("schema"), dict) else {}
    allowed = manifest.get("allowed_values") if isinstance(manifest.get("allowed_values"), dict) else {}
    required_fields = schema.get("required_fields") if isinstance(schema.get("required_fields"), list) else []
    case_pattern = str(schema.get("case_id_pattern", r"^case-[a-z0-9-]+$"))

    try:
        indexed = unique_ids(records, "case set")
    except ValueError as exc:
        errors.append(str(exc))
        indexed = {}

    summaries: set[str] = set()
    for case_id, case in indexed.items():
        for field in required_fields:
            _require(field in case, f"{case_id}: missing required field {field}", errors)
        _require(bool(re.fullmatch(case_pattern, case_id)), f"{case_id}: invalid case_id", errors)

        summary = str(case.get("input_summary", "")).strip()
        family = str(case.get("source_case_family", "")).strip()
        _require(bool(family), f"{case_id}: source_case_family must not be empty", errors)
        _require(bool(re.fullmatch(r"[a-z0-9_-]+", family)),
                 f"{case_id}: invalid source_case_family", errors)
        normalized_summary = " ".join(summary.lower().split())
        _require(bool(summary), f"{case_id}: input_summary must not be empty", errors)
        _require(normalized_summary not in summaries, f"{case_id}: duplicate input_summary", errors)
        summaries.add(normalized_summary)

        for field, values in allowed.items():
            if field == "expected_action_tags":
                actual = case.get(field)
                _require(isinstance(actual, list) and bool(actual), f"{case_id}: expected_action_tags must be non-empty", errors)
                unknown = set(str(item).strip() for item in (actual or [])) - set(str(item) for item in values)
                _require(not unknown, f"{case_id}: unknown expected_action_tags: {sorted(unknown)}", errors)
            elif field in case:
                _require(case[field] in values, f"{case_id}: invalid {field}={case[field]!r}", errors)

        for label, pattern in DIRECT_IDENTIFIER_PATTERNS.items():
            _require(not pattern.search(summary), f"{case_id}: possible direct {label} in input_summary", errors)

    _require(bool(manifest.get("dataset_id")), "manifest: dataset_id is required", errors)
    _require(isinstance(manifest.get("provenance"), dict), "manifest: provenance is required", errors)
    privacy = manifest.get("privacy")
    _require(isinstance(privacy, dict), "manifest: privacy is required", errors)
    if isinstance(privacy, dict):
        _require(privacy.get("contains_direct_identifiers") is False,
                 "manifest: contains_direct_identifiers must be false", errors)
        _require(privacy.get("contains_real_patient_records") is False,
                 "manifest: contains_real_patient_records must be false", errors)
    split_policy = manifest.get("split_policy")
    _require(isinstance(split_policy, dict) and split_policy.get("official_split_required") is True,
             "manifest: official_split_required must be true", errors)
    return errors


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True)
    args = parser.parse_args()

    errors = validate_dataset(load_records(args.cases), load_json(args.manifest))
    if errors:
        for error in errors:
            print(f"ERROR: {error}")
        raise SystemExit(1)
    print(f"OK: {len(load_records(args.cases))} cases satisfy the data-card contract")


if __name__ == "__main__":
    main()
