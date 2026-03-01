package dev.gitarchive.service;

import dev.gitarchive.model.ArchiveManifest;
import dev.gitarchive.model.ArchiveRequest;
import dev.gitarchive.model.CommitSnapshot;
import dev.gitarchive.support.VersionInfo;
import dev.gitarchive.zip.ManifestWriter;
import dev.gitarchive.zip.ZipArchiveWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.treewalk.TreeWalk;

public final class MinimalArchiver {
    private final ManifestWriter manifestWriter;

    public MinimalArchiver(ManifestWriter manifestWriter) {
        this.manifestWriter = manifestWriter;
    }

    public void writeArchive(ArchiveRequest request, Repository repository, List<RevCommit> commits, Path outputPath) {
        List<String> skippedEntries = new ArrayList<>();
        Instant createdAt = Instant.now();

        try (ZipArchiveWriter zipWriter = new ZipArchiveWriter(outputPath)) {
            boolean useCommitFolders = request.depth() > 1;
            for (int index = 0; index < commits.size(); index++) {
                RevCommit commit = commits.get(index);
                String prefix = useCommitFolders
                        ? "commits/%03d-%s/".formatted(index + 1, shortCommitId(commit.getName()))
                        : "";
                writeCommitTree(zipWriter, repository, commit, prefix, skippedEntries);
            }

            ArchiveManifest manifest = new ArchiveManifest(
                    VersionInfo.version(),
                    request.mode(),
                    request.depth(),
                    commits.size(),
                    request.repoRoot(),
                    commits.getFirst().getName(),
                    createdAt,
                    commits.stream().map(this::toSnapshot).toList(),
                    skippedEntries);
            zipWriter.writeTextFile("git-archive-manifest.txt", manifestWriter.render(manifest), 0644, createdAt);
        } catch (IOException exception) {
            throw GitArchiveException.archive("Failed to write archive: " + request.outputPath(), exception);
        }
    }

    private void writeCommitTree(
            ZipArchiveWriter zipWriter,
            Repository repository,
            RevCommit commit,
            String prefix,
            List<String> skippedEntries) throws IOException {
        Instant commitTime = Instant.ofEpochSecond(commit.getCommitTime());
        try (TreeWalk treeWalk = new TreeWalk(repository)) {
            treeWalk.addTree(commit.getTree());
            treeWalk.setRecursive(true);

            while (treeWalk.next()) {
                String entryName = prefix + treeWalk.getPathString();
                FileMode fileMode = treeWalk.getFileMode(0);
                ObjectLoader loader = repository.open(treeWalk.getObjectId(0));
                if (FileMode.SYMLINK.equals(fileMode)) {
                    String target = new String(loader.getBytes(), StandardCharsets.UTF_8);
                    zipWriter.writeSymlink(entryName, target, 0777, commitTime);
                } else if (FileMode.EXECUTABLE_FILE.equals(fileMode)) {
                    zipWriter.writeFile(entryName, loader::openStream, loader.getSize(), 0755, commitTime);
                } else if (FileMode.REGULAR_FILE.equals(fileMode)) {
                    zipWriter.writeFile(entryName, loader::openStream, loader.getSize(), 0644, commitTime);
                } else if (FileMode.GITLINK.equals(fileMode)) {
                    skippedEntries.add("Skipped submodule entry: %s @ %s".formatted(entryName, commit.getName()));
                } else {
                    skippedEntries.add("Skipped unsupported Git tree entry: %s @ %s".formatted(entryName, commit.getName()));
                }
            }
        }
    }

    private CommitSnapshot toSnapshot(RevCommit commit) {
        return new CommitSnapshot(
                commit.getName(),
                shortCommitId(commit.getName()),
                Instant.ofEpochSecond(commit.getCommitTime()),
                commit.getShortMessage());
    }

    private static String shortCommitId(String commitId) {
        return commitId.substring(0, Math.min(7, commitId.length()));
    }
}
