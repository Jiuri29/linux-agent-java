#!/usr/bin/env bash
set -euo pipefail

cd "$(dirname "$0")/../.."

echo "==> building jar"
mvn -q -DskipTests package

echo "==> building rpm"
RPMBUILD_DIR="$PWD/.rpmbuild"
rm -rf "$RPMBUILD_DIR"
mkdir -p "$RPMBUILD_DIR"/{BUILD,RPMS,SOURCES,SPECS,SRPMS}

VERSION=$(mvn -q -Dexec.executable=echo -Dexec.args='${project.version}' \
    --non-recursive exec:exec 2>/dev/null || echo 1.0.0)

tar czf "$RPMBUILD_DIR/SOURCES/kylin-aiops-agent-${VERSION}.tar.gz" \
    --transform "s|^|kylin-aiops-agent-${VERSION}/|" \
    pom.xml src deploy packaging/rpm/kylin-aiops-agent.spec

rpmbuild -ba packaging/rpm/kylin-aiops-agent.spec \
    --define "_topdir $RPMBUILD_DIR" \
    --define "_sourcedir $RPMBUILD_DIR/SOURCES" \
    --define "version ${VERSION}"

echo "==> done:"
find "$RPMBUILD_DIR/RPMS" -name '*.rpm' -print
