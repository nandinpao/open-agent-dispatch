#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
POM="$ROOT/pom.xml"
CONFIG="$ROOT/lombok.config"

fail() { echo "I7.10.2 Lombok baseline verification failed: $*" >&2; exit 1; }

grep -q '<lombok.version>1.18.46</lombok.version>' "$POM" || fail "parent pom must pin lombok.version 1.18.46"
grep -q '<groupId>org.projectlombok</groupId>' "$POM" || fail "parent pom must reference org.projectlombok"
grep -q '<artifactId>lombok</artifactId>' "$POM" || fail "parent pom must reference lombok artifact"
grep -q '<scope>provided</scope>' "$POM" || fail "lombok dependency must use provided scope"
grep -q '<optional>true</optional>' "$POM" || fail "inherited lombok dependency must be optional"
grep -q '<annotationProcessorPaths>' "$POM" || fail "maven-compiler-plugin must declare annotationProcessorPaths"
grep -q '<version>\${lombok.version}</version>' "$POM" || fail "annotation processor must use lombok.version"

[[ -f "$CONFIG" ]] || fail "lombok.config is missing"
grep -q '^config.stopBubbling = true$' "$CONFIG" || fail "lombok.config must stop bubbling"
grep -q '^lombok.addLombokGeneratedAnnotation = true$' "$CONFIG" || fail "generated annotation must be enabled"
grep -q '^lombok.data.flagUsage = warning$' "$CONFIG" || fail "@Data usage must be warned"
grep -q '^lombok.var.flagUsage = error$' "$CONFIG" || fail "var usage must be rejected"
grep -q '^lombok.toString.onlyExplicitlyIncluded = true$' "$CONFIG" || fail "toString must be explicit include only"
grep -q '^lombok.equalsAndHashCode.onlyExplicitlyIncluded = true$' "$CONFIG" || fail "equals/hashCode must be explicit include only"

if grep -RE "import[[:space:]]+lombok\.Data;|@Data([[:space:]\(]|$)" "$ROOT" --include='*.java' --exclude-dir=target >/dev/null; then
  fail "I7.10.2 must not introduce @Data; use later PO/DTO migration with review"
fi

if grep -R "<artifactId>lombok</artifactId>" "$ROOT" --include='pom.xml' | grep -v "$POM" | grep -v '<!--' >/dev/null; then
  fail "child modules must not declare ad-hoc lombok dependencies in I7.10.2"
fi

echo "I7.10.2 Core Lombok adoption baseline verification passed."
