package dev.gitarchive.cli;

import dev.gitarchive.support.VersionInfo;
import picocli.CommandLine;

public final class GitArchiveVersionProvider implements CommandLine.IVersionProvider {
    @Override
    public String[] getVersion() {
        return new String[] {"git-archive " + VersionInfo.version()};
    }
}
