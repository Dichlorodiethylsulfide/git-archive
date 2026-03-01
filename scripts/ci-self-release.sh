#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${SCRIPT_DIR}/.." && pwd)"
cd "${REPO_ROOT}"

RELEASE_DIR="${RELEASE_DIR:-dist/release}"
ARCHIVE_MODE="${ARCHIVE_MODE:-minimal}"
ARCHIVE_DEPTH="${ARCHIVE_DEPTH:-1}"
MAVEN_REPO_LOCAL="${MAVEN_REPO_LOCAL:-}"

MAVEN_ARGS=()
if [[ -n "${MAVEN_REPO_LOCAL}" ]]; then
  MAVEN_ARGS+=("-Dmaven.repo.local=${MAVEN_REPO_LOCAL}")
fi

PROJECT_VERSION="$(mvn "${MAVEN_ARGS[@]}" -q -DforceStdout help:evaluate -Dexpression=project.version)"
VERSION="${VERSION:-${PROJECT_VERSION}}"
GIT_REF_NAME="${GIT_REF_NAME:-}"

if [[ -n "${GIT_REF_NAME}" && "${GIT_REF_NAME}" == v* && "${GIT_REF_NAME}" != "v${PROJECT_VERSION}" ]]; then
  echo "Tag ${GIT_REF_NAME} does not match project version v${PROJECT_VERSION}." >&2
  exit 1
fi

mvn "${MAVEN_ARGS[@]}" -B clean test package

JAR_SOURCE="target/git-archive-${PROJECT_VERSION}-all.jar"
if [[ ! -f "${JAR_SOURCE}" ]]; then
  echo "Expected shaded jar not found: ${JAR_SOURCE}" >&2
  exit 1
fi

mkdir -p "${RELEASE_DIR}"

JAR_ARTIFACT="${RELEASE_DIR}/git-archive-${VERSION}-all.jar"
SELF_ARCHIVE="${RELEASE_DIR}/git-archive-${VERSION}-self.zip"
CHECKSUM_FILE="${RELEASE_DIR}/git-archive-${VERSION}-sha256.txt"
MANIFEST_FILE="${RELEASE_DIR}/git-archive-${VERSION}-release-manifest.txt"

cp -f "${JAR_SOURCE}" "${JAR_ARTIFACT}"

java -jar "${JAR_ARTIFACT}" \
  --mode "${ARCHIVE_MODE}" \
  --depth "${ARCHIVE_DEPTH}" \
  --output "${SELF_ARCHIVE}" \
  --overwrite \
  "${REPO_ROOT}"

PROJECT_COMMIT="$(git rev-parse HEAD)"
JAVA_VERSION="$(java -version 2>&1 | head -n 1)"
MAVEN_VERSION="$(mvn "${MAVEN_ARGS[@]}" -version | awk 'NR==1 {print $3}')"
ARCHIVE_JAR_SUM="$(sha256sum "${JAR_ARTIFACT}" | awk '{print $1}')"
SELF_ARCHIVE_SUM="$(sha256sum "${SELF_ARCHIVE}" | awk '{print $1}')"

(
  cd "${RELEASE_DIR}"
  sha256sum "$(basename "${JAR_ARTIFACT}")" "$(basename "${SELF_ARCHIVE}")" > "$(basename "${CHECKSUM_FILE}")"
)

cat > "${MANIFEST_FILE}" <<EOF
projectVersion: ${PROJECT_VERSION}
releaseVersion: ${VERSION}
commitId: ${PROJECT_COMMIT}
gitRefName: ${GIT_REF_NAME}
javaVersion: ${JAVA_VERSION}
mavenVersion: ${MAVEN_VERSION}
createdAt: $(date -u +"%Y-%m-%dT%H:%M:%SZ")
artifacts:
- $(basename "${JAR_ARTIFACT}") ${ARCHIVE_JAR_SUM}
- $(basename "${SELF_ARCHIVE}") ${SELF_ARCHIVE_SUM}
- $(basename "${CHECKSUM_FILE}")
EOF

printf '%s\n' "${RELEASE_DIR}" "${JAR_ARTIFACT}" "${SELF_ARCHIVE}" "${CHECKSUM_FILE}" "${MANIFEST_FILE}"
