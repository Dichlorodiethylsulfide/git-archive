package dev.gitarchive.cli;

import dev.gitarchive.service.GitArchiveException;
import java.io.PrintWriter;
import picocli.CommandLine;

public final class Main {
    private Main() {
    }

    public static void main(String[] args) {
        System.exit(execute(args));
    }

    static int execute(String... args) {
        CommandLine commandLine = new CommandLine(new GitArchiveCommand());
        commandLine.setExecutionExceptionHandler((exception, cmd, parseResult) -> handleExecutionException(exception, cmd.getErr()));
        return commandLine.execute(args);
    }

    private static int handleExecutionException(Exception exception, PrintWriter err) {
        if (exception instanceof GitArchiveException gitArchiveException) {
            err.println(gitArchiveException.getMessage());
            return gitArchiveException.exitCode();
        }

        err.println("Fatal error: " + exception.getMessage());
        exception.printStackTrace(err);
        return 1;
    }
}
