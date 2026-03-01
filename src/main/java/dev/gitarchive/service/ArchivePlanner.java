package dev.gitarchive.service;

import dev.gitarchive.git.CommitSelector;
import dev.gitarchive.git.RepositoryResolver;
import dev.gitarchive.model.ArchiveMode;
import dev.gitarchive.model.ArchiveRequest;
import dev.gitarchive.zip.ManifestWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;

public final class ArchivePlanner {
    private final RepositoryResolver repositoryResolver;
    private final CommitSelector commitSelector;
    private final MinimalArchiver minimalArchiver;
    private final MaxArchiver maxArchiver;

    public ArchivePlanner(
            RepositoryResolver repositoryResolver,
            CommitSelector commitSelector,
            MinimalArchiver minimalArchiver,
            MaxArchiver maxArchiver) {
        this.repositoryResolver = repositoryResolver;
        this.commitSelector = commitSelector;
        this.minimalArchiver = minimalArchiver;
        this.maxArchiver = maxArchiver;
    }

    public static ArchivePlanner defaultPlanner() {
        ManifestWriter manifestWriter = new ManifestWriter();
        return new ArchivePlanner(
                new RepositoryResolver(),
                new CommitSelector(),
                new MinimalArchiver(manifestWriter),
                new MaxArchiver(manifestWriter));
    }

    public Path archive(Path inputPath, ArchiveMode mode, int depth, Path outputPath, boolean overwrite) {
        if (depth < 1) {
            throw GitArchiveException.usage("Depth must be at least 1.");
        }
        if (mode == ArchiveMode.MAX && depth > 1) {
            throw GitArchiveException.usage("Mode 'max' cannot be combined with depth greater than 1.");
        }

        try (RepositoryResolver.ResolvedRepository resolvedRepository = repositoryResolver.resolve(inputPath)) {
            Repository repository = resolvedRepository.repository();
            Path repoRoot = resolvedRepository.repoRoot();

            List<RevCommit> commits = mode == ArchiveMode.MINIMAL
                    ? commitSelector.selectCommits(repository, depth)
                    : List.of();
            Optional<ObjectId> head = commitSelector.resolveHead(repository);
            String headShortId = commits.isEmpty()
                    ? head.map(ObjectId::getName).map(ArchivePlanner::shortCommitId).orElse(null)
                    : shortCommitId(commits.getFirst().getName());

            Path targetOutputPath = resolveOutputPath(outputPath, repoRoot, mode, depth, headShortId);
            prepareOutputPath(targetOutputPath, overwrite);

            ArchiveRequest request = new ArchiveRequest(
                    resolvedRepository.inputPath(),
                    repoRoot,
                    mode,
                    depth,
                    targetOutputPath,
                    overwrite);

            Path temporaryOutput = createTemporaryOutput(targetOutputPath);
            try {
                if (mode == ArchiveMode.MINIMAL) {
                    minimalArchiver.writeArchive(request, repository, commits, temporaryOutput);
                } else {
                    maxArchiver.writeArchive(request, temporaryOutput, head.map(ObjectId::getName));
                }
                moveIntoPlace(temporaryOutput, targetOutputPath);
                return targetOutputPath;
            } catch (RuntimeException | Error exception) {
                deleteQuietly(temporaryOutput);
                throw exception;
            }
        }
    }

    private static Path resolveOutputPath(Path requestedOutput, Path repoRoot, ArchiveMode mode, int depth, String headShortId) {
        if (requestedOutput != null) {
            return requestedOutput.toAbsolutePath().normalize();
        }
        Path currentDirectory = Path.of("").toAbsolutePath().normalize();
        String repoName = repoRoot.getFileName().toString();
        String fileName = switch (mode) {
            case MINIMAL -> depth == 1
                    ? "%s-%s.zip".formatted(repoName, headShortId)
                    : "%s-history-%s-d%d.zip".formatted(repoName, headShortId, depth);
            case MAX -> "%s-working-tree-max.zip".formatted(repoName);
        };
        return currentDirectory.resolve(fileName);
    }

    private static void prepareOutputPath(Path outputPath, boolean overwrite) {
        try {
            Path parent = outputPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
        } catch (IOException exception) {
            throw GitArchiveException.archive("Failed to create output directory for: " + outputPath, exception);
        }

        if (Files.exists(outputPath) && !overwrite) {
            throw GitArchiveException.usage("Output file already exists. Use --overwrite to replace it: " + outputPath);
        }
    }

    private static Path createTemporaryOutput(Path outputPath) {
        try {
            Path parent = outputPath.getParent();
            String prefix = outputPath.getFileName().toString();
            return Files.createTempFile(parent, prefix + ".", ".tmp");
        } catch (IOException exception) {
            throw GitArchiveException.archive("Failed to create temporary archive file for: " + outputPath, exception);
        }
    }

    private static void moveIntoPlace(Path temporaryOutput, Path targetOutputPath) {
        try {
            try {
                Files.move(temporaryOutput, targetOutputPath, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                Files.move(temporaryOutput, targetOutputPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException exception) {
            throw GitArchiveException.archive("Failed to move archive into place: " + targetOutputPath, exception);
        }
    }

    private static void deleteQuietly(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup only.
        }
    }

    private static String shortCommitId(String commitId) {
        return commitId.substring(0, Math.min(7, commitId.length()));
    }
}
