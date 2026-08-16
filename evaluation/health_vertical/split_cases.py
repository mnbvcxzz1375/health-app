#!/usr/bin/env python3
"""Create deterministic group-aware train/dev/blind-test case splits.

The splitter is intended for a future controlled dataset. It refuses cases
without ``source_case_family`` so near-duplicate narratives cannot silently
cross a split boundary. The public six-case fixture remains a development
fixture; its generated split must not be presented as an independent clinical
test set.
"""

from __future__ import annotations

import argparse
import json
import random
from collections import defaultdict
from pathlib import Path
from typing import Any

from evaluate_responses import load_records, unique_ids


def split_cases(
    records: list[dict[str, Any]],
    seed: int = 20260802,
    train_ratio: float = 0.7,
    dev_ratio: float = 0.15,
) -> dict[str, list[dict[str, Any]]]:
    if not records:
        raise ValueError("case set is empty")
    if train_ratio <= 0 or dev_ratio <= 0 or train_ratio + dev_ratio >= 1:
        raise ValueError("train_ratio and dev_ratio must be positive and leave blind_test examples")
    indexed = unique_ids(records, "case set")
    groups: dict[str, list[dict[str, Any]]] = defaultdict(list)
    for case in indexed.values():
        family = str(case.get("source_case_family", "")).strip()
        if not family:
            raise ValueError(f"{case.get('case_id', '<unknown>')}: source_case_family is required")
        groups[family].append(case)

    families = list(groups)
    random.Random(seed).shuffle(families)
    group_count = len(families)
    if group_count < 3:
        raise ValueError("at least three source_case_family groups are required for train/dev/blind_test")
    train_count = max(1, round(group_count * train_ratio))
    dev_count = max(1, round(group_count * dev_ratio))
    if train_count + dev_count >= group_count:
        train_count = group_count - 2
        dev_count = 1

    split_families = {
        "train": families[:train_count],
        "dev": families[train_count:train_count + dev_count],
        "blind_test": families[train_count + dev_count:],
    }
    result: dict[str, list[dict[str, Any]]] = {}
    for split, split_groups in split_families.items():
        result[split] = [case for family in split_groups for case in groups[family]]
    return result


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--cases", type=Path, required=True)
    parser.add_argument("--output-dir", type=Path, required=True)
    parser.add_argument("--seed", type=int, default=20260802)
    parser.add_argument("--train-ratio", type=float, default=0.7)
    parser.add_argument("--dev-ratio", type=float, default=0.15)
    args = parser.parse_args()

    splits = split_cases(load_records(args.cases), args.seed, args.train_ratio, args.dev_ratio)
    args.output_dir.mkdir(parents=True, exist_ok=True)
    for split, records in splits.items():
        output = args.output_dir / f"{split}.jsonl"
        output.write_text(
            "".join(json.dumps(record, ensure_ascii=False, separators=(",", ":")) + "\n" for record in records),
            encoding="utf-8",
        )
        print(f"{split}: {len(records)} cases -> {output}")


if __name__ == "__main__":
    main()
