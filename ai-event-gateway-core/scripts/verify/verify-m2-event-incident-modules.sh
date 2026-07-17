#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/architecture/verify-dependency-baseline.py

# Physical module ownership.
test -d event-processing/src/main/java/com/opensocket/aievent/core/processing
test -d event-processing/src/main/java/com/opensocket/aievent/core/normalize
test -d event-processing/src/main/java/com/opensocket/aievent/core/fingerprint
test -d event-processing/src/main/java/com/opensocket/aievent/core/dedup
test -d incident/src/main/java/com/opensocket/aievent/core/incident
test -d incident/src/main/java/com/opensocket/aievent/core/summary

test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/normalize
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/fingerprint
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/dedup
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/incident
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/summary

# Stable event domain model types are owned by data-model, preventing incident -> event-processing cycles.
test -f data-model/src/main/java/com/opensocket/aievent/core/event/EventSeverity.java
test -f data-model/src/main/java/com/opensocket/aievent/core/event/NormalizedEvent.java

# Event processing may see Incident only through its facade.
grep -R -q 'IncidentFacade' event-processing/src/main/java
! grep -R --include='*.java' -q 'IncidentRepository' event-processing/src/main/java
! grep -R --include='*.java' -q 'IncidentOccurrenceSummaryRepository' event-processing/src/main/java

grep -q 'EventProcessingFacade' control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java
! grep -q 'IncidentManager' control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java
! grep -q 'IncidentOccurrenceSummaryRepository' control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java

# Physical Maven dependency direction must stay acyclic.
grep -q '<artifactId>incident</artifactId>' event-processing/pom.xml
! grep -q '<artifactId>event-processing</artifactId>' incident/pom.xml

test -f control-plane-app/src/test/java/com/opensocket/aievent/core/architecture/M2EventIncidentModuleStructureTest.java
test -f event-processing/src/test/java/com/opensocket/aievent/core/processing/DefaultEventProcessingFacadeTest.java
test -f incident/src/test/java/com/opensocket/aievent/core/incident/DefaultIncidentFacadeTest.java

grep -q "$VERSION" README.md
grep -q "$VERSION" kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

python3 - <<'PYMODULES'
from pathlib import Path
import re
import xml.etree.ElementTree as ET

root = Path('.')
modules = sorted(path for path in root.iterdir() if (path / 'pom.xml').is_file() and (path / 'src/main/java/com/opensocket/aievent/core').is_dir())
module_names = {module.name for module in modules}
classes = {}
module_of = {}
for module in modules:
    for source in (module / 'src/main/java').rglob('*.java'):
        text = source.read_text(encoding='utf-8')
        package = re.search(r'^package\s+([\w.]+);', text, re.MULTILINE)
        if package:
            fqcn = package.group(1) + '.' + source.stem
            if fqcn in classes:
                raise SystemExit(f'Duplicate production type: {fqcn}')
            classes[fqcn] = source
            module_of[fqcn] = module.name

namespace = {'m': 'http://maven.apache.org/POM/4.0.0'}
declared = {}
for module in modules:
    dependencies = set()
    tree = ET.parse(module / 'pom.xml')
    for dependency in tree.findall('.//m:dependencies/m:dependency', namespace):
        artifact = dependency.find('m:artifactId', namespace)
        if artifact is not None and artifact.text and artifact.text in module_names:
            dependencies.add(artifact.text)
    declared[module.name] = dependencies

actual = {module.name: set() for module in modules}
violations = []
for fqcn, source in classes.items():
    source_module = module_of[fqcn]
    for imported in re.findall(r'^import\s+(com\.opensocket\.aievent\.core\.[\w.]+);',
                               source.read_text(encoding='utf-8'), re.MULTILINE):
        target_module = module_of.get(imported)
        if target_module and target_module != source_module:
            actual[source_module].add(target_module)
            if target_module not in declared[source_module]:
                violations.append((source_module, target_module, source, imported))
if violations:
    for violation in violations:
        print('Undeclared module dependency:', violation)
    raise SystemExit(1)

visited = set()
active = set()
stack = []
def visit(module):
    active.add(module)
    stack.append(module)
    for target in actual[module]:
        if target in active:
            raise SystemExit('Cyclic module dependency: ' + ' -> '.join(stack[stack.index(target):] + [target]))
        if target not in visited:
            visit(target)
    stack.pop()
    active.remove(module)
    visited.add(module)
for module in actual:
    if module not in visited:
        visit(module)
print('M2 physical Java module dependency graph is declared and acyclic.')
PYMODULES

echo "M2 event-processing and incident module verification passed."
