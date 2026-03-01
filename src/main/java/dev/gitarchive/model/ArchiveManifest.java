package dev.gitarchive.model;

import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record ArchiveManifest(
        String toolVersion,
        ArchiveMode mode,
        int requestedDepth,
        int actualIncludedCommitCount,
        Path repoRoot,
        String headCommitId,
        Instant createdAt,
        List<CommitSnapshot> commits,
        List<String> skippedEntries) {

    public ArchiveManifest {
        toolVersion = Objects.requireNonNull(toolVersion, "toolVersion");
        mode = Objects.requireNonNull(mode, "mode");
        repoRoot = Objects.requireNonNull(repoRoot, "repoRoot");
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        commits = List.copyOf(commits);
        skippedEntries = List.copyOf(skippedEntries);
    }
}
