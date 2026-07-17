package com.opensocket.aievent.core;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

class MybatisXmlResourceSmokeTest {
    @Test
    void domainScopedDaoXmlResourcesArePackaged() throws Exception {
        Map<String, String> daos = Map.of(
                "agent/GatewayNodeDao.xml", "com.opensocket.aievent.database.persistence.agent.dao.GatewayNodeDao",
                "incident/IncidentDao.xml", "com.opensocket.aievent.database.persistence.incident.dao.IncidentDao",
                "task/TaskDao.xml", "com.opensocket.aievent.database.persistence.task.dao.TaskDao",
                "agent/AgentDirectoryDao.xml", "com.opensocket.aievent.database.persistence.agent.dao.AgentDirectoryDao",
                "execution/TaskCallbackDao.xml", "com.opensocket.aievent.database.persistence.execution.dao.TaskCallbackDao",
                "execution/DispatchRequestDao.xml", "com.opensocket.aievent.database.persistence.execution.dao.DispatchRequestDao",
                "adapter/AdapterActionDao.xml", "com.opensocket.aievent.database.persistence.adapter.dao.AdapterActionDao",
                "agent/AgentGovernanceDao.xml", "com.opensocket.aievent.database.persistence.agent.dao.AgentGovernanceDao");
        for (Map.Entry<String, String> entry : daos.entrySet()) {
            ClassPathResource resource = new ClassPathResource(
                    "mybatis/postgresql/" + entry.getKey());
            assertThat(resource.exists()).as(entry.getKey()).isTrue();
            String xml = StreamUtils.copyToString(
                    resource.getInputStream(), StandardCharsets.UTF_8);
            assertThat(xml).contains(entry.getValue());
        }
    }
}
