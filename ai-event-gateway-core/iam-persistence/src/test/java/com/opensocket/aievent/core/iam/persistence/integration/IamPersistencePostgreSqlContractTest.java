package com.opensocket.aievent.core.iam.persistence.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.opensocket.aievent.core.iam.rbac.domain.PermissionCode;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class IamPersistencePostgreSqlContractTest {
    private static final String RUNTIME_USER = "opendispatch_runtime_test";
    private static final String RUNTIME_PASSWORD = "runtime-test-password";

    @Container
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18-alpine")
            .withDatabaseName("opendispatch_phase1a2")
            .withUsername("migration_owner")
            .withPassword("migration-owner-password");

    @BeforeAll
    static void migrateAndCreateRuntimeRole() throws Exception {
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .cleanDisabled(false)
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .validateOnMigrate(true)
                .load()
                .migrate();

        try (Connection connection = ownerConnection(); Statement statement = connection.createStatement()) {
            statement.execute("create role " + RUNTIME_USER + " login password '" + RUNTIME_PASSWORD
                    + "' nosuperuser nocreatedb nocreaterole noinherit nobypassrls");
            statement.execute("grant usage on schema public to " + RUNTIME_USER);
            statement.execute("grant select, insert, update, delete on all tables in schema public to " + RUNTIME_USER);
            statement.execute("grant usage, select on all sequences in schema public to " + RUNTIME_USER);
        }
        createTenantWithoutContext("tenant-a");
        createTenantWithoutContext("tenant-b");
    }

    @Test
    void rootTenantChoiceProjectionUsesCanonicalDisplayNameColumn() throws Exception {
        try (Connection connection = ownerConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select tenant_id,tenant_code,display_name,status from tenants order by display_name,tenant_id");
                ResultSet resultSet = statement.executeQuery()) {
            int rows = 0;
            while (resultSet.next()) {
                assertThat(resultSet.getString("tenant_id")).isNotBlank();
                assertThat(resultSet.getString("tenant_code")).isNotBlank();
                assertThat(resultSet.getString("display_name")).isNotBlank();
                rows++;
            }
            assertThat(rows).isGreaterThanOrEqualTo(2);
        }
    }


    @Test
    void activePermissionCatalogUsesCanonicalPermissionCodeContract() throws Exception {
        try (Connection connection = ownerConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "select entry.permission_code from permission_catalog_revision_entries entry "
                                + "join permission_catalog_active_revision active "
                                + "on active.singleton_id='ACTIVE' and active.revision_id=entry.revision_id "
                                + "order by entry.permission_code");
                ResultSet resultSet = statement.executeQuery()) {
            int rows = 0;
            int longFormCodes = 0;
            int maximumSegments = 0;
            while (resultSet.next()) {
                String code = resultSet.getString("permission_code");
                assertThat(new PermissionCode(code).value()).isEqualTo(code);
                int segments = code.split("\\.", -1).length;
                maximumSegments = Math.max(maximumSegments, segments);
                if (segments > 6) {
                    longFormCodes++;
                }
                rows++;
            }
            assertThat(rows).isGreaterThanOrEqualTo(579);
            assertThat(longFormCodes).isGreaterThanOrEqualTo(104);
            assertThat(maximumSegments).isGreaterThanOrEqualTo(11);
        }
    }

    @Test
    void cleanAndUpgradeMigrationsCreateExpectedIamBoundary() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.tables where table_schema='public' "
                            + "and table_name in ('iam_users','org_tenant_memberships','org_department_closure',"
                            + "'org_department_revisions','org_organization_snapshots','iam_tenant_security_epochs')"))
                    .isEqualTo(6);
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname in ('departments','organization_groups',"
                            + "'org_tenant_memberships','org_department_closure','org_department_revisions',"
                            + "'org_organization_snapshots','org_department_memberships','org_group_memberships',"
                            + "'iam_tenant_security_epochs') and relrowsecurity and relforcerowsecurity"))
                    .isEqualTo(9);
        }
    }


    @Test
    void phase5bCatalogSchemaUsesRevisionOwnedDraftsAndRemovesActiveOnlyAliasTable() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.tables where table_schema='public' and table_name in "
                            + "('permission_catalog_revision_entries','permission_catalog_revision_aliases',"
                            + "'permission_catalog_publication_events')"))
                    .isEqualTo(3);
            assertThat(singleLong(connection,
                    "select case when to_regclass('public.permission_catalog_aliases') is null then 1 else 0 end"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_catalog_revision_entries where revision_id="
                            + "'00000000-0000-0000-0000-000000000001'::uuid"))
                    .isGreaterThan(80);
        }
    }

    @Test
    void phase5bDatabaseAllowsOnlyOneDraftRevision() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into permission_catalog_revisions(revision_id,revision_code,revision_number,"
                        + "status,content_hash,description,supersedes_revision_id,created_at,created_by,version) values ("
                        + "'00000000-0000-0000-0000-000000005010','TEST-5B-ONE-DRAFT',5010,'DRAFT',"
                        + "'DRAFT:UNPUBLISHED','test','00000000-0000-0000-0000-000000000002',now(),'test',1)");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("insert into permission_catalog_revisions(revision_id,revision_code,revision_number,"
                            + "status,content_hash,description,supersedes_revision_id,created_at,created_by,version) values ("
                            + "'00000000-0000-0000-0000-000000005011','TEST-5B-TWO-DRAFT',5011,'DRAFT',"
                            + "'DRAFT:UNPUBLISHED','test','00000000-0000-0000-0000-000000000002',now(),'test',1)");
                }
            }).isInstanceOf(SQLException.class);
            connection.rollback();
        }
    }

    @Test
    void phase5bDraftContentIsMutableButPublishedContentIsImmutable() throws Exception {
        String revisionId = "00000000-0000-0000-0000-000000005002";
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into permission_catalog_revisions(revision_id,revision_code,revision_number,"
                        + "status,content_hash,description,supersedes_revision_id,created_at,created_by,version) values ('"
                        + revisionId + "','TEST-5B-DRAFT',5002,'DRAFT','DRAFT:UNPUBLISHED','test',"
                        + "'00000000-0000-0000-0000-000000000001',now(),'test',1)");
                statement.executeUpdate("insert into permission_catalog_revision_entries(revision_id,permission_code,owner_module,"
                        + "resource_type,action_code,description,risk_level,risk_lane,lifecycle,allowed_scope_types,system_managed,"
                        + "introduced_at,updated_at,updated_by,version) values ('" + revisionId
                        + "','test.catalog.read','test','TEST','READ','Test','LOW','READ','ACTIVE',array['INSTANCE'],false,"
                        + "now(),now(),'test',1)");
                statement.executeUpdate("update permission_catalog_revision_entries set description='Changed',version=2 "
                        + "where revision_id='" + revisionId + "' and permission_code='test.catalog.read'");
                statement.executeUpdate("update permission_catalog_revisions set status='PUBLISHED',content_hash='sha256:test',"
                        + "published_at=now(),published_by='test',version=2 where revision_id='" + revisionId + "'");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update permission_catalog_revision_entries set description='Forbidden' "
                            + "where revision_id='" + revisionId + "' and permission_code='test.catalog.read'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("PERMISSION_CATALOG_REVISION_NOT_DRAFT");
            connection.rollback();
        }
    }

    @Test
    void phase5bActiveProjectionRejectsDirectWritesWithoutPublicationContext() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update permission_definitions set description=description "
                            + "where permission_code='task.read'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("PERMISSION_CATALOG_ACTIVE_PROJECTION_WRITE_FORBIDDEN");
            connection.rollback();
        }
    }

    @Test
    void phase5dCatalogAndMembershipSchemaUseSingleAuthorities() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from permission_catalog_revisions where revision_id="
                            + "'00000000-0000-0000-0000-000000000003'::uuid and status='PUBLISHED' "
                            + "and content_hash like 'sha256:%'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_definitions where permission_code in "
                            + "('identity.platform_user.read','identity.platform_user.create',"
                            + "'identity.platform_user.update','identity.platform_user.security',"
                            + "'identity.platform_admin.manage','identity.tenant_membership.read',"
                            + "'identity.tenant_membership.manage') and active and lifecycle='ACTIVE'"))
                    .isEqualTo(7);
            assertThat(singleLong(connection,
                    "select count(*) from permission_definitions where permission_code in "
                            + "('identity.user.disable','identity.user.unlock') and not active and lifecycle='RETIRED'"))
                    .isEqualTo(2);
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.columns where table_schema='public' "
                            + "and table_name='org_tenant_memberships' and column_name in "
                            + "('updated_at','updated_by','status_reason','default_tenant','membership_source',"
                            + "'invited_at','activated_at','suspended_at','expired_at','removed_at')"))
                    .isEqualTo(10);
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname='iam_tenant_membership_events' "
                            + "and relrowsecurity and relforcerowsecurity"))
                    .isEqualTo(1);
        }
    }


    @Test
    void phase5eCatalogAndRbacGovernanceUsePlatformTenantBoundaries() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleString(connection,
                    "select revision_id::text from permission_catalog_active_revision where singleton_id='ACTIVE'"))
                    .isEqualTo("00000000-0000-0000-0000-000000000004");
            assertThat(singleLong(connection,
                    "select count(*) from permission_catalog_revisions where revision_id="
                            + "'00000000-0000-0000-0000-000000000004'::uuid and status='PUBLISHED' "
                            + "and content_hash like 'sha256:%'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_definitions where permission_code in "
                            + "('identity.platform_role.read','identity.platform_role.manage',"
                            + "'identity.tenant_role.read','identity.tenant_role.manage',"
                            + "'identity.role_permission.manage','identity.role_binding.read',"
                            + "'identity.role_binding.manage','identity.role_access_review.manage') "
                            + "and active and lifecycle='ACTIVE'"))
                    .isEqualTo(8);
            assertThat(singleLong(connection,
                    "select count(*) from permission_definitions where permission_code in "
                            + "('identity.role.read','identity.role.manage') and not active and lifecycle='RETIRED'"))
                    .isEqualTo(2);
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.columns where table_schema='public' "
                            + "and table_name='rbac_roles' and column_name in "
                            + "('risk_level','review_required','next_review_at','last_reviewed_at')"))
                    .isEqualTo(4);
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.columns where table_schema='public' "
                            + "and table_name='rbac_principal_role_bindings' and column_name in "
                            + "('review_required','next_review_at','last_reviewed_at','last_reviewed_by')"))
                    .isEqualTo(4);
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname in "
                            + "('rbac_separation_of_duties_rules','rbac_administration_events') "
                            + "and relrowsecurity and relforcerowsecurity"))
                    .isEqualTo(2);
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.views where table_schema='public' "
                            + "and table_name='rbac_access_review_candidates'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void phase5gEntryPointInventoryUsesPostgreSqlAuthorityAndFailClosedGovernance() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "INSTANCE", "phase5g-contract");
            assertThat(singleString(connection,
                    "select revision_id::text from permission_catalog_active_revision where singleton_id='ACTIVE'"))
                    .isEqualTo("00000000-0000-0000-0000-000000000005");
            assertThat(singleLong(connection,
                    "select count(*) from permission_entry_point_inventory"))
                    .isGreaterThanOrEqualTo(700);
            assertThat(singleLong(connection,
                    "select count(*) from permission_entry_point_inventory where entry_point_type in "
                            + "('REST','COMMAND','QUERY','JOB','EVENT_CONSUMER','WEBHOOK','EXPORT','INTERNAL_API')"))
                    .isEqualTo(singleLong(connection, "select count(*) from permission_entry_point_inventory"));
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname in "
                            + "('permission_entry_point_inventory','permission_legacy_authority_mappings',"
                            + "'permission_entry_point_bypasses','permission_entry_point_events') and relforcerowsecurity"))
                    .isEqualTo(4);
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update permission_entry_point_inventory "
                            + "set target_permission_code='unknown.phase5g.permission' "
                            + "where entry_point_id=(select min(entry_point_id) from permission_entry_point_inventory)");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("ENTRY_POINT_UNKNOWN_PERMISSION");
            connection.rollback();
        }
    }

    @Test
    void phase5cRootInstallationEvidenceIsAppendOnlyAndRootCannotBeRemoved() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into iam_root_identities(root_identity_id,identity_type,status,reason,created_at,updated_at,updated_by,version) "
                        + "values ('root','INSTANCE_ROOT','BOOTSTRAP_PENDING',null,now(),now(),'test',1) on conflict do nothing");
                statement.executeUpdate("insert into auth_root_installation_events(event_id,event_type,root_identity_id,bootstrap_source,credential_version,correlation_id,details,occurred_at) "
                        + "values ('00000000-0000-0000-0000-000000005084','ROOT_INSTALLATION_RECOVERY_REHEARSED','root','RECOVERY',null,'test','{}'::jsonb,now())");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update auth_root_installation_events set details='{}'::jsonb where event_id='00000000-0000-0000-0000-000000005084'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("ROOT_INSTALLATION_EVENT_APPEND_ONLY");
            connection.rollback();
        }

        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into iam_root_identities(root_identity_id,identity_type,status,reason,created_at,updated_at,updated_by,version) "
                        + "values ('root','INSTANCE_ROOT','BOOTSTRAP_PENDING',null,now(),now(),'test',1) on conflict do nothing");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("delete from iam_root_identities where root_identity_id='root'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("LAST_PLATFORM_ROOT_CANNOT_BE_DELETED");
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update iam_root_identities set status='DISABLED' where root_identity_id='root'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("LAST_PLATFORM_ROOT_CANNOT_BE_DISABLED");
            connection.rollback();
        }
    }

    @Test
    void phase5cSchemaContainsInstallationLifecycleColumnsAndEvidenceTable() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.columns where table_schema='public' "
                            + "and table_name='auth_root_bootstrap_state' and column_name in "
                            + "('installation_completed_at','initial_password_changed_at','installation_credential_version','installation_correlation_id')"))
                    .isEqualTo(4);
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.tables where table_schema='public' "
                            + "and table_name='auth_root_installation_events'"))
                    .isEqualTo(1);
        }
    }

    @Test
    void instanceScopeTenantCreationInitializesRlsProtectedDefaultsAndEpoch() throws Exception {
        createTenantWithoutContext("tenant-c");
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "tenant-c", "user-c");
            assertThat(singleLong(connection,
                    "select count(*) from departments where tenant_id='tenant-c' and department_id='UNASSIGNED'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select security_epoch from iam_tenant_security_epochs where tenant_id='tenant-c'"))
                    .isZero();
            assertThat(singleLong(connection,
                    "select count(*) from auth_tenant_mfa_policies where tenant_id='tenant-c'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from auth_tenant_password_policies where tenant_id='tenant-c'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from auth_tenant_session_policies where tenant_id='tenant-c'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from token_tenant_policies where tenant_id='tenant-c'"))
                    .isEqualTo(1);
            connection.rollback();
        }
    }

    @Test
    void phase5hManifestCoverageIsActiveCompleteAndRuntimeAligned() throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "INSTANCE", "phase5h-test");
            assertThat(singleLong(connection,
                    "select count(*) from permission_catalog_revisions where revision_id='00000000-0000-0000-0000-000000000006'::uuid and status in ('PUBLISHED','SUPERSEDED') and content_hash like 'sha256:%'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_application_manifests where status='ACTIVE' "
                            + "and coverage_percent=100.0000 and uncovered_count=0 and entry_count>=800"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_application_manifest_readiness where status='ACTIVE' "
                            + "and runtime_drift_blockers=0 and matching_entries>=800"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_coverage_evidence where evidence_type in "
                            + "('BUILD_COVERAGE_CERTIFIED','MANIFEST_ACTIVATED') and coverage_percent=100.0000"))
                    .isGreaterThanOrEqualTo(1);
            connection.rollback();
        }
    }

    @Test
    void phase5hManifestTablesUseForceRlsAndEvidenceIsAppendOnly() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname in "
                            + "('permission_application_manifests','permission_application_manifest_entries','permission_coverage_evidence') "
                            + "and relrowsecurity and relforcerowsecurity"))
                    .isEqualTo(3);
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update permission_coverage_evidence set actor_id='tampered'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("PERMISSION_COVERAGE_EVIDENCE_APPEND_ONLY");
        }
    }

    @Test
    void phase5iShadowMismatchAndDomainReadinessAuthoritiesAreFailClosed() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from permission_catalog_revisions where revision_id='00000000-0000-0000-0000-000000000007'::uuid and status in ('PUBLISHED','SUPERSEDED') and content_hash like 'sha256:%'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from permission_definitions where permission_code in "
                            + "('permission.shadow.read','permission.shadow.manage','permission.mismatch.read',"
                            + "'permission.mismatch.manage','permission.mismatch.waive','permission.domain_readiness.read',"
                            + "'permission.domain_readiness.evaluate','permission.phase6_eligibility.read',"
                            + "'permission.phase6_eligibility.evaluate') and active and lifecycle='ACTIVE'"))
                    .isEqualTo(9);
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname in "
                            + "('permission_shadow_sampling_policies','permission_shadow_mismatch_cases',"
                            + "'permission_shadow_mismatch_waivers','permission_shadow_regression_evidence',"
                            + "'permission_shadow_case_events','permission_domain_readiness_thresholds',"
                            + "'permission_domain_readiness_evidence','permission_phase6_eligibility_evidence') "
                            + "and relrowsecurity and relforcerowsecurity"))
                    .isEqualTo(8);
            assertThat(singleLong(connection,
                    "select count(*) from information_schema.views where table_schema='public' "
                            + "and table_name='resource_shadow_decision_comparisons_v2'"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from pg_proc where proname in "
                            + "('phase5i_resolve_shadow_sampling','phase5i_evaluate_domain_readiness',"
                            + "'phase5i_evaluate_phase6_eligibility')"))
                    .isEqualTo(3);
        }
    }

    @Test
    void phase5iCriticalMismatchCannotBeWaivedAndEvidenceIsAppendOnly() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into permission_shadow_mismatch_cases(case_id,source_tenant_id,comparison_id,"
                        + "domain_code,permission_code,category,severity,status,owner_id,sla_due_at,title,first_seen_at,last_seen_at,"
                        + "created_at,created_by,updated_at,updated_by,version) values "
                        + "('00000000-0000-0000-0000-000000005901','tenant-a','phase5i-critical','TASK','task.read',"
                        + "'UNEXPECTED_ALLOW','CRITICAL','OPEN','owner',now()+interval '1 day','Critical mismatch',now(),now(),"
                        + "now(),'test',now(),'test',1)");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("insert into permission_shadow_mismatch_waivers(waiver_id,case_id,reason,approved_by,"
                            + "approved_at,expires_at,status,version) values "
                            + "('00000000-0000-0000-0000-000000005902','00000000-0000-0000-0000-000000005901',"
                            + "'Critical mismatch waiver must fail','test',now(),now()+interval '1 day','ACTIVE',1)");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("CRITICAL_MISMATCH_WAIVER_FORBIDDEN");
            connection.rollback();
        }
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into permission_domain_readiness_evidence("
                        + "evidence_id,source_tenant_id,domain_code,status,window_started_at,window_ended_at,sample_count,match_count,"
                        + "unexpected_allow_count,scope_widened_count,visibility_widened_count,target_error_count,context_incomplete_count,"
                        + "unexpected_deny_count,reason_different_count,open_blocking_cases,active_waivers,failed_regressions,"
                        + "manifest_coverage_percent,runtime_drift_blockers,entry_point_blockers,expired_bypasses,blockers,catalog_revision_id,"
                        + "manifest_id,actor_id,audit_reason,correlation_id,evaluated_at) values "
                        + "('00000000-0000-0000-0000-000000005903','tenant-a','TASK','NO_EVIDENCE',now()-interval '1 day',now(),"
                        + "0,0,0,0,0,0,0,0,0,0,0,0,100,0,0,0,'{}'::jsonb,"
                        + "'00000000-0000-0000-0000-000000000007',null,'test','Phase 5I append-only test','test',now())");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update permission_domain_readiness_evidence set actor_id='tampered' "
                            + "where evidence_id='00000000-0000-0000-0000-000000005903'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("PHASE5I_EVIDENCE_APPEND_ONLY");
            connection.rollback();
        }
    }

    @Test
    void phase5jObservationPipelineUsesThreeTierPartitionedStorageAndSingleCompatibilityView() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleString(connection,
                    "select revision_code from permission_catalog_revisions r join permission_catalog_active_revision a "
                            + "on a.revision_id=r.revision_id where a.singleton_id='ACTIVE'"))
                    .isEqualTo("PHASE5J-0.8.2");
            assertThat(singleLong(connection,
                    "select count(*) from permission_definitions where permission_code in "
                            + "('permission.pipeline.read','permission.pipeline.manage','permission.storage.read',"
                            + "'permission.storage.manage','permission.runtime_evidence.read',"
                            + "'permission.runtime_evidence.generate') and active and lifecycle='ACTIVE'"))
                    .isEqualTo(6);
            assertThat(singleLong(connection,
                    "select count(*) from pg_partitioned_table p join pg_class c on c.oid=p.partrelid "
                            + "where c.relname in ('permission_shadow_durable_mismatches',"
                            + "'permission_shadow_sampled_matches','permission_shadow_observation_dead_letters')"))
                    .isEqualTo(3);
            assertThat(singleLong(connection,
                    "select count(*) from pg_class where relname in "
                            + "('permission_shadow_observation_ingress','permission_shadow_observation_receipts',"
                            + "'permission_shadow_observation_metrics','permission_shadow_durable_mismatches',"
                            + "'permission_shadow_sampled_matches','permission_shadow_observation_dead_letters',"
                            + "'permission_shadow_pipeline_events','permission_shadow_partition_archives',"
                            + "'permission_shadow_retention_runs','permission_phase5_runtime_certification_evidence') "
                            + "and relrowsecurity and relforcerowsecurity"))
                    .isEqualTo(10);
            assertThat(singleLong(connection,
                    "select count(*) from pg_proc where proname in "
                            + "('phase5j_enqueue_shadow_observation','phase5j_process_shadow_observation_batch',"
                            + "'phase5j_ensure_shadow_partitions','phase5j_run_shadow_retention',"
                            + "'phase5j_generate_runtime_certification_evidence')"))
                    .isEqualTo(5);
        }
    }

    @Test
    void phase5jEnqueueIsIdempotentAndMismatchRemainsDurableWhenQueueIsFull() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "INSTANCE", "phase5j-test");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("update permission_shadow_pipeline_settings set queue_capacity=1000 where singleton_id='ACTIVE'");
            }
            String sql = "select phase5j_enqueue_shadow_observation('tenant-a','phase5j-test-comparison','TASK',null," +
                    "'TASK','task-1','task.read','DENY','TENANT:tenant-a','SUMMARY','LEGACY_DENY',true,null,null," +
                    "'ALLOW','TENANT:tenant-a','SUMMARY','TARGET_ALLOW',true,null,'target-decision'," +
                    "'UNEXPECTED_ALLOW','CRITICAL','phase5j-test',now())";
            assertThat(singleString(connection, sql)).isEqualTo("ENQUEUED");
            assertThat(singleString(connection, sql)).isEqualTo("DUPLICATE");
            assertThat(singleLong(connection,
                    "select count(*) from permission_shadow_observation_ingress "
                            + "where tenant_id='tenant-a' and comparison_id='phase5j-test-comparison'"))
                    .isEqualTo(1);
            connection.rollback();
        }
    }

    @Test
    void phase5jHighVolumePollingWorkloadDrainsWithoutDuplicateOrDeadLetter() throws Exception {
        try (Connection connection = ownerConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "INSTANCE", "phase5j-load-test");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("update permission_shadow_sampling_policies set match_sample_bps=10000 "
                        + "where risk_lane='READ'");
                statement.executeUpdate("update permission_shadow_pipeline_settings set queue_capacity=100000, "
                        + "batch_size=500 where singleton_id='ACTIVE'");
            }
            String enqueueSql = "select phase5j_enqueue_shadow_observation(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)";
            try (PreparedStatement statement = connection.prepareStatement(enqueueSql)) {
                for (int i = 0; i < 5000; i++) {
                    int n = 1;
                    statement.setString(n++, "tenant-a");
                    statement.setString(n++, "phase5j-poll-" + i);
                    statement.setString(n++, "TASK");
                    statement.setString(n++, "GET:/api/tasks/poll");
                    statement.setString(n++, "TASK");
                    statement.setString(n++, "task-" + i);
                    statement.setString(n++, "task.read");
                    statement.setString(n++, "ALLOW");
                    statement.setString(n++, "TENANT:tenant-a");
                    statement.setString(n++, "SUMMARY");
                    statement.setString(n++, "LEGACY_ALLOW");
                    statement.setBoolean(n++, true);
                    statement.setNull(n++, java.sql.Types.VARCHAR);
                    statement.setString(n++, "legacy-" + i);
                    statement.setString(n++, "ALLOW");
                    statement.setString(n++, "TENANT:tenant-a");
                    statement.setString(n++, "SUMMARY");
                    statement.setString(n++, "TARGET_ALLOW");
                    statement.setBoolean(n++, true);
                    statement.setNull(n++, java.sql.Types.VARCHAR);
                    statement.setString(n++, "target-" + i);
                    statement.setString(n++, "MATCH");
                    statement.setString(n++, "INFO");
                    statement.setString(n++, "phase5j-load-test");
                    statement.setTimestamp(n, new java.sql.Timestamp(System.currentTimeMillis()));
                    try (ResultSet result = statement.executeQuery()) {
                        result.next();
                        assertThat(result.getString(1)).isIn("ENQUEUED", "METRICS_ONLY");
                    }
                }
            }
            for (int i = 0; i < 20 && singleLong(connection,
                    "select count(*) from permission_shadow_observation_ingress") > 0; i++) {
                singleString(connection,
                        "select phase5j_process_shadow_observation_batch('phase5j-load-worker',500)::text");
            }
            assertThat(singleLong(connection,
                    "select count(*) from permission_shadow_observation_ingress")).isZero();
            assertThat(singleLong(connection,
                    "select count(*) from permission_shadow_observation_receipts "
                            + "where tenant_id='tenant-a' and comparison_id like 'phase5j-poll-%'"))
                    .isEqualTo(5000);
            assertThat(singleLong(connection,
                    "select count(*) from permission_shadow_observation_dead_letters "
                            + "where tenant_id='tenant-a' and comparison_id like 'phase5j-poll-%'"))
                    .isZero();
            connection.rollback();
        }
    }

    @Test
    void phase5jEvidenceAndPartitionsAreProtectedAgainstDirectMutation() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from pg_class child join pg_inherits i on i.inhrelid=child.oid "
                            + "where child.relname like 'permission_shadow_%_p________' "
                            + "and child.relrowsecurity and child.relforcerowsecurity"))
                    .isGreaterThanOrEqualTo(9);
            connection.setAutoCommit(false);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into permission_phase5_runtime_certification_evidence(" +
                        "evidence_id,status,source_version,catalog_revision_id,postgresql_clean_status," +
                        "postgresql_upgrade_status,application_context_status,admin_ui_build_status," +
                        "playwright_status,load_test_status,pipeline_status,evidence,actor_id,audit_reason) values(" +
                        "'00000000-0000-0000-0000-000000005970','INCOMPLETE','test'," +
                        "'00000000-0000-0000-0000-000000000008','NOT_EXECUTED','NOT_EXECUTED'," +
                        "'NOT_EXECUTED','NOT_EXECUTED','NOT_EXECUTED','NOT_EXECUTED','NOT_EXECUTED'," +
                        "'{}'::jsonb,'test','Phase 5J append-only contract test')");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update permission_phase5_runtime_certification_evidence " +
                            "set actor_id='tampered' where evidence_id='00000000-0000-0000-0000-000000005970'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("PHASE5J_EVIDENCE_APPEND_ONLY");
            connection.rollback();
        }
    }


    @Test
    void instanceContextPersistsNullTenantShadowEvidence() throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "INSTANCE", "root");
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into rbac_shadow_decisions(shadow_id,tenant_id,principal_id,permission_point,route,"
                            + "legacy_decision,new_decision,reason_code,lane,high_risk,metadata_json,occurred_at) "
                            + "values (?,null,'root','permission.entry_point.read','/api/admin/access/platform/permission-readiness/summary',"
                            + "'ALLOW','ALLOW','ALLOW','CRITICAL',false,'{}'::jsonb,now())")) {
                statement.setString(1, "instance-shadow-contract");
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            assertThat(singleLong(connection,
                    "select count(*) from rbac_shadow_decisions where shadow_id='instance-shadow-contract' and tenant_id is null"))
                    .isEqualTo(1);
            connection.rollback();
        }
    }

    @Test
    void instanceContextReadsPermissionReadinessAndActiveCatalog() throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "INSTANCE", "root");
            assertThat(singleLong(connection, "select count(*) from permission_entry_point_readiness"))
                    .isGreaterThan(0);
            assertThat(singleLong(connection,
                    "select count(*) from permission_catalog_revisions revision "
                            + "join permission_catalog_active_revision active on active.revision_id=revision.revision_id "
                            + "where active.singleton_id='ACTIVE'"))
                    .isEqualTo(1);
            connection.rollback();
        }
    }

    @Test
    void tenantOwnedQueryFailsWithoutTransactionLocalContext() throws Exception {
        try (Connection connection = runtimeConnection(); Statement statement = connection.createStatement()) {
            assertThatThrownBy(() -> statement.executeQuery("select count(*) from departments"))
                    .isInstanceOf(SQLException.class)
                    .hasMessageContaining("TENANT_CONTEXT_REQUIRED");
        }
    }

    @Test
    void rlsReturnsOnlyActiveTenantAndRejectsCrossTenantWrite() throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "tenant-a", "user-a");
            assertThat(singleLong(connection, "select count(*) from departments")).isEqualTo(1);
            assertThat(singleString(connection, "select min(tenant_id) from departments")).isEqualTo("tenant-a");
            assertThatThrownBy(() -> {
                try (PreparedStatement statement = connection.prepareStatement(
                        "insert into departments(tenant_id,department_id,department_code,department_name,status,"
                                + "display_order,updated_by,version) values ('tenant-b','escape','escape','Escape',"
                                + "'ACTIVE',0,'user-a',1)")) {
                    statement.executeUpdate();
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("row-level security");
            connection.rollback();
        }
    }

    @Test
    void runtimeRoleCannotBypassRlsAndDoesNotOwnTenantTables() throws Exception {
        try (Connection connection = ownerConnection()) {
            assertThat(singleLong(connection,
                    "select count(*) from pg_roles where rolname='" + RUNTIME_USER + "' and not rolbypassrls"))
                    .isEqualTo(1);
            assertThat(singleLong(connection,
                    "select count(*) from pg_class c join pg_roles r on r.oid=c.relowner "
                            + "where r.rolname='" + RUNTIME_USER + "' and c.relname in "
                            + "('departments','organization_groups','org_tenant_memberships')"))
                    .isZero();
            assertThat(singleLong(connection,
                    "select case when has_schema_privilege('" + RUNTIME_USER + "','public','CREATE') "
                            + "then 1 else 0 end"))
                    .isZero();
        }
    }

    @Test
    void hierarchyTriggerBuildsClosureAndRejectsCycle() throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "tenant-a", "user-a");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into departments(tenant_id,department_id,department_code,"
                        + "department_name,parent_department_id,status,display_order,updated_by,version) values "
                        + "('tenant-a','child','CHILD','Child','UNASSIGNED','ACTIVE',1,'user-a',1)");
            }
            assertThat(singleLong(connection,
                    "select count(*) from org_department_closure where tenant_id='tenant-a' "
                            + "and ancestor_department_id='UNASSIGNED' and descendant_department_id='child' and depth=1"))
                    .isEqualTo(1);
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("update departments set status='DISABLED', version=version+1 "
                        + "where tenant_id='tenant-a' and department_id='UNASSIGNED'");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("insert into departments(tenant_id,department_id,department_code,"
                            + "department_name,parent_department_id,status,display_order,updated_by,version) values "
                            + "('tenant-a','disabled-child','DISABLED-CHILD','Disabled Child','UNASSIGNED',"
                            + "'ACTIVE',2,'user-a',1)");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("DEPARTMENT_PARENT_DISABLED");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("update departments set status='ACTIVE', version=version+1 "
                        + "where tenant_id='tenant-a' and department_id='UNASSIGNED'");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update departments set parent_department_id='child' "
                            + "where tenant_id='tenant-a' and department_id='UNASSIGNED'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("DEPARTMENT_CYCLE_DETECTED");
            connection.rollback();
        }
    }

    @Test
    void organizationSnapshotIsImmutable() throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            setContext(connection, "tenant-a", "user-a");
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("insert into org_department_revisions(tenant_id,department_id,revision,"
                        + "department_code,department_name,ancestor_path_ids,ancestor_path_codes,ancestor_path_names,"
                        + "valid_from,change_type,changed_by,change_reason,created_at) values "
                        + "('tenant-a','UNASSIGNED',1,'UNASSIGNED','Unassigned Department',array['UNASSIGNED'],"
                        + "array['UNASSIGNED'],array['Unassigned Department'],now(),'CREATED','user-a','test',now())");
                statement.executeUpdate("insert into org_organization_snapshots(snapshot_id,tenant_id,department_id,"
                        + "department_revision,department_code,department_name,ancestor_path_ids,ancestor_path_codes,"
                        + "ancestor_path_names,group_ids,captured_at,content_hash) values "
                        + "('snapshot-a','tenant-a','UNASSIGNED',1,'UNASSIGNED','Unassigned Department',"
                        + "array['UNASSIGNED'],array['UNASSIGNED'],array['Unassigned Department'],array[]::varchar[],"
                        + "now(),'hash-a')");
            }
            assertThatThrownBy(() -> {
                try (Statement statement = connection.createStatement()) {
                    statement.executeUpdate("update org_organization_snapshots set department_name='Changed' "
                            + "where tenant_id='tenant-a' and snapshot_id='snapshot-a'");
                }
            }).isInstanceOf(SQLException.class).hasMessageContaining("ORGANIZATION_SNAPSHOT_IMMUTABLE");
            connection.rollback();
        }
    }

    private static void createTenantWithoutContext(String tenantId) throws Exception {
        try (Connection connection = runtimeConnection()) {
            connection.setAutoCommit(false);
            try (PreparedStatement statement = connection.prepareStatement(
                    "insert into tenants(tenant_id,tenant_code,display_name,status,metadata_json,default_timezone,"
                            + "default_locale,data_region,updated_by,version) values (?,?,?,'ACTIVE','{}'::jsonb,"
                            + "'UTC','en','GLOBAL','test-bootstrap',1)")) {
                statement.setString(1, tenantId);
                statement.setString(2, tenantId);
                statement.setString(3, tenantId);
                statement.executeUpdate();
            }
            connection.commit();
        }
    }

    private static void setContext(Connection connection, String tenantId, String actorId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "select set_config('app.current_tenant_id', ?, true), set_config('app.current_actor_id', ?, true)")) {
            statement.setString(1, tenantId);
            statement.setString(2, actorId);
            statement.execute();
        }
    }

    private static Connection ownerConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
    }

    private static Connection runtimeConnection() throws SQLException {
        return DriverManager.getConnection(POSTGRES.getJdbcUrl(), RUNTIME_USER, RUNTIME_PASSWORD);
    }

    private static long singleLong(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getLong(1);
        }
    }

    private static String singleString(Connection connection, String sql) throws SQLException {
        try (Statement statement = connection.createStatement(); ResultSet resultSet = statement.executeQuery(sql)) {
            resultSet.next();
            return resultSet.getString(1);
        }
    }
}
