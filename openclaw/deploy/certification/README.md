# Certification Deployment Assets

This directory documents how infrastructure teams should collect external M8 evidence. Copy the JSON templates from `deploy/certification/evidence-examples`, replace template values with measured results, store artifact references in an access-controlled evidence repository, and run:

```bash
npm run certification:m8:evidence-check
```

Required evidence names:

- `docker-build.json`
- `live-soak.json`
- `cluster-failover.json`
- `credential-rotation.json`
- `rolling-upgrade.json`
- `security-verification.json`

Evidence must not contain credentials, raw prompts, customer issue text or raw Agent output.
