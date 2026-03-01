package dev.gitarchive.cli;

import dev.gitarchive.model.ArchiveMode;
import dev.gitarchive.service.ArchivePlanner;
import java.nio.file.Path;
import java.util.concurrent.Callable;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Model.CommandSpec;
import picocli.CommandLine.Option;
import picocli.CommandLine.Parameters;
import picocli.CommandLine.Spec;

@Command(
        name = "git-archive",
        mixinStandardHelpOptions = true,
        versionProvider = GitArchiveVersionProvider.class,
        description = "Archive Git repositories into ZIP files.",
        exitCodeOnInvalidInput = 2)
public final class GitArchiveCommand implements Callable<Integer> {
    private final ArchivePlanner archivePlanner;

    @Spec
    private CommandSpec commandSpec;

    @Parameters(index = "0", paramLabel = "<folder>", description = "A directory inside the target Git repository.")
    private Path folder;

    @Option(names = {"-d", "--depth"}, defaultValue = "1", description = "Commit history depth to include in minimal mode.")
    private int depth;

    @Option(names = {"-m", "--mode"}, defaultValue = "minimal", converter = ArchiveModeConverter.class,
            description = "Archive mode: ${COMPLETION-CANDIDATES}.")
    private ArchiveMode mode;

    @Option(names = {"-o", "--output"}, description = "Explicit output ZIP path.")
    private Path output;

    @Option(names = "--overwrite", description = "Replace the output file if it already exists.")
    private boolean overwrite;

    public GitArchiveCommand() {
        this(ArchivePlanner.defaultPlanner());
    }

    GitArchiveCommand(ArchivePlanner archivePlanner) {
        this.archivePlanner = archivePlanner;
    }

    @Override
    public Integer call() {
        Path archivePath = archivePlanner.archive(folder, mode, depth, output, overwrite);
        commandSpec.commandLine().getOut().println(archivePath);
        return 0;
    }

    public static final class ArchiveModeConverter implements CommandLine.ITypeConverter<ArchiveMode> {
        @Override
        public ArchiveMode convert(String value) {
            return ArchiveMode.fromCliValue(value);
        }
    }
}
