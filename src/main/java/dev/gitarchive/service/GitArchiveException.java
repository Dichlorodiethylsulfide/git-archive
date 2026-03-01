package dev.gitarchive.service;

public final class GitArchiveException extends RuntimeException {
    private final int exitCode;

    private GitArchiveException(int exitCode, String message, Throwable cause) {
        super(message, cause);
        this.exitCode = exitCode;
    }

    public static GitArchiveException usage(String message) {
        return new GitArchiveException(2, message, null);
    }

    public static GitArchiveException repository(String message) {
        return new GitArchiveException(3, message, null);
    }

    public static GitArchiveException repository(String message, Throwable cause) {
        return new GitArchiveException(3, message, cause);
    }

    public static GitArchiveException archive(String message) {
        return new GitArchiveException(4, message, null);
    }

    public static GitArchiveException archive(String message, Throwable cause) {
        return new GitArchiveException(4, message, cause);
    }

    public int exitCode() {
        return exitCode;
    }
}
