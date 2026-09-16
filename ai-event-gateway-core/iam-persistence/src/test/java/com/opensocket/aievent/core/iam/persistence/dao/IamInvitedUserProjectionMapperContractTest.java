package com.opensocket.aievent.core.iam.persistence.dao;

import static org.junit.jupiter.api.Assertions.*;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class IamInvitedUserProjectionMapperContractTest {
    @Test
    void tenantUserProjectionIncludesInvitedAndOtherNonRemovedMemberships() throws Exception {
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(
                "mybatis/postgresql/iam/IamApiRuntimeDao.xml")) {
            assertNotNull(input);
            String xml = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            int start = xml.indexOf("<select id=\"findTenantUser\"");
            int end = xml.indexOf("</select>", start);
            assertTrue(start >= 0 && end > start);
            String statement = xml.substring(start, end);
            assertTrue(statement.contains("m.status&lt;&gt;'REMOVED'"));
            assertFalse(statement.contains("m.status='ACTIVE'"));
            assertTrue(statement.contains("m.expires_at is null or m.expires_at&gt;now()"));
        }
    }
}
