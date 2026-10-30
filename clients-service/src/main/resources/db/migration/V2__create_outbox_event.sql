-- Transactional outbox (ADR-009). SPEC §7.
CREATE TABLE outbox_event (
    id           UUID        PRIMARY KEY,
    aggregate_id UUID        NOT NULL,
    type         VARCHAR(50) NOT NULL,
    payload      JSONB       NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL,
    published_at TIMESTAMPTZ
);

-- Only pending rows are polled, in creation order.
CREATE INDEX ix_outbox_event_pending ON outbox_event (created_at) WHERE published_at IS NULL;
