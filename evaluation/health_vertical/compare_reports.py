#!/usr/bin/env python3
"""Combine contract-evaluation reports from reproducible model arms.

This is a reporting utility, not a significance test and not a clinical
ranking. It keeps the baseline/RAG/safety-layer arms separate so results are
never pooled into a misleading single score.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path
from typing import Any


REPORT_METRICS = (
    "safety_flag_recall",
    "safety_flag_precision",
    "evidence_coverage",
    "escalation_match_rate",
    "action_tag_recall",
    "action_tag_precision",
    "fully_satisfied_case_rate",
)


def load_report(path: Path) -> dict[str, Any]:
    value = json.loads(path.read_text(encoding="utf-8"))
    if not isinstance(value, dict) or not isinstance(value.get("metrics"), dict):
        raise ValueError(f"{path} is not a contract-evaluation report")
    return value


def compare_reports(arms: dict[str, dict[str, Any]]) -> dict[str, Any]:
    if not arms:
        raise ValueError("at least one evaluation arm is required")
    rows: list[dict[str, Any]] = []
    case_counts: set[int] = set()
    for name, report in arms.items():
        metrics = report["metrics"]
        case_count = int(report.get("case_count", 0))
        case_counts.add(case_count)
        rows.append({
            "arm": name,
            "contract": report.get("contract", ""),
            "case_count": case_count,
            "metrics": {key: metrics.get(key) for key in REPORT_METRICS if key in metrics},
            "missing_predictions": report.get("missing_predictions", []),
        })
    return {
        "arms": rows,
        "same_case_count": len(case_counts) == 1,
        "limitations": [
            "Arms are reported separately; no pooled score or clinical ranking is produced.",
            "Contract metrics do not establish medical accuracy or clinical effectiveness.",
            "Official comparison requires a frozen blind set and identical evaluation configuration.",
        ],
    }


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--arm",
        action="append",
        required=True,
        metavar="NAME=REPORT.json",
        help="repeat for each arm, e.g. baseline=baseline.report.json",
    )
    parser.add_argument("--output", type=Path)
    args = parser.parse_args()

    arms: dict[str, dict[str, Any]] = {}
    for item in args.arm:
        if "=" not in item:
            raise SystemExit(f"invalid --arm value: {item!r}; expected NAME=REPORT.json")
        name, raw_path = item.split("=", 1)
        name = name.strip()
        if not name or name in arms:
            raise SystemExit(f"invalid or duplicate arm name: {name!r}")
        arms[name] = load_report(Path(raw_path))

    rendered = json.dumps(compare_reports(arms), ensure_ascii=False, indent=2) + "\n"
    if args.output:
        args.output.write_text(rendered, encoding="utf-8")
    else:
        print(rendered, end="")


if __name__ == "__main__":
    main()
