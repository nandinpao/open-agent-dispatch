#!/usr/bin/env python3
from dependency_model import repo_root, scan_dependencies, write_context_csv, write_detail_csv

root = repo_root()
edges, details, repository_details = scan_dependencies(root)
write_context_csv(root / "architecture/baseline/m8-context-edges.csv", edges)
write_detail_csv(root / "architecture/baseline/m8-cross-context-repository-imports.csv", repository_details)
write_detail_csv(root / "architecture/reports/m8-all-cross-context-imports.csv", details)
print(f"Generated M8 baseline: {len(edges)} context edges, {len(details)} cross-context imports, "
      f"and {len(repository_details)} cross-context Repository/DAO imports.")
