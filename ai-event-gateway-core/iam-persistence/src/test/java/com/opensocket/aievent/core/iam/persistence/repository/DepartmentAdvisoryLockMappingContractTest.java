package com.opensocket.aievent.core.iam.persistence.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class DepartmentAdvisoryLockMappingContractTest {
    @Test
    void advisoryLockReturnsNumericSentinelInsteadOfPostgreSqlVoid() throws Exception {
        try (var stream = getClass().getClassLoader().getResourceAsStream(
                "mybatis/postgresql/iam/IamTenantOrganizationDao.xml")) {
            assertThat(stream).isNotNull();
            String xml = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            assertThat(xml).contains("<select id=\"lockDepartmentHierarchy\" resultType=\"long\">");
            assertThat(xml).contains("select count(*)::bigint");
            assertThat(xml).contains("from (select pg_advisory_xact_lock");
            assertThat(xml).doesNotContain(
                    "<select id=\"lockDepartmentHierarchy\" resultType=\"long\">select pg_advisory_xact_lock");
        }
    }
}
