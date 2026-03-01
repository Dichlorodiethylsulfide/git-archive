# GitHub Copilot Instructions

## Project Overview

`git-archive` is a Java 21 command-line tool (version 0.1.0) that packages a Git repository into a ZIP archive. It supports two archive modes:

- **`minimal`** — archives only tracked files from committed history (configurable depth).
- **`max`** — archives the current working tree, excluding `.git/`.

Build produces a single shaded fat jar: `target/git-archive-<version>-all.jar`.

## Tech Stack

| Concern | Library / Tool |
|---|---|
| CLI parsing | [Picocli](https://picocli.info/) 4.7.7 |
| Git access | [Eclipse JGit](https://www.eclipse.org/jgit/) 6.10 |
| ZIP I/O | [Apache Commons Compress](https://commons.apache.org/proper/commons-compress/) 1.28 |
| Build | Maven 3 + `maven-shade-plugin` |
| Testing | JUnit Jupiter 5.12 |
| Java version | 21 |

## Package Structure

```
dev.gitarchive
├── cli/        # Picocli command, version provider, entry point
├── git/        # JGit wrappers: RepositoryResolver, CommitSelector
├── model/      # Immutable data types: ArchiveRequest, ArchiveMode, CommitSnapshot, ArchiveManifest
├── service/    # Business logic: ArchivePlanner, MinimalArchiver, MaxArchiver, GitArchiveException
├── support/    # Cross-cutting helpers: VersionInfo
└── zip/        # ZIP output: ZipArchiveWriter, ManifestWriter
```

## Coding Conventions

- **Java 21 features** — use records, sealed types, pattern matching, text blocks, and `var` where they add clarity.
- **`final` classes** — prefer `final` for all concrete classes unless extension is explicitly required.
- **Records for models** — all `model/` types are `record`s with compact constructors used for `Objects.requireNonNull` validation.
- **No DI framework** — dependencies are wired manually via constructor injection. `ArchivePlanner.defaultPlanner()` is the composition root.
- **Immutability** — prefer immutable types; favour `List.of()` and `Map.of()` over mutable collections.
- **Checked exceptions** — wrap `IOException` and JGit exceptions in the appropriate `GitArchiveException` factory method; never let checked exceptions leak through the public API.
- **Imports** — no wildcard imports; static imports are only used inside test files (JUnit assertions).
- **Formatting** — 4-space indentation, no trailing whitespace, Unix line endings.

## Error Handling

Use the static factory methods on `GitArchiveException` to signal different failure categories:

| Method | Exit code | When to use |
|---|---|---|
| `GitArchiveException.usage(msg)` | 2 | Invalid arguments or option combinations |
| `GitArchiveException.repository(msg[, cause])` | 3 | Cannot open or read the Git repository |
| `GitArchiveException.archive(msg[, cause])` | 4 | Failure during ZIP creation |

Never throw `RuntimeException` or `IllegalArgumentException` directly from service or git layer code.

## Testing Conventions

- Integration tests live in `src/test/java/dev/gitarchive/` and use `TestRepositorySupport` to create in-memory JGit repositories under `@TempDir`.
- Test class names end in `Test`.
- Use `CommandLine` from Picocli to invoke `GitArchiveCommand` end-to-end, capturing stdout and exit code.
- Assertions are made on the resulting ZIP contents via `ZipFile` (Commons Compress).
- No mocking framework is used; prefer real implementations wired through `ArchivePlanner.defaultPlanner()` or via constructor injection with test doubles.

## Archive Layout Rules

| Mode | Depth | ZIP layout |
|---|---|---|
| `minimal` | 1 | Files at ZIP root (HEAD snapshot) |
| `minimal` | > 1 | Files under `commits/<index>-<sha>/` per commit |
| `max` | 1 (only) | Working-tree files at ZIP root, `.git/` excluded |
| any | any | Always includes `git-archive-manifest.txt` at ZIP root |

## Build & Release

```bash
# Build fat jar
mvn package

# Run all tests
mvn verify

# Self-release script (build → test → archive this repo → write dist/release/)
bash scripts/ci-self-release.sh
```

Release artifacts written to `dist/release/`:
- `git-archive-<version>-all.jar`
- `git-archive-<version>-self.zip`
- `git-archive-<version>-sha256.txt`
- `git-archive-<version>-release-manifest.txt`

## CI / GitHub Actions

- `.github/workflows/ci.yml` — runs on `main` pushes and PRs; uploads build artifacts.
- `.github/workflows/release.yml` — runs on `v*` tags; publishes to GitHub Releases.
- Both workflows delegate to `scripts/ci-self-release.sh`.
- Release tags must match `v<project.version>` (e.g. `v0.1.0`).

## Key Design Decisions

1. **`ArchivePlanner` is the single orchestrator** — it resolves the repository, selects commits, determines the output path, and delegates to `MinimalArchiver` or `MaxArchiver`. Do not add git or filesystem logic elsewhere.
2. **Two-phase write (temp → rename)** — archivers write to a temporary file first; `ArchivePlanner` atomically renames it to the target path to avoid partial output.
3. **`RepositoryResolver.ResolvedRepository` is `AutoCloseable`** — always use it in a try-with-resources so the JGit `Repository` is closed promptly.
4. **Version comes from filtered resource** — `src/main/resources/git-archive.properties` is Maven-filtered; `VersionInfo` reads it at runtime. Do not hard-code version strings.
