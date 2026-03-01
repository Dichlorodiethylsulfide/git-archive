package dev.gitarchive;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

final class TestRepositorySupport {
    private final Path root;
    private final List<String> pendingExecutablePaths = new java.util.ArrayList<>();

    TestRepositorySupport(Path root) {
        this.root = root;
    }

    static TestRepositorySupport init(Path root) throws IOException, InterruptedException {
        Files.createDirectories(root);
        TestRepositorySupport repo = new TestRepositorySupport(root);
        repo.git("init", "-b", "main");
        repo.git("config", "user.name", "Test User");
        repo.git("config", "user.email", "test@example.com");
        return repo;
    }

    Path root() {
        return root;
    }

    Path writeFile(String relativePath, String content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content, StandardCharsets.UTF_8);
        return file;
    }

    Path writeBinaryFile(String relativePath, byte[] content) throws IOException {
        Path file = root.resolve(relativePath);
        Files.createDirectories(file.getParent());
        Files.write(file, content);
        return file;
    }

    Path writeExecutableFile(String relativePath, String content) throws IOException {
        Path file = writeFile(relativePath, content);
        try {
            Files.setPosixFilePermissions(file, Set.of(
                    PosixFilePermission.OWNER_READ,
                    PosixFilePermission.OWNER_WRITE,
                    PosixFilePermission.OWNER_EXECUTE,
                    PosixFilePermission.GROUP_READ,
                    PosixFilePermission.GROUP_EXECUTE,
                    PosixFilePermission.OTHERS_READ,
                    PosixFilePermission.OTHERS_EXECUTE));
        } catch (UnsupportedOperationException ignored) {
            // Non-POSIX filesystem (e.g. Windows): defer executable bit to git index
            pendingExecutablePaths.add(relativePath);
        }
        return file;
    }

    Path writeSymlink(String relativePath, String target) throws IOException {
        Path link = root.resolve(relativePath);
        Files.createDirectories(link.getParent());
        Files.createSymbolicLink(link, Path.of(target));
        return link;
    }

    void commitAll(String message) throws IOException, InterruptedException {
        git("add", "-A");
        for (String path : pendingExecutablePaths) {
            git("update-index", "--chmod=+x", path);
        }
        pendingExecutablePaths.clear();
        git("commit", "-m", message);
    }

    String head() throws IOException, InterruptedException {
        return git("rev-parse", "HEAD").trim();
    }

    String shortHead() throws IOException, InterruptedException {
        return git("rev-parse", "--short=7", "HEAD").trim();
    }

    private String git(String... args) throws IOException, InterruptedException {
        List<String> command = Arrays.asList(args);
        ProcessBuilder processBuilder = new ProcessBuilder();
        processBuilder.command(buildGitCommand(command));
        processBuilder.directory(root.toFile());
        Process process = processBuilder.start();
        String stdout = readFully(process.getInputStream());
        String stderr = readFully(process.getErrorStream());
        int exitCode = process.waitFor();
        assertEquals(0, exitCode, "git " + String.join(" ", command) + "\nstdout:\n" + stdout + "\nstderr:\n" + stderr);
        return stdout;
    }

    private static List<String> buildGitCommand(List<String> args) {
        List<String> command = new java.util.ArrayList<>();
        command.add("git");
        command.addAll(args);
        return command;
    }

    private static String readFully(InputStream inputStream) throws IOException {
        try (InputStream source = inputStream; ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            source.transferTo(outputStream);
            return outputStream.toString(StandardCharsets.UTF_8);
        }
    }
}
