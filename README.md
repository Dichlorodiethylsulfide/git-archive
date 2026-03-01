# git-archive

`git-archive` is a Java 21 command-line tool that packages a Git repository into a ZIP archive.

## Build

```bash
mvn package
```

The shaded runnable jar is written to `target/git-archive-<version>-all.jar`.

## Usage

```bash
java -jar target/git-archive-<version>-all.jar /path/to/repo
java -jar target/git-archive-<version>-all.jar --depth 5 /path/to/repo
java -jar target/git-archive-<version>-all.jar --mode max /path/to/repo
java -jar target/git-archive-<version>-all.jar --output backup.zip --overwrite /path/to/repo
```

Arguments:

- `<folder>` must be a directory inside the target Git worktree.
- `--depth` defaults to `1` and controls how many commits are included in `minimal` mode.
- `--mode minimal` archives only tracked files from committed history.
- `--mode max` archives the current working tree, including ignored and untracked files, but excludes `.git/`.
- `--mode max` cannot be combined with `--depth > 1`.

Archive layout:

- `minimal` with depth `1` writes the `HEAD` snapshot at the ZIP root.
- `minimal` with depth greater than `1` writes snapshots under `commits/<index>-<sha>/`.
- `max` writes the current working tree at the ZIP root.
- Every archive includes `git-archive-manifest.txt`.

## Release Script

```bash
bash scripts/ci-self-release.sh
```

The script:

- builds and tests the project with Maven
- creates a shaded jar
- runs the jar against this repository to prove the tool can archive itself
- writes release artifacts to `dist/release` by default

Artifacts:

- `git-archive-<version>-all.jar`
- `git-archive-<version>-self.zip`
- `git-archive-<version>-sha256.txt`
- `git-archive-<version>-release-manifest.txt`

Optional environment variables:

- `RELEASE_DIR`
- `ARCHIVE_MODE`
- `ARCHIVE_DEPTH`
- `VERSION`
- `GIT_REF_NAME`

## CI

GitHub Actions workflows under `.github/workflows/` call the same release script:

- `ci.yml` runs on pushes to `main` and on pull requests, then uploads the generated artifacts.
- `release.yml` runs for tags matching `v*` and publishes the jar, self-archive, checksums, and release manifest to GitHub Releases.

The release workflow expects tags of the form `v<project.version>`.
