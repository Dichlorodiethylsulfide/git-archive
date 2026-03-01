package dev.gitarchive.service;

import dev.gitarchive.model.ArchiveManifest;
import dev.gitarchive.model.ArchiveRequest;
import dev.gitarchive.support.VersionInfo;
import dev.gitarchive.zip.ManifestWriter;
import dev.gitarchive.zip.ZipArchiveWriter;
import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.FileVisitor;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class MaxArchiver {
    private final ManifestWriter manifestWriter;

    public MaxArchiver(ManifestWriter manifestWriter) {
        this.manifestWriter = manifestWriter;
    }

    public void writeArchive(ArchiveRequest request, Path outputPath, Optional<String> headCommitId) {
        List<String> skippedEntries = new ArrayList<>();
        Instant createdAt = Instant.now();
        Set<Path> excludedPaths = Set.of(outputPath.toAbsolutePath().normalize(), request.outputPath().toAbsolutePath().normalize());

        try (ZipArchiveWriter zipWriter = new ZipArchiveWriter(outputPath)) {
            Files.walkFileTree(request.repoRoot(), new FileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                    if (excludedPaths.contains(dir.toAbsolutePath().normalize())) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    if (isGitMetadataPath(request.repoRoot(), dir)) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (excludedPaths.contains(file.toAbsolutePath().normalize())) {
                        return FileVisitResult.CONTINUE;
                    }
                    if (isGitMetadataPath(request.repoRoot(), file)) {
                        return FileVisitResult.CONTINUE;
                    }

                    String entryName = toEntryName(request.repoRoot(), file);
                    Instant modifiedAt = Files.getLastModifiedTime(file, LinkOption.NOFOLLOW_LINKS).toInstant();
                    if (attrs.isSymbolicLink()) {
                        zipWriter.writeSymlink(entryName, Files.readSymbolicLink(file).toString(), 0777, modifiedAt);
                    } else if (attrs.isRegularFile()) {
                        zipWriter.writeFile(
                                entryName,
                                () -> Files.newInputStream(file),
                                attrs.size(),
                                resolvePermissions(file, attrs),
                                modifiedAt);
                    } else {
                        skippedEntries.add("Skipped special filesystem entry: " + entryName);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException exception) {
                    throw GitArchiveException.archive("Failed to read filesystem entry: " + file, exception);
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exception) {
                    if (exception != null) {
                        throw GitArchiveException.archive("Failed while reading directory: " + dir, exception);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });

            ArchiveManifest manifest = new ArchiveManifest(
                    VersionInfo.version(),
                    request.mode(),
                    request.depth(),
                    0,
                    request.repoRoot(),
                    headCommitId.orElse(null),
                    createdAt,
                    List.of(),
                    skippedEntries);
            zipWriter.writeTextFile("git-archive-manifest.txt", manifestWriter.render(manifest), 0644, createdAt);
        } catch (IOException exception) {
            throw GitArchiveException.archive("Failed to write archive: " + request.outputPath(), exception);
        }
    }

    private static boolean isGitMetadataPath(Path repoRoot, Path candidate) {
        Path relative = repoRoot.relativize(candidate);
        if (relative.getNameCount() == 0) {
            return false;
        }
        for (Path name : relative) {
            if (".git".equals(name.toString())) {
                return true;
            }
        }
        return false;
    }

    private static String toEntryName(Path repoRoot, Path file) {
        return repoRoot.relativize(file).toString().replace('\\', '/');
    }

    private static int resolvePermissions(Path path, BasicFileAttributes attributes) {
        try {
            Set<PosixFilePermission> permissions = Files.getPosixFilePermissions(path, LinkOption.NOFOLLOW_LINKS);
            return toUnixMode(permissions);
        } catch (UnsupportedOperationException | IOException ignored) {
            return Files.isExecutable(path) || attributes.isDirectory() ? 0755 : 0644;
        }
    }

    private static int toUnixMode(Set<PosixFilePermission> permissions) {
        int mode = 0;
        if (permissions.contains(PosixFilePermission.OWNER_READ)) {
            mode |= 0400;
        }
        if (permissions.contains(PosixFilePermission.OWNER_WRITE)) {
            mode |= 0200;
        }
        if (permissions.contains(PosixFilePermission.OWNER_EXECUTE)) {
            mode |= 0100;
        }
        if (permissions.contains(PosixFilePermission.GROUP_READ)) {
            mode |= 0040;
        }
        if (permissions.contains(PosixFilePermission.GROUP_WRITE)) {
            mode |= 0020;
        }
        if (permissions.contains(PosixFilePermission.GROUP_EXECUTE)) {
            mode |= 0010;
        }
        if (permissions.contains(PosixFilePermission.OTHERS_READ)) {
            mode |= 0004;
        }
        if (permissions.contains(PosixFilePermission.OTHERS_WRITE)) {
            mode |= 0002;
        }
        if (permissions.contains(PosixFilePermission.OTHERS_EXECUTE)) {
            mode |= 0001;
        }
        return mode == 0 ? 0644 : mode;
    }
}
