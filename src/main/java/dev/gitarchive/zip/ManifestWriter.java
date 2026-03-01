package dev.gitarchive.zip;

import dev.gitarchive.model.ArchiveManifest;
import dev.gitarchive.model.CommitSnapshot;
import java.time.format.DateTimeFormatter;

public final class ManifestWriter {
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ISO_INSTANT;

    public String render(ArchiveManifest manifest) {
        StringBuilder builder = new StringBuilder();
        appendLine(builder, "toolVersion", manifest.toolVersion());
        appendLine(builder, "mode", manifest.mode().cliValue());
        appendLine(builder, "requestedDepth", Integer.toString(manifest.requestedDepth()));
        appendLine(builder, "actualIncludedCommitCount", Integer.toString(manifest.actualIncludedCommitCount()));
        appendLine(builder, "repoRoot", manifest.repoRoot().toString());
        appendLine(builder, "headCommitId", manifest.headCommitId() == null ? "" : manifest.headCommitId());
        appendLine(builder, "createdAt", TIMESTAMP_FORMAT.format(manifest.createdAt()));
        builder.append("includedCommits:\n");
        for (CommitSnapshot commit : manifest.commits()) {
            builder.append("- ")
                    .append(commit.shortCommitId())
                    .append(" ")
                    .append(TIMESTAMP_FORMAT.format(commit.commitTime()))
                    .append(" ")
                    .append(sanitize(commit.subject()))
                    .append('\n');
        }

        builder.append("skippedEntries:\n");
        for (String skippedEntry : manifest.skippedEntries()) {
            builder.append("- ").append(sanitize(skippedEntry)).append('\n');
        }
        return builder.toString();
    }

    private static void appendLine(StringBuilder builder, String key, String value) {
        builder.append(key).append(": ").append(value).append('\n');
    }

    private static String sanitize(String value) {
        return value.replace('\n', ' ').replace('\r', ' ');
    }
}
