#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/architecture/verify-dependency-baseline.py

# Physical module ownership.
test -d agent-control/src/main/java/com/opensocket/aievent/core/agent
test -d agent-control/src/main/java/com/opensocket/aievent/core/gateway
test -d task-orchestration/src/main/java/com/opensocket/aievent/core/task
test -d task-orchestration/src/main/java/com/opensocket/aievent/core/routing
test -d task-orchestration/src/main/java/com/opensocket/aievent/core/assignment

for package_name in agent gateway task routing assignment; do
  test ! -d "control-plane-app/src/main/java/com/opensocket/aievent/core/${package_name}"
done

# Stable facade boundaries.
test -f agent-control/src/main/java/com/opensocket/aievent/core/agent/AgentDirectoryFacade.java
test -f task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskOrchestrationFacade.java
test -f task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskDispatchPort.java

grep -R -q 'AgentDirectoryFacade' task-orchestration/src/main/java
! grep -R --include='*.java' -q 'AgentDirectoryRepository' task-orchestration/src/main/java

grep -q 'TaskOrchestrationFacade' control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java
! grep -q 'TaskDecisionService' control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java
! grep -q 'TaskAssignmentService' control-plane-app/src/main/java/com/opensocket/aievent/core/decision/DecisionEngine.java
! grep -R --include='*.java' -q 'import com.opensocket.aievent.core.assignment.TaskAssignmentService' control-plane-app/src/main/java

# Capacity reservation is owned by agent-control and recorded by task assignment.
grep -q 'reservedTaskCount' agent-control/src/main/java/com/opensocket/aievent/core/agent/AgentSnapshot.java
grep -q 'reserveCapacity' agent-control/src/main/java/com/opensocket/aievent/core/agent/AgentDirectoryRepository.java
grep -q 'capacityReserved' task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignment.java
grep -q 'releaseCapacityReservation' task-orchestration/src/main/java/com/opensocket/aievent/core/assignment/TaskAssignmentRepository.java

test -f database-platform/src/main/resources/db/migration/V17__agent_capacity_reservation.sql
grep -q 'reserved_task_count' deploy/sql/postgresql/common/01_schema.sql
grep -q 'capacity_reserved' deploy/sql/postgresql/common/01_schema.sql

# Required tests and module markers.
test -f agent-control/src/main/java/com/opensocket/aievent/core/agent/CoreAgentControlModule.java
test -f task-orchestration/src/main/java/com/opensocket/aievent/core/task/CoreTaskOrchestrationModule.java
test -f agent-control/src/test/java/com/opensocket/aievent/core/agent/AgentCapacityReservationTest.java
test -f task-orchestration/src/test/java/com/opensocket/aievent/core/assignment/TaskAssignmentCapacityBoundaryTest.java
test -f control-plane-app/src/test/java/com/opensocket/aievent/core/architecture/M3AgentTaskModuleStructureTest.java

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
print(f'M3 physical Java module dependency graph is declared and acyclic across {len(modules)} modules.')
PYMODULES

echo "M3 agent-control and task-orchestration module verification passed."
