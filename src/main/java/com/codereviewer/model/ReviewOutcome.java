package com.codereviewer.model;

public enum ReviewOutcome {
    /** Inline review comments posted successfully to GitHub. */
    POSTED,

    /** Inline post failed; a top-level summary comment was posted as fallback. */
    FALLBACK_POSTED,

    /** PR action (e.g. closed, labeled) is not one the agent processes. */
    SKIPPED,

    /** Both inline post and fallback comment failed. */
    FAILED

}
