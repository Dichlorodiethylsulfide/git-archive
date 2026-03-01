package dev.gitarchive.model;

import java.time.Instant;
import java.util.Objects;

public record CommitSnapshot(String commitId, String shortCommitId, Instant commitTime, String subject) {
    public CommitSnapshot {
        commitId = Objects.requireNonNull(commitId, "commitId");
        shortCommitId = Objects.requireNonNull(shortCommitId, "shortCommitId");
        commitTime = Objects.requireNonNull(commitTime, "commitTime");
        subject = Objects.requireNonNull(subject, "subject");
    }
}
