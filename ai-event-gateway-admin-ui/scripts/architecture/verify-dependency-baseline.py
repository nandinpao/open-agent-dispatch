#!/usr/bin/env python3
import sys
from dependency_model import repo_root, scan_dependencies, read_context_baseline, read_detail_baseline

root = repo_root()
edges, _, repository_details = scan_dependencies(root)
current_edges = set(edges)
allowed_edges = read_context_baseline(root / "architecture/baseline/m8-context-edges.csv")
current_repository_imports = set(repository_details)
allowed_repository_imports = read_detail_baseline(root / "architecture/baseline/m8-cross-context-repository-imports.csv")
new_edges = sorted(current_edges - allowed_edges)
new_repository_imports = sorted(current_repository_imports - allowed_repository_imports)
if new_edges or new_repository_imports:
    if new_edges:
        print("New cross-context dependency edges are not allowed after M8 baseline review:", file=sys.stderr)
        for source, target in new_edges:
            print(f"  {source} -> {target}", file=sys.stderr)
    if new_repository_imports:
        print("New cross-context Repository/DAO imports are not allowed after M8 baseline review:", file=sys.stderr)
        for dependency in new_repository_imports:
            print(f"  {dependency.source_file}: {dependency.source_context} -> {dependency.target_context} "
                  f"imports {dependency.imported_type}", file=sys.stderr)
    sys.exit(1)
print(f"M8 architecture baseline verified: {len(current_edges)} context edges and "
      f"{len(current_repository_imports)} cross-context Repository/DAO imports are allowed.")
