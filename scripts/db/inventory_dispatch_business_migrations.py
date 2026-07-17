#!/usr/bin/env python3
"""Inventory historical Flyway business-data changes without mutating a database."""
from __future__ import annotations
import argparse, csv, json, re
from collections import Counter
from pathlib import Path

ROOT=Path(__file__).resolve().parents[2]
MIGRATIONS=ROOT/'ai-event-gateway-core/database-platform/src/main/resources/db/migration'
BUSINESS_TABLES={
 'dispatch_flows','dispatch_policies','flow_required_skills','flow_agent_assignments',
 'agent_capability_catalog','agent_capability_assignments','agent_assignment_profiles',
 'assignment_profile_capability_bindings','agent_skill_registry','task_definitions',
 'source_systems','source_system_catalog','source_system_dispatch_defaults',
 'agent_source_assignments','task_requirement_evidence','agents','tasks'
}
DML=re.compile(r'\b(insert\s+into|update|delete\s+from)\s+(?:public\.)?([A-Za-z_][A-Za-z0-9_]*)',re.I)
VERSION=re.compile(r'^V([^_]+)__(.+)\.sql$')

def classify(name:str,text:str)->str:
 s=(name+' '+text[:2500]).lower()
 if 'fixture' in s or 'e2e' in s or 'acceptance' in s: return 'FIXTURE'
 if 'repair' in s or 'remediation' in s or 'rerun' in s or 'reapply' in s: return 'REPAIR'
 if 'backfill' in s or 'migrate' in s or 'migration' in s: return 'BACKFILL'
 if 'seed' in s or 'bootstrap' in s or 'default' in s or 'template' in s: return 'SEED'
 if 'validation' in s or 'validate' in s or 'readiness' in s: return 'VALIDATION'
 return 'OTHER'

def inventory():
 rows=[]
 for path in sorted(MIGRATIONS.glob('V*.sql')):
  text=path.read_text(encoding='utf-8')
  matches=[m for m in DML.finditer(text) if m.group(2).lower() in BUSINESS_TABLES]
  if not matches: continue
  meta=VERSION.match(path.name)
  category=classify(path.name,text)
  for m in matches:
   rows.append({
    'version':meta.group(1) if meta else '', 'file':path.name,
    'category':category,'operation':re.sub(r'\s+','_',m.group(1).upper()),
    'table':m.group(2).lower(),'line':text.count('\n',0,m.start())+1,
    'automatic_on_install':True,
    'p6_recommendation':'KEEP_SCHEMA_ONLY_AND_MOVE_BUSINESS_DATA_TO_EXPLICIT_MANIFEST'
   })
 return rows

def main():
 ap=argparse.ArgumentParser(); ap.add_argument('--json'); ap.add_argument('--csv'); ap.add_argument('--markdown')
 args=ap.parse_args(); rows=inventory(); bycat=Counter(r['category'] for r in rows); bytable=Counter(r['table'] for r in rows)
 report={'generatedFrom':str(MIGRATIONS.relative_to(ROOT)),'businessDataStatements':len(rows),'categoryCounts':dict(sorted(bycat.items())),'tableCounts':dict(sorted(bytable.items())),'statements':rows}
 payload=json.dumps(report,ensure_ascii=False,indent=2)+'\n'
 if args.json: Path(args.json).write_text(payload,encoding='utf-8')
 else: print(payload,end='')
 if args.csv:
  with Path(args.csv).open('w',newline='',encoding='utf-8') as f:
   w=csv.DictWriter(f,lineterminator='\n',fieldnames=list(rows[0]) if rows else ['version','file','category','operation','table','line','automatic_on_install','p6_recommendation']); w.writeheader(); w.writerows(rows)
 if args.markdown:
  lines=['# Historical Dispatch Business Migration Inventory','',f'- Business-data statements: **{len(rows)}**','', '## Categories','']
  lines += [f'- {k}: {v}' for k,v in sorted(bycat.items())]
  lines += ['', '## Affected tables','']+[f'- `{k}`: {v}' for k,v in sorted(bytable.items())]
  lines += ['', '## Files','', '| Version | File | Category | Operation | Table | Line |','|---|---|---|---|---|---:|']
  lines += [f"| {r['version']} | `{r['file']}` | {r['category']} | {r['operation']} | `{r['table']}` | {r['line']} |" for r in rows]
  Path(args.markdown).write_text('\n'.join(lines)+'\n',encoding='utf-8')
 return 0
if __name__=='__main__': raise SystemExit(main())
