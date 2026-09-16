# Legacy Access Management Consoles

These consoles are retained only as compatibility/history surfaces while the
Phase 7 Release Candidate is certified. Production application routes and
canonical Access Management components must not import from this directory.

Canonical surfaces:

- `people/` — Person lifecycle and effective access
- `organization/` — Department / Group / membership lifecycle
- `access/` — Responsibilities, assignments, approvals, reviews
- `security/` — sessions, MFA, security policy and audit
- `tenants/` — company/business-unit directory and overview
- `shared/` — common workspace UI

Remove the compatibility exports only after downstream references and archived
verification bundles no longer require the historical symbols.
