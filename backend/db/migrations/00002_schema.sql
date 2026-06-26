-- +goose Up

-- ENUM types must be created before tables that reference them
CREATE TYPE event_type AS ENUM ('pee', 'poo', 'both', 'accident', 'tried');
CREATE TYPE device_platform AS ENUM ('android', 'ios');

-- consent_events: no child_id (D-05 FK direction), no IP address (REQ-C-009), no PII (REQ-C-005)
-- Must exist before children — children.consent_event_id references this table
CREATE TABLE consent_events (
    id                   UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id        TEXT        NOT NULL,
    consented_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    app_version          TEXT        NOT NULL,
    consent_text_version TEXT        NOT NULL
);

-- children: FK to consent_events is the DB-level consent gate (D-05)
-- A children row physically cannot exist without a referencing consent_events row
-- No PII columns: no legal_name, no gender, no photo, no full dob, no medical (REQ-008, REQ-C-005)
CREATE TABLE children (
    id               UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_org_id     TEXT        NOT NULL,
    nickname         TEXT        NOT NULL,
    birth_month      INTEGER     NOT NULL CHECK (birth_month BETWEEN 1 AND 12),
    birth_year       INTEGER     NOT NULL,
    consent_event_id UUID        NOT NULL REFERENCES consent_events(id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_children_clerk_org_id ON children(clerk_org_id);

-- potty_events: id has NO DEFAULT — client generates the UUID (REQ-006, supports idempotent retries)
-- event_type is nullable: NULL means quick-tap (no type selected yet) per D-02
CREATE TABLE potty_events (
    id          UUID        PRIMARY KEY,
    child_id    UUID        NOT NULL REFERENCES children(id),
    logged_by   TEXT        NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_by  TEXT,
    event_type  event_type,
    notes       TEXT,
    deleted_at  TIMESTAMPTZ,
    deleted_by  TEXT
);

CREATE INDEX idx_potty_events_child_occurred ON potty_events(child_id, occurred_at);

-- device_tokens: no FK to children; independent of the child profile lifecycle
CREATE TABLE device_tokens (
    id             UUID            PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  TEXT            NOT NULL,
    token          TEXT            NOT NULL,
    platform       device_platform NOT NULL,
    created_at     TIMESTAMPTZ     NOT NULL DEFAULT NOW(),
    last_seen_at   TIMESTAMPTZ     NOT NULL DEFAULT NOW()
);

-- notification_preferences: per-user per-child; UNIQUE prevents duplicate notifications
CREATE TABLE notification_preferences (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  TEXT        NOT NULL,
    child_id       UUID        NOT NULL REFERENCES children(id),
    enabled        BOOLEAN     NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(clerk_user_id, child_id)
);

-- erasure_audit: no FK to any other table so audit rows survive after data deletion (D-11)
-- action values: 'child_deletion' | 'account_deletion'
-- target_type values: 'child' | 'family'
CREATE TABLE erasure_audit (
    id             UUID        PRIMARY KEY DEFAULT gen_random_uuid(),
    clerk_user_id  TEXT        NOT NULL,
    action         TEXT        NOT NULL,
    target_id      UUID        NOT NULL,
    target_type    TEXT        NOT NULL,
    deleted_at     TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- +goose Down
-- WARNING: Down is for LOCAL DEVELOPMENT ONLY. NEVER run on staging or production.
-- Running Down drops all tables and enums, destroying all data irreversibly.

DROP TABLE IF EXISTS erasure_audit;
DROP TABLE IF EXISTS notification_preferences;
DROP TABLE IF EXISTS device_tokens;
DROP TABLE IF EXISTS potty_events;
DROP TABLE IF EXISTS children;
DROP TABLE IF EXISTS consent_events;

DROP TYPE IF EXISTS device_platform;
DROP TYPE IF EXISTS event_type;
