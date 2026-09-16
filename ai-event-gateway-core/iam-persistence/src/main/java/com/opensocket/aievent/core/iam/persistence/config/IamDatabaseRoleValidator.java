package com.opensocket.aievent.core.iam.persistence.config;

import java.util.List;
import javax.sql.DataSource;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.jdbc.core.JdbcTemplate;

/** Startup fail-closed validation for runtime/migration role separation and RLS bypass. */
public final class IamDatabaseRoleValidator implements SmartInitializingSingleton {
    private static final String[] IAM_TABLES = {
        "iam_root_identities",
        "iam_users",
        "tenants",
        "departments",
        "organization_groups",
        "org_tenant_memberships",
        "org_department_closure",
        "org_department_revisions",
        "org_organization_snapshots",
        "org_department_memberships",
        "org_group_memberships",
        "iam_tenant_security_epochs"
    };

    private final JdbcTemplate jdbc;
    private final IamPersistenceProperties properties;

    public IamDatabaseRoleValidator(DataSource dataSource, IamPersistenceProperties properties) {
        this.jdbc = new JdbcTemplate(dataSource);
        this.properties = properties;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!properties.isRoleValidationEnabled()) {
            return;
        }

        String current = jdbc.queryForObject("select current_user", String.class);
        if (!properties.getExpectedRuntimeRole().equals(current)) {
            throw new IllegalStateException(
                    "IAM_RUNTIME_DB_ROLE_MISMATCH expected="
                            + properties.getExpectedRuntimeRole() + " actual=" + current
            );
        }

        Boolean unsafe = jdbc.queryForObject(
                "select rolsuper or rolcreatedb or rolcreaterole or rolreplication or rolbypassrls "
                        + "from pg_roles where rolname=current_user",
                Boolean.class
        );
        if (Boolean.TRUE.equals(unsafe)) {
            throw new IllegalStateException("IAM_RUNTIME_DB_ROLE_PRIVILEGE_FORBIDDEN");
        }

        Boolean canCreateInPublic = jdbc.queryForObject(
                "select has_schema_privilege(current_user, 'public', 'CREATE')",
                Boolean.class
        );
        if (Boolean.TRUE.equals(canCreateInPublic)) {
            throw new IllegalStateException("IAM_RUNTIME_DB_SCHEMA_CREATE_FORBIDDEN");
        }

        String tableList = String.join("','", IAM_TABLES);
        List<String> owned = jdbc.queryForList(
                "select c.relname from pg_class c join pg_roles r on r.oid=c.relowner "
                        + "where r.rolname=current_user and c.relname in ('" + tableList + "')",
                String.class
        );
        if (!owned.isEmpty()) {
            throw new IllegalStateException("IAM_RUNTIME_DB_ROLE_MUST_NOT_OWN_TABLES: " + owned);
        }
    }
}
