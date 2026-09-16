# OpenDispatch Admin UI

The Admin UI is the presentation and administration plane for OpenDispatch. It exposes user journeys for source systems, dispatch flows, Agents, Tasks, A2A operations, IAM/security administration, resource access, observability, and platform administration. It does not define backend authorization or routing authority.

## Documentation

Current UI/UX and route/feature documentation is maintained in the root portal:

- [`../docs/06-uiux/`](../docs/06-uiux/index.html)
- [`../docs/05-workflows/`](../docs/05-workflows/index.html)
- [`../docs/generated/ui/route-catalog.html`](../docs/generated/ui/route-catalog.html)
- [`../docs/generated/security/permission-catalog.html`](../docs/generated/security/permission-catalog.html)

The module-local historical `docs/` tree was removed during the documentation cutover. Route and entitlement catalogs are generated from current source.

## Development

Use the package scripts in `package.json` for local UI checks. Repository-wide verification remains:

```bash
make verify
```
