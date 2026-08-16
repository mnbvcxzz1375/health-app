ALTER TABLE consult_history
  ADD COLUMN evidence_json TEXT NULL,
  ADD COLUMN safety_json TEXT NULL;
