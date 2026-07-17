#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/architecture/verify-dependency-baseline.py

# Physical module ownership.
test -d execution-control/src/main/java/com/opensocket/aievent/core/dispatch
test -d execution-control/src/main/java/com/opensocket/aievent/core/callback
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/dispatch
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/callback

# Public boundaries and outbound ports.
test -f execution-control/src/main/java/com/opensocket/aievent/core/dispatch/CoreExecutionControlModule.java
test -f execution-control/src/main/java/com/opensocket/aievent/core/dispatch/ExecutionControlFacade.java
test -f execution-control/src/main/java/com/opensocket/aievent/core/dispatch/NettyDispatchPort.java
test -f execution-control/src/main/java/com/opensocket/aievent/core/dispatch/ExecutionMetricsPort.java
test -f execution-control/src/main/java/com/opensocket/aievent/core/callback/TaskTerminalActionPort.java

# Execution Control may use Task Orchestration facade but not its repository.
grep -R -q 'TaskOrchestrationFacade' execution-control/src/main/java
! grep -R --include='*.java' -q 'import com.opensocket.aievent.core.task.TaskRepository' execution-control/src/main/java
! grep -R --include='*.java' -q 'AdapterActionService' execution-control/src/main/java
! grep -R --include='*.java' -q 'CoreMetricsService' execution-control/src/main/java

# Task Orchestration exposes the dispatch port and does not depend on execution implementation.
test -f task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskDispatchPort.java
! grep -R --include='*.java' -q 'DispatchRequestService' task-orchestration/src/main/java
! grep -R --include='*.java' -q 'DispatchExecutionService' task-orchestration/src/main/java
! grep -R --include='*.java' -q 'TaskCallbackService' task-orchestration/src/main/java

# App adapters implement the execution ports.
grep -R -q 'TaskTerminalEventHandler' adapter-action/src/main/java
grep -q 'implements AdapterActionFacade' adapter-action/src/main/java/com/opensocket/aievent/core/action/AdapterActionService.java
grep -q 'ExecutionMetricsPort' observability/src/main/java/com/opensocket/aievent/core/observability/CoreMetricsService.java

# Required tests and docs.
test -f execution-control/src/test/java/com/opensocket/aievent/core/dispatch/ExecutionControlRetryTest.java
test -f execution-control/src/test/java/com/opensocket/aievent/core/dispatch/TaskCallbackPortBoundaryTest.java
test -f control-plane-app/src/test/java/com/opensocket/aievent/core/architecture/M4ExecutionControlModuleStructureTest.java
test -f docs/m4-execution-control-module.md

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
print(f'M4 boundary remains valid under M8; physical Java module dependency graph is declared and acyclic across {len(modules)} modules.')
PYMODULES

echo "M4 execution-control module verification remains valid under M8."
