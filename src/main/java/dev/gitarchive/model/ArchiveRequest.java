package dev.gitarchive.model;

import java.nio.file.Path;
import java.util.Objects;

public record ArchiveRequest(
        Path inputPath,
        Path repoRoot,
        ArchiveMode mode,
        int depth,
        Path outputPath,
        boolean overwrite) {

    public ArchiveRequest {
        inputPath = Objects.requireNonNull(inputPath, "inputPath");
        repoRoot = Objects.requireNonNull(repoRoot, "repoRoot");
        mode = Objects.requireNonNull(mode, "mode");
        outputPath = Objects.requireNonNull(outputPath, "outputPath");
    }
}
