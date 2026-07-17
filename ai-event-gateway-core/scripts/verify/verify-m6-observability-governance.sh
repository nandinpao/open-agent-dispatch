#!/usr/bin/env bash
set -euo pipefail
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"
VERSION="1.0.0-p25.7.4-p5-callback-transition-governance-fix"

./scripts/verify/verify-pom-build-closure.sh
./scripts/architecture/verify-dependency-baseline.py

test -d observability/src/main/java/com/opensocket/aievent/core/observability
test -f observability/src/main/java/com/opensocket/aievent/core/observability/CoreObservabilityModule.java
test -f observability/src/main/java/com/opensocket/aievent/core/observability/CoreMetricsService.java
test -f observability/src/main/java/com/opensocket/aievent/core/observability/CoreOperationalHealthIndicator.java
test ! -d control-plane-app/src/main/java/com/opensocket/aievent/core/observability

test -f incident/src/main/java/com/opensocket/aievent/core/incident/IncidentOperationalQuery.java
test -f agent-control/src/main/java/com/opensocket/aievent/core/agent/AgentControlOperationalQuery.java
test -f task-orchestration/src/main/java/com/opensocket/aievent/core/task/TaskOperationalQuery.java
test -f execution-control/src/main/java/com/opensocket/aievent/core/dispatch/ExecutionOperationalQuery.java
test -f event-processing/src/main/java/com/opensocket/aievent/core/processing/EventProcessingOperationalQuery.java

test -f architecture/table-ownership.csv
test -f architecture/repository-access-exceptions.csv

# API and observability code may only use facades/query ports, never repositories or mappers.
if grep -R --include='*.java' -nE '^import .*\.(Repository|Dao);' \
    */src/main/java/com/opensocket/aievent/core/api \
    observability/src/main/java 2>/dev/null; then
  echo "API/Observability repository access is forbidden in M6" >&2
  exit 1
fi

python3 - <<'PY'
from pathlib import Path
import csv, re, xml.etree.ElementTree as ET
root=Path('.')
modules=sorted(p for p in root.iterdir() if (p/'pom.xml').is_file() and ((p/'src/main/java/com/opensocket/aievent/core').is_dir() or (p/'src/main/java/com/opensocket/aievent/database').is_dir()))
module_names={m.name for m in modules}
source_to_module={}
type_to_source={}
type_to_module={}
package_re=re.compile(r'^package\s+([\w.]+);',re.M)
import_re=re.compile(r'^import\s+([\w.]+);',re.M)
for module in modules:
    for source in (module/'src/main/java').rglob('*.java'):
        text=source.read_text()
        m=package_re.search(text)
        if not m: continue
        fqcn=m.group(1)+'.'+source.stem
        if fqcn in type_to_source: raise SystemExit(f'Duplicate type {fqcn}')
        type_to_source[fqcn]=source
        type_to_module[fqcn]=module.name
        source_to_module[source]=module.name

# Table ownership contracts must exist in the declared owner module.
with (root/'architecture/table-ownership.csv').open(newline='') as f:
    rows=list(csv.DictReader(f))
if not rows: raise SystemExit('table ownership registry is empty')
for row in rows:
    contract=row['repository_contract']
    owner=row['owner_module']
    if contract not in type_to_module:
        raise SystemExit(f'Missing repository contract for table {row["table_name"]}: {contract}')
    if type_to_module[contract] != owner:
        raise SystemExit(f'Table {row["table_name"]} owner mismatch: expected {owner}, got {type_to_module[contract]}')

# Freeze the only remaining cross-module Repository imports as explicit lifecycle exceptions.
exceptions=set()
with (root/'architecture/repository-access-exceptions.csv').open(newline='') as f:
    for row in csv.DictReader(f):
        exceptions.add((row['source_file'],row['imported_type']))
seen_exceptions=set()
violations=[]
for source, source_module in source_to_module.items():
    text=source.read_text()
    for imported in import_re.findall(text):
        target_module=type_to_module.get(imported)
        if not target_module or target_module==source_module: continue
        simple=imported.rsplit('.',1)[-1]
        if not (simple.endswith('Repository') or (simple.endswith('Dao') and '.database.persistence.' in imported)): continue
        rel=source.as_posix()
        # P3 database-platform adapters implement Repository ports owned by feature modules.
        if (source_module == 'database-platform'
                and '/repository/' in rel
                and (simple.endswith('Repository')
                     or (simple.endswith('Dao') and '.database.persistence.' in imported))):
            continue
        key=(rel,imported)
        if key in exceptions:
            seen_exceptions.add(key)
            continue
        violations.append((rel,source_module,target_module,imported))
if violations:
    for v in violations: print('Unapproved cross-module persistence access:',v)
    raise SystemExit(1)
unused=exceptions-seen_exceptions
if unused:
    for item in sorted(unused): print('Stale repository exception:',item)
    raise SystemExit(1)

# Verify actual Java module dependencies are declared and acyclic.
ns={'m':'http://maven.apache.org/POM/4.0.0'}
declared={}
for module in modules:
    deps=set()
    tree=ET.parse(module/'pom.xml')
    for dep in tree.findall('.//m:dependencies/m:dependency',ns):
        artifact=dep.find('m:artifactId',ns)
        if artifact is not None and artifact.text and artifact.text in module_names:
            deps.add(artifact.text)
    declared[module.name]=deps
actual={m.name:set() for m in modules}
for fqcn,source in type_to_source.items():
    sm=type_to_module[fqcn]
    for imported in import_re.findall(source.read_text()):
        tm=type_to_module.get(imported)
        if tm and tm!=sm:
            actual[sm].add(tm)
            if tm not in declared[sm]:
                raise SystemExit(f'Undeclared module dependency: {sm} -> {tm} from {source}')
visited=set(); active=[]
def visit(m):
    if m in active: raise SystemExit('Cyclic module dependency: '+' -> '.join(active+[m]))
    if m in visited: return
    active.append(m)
    for t in actual[m]: visit(t)
    active.pop(); visited.add(m)
for m in actual: visit(m)
print(f'M6 ownership and dependency governance passed across {len(modules)} modules and {len(rows)} owned tables.')
PY

grep -q "$VERSION" README.md
grep -q "$VERSION" kernel/src/main/java/com/opensocket/aievent/core/kernel/CoreVersion.java

echo "M6 observability and architecture governance verification passed."
