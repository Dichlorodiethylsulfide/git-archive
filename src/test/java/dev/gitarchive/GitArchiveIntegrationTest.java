package dev.gitarchive;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.gitarchive.cli.GitArchiveCommand;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import picocli.CommandLine;

class GitArchiveIntegrationTest {

    @TempDir
    Path tempDir;

    @Test
    void minimalModeArchivesTrackedHeadContentsOnly() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile(".gitignore", "ignored.log\n");
        repo.writeFile("tracked.txt", "v1\n");
        repo.commitAll("initial");
        repo.writeFile("tracked.txt", "v2\n");
        repo.commitAll("second");
        repo.writeFile("tracked.txt", "working-tree-edit\n");
        repo.writeFile("untracked.txt", "untracked\n");
        repo.writeFile("ignored.log", "ignored\n");

        Path archivePath = tempDir.resolve("minimal-head.zip");
        CommandResult result = execute("--output", archivePath.toString(), repo.root().toString());

        assertEquals(0, result.exitCode());
        assertTrue(Files.exists(archivePath));

        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            assertEquals("v2\n", readText(zipFile, "tracked.txt"));
            assertEquals("ignored.log\n", readText(zipFile, ".gitignore"));
            assertFalse(hasEntry(zipFile, "untracked.txt"));
            assertFalse(hasEntry(zipFile, "ignored.log"));
            String manifest = readText(zipFile, "git-archive-manifest.txt");
            assertTrue(manifest.contains("mode: minimal"));
            assertTrue(manifest.contains("requestedDepth: 1"));
            assertTrue(manifest.contains("actualIncludedCommitCount: 1"));
            assertTrue(manifest.contains("headCommitId: " + repo.head()));
        }
    }

    @Test
    void minimalModeWithDepthCreatesPerCommitFolders() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile("alpha.txt", "1\n");
        repo.commitAll("one");
        String commit1 = repo.shortHead();
        repo.writeFile("beta.txt", "2\n");
        repo.commitAll("two");
        String commit2 = repo.shortHead();
        repo.writeFile("gamma.txt", "3\n");
        repo.commitAll("three");
        String commit3 = repo.shortHead();

        Path archivePath = tempDir.resolve("history.zip");
        CommandResult result = execute("--depth", "3", "--output", archivePath.toString(), repo.root().toString());

        assertEquals(0, result.exitCode());
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            assertTrue(hasEntry(zipFile, "commits/001-" + commit3 + "/alpha.txt"));
            assertTrue(hasEntry(zipFile, "commits/001-" + commit3 + "/beta.txt"));
            assertTrue(hasEntry(zipFile, "commits/001-" + commit3 + "/gamma.txt"));
            assertTrue(hasEntry(zipFile, "commits/002-" + commit2 + "/alpha.txt"));
            assertTrue(hasEntry(zipFile, "commits/002-" + commit2 + "/beta.txt"));
            assertFalse(hasEntry(zipFile, "commits/002-" + commit2 + "/gamma.txt"));
            assertTrue(hasEntry(zipFile, "commits/003-" + commit1 + "/alpha.txt"));
            String manifest = readText(zipFile, "git-archive-manifest.txt");
            assertTrue(manifest.contains("requestedDepth: 3"));
            assertTrue(manifest.contains("actualIncludedCommitCount: 3"));
        }
    }

    @Test
    void minimalModePreservesExecutableBitsAndSymlinks() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeExecutableFile("bin/run.sh", "#!/usr/bin/env bash\necho ok\n");
        repo.writeSymlink("bin/link.sh", "run.sh");
        repo.commitAll("symlink");

        Path archivePath = tempDir.resolve("minimal.zip");
        CommandResult result = execute("--output", archivePath.toString(), repo.root().toString());

        assertEquals(0, result.exitCode());
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            ZipArchiveEntry executable = zipFile.getEntry("bin/run.sh");
            ZipArchiveEntry symlink = zipFile.getEntry("bin/link.sh");
            assertNotNull(executable);
            assertNotNull(symlink);
            assertTrue((executable.getUnixMode() & 0111) != 0);
            assertEquals(0120777, symlink.getUnixMode());
            assertEquals("run.sh", readText(zipFile, "bin/link.sh"));
        }
    }

    @Test
    void maxModeIncludesIgnoredAndUntrackedFilesAndExcludesGitMetadata() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile(".gitignore", "ignored.log\n");
        repo.writeFile("tracked.txt", "tracked\n");
        repo.commitAll("tracked");
        repo.writeFile("tracked.txt", "modified\n");
        repo.writeFile("untracked.txt", "untracked\n");
        repo.writeFile("ignored.log", "ignored\n");

        Path archivePath = tempDir.resolve("max.zip");
        CommandResult result = execute("--mode", "max", "--output", archivePath.toString(), repo.root().toString());

        assertEquals(0, result.exitCode());
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            assertEquals("modified\n", readText(zipFile, "tracked.txt"));
            assertEquals("untracked\n", readText(zipFile, "untracked.txt"));
            assertEquals("ignored\n", readText(zipFile, "ignored.log"));
            assertFalse(hasEntry(zipFile, ".git/config"));
        }
    }

    @Test
    void maxModeSupportsRepositoriesWithoutCommits() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile("draft.txt", "draft\n");

        Path archivePath = tempDir.resolve("max.zip");
        CommandResult result = execute("--mode", "max", "--output", archivePath.toString(), repo.root().toString());

        assertEquals(0, result.exitCode());
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            assertEquals("draft\n", readText(zipFile, "draft.txt"));
            String manifest = readText(zipFile, "git-archive-manifest.txt");
            assertTrue(manifest.contains("actualIncludedCommitCount: 0"));
        }
    }

    @Test
    void acceptsSubdirectoryInsideRepository() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile("nested/deep/file.txt", "content\n");
        repo.commitAll("nested");

        Path archivePath = tempDir.resolve("subdir.zip");
        CommandResult result = execute("--output", archivePath.toString(), repo.root().resolve("nested/deep").toString());

        assertEquals(0, result.exitCode());
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            assertEquals("content\n", readText(zipFile, "nested/deep/file.txt"));
        }
    }

    @Test
    void minimalModeFailsWithoutCommits() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile("draft.txt", "draft\n");

        CommandResult result = execute(repo.root());

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("at least one commit"));
    }

    @Test
    void rejectsMaxModeWithDepthGreaterThanOne() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile("tracked.txt", "tracked\n");
        repo.commitAll("tracked");

        CommandResult result = execute("--mode", "max", "--depth", "2", repo.root().toString());

        assertEquals(2, result.exitCode());
        assertTrue(result.stderr().contains("cannot be combined"));
    }

    @Test
    void existingOutputRequiresOverwrite() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        repo.writeFile("tracked.txt", "tracked\n");
        repo.commitAll("tracked");

        Path archivePath = tempDir.resolve("archive.zip");
        assertEquals(0, execute("--output", archivePath.toString(), repo.root().toString()).exitCode());
        CommandResult secondRun = execute("--output", archivePath.toString(), repo.root().toString());

        assertEquals(2, secondRun.exitCode());
        assertTrue(secondRun.stderr().contains("already exists"));
        assertEquals(0, execute("--overwrite", "--output", archivePath.toString(), repo.root().toString()).exitCode());
    }

    @Test
    void failsWhenPathIsNotInsideARepository() throws Exception {
        Path plainDirectory = tempDir.resolve("plain");
        Files.createDirectories(plainDirectory);

        CommandResult result = execute(plainDirectory.toString());

        assertEquals(3, result.exitCode());
        assertTrue(result.stderr().contains("not inside a Git worktree"));
    }

    @Test
    void preservesBinaryFiles() throws Exception {
        TestRepositorySupport repo = TestRepositorySupport.init(tempDir.resolve("repo"));
        byte[] bytes = new byte[] {0, 1, 2, 3, 4, 5, 127, -1};
        repo.writeBinaryFile("image.bin", bytes);
        repo.commitAll("binary");

        Path archivePath = tempDir.resolve("binary.zip");
        CommandResult result = execute("--output", archivePath.toString(), repo.root().toString());

        assertEquals(0, result.exitCode());
        try (ZipFile zipFile = new ZipFile(archivePath.toFile())) {
            assertArrayEquals(bytes, readBytes(zipFile, "image.bin"));
        }
    }

    private CommandResult execute(Path repoRoot) {
        return execute(repoRoot.toString());
    }

    private CommandResult execute(String... args) {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream();
        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        CommandLine commandLine = new CommandLine(new GitArchiveCommand());
        commandLine.setOut(new PrintWriter(stdout, true, StandardCharsets.UTF_8));
        commandLine.setErr(new PrintWriter(stderr, true, StandardCharsets.UTF_8));
        commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> {
            cmd.getErr().println(exception.getMessage());
            if (exception instanceof dev.gitarchive.service.GitArchiveException gitArchiveException) {
                return gitArchiveException.exitCode();
            }
            return 1;
        });
        int exitCode = commandLine.execute(args);
        return new CommandResult(
                exitCode,
                stdout.toString(StandardCharsets.UTF_8),
                stderr.toString(StandardCharsets.UTF_8));
    }

    private static boolean hasEntry(ZipFile zipFile, String entryName) {
        return zipFile.getEntry(entryName) != null;
    }

    private static String readText(ZipFile zipFile, String entryName) throws IOException {
        return new String(readBytes(zipFile, entryName), StandardCharsets.UTF_8);
    }

    private static byte[] readBytes(ZipFile zipFile, String entryName) throws IOException {
        ZipArchiveEntry entry = zipFile.getEntry(entryName);
        assertNotNull(entry, "Missing zip entry: " + entryName);
        try (InputStream inputStream = zipFile.getInputStream(entry)) {
            return inputStream.readAllBytes();
        }
    }

    private record CommandResult(int exitCode, String stdout, String stderr) {
    }
}
