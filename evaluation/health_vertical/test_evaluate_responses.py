import json
import tempfile
import unittest
from pathlib import Path

from evaluate_responses import evaluate, load_records
from compare_reports import compare_reports
from split_cases import split_cases
from validate_dataset import load_json, validate_dataset


class ContractEvaluationTest(unittest.TestCase):
    def test_example_predictions_cover_every_contract_requirement(self) -> None:
        root = Path(__file__).parent
        report = evaluate(
            load_records(root / "cases.v1.jsonl"),
            load_records(root / "predictions.example.jsonl"),
        )
        self.assertEqual(report["metrics"]["safety_flag_recall"], 1.0)
        self.assertEqual(report["metrics"]["evidence_coverage"], 1.0)
        self.assertEqual(report["metrics"]["escalation_match_rate"], 1.0)
        self.assertEqual(report["metrics"]["fully_satisfied_case_rate"], 1.0)

    def test_missing_evidence_is_reported(self) -> None:
        gold = [{
            "case_id": "one",
            "expected_safety_flags": ["FLAG"],
            "required_evidence_sources": ["monitoring_baseline"],
            "expected_escalation": "routine_review",
        }]
        prediction = [{"case_id": "one", "safety": {"flags": ["FLAG"]}}]
        report = evaluate(gold, prediction)
        self.assertEqual(report["metrics"]["safety_flag_recall"], 1.0)
        self.assertEqual(report["metrics"]["evidence_coverage"], 0.0)
        self.assertEqual(report["metrics"]["escalation_match_rate"], 0.0)
        self.assertEqual(report["per_case"][0]["missing_evidence"], ["monitoring_baseline"])

    def test_natural_language_escalation_is_normalized(self) -> None:
        gold = [{
            "case_id": "one",
            "expected_safety_flags": ["FLAG"],
            "required_evidence_sources": [],
            "expected_escalation": "clinician_review",
        }]
        prediction = [{
            "case_id": "one",
            "safety": {"flags": ["FLAG"], "escalation": "Seek clinician review before progressing."},
        }]
        report = evaluate(gold, prediction)
        self.assertEqual(report["metrics"]["escalation_match_rate"], 1.0)

    def test_cli_files_are_utf8_json_lines(self) -> None:
        root = Path(__file__).parent
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / "report.json"
            records = load_records(root / "predictions.example.jsonl")
            output.write_text(json.dumps(records, ensure_ascii=False), encoding="utf-8")
            self.assertEqual(len(load_records(output)), 6)

    def test_public_fixture_satisfies_its_data_card(self) -> None:
        root = Path(__file__).parent
        errors = validate_dataset(
            load_records(root / "cases.v1.jsonl"),
            load_json(root / "data_card.v1.json"),
        )
        self.assertEqual(errors, [])

    def test_action_tags_are_part_of_full_contract(self) -> None:
        root = Path(__file__).parent
        report = evaluate(
            load_records(root / "cases.v1.jsonl"),
            load_records(root / "predictions.example.jsonl"),
        )
        self.assertEqual(report["metrics"]["action_tag_recall"], 1.0)
        self.assertEqual(report["metrics"]["action_tag_precision"], 1.0)

    def test_direct_identifier_is_rejected(self) -> None:
        root = Path(__file__).parent
        cases = load_records(root / "cases.v1.jsonl")
        cases[0] = {**cases[0], "input_summary": "Contact 13800138000 for this case."}
        errors = validate_dataset(cases, load_json(root / "data_card.v1.json"))
        self.assertTrue(any("direct phone" in error for error in errors))

    def test_compare_reports_keeps_arms_separate(self) -> None:
        report = {"contract": "contract-v1", "case_count": 6, "metrics": {
            "safety_flag_recall": 1.0,
            "evidence_coverage": 0.5,
        }}
        comparison = compare_reports({"baseline": report, "rag_safety": report})
        self.assertTrue(comparison["same_case_count"])
        self.assertEqual([row["arm"] for row in comparison["arms"]], ["baseline", "rag_safety"])
        self.assertEqual(comparison["arms"][0]["metrics"]["evidence_coverage"], 0.5)

    def test_splitter_keeps_source_families_together_and_is_deterministic(self) -> None:
        root = Path(__file__).parent
        cases = load_records(root / "cases.v1.jsonl")
        first = split_cases(cases, seed=11)
        second = split_cases(cases, seed=11)
        self.assertEqual(first, second)
        family_to_split = {
            case["source_case_family"]: split
            for split, rows in first.items()
            for case in rows
        }
        self.assertEqual(len(family_to_split), len(cases))
        self.assertTrue(all(first[split] for split in ("train", "dev", "blind_test")))


if __name__ == "__main__":
    unittest.main()
