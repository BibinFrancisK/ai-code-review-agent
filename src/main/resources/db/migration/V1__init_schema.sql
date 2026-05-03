CREATE TABLE IF NOT EXISTS review (
    id             BIGSERIAL    PRIMARY KEY,
    owner          VARCHAR(255) NOT NULL,
    repo           VARCHAR(255) NOT NULL,
    pr_number      INTEGER      NOT NULL,
    pr_title       VARCHAR(500),
    overall_risk   VARCHAR(20)  NOT NULL,
    comment_count  INTEGER      NOT NULL DEFAULT 0,
    created_at     TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS review_comment (
    id                 BIGSERIAL    PRIMARY KEY,
    review_id          BIGINT       NOT NULL REFERENCES review(id) ON DELETE CASCADE,
    filename           VARCHAR(500) NOT NULL,
    line               INTEGER,
    severity           VARCHAR(20)  NOT NULL,
    category           VARCHAR(20)  NOT NULL,
    message            TEXT         NOT NULL,
    suggestion         TEXT,
    github_comment_id  BIGINT
);

CREATE INDEX IF NOT EXISTS idx_review_repo    ON review(owner, repo);
CREATE INDEX IF NOT EXISTS idx_review_pr      ON review(owner, repo, pr_number);
CREATE INDEX IF NOT EXISTS idx_comment_review ON review_comment(review_id);
