DROP TABLE IF EXISTS upload_file;
DROP TABLE IF EXISTS extension_policy;

CREATE TABLE extension_policy (
    id          BIGSERIAL PRIMARY KEY,
    extension   VARCHAR(20) NOT NULL,
    type        VARCHAR(10) NOT NULL,
    is_blocked  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Case-insensitive uniqueness enforced at the DB level (not just in application code)
-- so two concurrent inserts of e.g. "sh" and "SH" can't both succeed, and a custom
-- extension can never collide with an existing fixed one regardless of case.
CREATE UNIQUE INDEX uq_extension_policy_lower ON extension_policy (LOWER(extension));

-- 업로드 시도 이력. stored_filename/detected_extension은 REJECTED 건에서는 채워지지
-- 않을 수 있어 NOT NULL을 걸지 않는다(예: null byte 파일명은 콘텐츠를 읽기도 전에
-- 거부되어 실제 확장자 자체가 없다).
CREATE TABLE upload_file (
    id                  BIGSERIAL PRIMARY KEY,
    original_filename   VARCHAR(500) NOT NULL,
    stored_filename     VARCHAR(255),
    detected_extension  VARCHAR(20),
    status              VARCHAR(10)  NOT NULL,
    reject_reason       VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
