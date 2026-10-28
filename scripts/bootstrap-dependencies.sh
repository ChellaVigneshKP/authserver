#!/usr/bin/env bash
set -euo pipefail
repo_root="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd)"
export MAVEN_USER_HOME="${MAVEN_USER_HOME:-$repo_root/.local/maven}"
cache="${MAVEN_REPO_LOCAL:-$repo_root/.local/m2}"
mkdir -p "$repo_root/.local/dependencies" "$cache"
# Source revisions are immutable. No binary JARs or credentials are committed.
for entry in 'lib-crypto 336c12f2503679eb300c7a443eb4fccc78a0398d 2.0' 'auth-java-sdk 591f43eda5daa82d6597ab18fbd3df591d4df5ba 1.0'; do
  read -r dependency revision version <<< "$entry"
  destination="$repo_root/.local/dependencies/$dependency"
  if [[ ! -d "$destination/.git" ]]; then
    git clone "https://github.com/ChellaVigneshKP/$dependency.git" "$destination"
  fi
  git -C "$destination" fetch origin "$revision"
  git -C "$destination" checkout --detach "$revision"
  if [[ "$dependency" == lib-crypto ]]; then
    # Upstream hardcodes an old annotation processor incompatible with Java 25.
    # Change only that build-tool version in this disposable checkout.
    python3 - "$destination/pom.xml" <<'PY'
from pathlib import Path
import sys
p=Path(sys.argv[1]);p.write_text(p.read_text().replace('<version>1.18.32</version>','<version>1.18.42</version>'))
PY
  fi
  "$repo_root/mvnw" -f "$destination/pom.xml" -B -ntp -Dmaven.repo.local="$cache" \
    -Dmaven.test.skip=true -Dspotbugs.skip=true -Dpmd.skip=true -Dcheckstyle.skip=true \
    -Ddependency-check.skip=true -Djacoco.skip=true -Dmaven.javadoc.skip=true -Dgpg.skip=true package
  "$repo_root/mvnw" -B -ntp -Dmaven.repo.local="$cache" install:install-file \
    -Dfile="$destination/target/$dependency-$version.jar" -DpomFile="$destination/pom.xml"
done
printf 'Dependencies built. Use MAVEN_REPO_LOCAL=%s for application builds.\n' "$cache"
