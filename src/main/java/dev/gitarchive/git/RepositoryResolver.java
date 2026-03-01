package dev.gitarchive.git;

import dev.gitarchive.service.GitArchiveException;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;

public final class RepositoryResolver {

    public ResolvedRepository resolve(Path inputPath) {
        Path normalized = inputPath.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw GitArchiveException.usage("Input folder does not exist: " + normalized);
        }
        if (!Files.isDirectory(normalized)) {
            throw GitArchiveException.usage("Input path must be a directory: " + normalized);
        }

        FileRepositoryBuilder builder = new FileRepositoryBuilder().findGitDir(normalized.toFile());
        File gitDir = builder.getGitDir();
        if (gitDir == null) {
            throw GitArchiveException.repository("Path is not inside a Git worktree: " + normalized);
        }

        try {
            Repository repository = builder.build();
            if (repository.isBare() || repository.getWorkTree() == null) {
                repository.close();
                throw GitArchiveException.repository("Bare repositories are not supported: " + normalized);
            }
            Path repoRoot = repository.getWorkTree().toPath().toAbsolutePath().normalize();
            return new ResolvedRepository(normalized, repoRoot, repository);
        } catch (IOException exception) {
            throw GitArchiveException.repository("Failed to open Git repository for: " + normalized, exception);
        }
    }

    public static final class ResolvedRepository implements AutoCloseable {
        private final Path inputPath;
        private final Path repoRoot;
        private final Repository repository;

        public ResolvedRepository(Path inputPath, Path repoRoot, Repository repository) {
            this.inputPath = inputPath;
            this.repoRoot = repoRoot;
            this.repository = repository;
        }

        public Path inputPath() {
            return inputPath;
        }

        public Path repoRoot() {
            return repoRoot;
        }

        public Repository repository() {
            return repository;
        }

        @Override
        public void close() {
            repository.close();
        }
    }
}
