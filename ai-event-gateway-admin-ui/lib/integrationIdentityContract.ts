export const integrationAdministrationSections = ['Provider Instance','Principals','Credentials','Project Mappings','Project Isolation','Permission Probes','Credential Federation','Credential Risk','Pending Sync','Failed Sync','Audit Timeline'] as const;
export const principalLifecycle = ['DRAFT','VALIDATING','ACTIVE','DEGRADED','EXPIRED','REVOKED','DISABLED','OVER_PRIVILEGED'] as const;
export const permissionProbeCapabilities = ['Authentication','Project Visible','Read Issue','Create Issue','Add Comment','Update Issue','Create Relation','Read Attachment Metadata','Read Attachment Content','Observe Status'] as const;
export const integrationIsolationModes = ['PER_PROJECT','PER_TRUST_ZONE','SHARED_SCOPED'] as const;
export const integrationOperationPrincipals = ['Read Principal','Create Principal','Comment Principal','Update Principal','Relation Principal','Webhook Principal'] as const;
export const integrationIdentityMessages = {
  overPrivileged: 'This Integration Principal has permissions beyond the selected Project Mapping. Production activation is blocked.',
  noSecret: 'OpenDispatch stores only a Secret reference and non-sensitive credential metadata.',
  noPrincipal: 'Select a scoped Principal before enabling this Project Mapping.',
  probeRequired: 'Run a Project Permission Probe after creating a Principal or rotating its credential.',
  blastRadius: 'Review mapped Projects and pending sync jobs before rotating or revoking this credential.',
  isolation: 'Each Principal can operate only the Projects or Trust Zone explicitly granted by its isolation scope.',
  noGlobalAdmin: 'Global administrator credentials are prohibited for production Project Mappings. Break-glass access requires an expiring security override.',
  rotation: 'A new credential becomes active only after its Permission Probe succeeds. The previous version then enters a limited grace period.',
  reprobe: 'A provider authorization failure automatically degrades the Mapping and starts a new Permission Probe.',
  relayReady: 'Source and target Projects may remain isolated. OpenDispatch verifies each side independently before starting a cross-project relay.'
} as const;
