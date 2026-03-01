package dev.gitarchive.git;

import dev.gitarchive.service.GitArchiveException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public final class CommitSelector {

    public List<RevCommit> selectCommits(Repository repository, int depth) {
        ObjectId head = resolveHead(repository)
                .orElseThrow(() -> GitArchiveException.usage("Minimal mode requires a repository with at least one commit."));

        try (RevWalk revWalk = new RevWalk(repository)) {
            RevCommit headCommit = revWalk.parseCommit(head);
            revWalk.markStart(headCommit);

            List<RevCommit> commits = new ArrayList<>();
            for (RevCommit commit : revWalk) {
                commits.add(commit);
                if (commits.size() >= depth) {
                    break;
                }
            }
            return commits;
        } catch (IOException exception) {
            throw GitArchiveException.repository("Failed to read commit history.", exception);
        }
    }

    public Optional<ObjectId> resolveHead(Repository repository) {
        try {
            return Optional.ofNullable(repository.resolve(Constants.HEAD));
        } catch (IOException exception) {
            throw GitArchiveException.repository("Failed to resolve HEAD.", exception);
        }
    }
}
