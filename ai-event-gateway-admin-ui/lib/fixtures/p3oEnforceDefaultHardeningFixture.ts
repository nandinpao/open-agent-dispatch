import { p3nFullEnforceAutomationFixture } from './p3nFullEnforceAutomationFixture';

export const p3oEnforceDefaultHardeningFixture = {
  id: 'P3O_ENFORCE_DEFAULT_HARDENING',
  releaseProfile: {
    devDefault: 'SHADOW',
    localDefault: 'SHADOW',
    prodDefault: 'WARN',
    controlledEnforceProfile: 'application-enforce.yml',
    enforceMode: 'ENFORCE',
    env: 'ROUTING_ELIGIBILITY_ENGINE_MODE',
  },
  mandatoryLiveGate: {
    script: 'scripts/p3o-release-artifact-gate.mjs',
    npmScript: 'release:verify:p3o-enforce-artifact',
    requiredMode: 'live',
    summaryArtifact: p3nFullEnforceAutomationFixture.artifacts.summaryReport,
    readinessArtifact: p3nFullEnforceAutomationFixture.artifacts.readinessReport,
    acceptanceArtifact: p3nFullEnforceAutomationFixture.artifacts.acceptanceReport,
  },
  archive: {
    command: 'npm run archive:p3o-release-artifacts',
    outputDirEnv: 'P3O_RELEASE_ARTIFACT_ARCHIVE_DIR',
    defaultOutputDir: '../.ci-output/release-artifacts/p3o-enforce',
    manifest: 'p3o-release-artifact-manifest.json',
    requiredArtifacts: Object.values(p3nFullEnforceAutomationFixture.artifacts),
  },
  enforceScoreBreakdownRequiredKeys: [
    'eligibilityEngineMode',
    'eligibilityV2Applied',
    'eligibilityV2CandidateEligible',
    'eligibilityV2BlockingReasons',
    'eligibilityV2ScoreBreakdown',
    'eligibilityV2Score',
  ],
  legacyRemoval: {
    disabledInEnforceProperty: 'routing.legacy-profile-eligibility-disabled-in-enforce',
    scoreBreakdownRequiredProperty: 'routing.require-v2-score-breakdown-in-enforce',
    readonlyReportView: 'p3o_legacy_readonly_report',
    removedFallback: 'ENFORCE mode skips legacy Dispatch Flow Agent Assignment not-configured gate and backend profile scoring.',
  },
  rollbackLiveRehearsal: {
    command: 'npm run rehearsal:p3o-rollback-live',
    artifact: 'p3o-rollback-live-rehearsal.json',
    from: 'ENFORCE',
    to: 'WARN',
  },
} as const;
