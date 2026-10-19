ALTER TABLE reconciliation.reconciliations
    ADD COLUMN exception_decision TEXT,
    ADD CONSTRAINT chk_reconciliations_exception_decision CHECK (
        (status IN ('REVIEW_REQUIRED', 'UNMATCHED') AND exception_decision IS NOT NULL)
        OR
        (status NOT IN ('REVIEW_REQUIRED', 'UNMATCHED') AND exception_decision IS NULL)
    );

ALTER TABLE event_publication ALTER COLUMN serialized_event TYPE TEXT;
